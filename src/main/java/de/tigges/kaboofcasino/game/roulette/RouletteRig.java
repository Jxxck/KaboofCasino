package de.tigges.kaboofcasino.game.roulette;

import de.tigges.kaboofcasino.machine.MachineRecord;
import de.tigges.kaboofcasino.visual.Coins;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.FloatingPanel;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

/**
 * The roulette table's entities ({@code RouletteBetRenderer}): the four body parts, the rotor (rotating about the
 * vertical axis through the table centre by {@code phase x 2 pi / 37}, counter-clockwise from above), the ball (on
 * the track around the same axis at {@code theta = (ballPhase + 0.5) x 2 pi / 37 - pi / 2} from +X towards +Z, radius
 * {@code 8 + 2.1 track} px, spinning {@code rotationXYZ(3 theta, 0, 2 theta)}; at rest {@code ballPhase = 2 frame},
 * which lies over pocket {@code 3 frame}), the floating bet panel and the payout coins. The table has no
 * facing; all entities stand at the origin block's bottom centre, non-persistent. Thread: MAIN.
 */
final class RouletteRig {

    /** The rotor axis and the ball's track centre: the table centre (2 x 2 blocks), px from the origin corner. */
    static final double CENTRE = 16;
    static final double PANEL_Y = 1.95;

    private final MachineRecord record;
    private final World world;
    private final List<ItemDisplay> parts = new ArrayList<>();
    private ItemDisplay rotor;
    private ItemDisplay ball;
    private double rotorPhase = Double.NaN;
    private final FloatingPanel panel;
    private final Coins coins;

    RouletteRig(MachineRecord record, World world) {
        this.record = record;
        this.world = world;
        this.panel = new FloatingPanel(record.id());
        this.coins = new Coins(record.id());
    }

    private Location base() {
        return new Location(world, record.anchor().x() + 0.5, record.anchor().y(), record.anchor().z() + 0.5);
    }

    boolean spawned() {
        return rotor != null;
    }

    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int part = 0; part < 4; part++) {
            out.add("roulettecasino:block/roulette_body_" + part);
        }
        out.add("kaboofcasino:roulette/rotor");
        out.add("kaboofcasino:roulette/ball");
        return out;
    }

    void spawn(int frame) {
        despawn();
        Location base = base();
        for (int part = 0; part < 4; part++) {
            parts.add(Displays.item(world, base, record.id(),
                    Displays.model("roulettecasino:block/roulette_body_" + part),
                    RigMath.part(record.facing(), RigMath.point(8 + 16 * (part & 1), 8, 8 + 16 * (part >> 1))), 2.0f,
                    d -> { }));
        }
        rotorPhase = frame;
        rotor = Displays.item(world, base, record.id(), Displays.model("kaboofcasino:roulette/rotor"),
                rotorTransform(frame), 2.0f, d -> { });
        parts.add(rotor);
        ball = Displays.item(world, base, record.id(), Displays.model("kaboofcasino:roulette/ball"),
                ballTransform(2.0 * frame, RouletteSpin.ballRadius(0), 12.8), 2.0f, d -> { });
        parts.add(ball);
        for (ItemDisplay d : parts) {
            d.setDisplayWidth(2.5f);
            d.setDisplayHeight(1.5f);
        }
    }

    boolean broken() {
        for (ItemDisplay d : parts) {
            if (!d.isValid()) {
                return true;
            }
        }
        return panel.broken();
    }

    void despawn() {
        parts.forEach(Displays::remove);
        parts.clear();
        rotor = null;
        ball = null;
        panel.hide();
        coins.clear();
    }

    /** The rotor at {@code phase} frames: rotation about Y through the table centre. */
    Transformation rotorTransform(double phase) {
        return RigMath.part(record.facing(), RigMath.point(CENTRE, 0, CENTRE),
                new Quaternionf().rotationY((float) (phase * RouletteSpin.ANGLE)));
    }

    void rotor(double phase, int duration) {
        if (rotor == null || Math.abs(phase - rotorPhase) < 1e-6) {
            return;
        }
        rotorPhase = phase;
        rotor.setInterpolationDelay(0);
        rotor.setInterpolationDuration(duration);
        rotor.setTransformation(rotorTransform(phase));
    }

    /** {@code RouletteBetRenderer}: the ball's angle from +X towards +Z for a ball phase (in frames of the track). */
    static double ballAngle(double ballPhase) {
        return (ballPhase + 0.5) * RouletteSpin.ANGLE - Math.PI / 2;
    }

    /** The ball's centre (px from the origin corner). */
    static double[] ballPoint(double ballPhase, double radius, double height) {
        double theta = ballAngle(ballPhase);
        return new double[] {CENTRE + radius * Math.cos(theta), height, CENTRE + radius * Math.sin(theta)};
    }

    Transformation ballTransform(double ballPhase, double radius, double height) {
        double[] p = ballPoint(ballPhase, radius, height);
        double theta = ballAngle(ballPhase);
        return RigMath.part(record.facing(), RigMath.point(p[0], p[1], p[2]),
                new Quaternionf().rotationXYZ((float) (3 * theta), 0f, (float) (2 * theta)));
    }

    /** The ball for {@code elapsed} of a spin; its own spin needs 1-tick updates (up to ~80 degrees per tick). */
    void ball(RouletteSpin.Plan plan, double elapsed, int duration) {
        if (ball == null) {
            return;
        }
        double track = plan.track(elapsed);
        ball.setInterpolationDelay(0);
        ball.setInterpolationDuration(duration);
        ball.setTransformation(ballTransform(plan.ballPhase(elapsed), RouletteSpin.ballRadius(track),
                plan.ballHeight(elapsed)));
    }

    void panel(Component text) {
        panel.show(world, new Location(world, record.anchor().x() + 1.0, record.anchor().y() + PANEL_Y,
                record.anchor().z() + 1.0), text);
    }

    void hidePanel() {
        panel.hide();
    }

    void coin(Material material, Location target, long now) {
        coins.spawn(world, new Location(world, record.anchor().x() + 1.0, record.anchor().y() + 1.0,
                record.anchor().z() + 1.0), material, target, now);
    }

    void tickCoins(long now) {
        coins.tick(now);
    }
}
