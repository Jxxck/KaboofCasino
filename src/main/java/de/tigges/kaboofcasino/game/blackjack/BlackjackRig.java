package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineRecord;
import de.tigges.kaboofcasino.visual.Coins;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.FloatingPanel;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;

/**
 * The blackjack table's entities ({@code BlackjackTableRenderer} and the table's block models). All item displays
 * stand at the anchor's bottom centre (light sampled there = the renderer's {@code lightCoords} of the block
 * entity), non-persistent, tagged; culling box 5.2 x 2.25 blocks, which holds the whole 4 x 2 table, the dealer
 * behind it (down to table z -8.4 px, up to 2 blocks) and every card position:
 * <ul>
 *   <li>table: 8 displays of the block models {@code roulettecasino:block/blackjack_0..7} (view range 2.0 like the
 *       blocks; felt, rim, the shoe and tray are in these models, the renderer draws nothing static);</li>
 *   <li>dealer: 8 displays (torso, head, upper arms, forearms, legs; view range 1.0 = the renderer's 64 blocks),
 *       forward kinematics from {@link BlackjackDealerRig}; brightness = the renderer's {@code dealerLight} (the
 *       maximum of the anchor's light and the two cells behind the table, refreshed every 40 ticks);</li>
 *   <li>cards: one display per card while {@code BlackjackCardMotion.pose} shows it (dealt + 7 until the clearing
 *       sweep ends), plus up to 7 shoe cards while the SHUFFLE gesture runs ({@code shuffleDeck}); view range 1.0;</li>
 *   <li>the floating panel ({@link FloatingPanel}, a text display) and the payout coins ({@link Coins}).</li>
 * </ul>
 * Cadence: moving cards every tick with the pose of {@code now + 1} and interpolation 1 (like the roulette ball: the
 * grab from the shoe turns a card by up to 55 degrees per tick, so the client's slerp needs tick-sized steps; a new
 * card appears with its pose of {@code now + 1}); dealer gestures (also the watch fade and its finger tap) every tick
 * with the pose of {@code now + 2} and interpolation 2 (no stall when a packet is late; for linear motion equal to
 * the true pose at every tick); the dealer's idle loop and the steady watch every 4 ticks with the pose of
 * {@code now + 5} and interpolation 5, only while a player is within 32 blocks (gestures: within 64, the view range).
 * A transformation equal to the last one sent is not sent again (cards at rest, the legs). Thread: MAIN.
 */
public final class BlackjackRig {

    static final float WIDTH = 5.2F;
    static final float HEIGHT = 2.25F;
    static final float BODY_RANGE = 2.0F;
    static final float RANGE = 1.0F;
    /** Panel: horizontally at table px (32, 12) like the mod; the text display grows upwards from 2.25 blocks, so the
     * full four-line panel is centred near the mod's 2.5. */
    static final double PANEL_X = 32.0;
    static final double PANEL_Z = 12.0;
    static final double PANEL_Y = 2.25;
    static final int CARD_DURATION = 1;
    static final int FAST_DURATION = 2;
    static final int IDLE_EVERY = 4;
    static final int IDLE_DURATION = 5;
    static final int IDLE_RANGE = 32;
    static final int GESTURE_RANGE = 64;
    static final int LIGHT_EVERY = 40;
    /** {@code BlackjackCardMotion.SHUFFLE_CARDS}. */
    static final int DECK = 7;
    /** Safety cap on card displays (a hand never comes near it). */
    static final int MAX_CARDS = 32;

    private final MachineRecord record;
    private final World world;
    private final Facing facing;
    private final long phase;
    private final List<ItemDisplay> body = new ArrayList<>();
    private final ItemDisplay[] dealer = new ItemDisplay[BlackjackDealerRig.PARTS.length];
    private final Transformation[] dealerSent = new Transformation[BlackjackDealerRig.PARTS.length];
    private final Map<CardKey, CardDisplay> cards = new LinkedHashMap<>();
    private final CardDisplay[] deck = new CardDisplay[DECK];
    private final FloatingPanel panel;
    private final Coins coins;
    private Display.Brightness dealerLight;
    private long lastDealer = Long.MIN_VALUE / 2;
    private long lastLight = Long.MIN_VALUE / 2;
    private BlackjackPanel.Content content;
    private BlackjackPanel.Totals shownTotals;
    private BlackjackPanel.Content shownContent;
    private BlackjackTableState lastView = new BlackjackTableState();
    private long lastNow;

