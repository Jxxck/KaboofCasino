package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.game.blackjack.BlackjackDealerRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRig;
import de.tigges.kaboofcasino.game.blackjack.DealerIdleAnimation;
import de.tigges.kaboofcasino.game.blackjack.DealerPlayAnimation;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * A croupier's entities: the blackjack dealer's eight displays ({@link BlackjackDealerRig}, the model that wears the
 * mod's croupier profession texture) standing with their feet at the croupier's spot and looking along its facing, and
 * an Interaction with the villager's hit box (0.6 x 1.95) as its click box. All non-persistent, tagged
 * {@code kaboofcasino:part} with the croupier's id, rebuilt from the record when the chunk loads.
 *
 * <p>The dealer plays only the mod's idle loop ({@code DealerIdleAnimation} at {@code gameTime + phase}, the phase of
 * the croupier's block): every 8 ticks (4 during the loop's quick moves, {@link BlackjackRig#idleEvery}) with the pose
 * of {@code now + n + 1} and interpolation n + 1, only while a player is within {@value #IDLE_RANGE} blocks and the
 * shared idle budget ({@code lights.max-updates-per-tick}) grants it; a part whose transformation did not change is
 * not sent. Brightness = the brighter of the two blocks the body is in, refreshed every {@value #LIGHT_EVERY} ticks.
 * Thread: MAIN.
 */
final class CroupierRig {

    /** Culling box around the feet: the arms reach about 0.6 blocks, the head about 1.9. */
    static final float WIDTH = 1.6F;
    static final float HEIGHT = 2.2F;
    /** View range 1.0 (64 blocks) like the table dealers. */
    static final float RANGE = 1.0F;
    static final int IDLE_RANGE = 32;
    static final int LIGHT_EVERY = 40;
    /** The villager's hit box ({@code EntityType.VILLAGER}: 0.6 x 1.95). */
    static final float BOX_WIDTH = 0.6F;
    static final float BOX_HEIGHT = 1.95F;
    /**
     * Where {@link BlackjackDealerRig#transforms} puts the dealer's feet: {@code DealerPlacement}'s (0.5, -1.7875) from
     * a table's anchor, south frame (checked against the dealer's legs by the tests).
     */
    static final Vector3f DEALER_FEET = new Vector3f(0.5F, 0F, -1.7875F);

    private final UUID id;
    private final World world;
    private final CroupierPlacement.Stand stand;
    private final long phase;
    private final ItemDisplay[] parts = new ItemDisplay[BlackjackDealerRig.PARTS.length];
    private final Transformation[] sent = new Transformation[BlackjackDealerRig.PARTS.length];
    private Interaction box;
    private Display.Brightness light;
    private long lastIdle = Long.MIN_VALUE / 2;
    private long lastLight = Long.MIN_VALUE / 2;

    CroupierRig(UUID id, World world, CroupierPlacement.Stand stand) {
        this.id = id;
        this.world = world;
        this.stand = stand;
        this.phase = BlackjackDealerRig.phase(stand.feet().x(), stand.feet().y(), stand.feet().z());
    }

    /** Every model key the rig uses. */
    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < BlackjackDealerRig.PARTS.length; i++) {
            out.add(BlackjackDealerRig.model(i));
        }
        return out;
    }

    /** The dealer's idle pose at {@code now} (no gesture: the croupier plays no game). */
    static DealerIdleAnimation.Pose idlePose(double now, long phase) {
        return BlackjackDealerRig.pose(new DealerPlayAnimation.Input(), now, phase);
    }

    /** The eight part transformations relative to the feet (the dealer's placement minus its feet offset). */
    static Transformation[] transforms(Facing facing, DealerIdleAnimation.Pose pose) {
        Transformation[] t = BlackjackDealerRig.transforms(facing, pose);
        Vector3f feet = RigMath.facing(facing).transform(new Vector3f(DEALER_FEET));
        Transformation[] out = new Transformation[t.length];
        for (int i = 0; i < t.length; i++) {
            out[i] = new Transformation(new Vector3f(t[i].getTranslation()).sub(feet), t[i].getLeftRotation(),
                    t[i].getScale(), t[i].getRightRotation());
        }
        return out;
    }

    /** The feet (bottom centre of the stand block, plus the lift); yaw 0 as every rig entity. */
    Location feet() {
        return new Location(world, stand.feet().x() + 0.5, stand.feet().y() + stand.lift(), stand.feet().z() + 0.5);
    }

    /** The centre of the hit box (where the mod's burst and stream start: the villager's bounding-box centre). */
    Location centre() {
        return feet().add(0, BOX_HEIGHT / 2.0, 0);
    }

    CroupierPlacement.Stand stand() {
        return stand;
    }

    boolean spawned() {
        return parts[0] != null;
    }

    /** The displays at the idle pose of {@code now} (game time) and the click box. */
    void spawn(long now) {
        despawn();
        Location at = feet();
        light = light();
        lastLight = now;
        Transformation[] t = transforms(stand.facing(), idlePose(now, phase));
        for (int i = 0; i < parts.length; i++) {
            Display.Brightness b = light;
            parts[i] = Displays.item(world, at, id, Displays.model(BlackjackDealerRig.model(i)), t[i], RANGE, d -> {
                d.setDisplayWidth(WIDTH);
                d.setDisplayHeight(HEIGHT);
                d.setBrightness(b);
            });
            sent[i] = t[i];
        }
        lastIdle = now;
        box = world.spawn(at, Interaction.class, e -> {
            e.setPersistent(false);
            e.setInteractionWidth(BOX_WIDTH);
            e.setInteractionHeight(BOX_HEIGHT);
            e.setResponsive(true);
            e.getPersistentDataContainer().set(Keys.PART, PersistentDataType.STRING, id.toString());
        });
    }

    void despawn() {
        for (int i = 0; i < parts.length; i++) {
            Displays.remove(parts[i]);
            parts[i] = null;
            sent[i] = null;
        }
        Displays.remove(box);
        box = null;
    }

    /** True if any entity vanished (/kill, another plugin); the service then respawns the rig. */
    boolean broken() {
        if (box == null || !box.isValid()) {
            return true;
        }
        for (ItemDisplay d : parts) {
            if (d == null || !d.isValid()) {
                return true;
            }
        }
        return false;
    }

    boolean owns(Entity e) {
        return box != null && box.getUniqueId().equals(e.getUniqueId());
    }

    /**
     * Every server tick while loaded: the idle loop when due ({@code gameTime}: the world's game time, the mod's
     * renderer clock), light every {@value #LIGHT_EVERY} ticks.
     *
     * @param playerNear a player within {@value #IDLE_RANGE} blocks
     * @param grant      the shared idle budget, asked only when this croupier is due
     */
    void tick(long gameTime, boolean playerNear, BooleanSupplier grant) {
        if (!spawned()) {
            return;
        }
        if (gameTime - lastLight >= LIGHT_EVERY) {
            lastLight = gameTime;
            Display.Brightness b = light();
            if (!b.equals(light)) {
                light = b;
                for (ItemDisplay d : parts) {
                    d.setBrightness(b);
                }
            }
        }
        int every = BlackjackRig.idleEvery(gameTime, phase);
        if (gameTime - lastIdle < every || !playerNear || !grant.getAsBoolean()) {
            return;
        }
        lastIdle = gameTime;
        int duration = every + 1;
        Transformation[] t = transforms(stand.facing(), idlePose((double) (gameTime + duration), phase));
        for (int i = 0; i < parts.length; i++) {
            if (t[i].equals(sent[i])) {
                continue;
            }
            sent[i] = t[i];
            parts[i].setInterpolationDelay(0);
            parts[i].setInterpolationDuration(duration);
            parts[i].setTransformation(t[i]);
        }
    }

    /** The two blocks the body is in: the feet's block (the one above on a counter top) and the next one up. */
    static int[] lightBlocks(CroupierPlacement.Stand s) {
        int low = s.feet().y() + (int) Math.floor(s.lift() + 0.5);
        return new int[] {low, low + 1};
    }

    private Display.Brightness light() {
        int block = 0;
        int sky = 0;
        for (int y : lightBlocks(stand)) {
            Block b = world.getBlockAt(stand.feet().x(), y, stand.feet().z());
            block = Math.max(block, b.getLightFromBlocks());
            sky = Math.max(sky, b.getLightFromSky());
        }
        return new Display.Brightness(block, sky);
    }
}
