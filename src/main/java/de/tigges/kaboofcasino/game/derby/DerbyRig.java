package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.game.slot.IdleLights;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineRecord;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.RigMath;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongPredicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The duck derby's entities ({@code DuckDerbyRenderer} and the machine's block models). All item displays stand at
 * the anchor's bottom centre (their light is sampled there = the renderer's {@code lightCoords} of the block entity,
 * which the ducks and gates use), non-persistent, tagged, with a culling box of {@value #WIDTH} x {@value #HEIGHT}
 * blocks that holds the whole 3 x 4 machine, the sign and every duck pose in every facing ({@code DerbyGeometryTest}):
 * <ul>
 *   <li>tub: 12 displays of the block models {@code roulettecasino:block/duck_derby_0..11} at their cell centres
 *       {@code anchor + rotate(facing, p % 3 - 1, p / 3 - 3)}, turned like the blockstate's {@code "y": 90 * turns}
 *       (view range 2.0 like blocks; tub, rim, posts, the sign board and the flag are in these models);</li>
 *   <li>ducks: 4 displays ({@code kaboofcasino:derby/duck_<lane>}, the lane's UV offset baked in) at the renderer's
 *       {@code translate((LANE_CENTERS[lane] - 24, 13 + lift, z - 56) / 16)} turned about X by the pitch, from
 *       {@code plan.pose(lane, elapsed)} while {@code 0 <= elapsed <= endTick + 1}, else the rest pose z 56.5;</li>
 *   <li>gates: 1 display ({@code kaboofcasino:derby/gates}: the four bars) sunk by {@code 3.6 px x plan.gate}
 *       (the renderer draws the same quads four times with one sink);</li>
 *   <li>sign lights: 1 display ({@code kaboofcasino:derby/sign}: {@code signLights}' full-bright sign overlay and the
 *       12 bulbs), brightness 15 like the renderer's light {@code 15728880}, tints from {@link #bulbs};</li>
 *   <li>the floating panel: a text display ({@link DerbyPanel}) centred like the renderer's panel at machine
 *       (24, 2.6 blocks, 32) (a tall group panel is lifted as the renderer does), view range 10 blocks (the mod fades it
 *       between 10 and 7.5; a display has a hard cut).</li>
 * </ul>
 * 18 item displays + the panel = 19 entities (render.md 2.5: 21 with label and panel; machine labels are not built).
 *
 * <p>Cadence: the ducks and the gates are sent every tick with the pose of {@code now + 1} and interpolation 1 (exact
 * at every tick, linear between), only while a player's eyes are within {@value #VIEW_RANGE} blocks of the anchor
 * block's centre (the renderer's view distance; checked every {@value #NEAR_EVERY} ticks) and only what changed; a
 * machine at rest sends nothing and asks nobody, a race with nobody near costs one cached distance check. A rig that
 * missed motion while nobody was near catches up with the first update in range (ducks glide to the current pose in
 * one tick). The panel is rebuilt only when its content changes and only while a player is within
 * {@value #PANEL_NEAR} blocks (it shows to 10). The bulbs are sent when the controller calls {@link #bulbs} (its idle
 * cadence under the shared budget) and only if a grey changed. One clock: the world's game time (the renderer's
 * {@code level.getGameTime()}), for the poses, the panel and the spawn. Thread: MAIN.
 */
public final class DerbyRig {

    /** Culling box: |x|, |z| <= 3.6 blocks around the anchor's bottom centre (the machine reaches 3.5), 2 up (31 px). */
    static final float WIDTH = 7.2F;
    static final float HEIGHT = 2.0F;
    static final float BODY_RANGE = 2.0F;
    /** {@code DuckDerbyRenderer.getViewDistance}: 64 blocks. */
    static final float RANGE = 1.0F;
    static final int VIEW_RANGE = 64;
    /** {@code panelVisibility}: zero beyond 10 blocks. */
    static final float PANEL_RANGE = 10.0F / 64.0F;
    static final int PANEL_NEAR = 24;
    static final int NEAR_EVERY = 4;
    static final int DURATION = 1;
    /** {@code DuckDerbyBlock.PART} 0..11: 3 columns x 4 rows, part p at (p % 3 - 1, p / 3 - 3). */
    static final int PARTS = 12;
    static final int LANES = 4;
    /** {@code DuckDerbyRace.Pose} at rest ({@code extractRenderState} 98-102). */
    static final double REST_Z = 56.5;
    /** The renderer's duck pivot height (13 px + lift) and the gate's sink per unit ({@code GATE_SINK}). */
    static final double DUCK_Y = 13.0;
    static final double GATE_SINK = 3.6;
    /** Pivots (machine px) of the gate and sign models ({@code DerbyModels} in the pack tool). */
    static final double[] GATE_PIVOT = {24.0, 15.5, 51.5};
    static final double[] SIGN_PIVOT = {24.0, 24.0, 3.0};
    /** The panel: centre (24, y, 32) px, {@code PANEL_Y} 2.6 blocks; text 10 px per line at 0.025 x 0.44 blocks/px. */
    static final double PANEL_Y = 2.6;
    static final double PANEL_PX = 0.011;
    static final float PANEL_SCALE = 0.44F;
    /** {@code INSIDE} (-266188488): the panel's felt colour. */
    static final int PANEL_BACKGROUND = -266188488;
    static final String GATES = "kaboofcasino:derby/gates";
    static final String SIGN = "kaboofcasino:derby/sign";
    static final Display.Brightness FULL = new Display.Brightness(15, 15);

    private final MachineRecord record;
    private final World world;
    private final Facing facing;
    private final List<ItemDisplay> body = new ArrayList<>();
    private final ItemDisplay[] ducks = new ItemDisplay[LANES];
    private final Transformation[] duckSent = new Transformation[LANES];
    private ItemDisplay gates;
    private Transformation gateSent;
    private ItemDisplay sign;
    private int[] greySent;
    private float[] levels;
    private TextDisplay panel;
    private DerbyPanel.Content panelShown;
    private double panelY = Double.NaN;
    private DerbyView lastView = DerbyView.idle();
    private boolean atRest;
    private long nearAt = Long.MIN_VALUE;
    private double nearest = Double.POSITIVE_INFINITY;

    /**
     * @param idleGrant the shared idle budget ({@code lights.max-updates-per-tick}); the rig has no idle motion of its
     *                  own (the ducks rest at z 56.5, the gates at 0), so it never asks: the controller sends the sign
     *                  bulbs at its idle cadence under that budget through {@link #bulbs}
     */
    public DerbyRig(MachineRecord record, World world, LongPredicate idleGrant) {
        this.record = record;
        this.world = world;
        this.facing = record.facing();
    }

    // ---- pure geometry (DerbyGeometryTest) ------------------------------------------------------------------------

    /** Every model key the rig uses. */
    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int part = 0; part < PARTS; part++) {
            out.add(bodyModel(part));
        }
        for (int lane = 0; lane < LANES; lane++) {
            out.add(duckModel(lane));
        }
        out.add(GATES);
        out.add(SIGN);
        return out;
    }

    static String bodyModel(int part) {
        return "roulettecasino:block/duck_derby_" + part;
    }

    static String duckModel(int lane) {
        return "kaboofcasino:derby/duck_" + lane;
    }

    /** A machine point (px, south frame, {@code DuckDerbyLayout}: anchor cell centre (24, 56)) as a pivot in blocks. */
    static Vector3f point(double x, double y, double z) {
        return new Vector3f((float) ((x - 24.0) / 16.0), (float) (y / 16.0), (float) ((z - 56.0) / 16.0));
    }

    /** Tub part p's model centre: its cell centre, (16 (p % 3) + 8, 8, 16 (p / 3) + 8) px. */
    static Transformation bodyTransform(Facing facing, int part) {
        return RigMath.part(facing, point(16 * (part % 3) + 8, 8, 16 * (part / 3) + 8));
    }

    /** {@code submit} 183-201: translate to (lane centre, 13 + lift, z) px, then {@code rotationX(pitch)}. */
    static Transformation duckTransform(Facing facing, int lane, DuckDerbyRace.Pose p) {
        Quaternionf q = new Quaternionf();
        if (p.pitch != 0.0) {
            q.rotationX((float) p.pitch);
        }
        return RigMath.part(facing, point(DuckDerbyGeometry.LANE_CENTERS[lane], DUCK_Y + p.lift, p.z), q);
    }

    /** {@code submit} 168-181: every gate drawn {@code sink = (float) (gate * 3.6)} px lower. */
    static Transformation gateTransform(Facing facing, double gate) {
        float sink = (float) (gate * GATE_SINK);
        return RigMath.part(facing, point(GATE_PIVOT[0], GATE_PIVOT[1] - sink, GATE_PIVOT[2]));
    }

    static Transformation signTransform(Facing facing) {
        return RigMath.part(facing, point(SIGN_PIVOT[0], SIGN_PIVOT[1], SIGN_PIVOT[2]));
    }

    /** {@code extractRenderState} 86-103 at {@code gameTime}: the plan's pose inside the race window, else at rest. */
    static DuckDerbyRace.Pose pose(DerbyView view, int lane, double gameTime) {
        DuckDerbyRace.Pose p = new DuckDerbyRace.Pose();
        double elapsed = view == null ? -1.0 : view.elapsed(gameTime);
        if (elapsed >= 0.0) {
            view.plan().pose(lane, elapsed, p);
        } else {
            p.z = REST_Z;
            p.pitch = 0.0;
            p.lift = 0.0;
        }
        return p;
    }

    /** {@code state.gate} (a float in the render state). */
    static double gate(DerbyView view, double gameTime) {
        double elapsed = view == null ? -1.0 : view.elapsed(gameTime);
        return elapsed >= 0.0 ? (float) view.plan().gate(elapsed) : 0.0F;
    }

    /** The panel's horizontal centre relative to the anchor block's corner ({@code toBlock(24, 0, 32, turns)}). */
    static double[] panelCentre(Facing facing) {
        return DuckDerbyLayout.toBlock(24.0, 0.0, 32.0, facing.turns());
    }

    /**
     * The text display's foot (its text grows upwards from there, {@code lines x 10 - 1} px at 0.011 blocks/px) so that
     * the text is centred where the renderer centres its panel: 2.6 blocks for the solo panel, a group panel taller
     * than 70 px lifted by half the excess ({@code sharedPanel} 326).
     */
    static double panelFoot(DerbyPanel.Content content) {
        int px = content.lines() * 10 - 1;
        double centre = content instanceof DerbyPanel.Shared
                ? PANEL_Y + Math.max(0, px - 70) * PANEL_PX / 2.0 : PANEL_Y;
        return centre - px * PANEL_PX / 2.0;
    }

    /** {@code signLights}: {@code g = round(255 x level)} per bulb, level = 0.28 + 0.72 x the chase. */
    static int[] greys(float[] levels) {
        int[] g = new int[DuckDerbySignLights.BULBS];
        for (int i = 0; i < g.length; i++) {
            g[i] = (int) Math.round(255.0 * Math.clamp(levels[i], 0.0, 1.0));
        }
        return g;
    }

    /** The renderer's idle chase at {@code gameTime} (the levels before the controller's first {@link #bulbs}). */
    static float[] idleLevels(double gameTime, int x, int y, int z) {
        double t = IdleLights.clock(gameTime, x, y, z);
        float[] out = new float[DuckDerbySignLights.BULBS];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (0.28 + 0.72 * IdleLights.bulb(i, DuckDerbySignLights.BULBS, t));
        }
        return out;
    }

    private static ItemStack signItem(int[] greys) {
        ItemStack item = Displays.model(SIGN);
        CustomModelData.Builder data = CustomModelData.customModelData();
        for (int g : greys) {
            data.addColor(Color.fromRGB(g, g, g));
        }
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        return item;
    }

    // ---- entities -------------------------------------------------------------------------------------------------

    /** The anchor's bottom centre, where every item display stands. */
    private Location base() {
        return new Location(world, record.anchor().x() + 0.5, record.anchor().y(), record.anchor().z() + 0.5);
    }

    public boolean spawned() {
        return sign != null;
    }

    /** Tub, ducks and gates at the last view's pose of the current game time, the sign with its bulbs. */
    public void spawn() {
        despawn();
        Location base = base();
        long now = world.getGameTime();
        for (int part = 0; part < PARTS; part++) {
            body.add(Displays.item(world, base, record.id(), Displays.model(bodyModel(part)),
                    bodyTransform(facing, part), BODY_RANGE, this::box));
        }
        boolean rest = true;
        for (int lane = 0; lane < LANES; lane++) {
            DuckDerbyRace.Pose p = pose(lastView, lane, now);
            rest &= resting(p);
            Transformation t = duckTransform(facing, lane, p);
            ducks[lane] = Displays.item(world, base, record.id(), Displays.model(duckModel(lane)), t, RANGE,
                    this::box);
            duckSent[lane] = t;
        }
        double g = gate(lastView, now);
        rest &= g == 0.0;
        gateSent = gateTransform(facing, g);
        gates = Displays.item(world, base, record.id(), Displays.model(GATES), gateSent, RANGE, this::box);
        atRest = rest;
        if (levels == null) {
            levels = idleLevels(now, record.anchor().x(), record.anchor().y(), record.anchor().z());
        }
        greySent = greys(levels);
        sign = Displays.item(world, base, record.id(), signItem(greySent), signTransform(facing), RANGE, d -> {
            box(d);
            d.setBrightness(FULL);
        });
    }

    private void box(ItemDisplay d) {
        d.setDisplayWidth(WIDTH);
        d.setDisplayHeight(HEIGHT);
    }

    private static boolean resting(DuckDerbyRace.Pose p) {
        return p.z == REST_Z && p.pitch == 0.0 && p.lift == 0.0;
    }

    public void despawn() {
        body.forEach(Displays::remove);
        body.clear();
        for (int lane = 0; lane < LANES; lane++) {
            Displays.remove(ducks[lane]);
            ducks[lane] = null;
            duckSent[lane] = null;
        }
        Displays.remove(gates);
        gates = null;
        gateSent = null;
        Displays.remove(sign);
        sign = null;
        greySent = null;
        hidePanel();
        nearAt = Long.MIN_VALUE;
    }

    /** True if any rig entity vanished (/kill, another plugin); the controller then respawns the rig. */
    public boolean broken() {
        for (ItemDisplay d : body) {
            if (!d.isValid()) {
                return true;
            }
        }
        for (ItemDisplay d : ducks) {
            if (d != null && !d.isValid()) {
                return true;
            }
        }
        return (gates != null && !gates.isValid()) || (sign != null && !sign.isValid())
                || (panel != null && !panel.isValid());
    }

    /**
     * Every server tick while the chunk is loaded: {@code view} is what the block entity shows, {@code gameTime} the
     * world's game time (the view's clock).
     */
    public void update(DerbyView view, long gameTime) {
        DerbyView v = view == null ? DerbyView.idle() : view;
        lastView = v;
        if (!spawned()) {
            return;
        }
        boolean racing = v.elapsed((double) gameTime) >= 0.0 || v.elapsed((double) (gameTime + DURATION)) >= 0.0;
        if ((racing || !atRest) && nearest(gameTime) <= (double) VIEW_RANGE * VIEW_RANGE) {
            motion(v, gameTime);
        }
        updatePanel(v, gameTime);
    }

    /** Ducks and gates at the pose of {@code now + 1}, interpolated over one tick; unchanged ones are not resent. */
    private void motion(DerbyView view, long now) {
        double t = (double) (now + DURATION);
        boolean rest = true;
        for (int lane = 0; lane < LANES; lane++) {
            DuckDerbyRace.Pose p = pose(view, lane, t);
            rest &= resting(p);
            Transformation tr = duckTransform(facing, lane, p);
            if (!tr.equals(duckSent[lane])) {
                duckSent[lane] = tr;
                send(ducks[lane], tr);
            }
        }
        double g = gate(view, t);
        rest &= g == 0.0;
        Transformation tr = gateTransform(facing, g);
        if (!tr.equals(gateSent)) {
            gateSent = tr;
            send(gates, tr);
        }
        atRest = rest;
    }

    private static void send(ItemDisplay d, Transformation t) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(DURATION);
        d.setTransformation(t);
    }

    /**
     * The squared distance from the anchor block's centre to the nearest player's eyes in this world (the renderer's
     * camera rule), cached for {@value #NEAR_EVERY} ticks.
     */
    private double nearest(long now) {
        if (now - nearAt < NEAR_EVERY && now >= nearAt) {
            return nearest;
        }
        nearAt = now;
        Location centre = new Location(world, record.anchor().x() + 0.5, record.anchor().y() + 0.5,
                record.anchor().z() + 0.5);
        double best = Double.POSITIVE_INFINITY;
        for (Player p : world.getPlayers()) {
            best = Math.min(best, p.getEyeLocation().distanceSquared(centre));
        }
        nearest = best;
        return best;
    }

    // ---- bulbs ----------------------------------------------------------------------------------------------------

    /**
     * The 12 sign bulbs: {@code levels[i] = 0.28 + 0.72 x IdleLights.bulb(i, 12, lightTime)} (the renderer's
     * brightness, already including the 0.28 floor), sent as grey tints when one changed.
     */
    public void bulbs(float[] levels) {
        if (levels == null || levels.length < DuckDerbySignLights.BULBS) {
            return;
        }
        this.levels = Arrays.copyOf(levels, DuckDerbySignLights.BULBS);
        int[] g = greys(this.levels);
        if (sign == null || Arrays.equals(g, greySent)) {
            return;
        }
        greySent = g;
        sign.setItemStack(signItem(g));
    }

    // ---- panel ----------------------------------------------------------------------------------------------------

    private void updatePanel(DerbyView view, long now) {
        if (nearest(now) > (double) PANEL_NEAR * PANEL_NEAR) {
            return;
        }
        DerbyPanel.Content content = DerbyPanel.content(view, now);
        if (content == null) {
            hidePanel();
            return;
        }
        if (content.equals(panelShown) && panel != null && panel.isValid()) {
            return;
        }
        Component text = DerbyPanel.render(content);
        double foot = panelFoot(content);
        double[] c = panelCentre(facing);
        Location at = new Location(world, record.anchor().x() + c[0], record.anchor().y() + foot,
                record.anchor().z() + c[2]);
        if (panel == null || !panel.isValid()) {
            panel = Displays.text(world, at, record.id(), d -> {
                d.setViewRange(PANEL_RANGE);
                d.setBackgroundColor(Color.fromARGB(PANEL_BACKGROUND));
                d.setShadowed(true);
                d.setLineWidth(400);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(PANEL_SCALE, PANEL_SCALE, PANEL_SCALE), new Quaternionf()));
                d.setBrightness(FULL);
                d.text(text);
            });
        } else {
            panel.text(text);
            if (foot != panelY) {
                panel.teleport(at);
            }
        }
        panelShown = content;
        panelY = foot;
    }

    private void hidePanel() {
        Displays.remove(panel);
        panel = null;
        panelShown = null;
        panelY = Double.NaN;
    }
}