    /** A dealt card's identity within a view. */
    private record CardKey(boolean toDealer, int index, long dealtAt) {
    }

    private static final class CardDisplay {
        final ItemDisplay display;
        int value;
        Transformation sent;

        CardDisplay(ItemDisplay display, int value, Transformation sent) {
            this.display = display;
            this.value = value;
            this.sent = sent;
        }
    }

    public BlackjackRig(MachineRecord record, World world) {
        this.record = record;
        this.world = world;
        this.facing = record.facing();
        this.phase = BlackjackDealerRig.phase(record.anchor().x(), record.anchor().y(), record.anchor().z());
        this.panel = new FloatingPanel(record.id());
        this.coins = new Coins(record.id());
    }

    /** Every model key the rig uses. */
    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int part = 0; part < 8; part++) {
            out.add(bodyModel(part));
        }
        for (int i = 0; i < BlackjackDealerRig.PARTS.length; i++) {
            out.add(BlackjackDealerRig.model(i));
        }
        for (int card = -1; card < 52; card++) {
            out.add(BlackjackCardRig.model(card));
        }
        return out;
    }

    static String bodyModel(int part) {
        return "roulettecasino:block/blackjack_" + part;
    }

    /** The anchor's bottom centre, where every item display stands. */
    private Location base() {
        return new Location(world, record.anchor().x() + 0.5, record.anchor().y(), record.anchor().z() + 0.5);
    }

    /** A table point (px, {@code BlackjackLayout} frame) in the world. */
    private Location at(double x, double y, double z) {
        double[] b = BlackjackLayout.toBlock(x, y, z, facing.turns());
        return new Location(world, record.anchor().x() + b[0], record.anchor().y() + b[1],
                record.anchor().z() + b[2]);
    }

    /** The table part p's model centre: its cell centre, (p % 4 - 1, 0.5, p / 4 - 1) blocks in the south frame. */
    static Transformation bodyTransform(Facing facing, int part) {
        return RigMath.part(facing, RigMath.point(16 * (part % 4) - 8, 8, 16 * (part / 4) - 8));
    }

    public boolean spawned() {
        return dealer[0] != null;
    }

    /** Table, dealer at the idle pose of the current game time; cards and panel follow with {@link #update}. */
    public void spawn() {
        despawn();
        Location base = base();
        for (int part = 0; part < 8; part++) {
            body.add(Displays.item(world, base, record.id(), Displays.model(bodyModel(part)),
                    bodyTransform(facing, part), BODY_RANGE, this::box));
        }
        long now = world.getGameTime();
        dealerLight = light();
        lastLight = now;
        Transformation[] t = BlackjackDealerRig.transforms(facing,
                BlackjackDealerRig.pose(new BlackjackTableState(), now, phase));
        for (int i = 0; i < dealer.length; i++) {
            dealer[i] = Displays.item(world, base, record.id(), Displays.model(BlackjackDealerRig.model(i)), t[i],
                    RANGE, d -> {
                        box(d);
                        d.setBrightness(dealerLight);
                    });
            dealerSent[i] = t[i];
        }
        lastDealer = now;
    }

    private void box(ItemDisplay d) {
        d.setDisplayWidth(WIDTH);
        d.setDisplayHeight(HEIGHT);
    }

    public void despawn() {
        body.forEach(Displays::remove);
        body.clear();
        for (int i = 0; i < dealer.length; i++) {
            Displays.remove(dealer[i]);
            dealer[i] = null;
            dealerSent[i] = null;
        }
        cards.values().forEach(c -> Displays.remove(c.display));
        cards.clear();
        for (int i = 0; i < DECK; i++) {
            if (deck[i] != null) {
                Displays.remove(deck[i].display);
                deck[i] = null;
            }
        }
        panel.hide();
        shownTotals = null;
        shownContent = null;
        coins.clear();
    }

    /** True if any rig entity vanished (/kill, another plugin); the controller then respawns the rig. */
    public boolean broken() {
        for (ItemDisplay d : body) {
            if (!d.isValid()) {
                return true;
            }
        }
        for (ItemDisplay d : dealer) {
            if (d != null && !d.isValid()) {
                return true;
            }
        }
        for (CardDisplay c : cards.values()) {
            if (!c.display.isValid()) {
                return true;
            }
        }
        for (CardDisplay c : deck) {
            if (c != null && !c.display.isValid()) {
                return true;
            }
        }
        return panel.broken();
    }

    /**
     * Every server tick while the chunk is loaded: {@code view} is the table's {@code BlackjackTableState} (a fresh
     * one when idle), {@code gameTime} the world's game time (the view's clock).
     */
    public void update(BlackjackTableState view, long gameTime) {
        BlackjackTableState v = view == null ? new BlackjackTableState() : view;
        lastView = v;
        lastNow = gameTime;
        if (!spawned()) {
            return;
        }
        updateCards(v, gameTime);
        updateDeck(v, gameTime);
        updateDealer(v, gameTime);
        updatePanel(v, gameTime);
    }

    // ---- cards ------------------------------------------------------------------------------------------------------

    private void updateCards(BlackjackTableState view, long now) {
        Set<CardKey> seen = new HashSet<>();
        BlackjackCardMotion.Pose pose = new BlackjackCardMotion.Pose();
        BlackjackCardMotion.Pose ahead = new BlackjackCardMotion.Pose();
        for (BlackjackCardMotion.Card card : view.cards) {
            if (!BlackjackCardMotion.pose(card, now, pose) || seen.size() >= MAX_CARDS) {
                continue;
            }
            CardKey key = new CardKey(card.toDealer, card.index, card.dealtAt);
            seen.add(key);
            Transformation t = BlackjackCardRig.transform(facing,
                    BlackjackCardMotion.pose(card, now + CARD_DURATION, ahead) ? ahead : pose);
            CardDisplay shown = cards.get(key);
            if (shown == null) {
                ItemDisplay d = Displays.item(world, base(), record.id(),
                        Displays.model(BlackjackCardRig.model(card.value)), t, RANGE, this::box);
                cards.put(key, new CardDisplay(d, card.value, t));
                continue;
            }
            if (shown.value != card.value) {
                shown.value = card.value;
                shown.display.setItemStack(Displays.model(BlackjackCardRig.model(card.value)));
            }
            send(shown, t);
        }
        for (Iterator<Map.Entry<CardKey, CardDisplay>> it = cards.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<CardKey, CardDisplay> e = it.next();
            if (!seen.contains(e.getKey())) {
                Displays.remove(e.getValue().display);
                it.remove();
            }
        }
    }

    /** The shoe cards of the SHUFFLE gesture ({@code BlackjackTableRenderer} 170-179). */
    private void updateDeck(BlackjackTableState view, long now) {
        List<BlackjackCardMotion.Pose> poses = new ArrayList<>();
        List<BlackjackCardMotion.Pose> ahead = new ArrayList<>();
        if (view.gesture == DealerPlayAnimation.Kind.SHUFFLE) {
            BlackjackCardMotion.shuffleDeck((double) (now - view.gestureAt), poses);
            BlackjackCardMotion.shuffleDeck((double) (now + CARD_DURATION - view.gestureAt), ahead);
        }
        for (int i = 0; i < DECK; i++) {
            boolean visible = i < poses.size() && poses.get(i).visible;
            if (!visible) {
                if (deck[i] != null) {
                    Displays.remove(deck[i].display);
                    deck[i] = null;
                }
                continue;
            }
            BlackjackCardMotion.Pose target = i < ahead.size() && ahead.get(i).visible ? ahead.get(i) : poses.get(i);
            Transformation t = BlackjackCardRig.transform(facing, target);
            if (deck[i] == null) {
                ItemDisplay d = Displays.item(world, base(), record.id(), Displays.model(BlackjackCardRig.model(-1)),
                        t, RANGE, this::box);
                deck[i] = new CardDisplay(d, -1, t);
            } else {
                send(deck[i], t);
            }
        }
    }

    private static void send(CardDisplay c, Transformation t) {
        if (t.equals(c.sent)) {
            return;
        }
        c.sent = t;
        c.display.setInterpolationDelay(0);
        c.display.setInterpolationDuration(CARD_DURATION);
        c.display.setTransformation(t);
    }

    // ---- dealer -----------------------------------------------------------------------------------------------------

    private void updateDealer(BlackjackTableState view, long now) {
        if (now - lastLight >= LIGHT_EVERY) {
            lastLight = now;
            Display.Brightness light = light();
            if (!light.equals(dealerLight)) {
                dealerLight = light;
                for (ItemDisplay d : dealer) {
                    d.setBrightness(light);
                }
            }
        }
        boolean fast = BlackjackDealerRig.fast(BlackjackDealerRig.input(view, now));
        int duration;
        if (fast) {
            if (!playerWithin(GESTURE_RANGE)) {
                return;
            }
            duration = FAST_DURATION;
        } else {
            if (now - lastDealer < IDLE_EVERY || !playerWithin(IDLE_RANGE)) {
                return;
            }
            duration = IDLE_DURATION;
        }
        lastDealer = now;
        Transformation[] t = BlackjackDealerRig.transforms(facing,
                BlackjackDealerRig.pose(view, (double) (now + duration), phase));
        for (int i = 0; i < dealer.length; i++) {
            if (t[i].equals(dealerSent[i])) {
                continue;
            }
            dealerSent[i] = t[i];
            dealer[i].setInterpolationDelay(0);
            dealer[i].setInterpolationDuration(duration);
            dealer[i].setTransformation(t[i]);
        }
    }

    /** {@code BlackjackTableRenderer} 72-78: the anchor's light, raised to the two cells behind the dealer's edge. */
    private Display.Brightness light() {
        int block = 0;
        int sky = 0;
        int[][] cells = {{0, 0}, {0, -2}, {1, -2}};
        for (int[] c : cells) {
            int x = c[0];
            int z = c[1];
            for (int i = 0; i < facing.turns(); i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            int bx = record.anchor().x() + x;
            int bz = record.anchor().z() + z;
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }
            Block b = world.getBlockAt(bx, record.anchor().y(), bz);
            block = Math.max(block, b.getLightFromBlocks());
            sky = Math.max(sky, b.getLightFromSky());
        }
        return new Display.Brightness(block, sky);
    }

    private boolean playerWithin(int range) {
        Location centre = at(PANEL_X, 16.0, PANEL_Z);
        double max = (double) range * range;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(centre) <= max) {
                return true;
            }
        }
        return false;
    }

    // ---- panel ------------------------------------------------------------------------------------------------------

    /**
     * Who plays and his stake; {@code null} hides the panel. The rig shows it while the view is active and fills in
     * the totals and the result from the view every tick ({@code extractPanel}); calling this only on change is
     * enough.
     */
    public void panel(BlackjackPanel.Content content) {
        this.content = content;
        if (spawned()) {
            updatePanel(lastView, lastNow);
        }
    }

    private void updatePanel(BlackjackTableState view, long now) {
        if (!BlackjackPanel.visible(content, view)) {
            if (shownContent != null) {
                panel.hide();
                shownContent = null;
                shownTotals = null;
            }
            return;
        }
        BlackjackPanel.Totals totals = BlackjackPanel.totals(view, now);
        if (content.equals(shownContent) && totals.equals(shownTotals) && !panel.broken()) {
            return;
        }
        Component text = BlackjackPanel.render(content, totals);
        panel.show(world, at(PANEL_X, 0.0, PANEL_Z).add(0.0, PANEL_Y, 0.0), text);
        shownContent = content;
        shownTotals = totals;
    }

    // ---- coins ------------------------------------------------------------------------------------------------------

    /** One payout coin from player spot {@code spot} ({@code eject}: spot centre, 15.5 px) towards {@code target}. */
    public void coin(Material item, int spot, Location target, long now) {
        double[] c = BlackjackLayout.spotCenter(Math.max(0, Math.min(BlackjackLayout.SPOTS - 1, spot)));
        coins.spawn(world, at(c[0], 15.5, c[1]), item, target, now);
    }

    public void tickCoins(long now) {
        coins.tick(now);
    }
}
