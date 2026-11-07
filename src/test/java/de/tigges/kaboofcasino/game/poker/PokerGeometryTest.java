package de.tigges.kaboofcasino.game.poker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardGeometry;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardMotion;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackDealerRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackLayout;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackShoe;
import de.tigges.kaboofcasino.game.blackjack.DealerIdleAnimation;
import de.tigges.kaboofcasino.game.blackjack.DealerPlayAnimation;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.pack.PackFiles;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.visual.RigMath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The poker rig against an independent evaluation of {@code PokerTableRenderer} and the table's blockstate: the twelve
 * block models (placed by the rig's transformations) land where the blockstate puts them ({@code "y": 90 * turns} in
 * the cell {@code rotatedOffset(p % 4 - 1, p / 4 - 1)}); the dealer's parts on the mod's {@code ModelPart} cubes under
 * {@code DealerPlacement} with the renderer's pose (idle at {@code time + phase}, then
 * {@code PokerTableState.dealerInput}); the cards on {@code BlackjackCardGeometry.quads} of
 * {@code BlackjackCardMotion.pose} at the poker slots (typed from the mod source) at {@code BlackjackLayout.toBlock}.
 * Whole hands run through the real {@link PokerRound} with the view as listener (call, fold, timeout; all three
 * seats; all four facings). World positions are blocks from the anchor block's corner, like the mod's block-entity
 * pose.
 */
class PokerGeometryTest {

    private static final double EPS = 2e-4;
    /** A world far from 0 with an anchor whose idle phase is not 0. */
    private static final long START = 33_389_712L;
    private static final int[] ANCHOR = {-1234, 70, 5678};

    // ---- the mod, typed independently ------------------------------------------------------------------------------

    /** {@code BlackjackDealerModel.createLayer} 39-70: per display part its cubes {x, y, z, w, h, d, grow x/y/z} and
     * the extra translation of a non-rotating child (the nose: (0, -2, 0) inside the head). */
    private static final float[][][] CUBES = {
        {{-4, -12, -3, 8, 12, 6, 0, 0, 0, 0}, {-4, -12, -3, 8, 20, 6, 0.5F, 0.5F, 0.5F, 0}},
        {{-4, -10, -4, 8, 10, 8, 0, 0, 0, 0}, {-1, -1, -6, 2, 4, 2, 0, 0, 0, -2}},
        {{-2, -2, -2, 4, 6, 4, 0, 0, 0, 0}},
        {{-2, 0, -2, 4, 6, 4, -0.04F, 0, 0, 0}},
        {{-2, -2, -2, 4, 6, 4, 0, 0, 0, 0}},
        {{-2, 0, -2, 4, 6, 4, -0.04F, 0, 0, 0}},
        {{-2, 0, -2, 4, 12, 4, 0, 0, 0, 0}},
        {{-2, 0, -2, 4, 12, 4, 0, 0, 0, 0}},
    };

    /** A PoseStack entry in double precision: {@code p -> m * p + t} (row-major 3x3 plus translation). */
    private static final class Pose {
        final double[] m = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        final double[] t = {0, 0, 0};

        void translate(double x, double y, double z) {
            t[0] += m[0] * x + m[1] * y + m[2] * z;
            t[1] += m[3] * x + m[4] * y + m[5] * z;
            t[2] += m[6] * x + m[7] * y + m[8] * z;
        }

        void mul(double[] r) {
            double[] o = new double[9];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    o[3 * i + j] = m[3 * i] * r[j] + m[3 * i + 1] * r[3 + j] + m[3 * i + 2] * r[6 + j];
                }
            }
            System.arraycopy(o, 0, m, 0, 9);
        }

        void scale(double x, double y, double z) {
            mul(new double[] {x, 0, 0, 0, y, 0, 0, 0, z});
        }

        void rotateX(double a) {
            mul(new double[] {1, 0, 0, 0, Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a)});
        }

        void rotateY(double a) {
            mul(new double[] {Math.cos(a), 0, Math.sin(a), 0, 1, 0, -Math.sin(a), 0, Math.cos(a)});
        }

        void rotateZ(double a) {
            mul(new double[] {Math.cos(a), -Math.sin(a), 0, Math.sin(a), Math.cos(a), 0, 0, 0, 1});
        }

        Vector3f apply(double x, double y, double z) {
            return new Vector3f((float) (m[0] * x + m[1] * y + m[2] * z + t[0]),
                    (float) (m[3] * x + m[4] * y + m[5] * z + t[1]), (float) (m[6] * x + m[7] * y + m[8] * z + t[2]));
        }
    }

    /** {@code ModelPart.translateAndRotate}: translate by the offset / 16, then {@code rotationZYX} = Rz Ry Rx. */
    private static void part(Pose m, float x, float y, float z, float zRot, float yRot, float xRot) {
        m.translate(x / 16.0, y / 16.0, z / 16.0);
        m.rotateZ(zRot);
        m.rotateY(yRot);
        m.rotateX(xRot);
    }

    /** {@code DealerPlacement.apply} (baseline (0.5, -1.7875), the renderer's first transform) then display part i. */
    private static Pose modPose(Facing facing, DealerIdleAnimation.Pose p, int i) {
        float x = 0.5F;
        float z = -1.7875F;
        for (int k = 0; k < facing.turns(); k++) {
            float nx = -z;
            z = x;
            x = nx;
        }
        Pose m = new Pose();
        m.translate(0.5F + x, 0F, 0.5F + z);
        m.rotateY(Math.toRadians(180.0F - facing.turns() * 90.0F));
        m.scale(0.9375F, 0.9375F, 0.9375F);
        m.scale(-1F, -1F, 1F);
        m.translate(0F, -1.501F, 0F);
        if (i >= 6) {
            int side = i - 6;
            part(m, (side == 0 ? -2F : 2F) + p.legX[side], 12F, 0F, p.legZRot[side], 0F, 0F);
            return m;
        }
        part(m, p.torsoX, 12F + p.torsoY, 0F, p.torsoZRot, p.torsoYRot, p.torsoXRot);
        if (i == 1) {
            part(m, 0F, -12F, 0F, p.headZRot, p.headYRot, p.headXRot);
        } else if (i >= 2) {
            int side = (i - 2) / 2;
            part(m, side == 0 ? -6F : 6F, -10F, 0F, p.shoulderZRot[side], p.shoulderYRot[side],
                    p.shoulderXRot[side]);
            if ((i - 2) % 2 == 1) {
                part(m, 0F, 4F, 0F, 0F, 0F, p.elbowXRot[side]);
            }
        }
        return m;
    }

    private static List<Vector3f> modCorners(Facing facing, DealerIdleAnimation.Pose p, int i, int cube) {
        float[] c = CUBES[i][cube];
        Pose m = modPose(facing, p, i);
        if (c[9] != 0) {
            m.translate(0F, c[9] / 16.0, 0F);
        }
        List<Vector3f> out = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            float x = (k & 1) == 0 ? c[0] - c[6] : c[0] + c[3] + c[6];
            float y = (k & 2) == 0 ? c[1] - c[7] : c[1] + c[4] + c[7];
            float z = (k & 4) == 0 ? c[2] - c[8] : c[2] + c[5] + c[8];
            out.add(m.apply(x / 16.0, y / 16.0, z / 16.0));
        }
        return out;
    }

    /** {@code PokerTableRenderer.extractRenderState} 70. */
    private static long modPhase() {
        return Math.floorMod((long) ANCHOR[0] * 31L + (long) ANCHOR[1] * 7L + (long) ANCHOR[2] * 17L, 480L);
    }

    /** {@code BlackjackDealerModel.setupAnim} with the renderer's state at an integer game tick (partialTick 0). */
    private static DealerIdleAnimation.Pose modDealerPose(PokerTableState view, long time) {
        DealerIdleAnimation.Pose p = new DealerIdleAnimation.Pose();
        DealerIdleAnimation.evaluate((float) time + 0F + (float) modPhase(), p);
        DealerPlayAnimation.Input in = new DealerPlayAnimation.Input();
        view.dealerInput((double) time + 0.0, in);
        DealerPlayAnimation.apply(in, p);
        return p;
    }

    /** {@code PokerLayout} 23-41 typed from the mod source: {x, y, z, yaw} of a card's slot (table px). */
    private static double[] modSlot(boolean toDealer, int seat, int index) {
        if (toDealer) {
            if (index < 2) {
                return new double[] {index == 0 ? 28.2 : 35.8, 13.8, 9.4, -Math.PI / 2};
            }
            return new double[] {18.0 + 7.0 * Math.min(index - 2, 4), 13.8, 16.8, -Math.PI / 2};
        }
        double[] seatX = {17.0, 32.0, 47.0};
        return new double[] {seatX[seat] + (index == 0 ? -2.9 : 2.9), 13.8, 24.8, -Math.PI / 2};
    }

    /** {@code BlackjackLayout.toBlock} typed: table px to blocks from the anchor's corner, turned with the facing. */
    private static Vector3f toBlock(double x, double y, double z, Facing facing) {
        double dx = x - 24.0;
        double dz = z - 24.0;
        for (int i = 0; i < facing.turns(); i++) {
            double nx = -dz;
            dz = dx;
            dx = nx;
        }
        return new Vector3f((float) (0.5 + dx / 16.0), (float) (y / 16.0), (float) (0.5 + dz / 16.0));
    }

    /**
     * The blockstate's placement of part p's model point (px): cell {@code rotatedOffset(facing, p % 4 - 1, p / 4 - 1)}
     * ({@code PokerTableBlock} 62-64, {@code BlackjackTableBlock} 62-73) and the model turned by {@code "y": 90 *
     * turns} about the block centre ({@code BlockModelRotation}: Ry(-y degrees), (dx, dz) -> (-dz, dx) per turn).
     */
    private static Vector3f modBlockPoint(Facing facing, int part, double mx, double my, double mz) {
        int u = part % 4 - 1;
        int v = part / 4 - 1;
        double dx = mx / 16.0 - 0.5;
        double dz = mz / 16.0 - 0.5;
        for (int i = 0; i < facing.turns(); i++) {
            int nu = -v;
            v = u;
            u = nu;
            double nx = -dz;
            dz = dx;
            dx = nx;
        }
        return new Vector3f((float) (u + 0.5 + dx), (float) (my / 16.0), (float) (v + 0.5 + dz));
    }

    // ---- the rig + the built pack ------------------------------------------------------------------------------------

    private static final java.util.Map<String, JsonObject> MODELS = new java.util.concurrent.ConcurrentHashMap<>();

    private static JsonObject model(String key) {
        return MODELS.computeIfAbsent(key, k -> {
            String ns = k.substring(0, k.indexOf(':'));
            String path = k.substring(k.indexOf(':') + 1);
            byte[] json = PackFiles.all().get("assets/" + ns + "/models/" + path + ".json");
            assertNotNull(json, k);
            return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        });
    }

    private static double[] vec(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** Where model point m ends up, from the anchor's corner (the rig stands at the anchor's bottom centre). */
    private static Vector3f world(Transformation t, double x, double y, double z) {
        return RigMath.apply(t, x, y, z).add(0.5F, 0F, 0.5F);
    }

    /** An element's 8 box corners (px), with its own rotation applied when {@code rotated}. */
    private static List<double[]> corners(JsonObject element, boolean rotated) {
        double[] f = vec(element.getAsJsonArray("from"));
        double[] to = vec(element.getAsJsonArray("to"));
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            double[] p = {(k & 1) == 0 ? f[0] : to[0], (k & 2) == 0 ? f[1] : to[1], (k & 4) == 0 ? f[2] : to[2]};
            if (rotated && element.has("rotation")) {
                JsonObject r = element.getAsJsonObject("rotation");
                double a = Math.toRadians(r.get("angle").getAsDouble());
                double[] o = vec(r.getAsJsonArray("origin"));
                int[] axes = switch (r.get("axis").getAsString()) {
                    case "x" -> new int[] {1, 2};
                    case "y" -> new int[] {2, 0};
                    default -> new int[] {0, 1};
                };
                double s = p[axes[0]] - o[axes[0]];
                double t = p[axes[1]] - o[axes[1]];
                p[axes[0]] = o[axes[0]] + s * Math.cos(a) - t * Math.sin(a);
                p[axes[1]] = o[axes[1]] + s * Math.sin(a) + t * Math.cos(a);
            }
            out.add(p);
        }
        return out;
    }

    private static List<Vector3f> rigCorners(Transformation t, JsonObject element) {
        List<Vector3f> out = new ArrayList<>();
        for (double[] p : corners(element, false)) {
            out.add(world(t, p[0], p[1], p[2]));
        }
        return out;
    }

    private static void assertSameSet(List<Vector3f> want, List<Vector3f> got, String what) {
        assertEquals(want.size(), got.size(), what);
        for (Vector3f w : want) {
            boolean found = false;
            for (Vector3f g : got) {
                found |= g.distance(w) < EPS;
            }
            assertTrue(found, what + ": " + w + " not in " + got);
        }
    }

    // ---- whole hands through the real round --------------------------------------------------------------------------

    /** What a hand does at the decision. */
    enum Play { CALL, FOLD, TIMEOUT }

    @FunctionalInterface
    private interface Tick {
        void at(PokerTableState view, long now);
    }

    /**
     * One hand from DEAL (the block entity: {@code new PokerTableState(gameTime, seat)}, DEALING at 0, the round on a
     * one-deck shoe, {@code rigged} cards drawn first in the order player, dealer, player, dealer, flop, turn, river)
     * until 40 ticks after DONE (the clear gesture outlasts the round); {@code each} sees the view as the renderer would
     * at every game tick.
     */
    static PokerTableState play(int seat, Play play, int decideAfter, long seed, Tick each, int... rigged) {
        PokerTableState view = new PokerTableState(START, seat);
        view.phaseChanged(PokerRound.Phase.DEALING, 0);
        PokerRound round = new PokerRound(BlackjackShoe.riggedDecks(new Random(seed), 1, rigged), view);
        long now = START;
        if (each != null) {
            each.at(view, now);
        }
        int opened = -1;
        int after = 0;
        while (after < 40) {
            if (round.isOver()) {
                after++;
            }
            round.tick();
            now++;
            if (round.canCall()) {
                opened = opened < 0 ? round.now() : opened;
                if (play != Play.TIMEOUT && round.now() >= opened + decideAfter) {
                    if (play == Play.CALL) {
                        round.call();
                    } else {
                        round.fold();
                    }
                }
            }
            if (each != null) {
                each.at(view, now);
            }
        }
        return view;
    }

    /** Player A A, dealer 5 7, board A K K 2 3: full house beats the dealer's kings (WIN, CELEBRATE). */
    static final int[] CELEBRATE = {0, 4, 13, 19, 26, 51, 12, 27, 41};
    /** Player 2 3, dealer 2 4, board 10 J Q K A: both play the board's straight (PUSH). */
    static final int[] PUSH = {14, 40, 28, 29, 9, 23, 37, 51, 0};
    /** Player A A, dealer 2 7, board K 9 4 5 J: the dealer has king high (NO_QUALIFY, PAY). */
    static final int[] NO_QUALIFY = {0, 1, 13, 19, 38, 47, 3, 17, 49};

    /** Every seat; called with each reaction (celebrate, push, pay, a random showdown), folded and timed out. */
    private static void hands(Tick each) {
        play(0, Play.CALL, 0, 1, each, CELEBRATE);
        play(1, Play.CALL, 37, 2, each, PUSH);
        play(2, Play.CALL, 74, 3, each, NO_QUALIFY);
        play(1, Play.CALL, 160, 4, each);
        for (int seat = 0; seat < 3; seat++) {
            play(seat, Play.FOLD, 120, 10 + seat, each);
        }
        play(1, Play.TIMEOUT, 0, 20, each);
    }

    // ---- the table ----------------------------------------------------------------------------------------------------

    @Test
    void tableModelsLandWhereTheBlockstatePutsThem() {
        int checked = 0;
        for (Facing facing : Facing.values()) {
            for (int part = 0; part < PokerRig.PARTS; part++) {
                Transformation t = PokerRig.bodyTransform(facing, part);
                for (JsonElement e : model(PokerRig.bodyModel(part)).getAsJsonArray("elements")) {
                    List<Vector3f> want = new ArrayList<>();
                    for (double[] p : corners(e.getAsJsonObject(), false)) {
                        want.add(modBlockPoint(facing, part, p[0], p[1], p[2]));
                    }
                    assertSameSet(want, rigCorners(t, e.getAsJsonObject()), facing + " part " + part);
                    checked++;
                }
            }
        }
        assertTrue(checked > 4 * 150, "checked " + checked);
    }

    @Test
    void theTableCellsAreTheFootprint() {
        for (Facing facing : Facing.values()) {
            List<Pos> carriers = MachineType.POKER_TABLE.carriers(new Pos("w", 0, 0, 0), facing);
            assertEquals(PokerRig.PARTS, carriers.size());
            for (int part = 0; part < PokerRig.PARTS; part++) {
                Vector3f centre = world(PokerRig.bodyTransform(facing, part), 8, 8, 8);
                Pos cell = carriers.get(part);
                assertEquals(cell.x() + 0.5, centre.x, 1e-5, facing + " part " + part);
                assertEquals(0.5, centre.y, 1e-5);
                assertEquals(cell.z() + 0.5, centre.z, 1e-5, facing + " part " + part);
            }
        }
    }

    // ---- the dealer ---------------------------------------------------------------------------------------------------

    @Test
    void phaseAndPoseAreTheRenderers() {
        assertEquals(modPhase(), BlackjackDealerRig.phase(ANCHOR[0], ANCHOR[1], ANCHOR[2]));
        long phase = modPhase();
        int[] checked = {0};
        hands((view, now) -> {
            DealerIdleAnimation.Pose want = modDealerPose(view, now);
            DealerIdleAnimation.Pose got = PokerRig.dealerPose(view, (double) now, phase);
            float[] w = flat(want);
            float[] g = flat(got);
            assertArrayEquals(w, g, 0F, "pose at " + (now - START));
            checked[0]++;
        });
        assertTrue(checked[0] > 2000, "checked " + checked[0]);
    }

    private static float[] flat(DealerIdleAnimation.Pose p) {
        return new float[] {p.torsoX, p.torsoY, p.torsoXRot, p.torsoYRot, p.torsoZRot, p.headXRot, p.headYRot,
            p.headZRot, p.shoulderXRot[0], p.shoulderXRot[1], p.shoulderYRot[0], p.shoulderYRot[1],
            p.shoulderZRot[0], p.shoulderZRot[1], p.elbowXRot[0], p.elbowXRot[1], p.legX[0], p.legX[1],
            p.legZRot[0], p.legZRot[1]};
    }

    @Test
    void dealerPartsLandOnTheModsCubes() {
        long phase = modPhase();
        Set<DealerPlayAnimation.Kind> gestures = EnumSet.noneOf(DealerPlayAnimation.Kind.class);
        int[] checked = {0};
        boolean[] watched = {false};
        hands((view, now) -> {
            if ((now - START) % 3 != 0) {
                return;
            }
            DealerPlayAnimation.Input in = PokerRig.input(view, (double) now);
            gestures.add(in.kind);
            watched[0] |= in.watch > 0.99F;
            DealerIdleAnimation.Pose p = PokerRig.dealerPose(view, (double) now, phase);
            for (Facing facing : Facing.values()) {
                Transformation[] t = BlackjackDealerRig.transforms(facing, p);
                for (int i = 0; i < BlackjackDealerRig.PARTS.length; i++) {
                    JsonArray elements = model(BlackjackDealerRig.model(i)).getAsJsonArray("elements");
                    assertEquals(CUBES[i].length, elements.size());
                    for (int c = 0; c < CUBES[i].length; c++) {
                        assertSameSet(modCorners(facing, modDealerPose(view, now), i, c),
                                rigCorners(t[i], elements.get(c).getAsJsonObject()),
                                facing + " " + BlackjackDealerRig.PARTS[i] + " cube " + c + " at " + (now - START));
                        checked[0]++;
                    }
                }
            }
        });
        // every gesture the poker round makes (no PEEK in Casino Hold'em), and the watch during the decision
        assertEquals(EnumSet.complementOf(EnumSet.of(DealerPlayAnimation.Kind.PEEK)), gestures);
        assertTrue(watched[0], "the dealer watches the seat");
        assertTrue(checked[0] > 50_000, "checked " + checked[0]);
    }

    @Test
    void theDealerWatchesTheSeatDuringTheDecision() {
        for (int seat = 0; seat < 3; seat++) {
            double[] c = {new double[] {17.0, 32.0, 47.0}[seat], 31.4};
            double bearing = Math.atan2(c[0] - 32.0, c[1] + 4.6);
            int s = seat;
            boolean[] seen = {false};
            play(seat, Play.CALL, 100, 3, (view, now) -> {
                DealerPlayAnimation.Input in = PokerRig.input(view, (double) now);
                if (view.phase == PokerRound.Phase.DECISION && now - view.phaseAt >= 10) {
                    assertEquals(1F, in.watch, 1e-6F, "seat " + s);
                    assertEquals((float) bearing, in.watchBearing, 1e-6F, "seat " + s);
                    seen[0] = true;
                }
            });
            assertTrue(seen[0]);
        }
    }

    @Test
    void theDealersLightCellsAreRowMinusTwo() {
        // PokerTableRenderer 74-77: rotatedOffset(facing, column - 1, -2) for column 1..2, plus the anchor itself
        int[][][] want = {
            {{0, 0}, {0, -2}, {1, -2}},
            {{0, 0}, {2, 0}, {2, 1}},
            {{0, 0}, {0, 2}, {-1, 2}},
            {{0, 0}, {-2, 0}, {-2, -1}},
        };
        for (Facing facing : Facing.values()) {
            int[][] got = PokerRig.lightCells(facing);
            for (int k = 0; k < 3; k++) {
                assertArrayEquals(want[facing.turns()][k], got[k], facing.toString());
            }
        }
    }

    // ---- the cards ----------------------------------------------------------------------------------------------------

    /** The card model's from/to (px) as the pack has it. */
    private static double[][] cardBox(int value) {
        return CARD_BOXES.computeIfAbsent(value, PokerGeometryTest::readCardBox);
    }

    private static final java.util.Map<Integer, double[][]> CARD_BOXES = new java.util.concurrent.ConcurrentHashMap<>();

    private static double[][] readCardBox(int value) {
        JsonObject element = model(BlackjackCardRig.model(value)).getAsJsonArray("elements").get(0).getAsJsonObject();
        return new double[][] {vec(element.getAsJsonArray("from")), vec(element.getAsJsonArray("to"))};
    }

    /** The card model's up (face) and down (back) corners vs the mod's two quads. */
    private static void assertCard(Facing facing, Transformation t, BlackjackCardMotion.Pose pose, int value,
                                   String what) {
        List<BlackjackCardGeometry.Quad> quads = new ArrayList<>();
        BlackjackCardGeometry.quads(pose, value, quads::add);
        double[][] box = cardBox(value);
        for (int side = 0; side < 2; side++) {
            List<Vector3f> want = new ArrayList<>();
            for (double[] v : quads.get(side).v) {
                want.add(toBlock(v[0], v[1], v[2], facing));
            }
            double y = side == 0 ? box[1][1] : box[0][1];
            List<Vector3f> got = List.of(world(t, box[0][0], y, box[0][2]), world(t, box[0][0], y, box[1][2]),
                    world(t, box[1][0], y, box[0][2]), world(t, box[1][0], y, box[1][2]));
            assertSameSet(want, got, what + " side " + side);
        }
    }

    @Test
    void cardsLandOnTheModsQuadsAtThePokerSlots() {
        int[] checked = {0};
        boolean[] revealed = {false};
        hands((view, now) -> {
            for (BlackjackCardMotion.Card card : view.cards) {
                // PokerTableState.slot = the mod's PokerLayout slot
                double[] want = modSlot(card.toDealer, view.seat, card.index);
                BlackjackLayout.Slot slot = view.slot(card);
                assertArrayEquals(want, new double[] {slot.x(), slot.y(), slot.z(), slot.yaw()}, 1e-12);
                BlackjackCardMotion.Pose pose = new BlackjackCardMotion.Pose();
                boolean shown = BlackjackCardMotion.pose(card, new BlackjackLayout.Slot(want[0], want[1], want[2],
                        want[3]), (double) now, pose);
                for (Facing facing : Facing.values()) {
                    Transformation t = PokerRig.cardTransform(facing, view, card, (double) now);
                    if (!shown) {
                        assertNull(t);
                        continue;
                    }
                    assertCard(facing, t, pose, card.value, facing + " card " + card.toDealer + card.index + " at "
                            + (now - START));
                    checked[0]++;
                }
                // the dealer's hole cards show their back until revealed (index 1 first, then 0)
                if (card.toDealer && card.index < 2) {
                    assertEquals(card.flipAt < 0, card.value < 0);
                    revealed[0] |= card.value >= 0;
                }
            }
            List<BlackjackCardMotion.Pose> deck = new ArrayList<>();
            if (view.gesture == DealerPlayAnimation.Kind.SHUFFLE) {
                BlackjackCardMotion.shuffleDeck((double) (now - view.gestureAt), deck);
            }
            for (BlackjackCardMotion.Pose p : deck) {
                if (p.visible) {
                    for (Facing facing : Facing.values()) {
                        assertCard(facing, BlackjackCardRig.transform(facing, p), p, -1, facing + " deck");
                        checked[0]++;
                    }
                }
            }
        });
        assertTrue(revealed[0]);
        assertTrue(checked[0] > 20_000, "checked " + checked[0]);
    }

    @Test
    void holeCardsAreRevealedIndexOneFirst() {
        PokerTableState v = play(2, Play.CALL, 0, 11, null);
        BlackjackCardMotion.Card one = v.dealerCard(1);
        BlackjackCardMotion.Card zero = v.dealerCard(0);
        assertTrue(one.flipAt > 0 && zero.flipAt == one.flipAt + PokerRound.SECOND_FLIP);
        assertEquals(9, v.cards.size());
        assertEquals(5, v.cards.stream().filter(c -> c.toDealer && c.index >= 2).count());
    }

    // ---- client-side limits -----------------------------------------------------------------------------------------

    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.dot(b) / Math.sqrt((double) a.lengthSquared() * b.lengthSquared()));
        return Math.toDegrees(2 * Math.acos(Math.min(1, dot)));
    }

    /**
     * The client slerps from where it is to each new target: consecutive targets of the rig's real send schedule
     * (dealer: every tick with the pose of now + 2 while fast, else the idle cadence with now + n + 1; cards every tick
     * with now + 1) must stay far below 180 degrees, also across gesture changes.
     */
    @Test
    void everyInterpolatedRotationStepIsSmall() {
        long phase = modPhase();
        double[] worst = {0, 0};
        for (Facing facing : Facing.values()) {
            Transformation[][] sent = {null};
            long[] last = {Long.MIN_VALUE / 2};
            java.util.Map<String, Transformation> cardSent = new java.util.HashMap<>();
            hands((view, now) -> {
                int duration;
                if (PokerRig.fast(view, now)) {
                    duration = PokerRig.FAST_DURATION;
                } else {
                    int every = BlackjackRig.idleEvery(now, phase);
                    duration = now - last[0] < every ? -1 : every + 1;
                }
                if (duration > 0) {
                    last[0] = now;
                    Transformation[] t = BlackjackDealerRig.transforms(facing,
                            PokerRig.dealerPose(view, (double) (now + duration), phase));
                    if (sent[0] != null) {
                        for (int i = 0; i < t.length; i++) {
                            worst[0] = Math.max(worst[0], angle(sent[0][i].getLeftRotation(), t[i].getLeftRotation()));
                        }
                    }
                    sent[0] = t;
                }
                for (BlackjackCardMotion.Card c : view.cards) {
                    Transformation t = PokerRig.cardTransform(facing, view, c, (double) (now + PokerRig.CARD_DURATION));
                    String key = c.toDealer + "/" + c.index + "/" + c.dealtAt;
                    if (t == null) {
                        cardSent.remove(key);
                        continue;
                    }
                    Transformation before = cardSent.put(key, t);
                    if (before != null) {
                        worst[1] = Math.max(worst[1], angle(before.getLeftRotation(), t.getLeftRotation()));
                    }
                }
            });
        }
        System.out.printf("poker: largest dealer step %.1f degrees, card step %.1f degrees%n", worst[0], worst[1]);
        assertTrue(worst[0] < 90, "dealer step " + worst[0]);
        assertTrue(worst[1] < 90, "card step " + worst[1]);
    }

    /** Extents of everything drawn, relative to the rig entity at the anchor's bottom centre. */
    private static final class Box {
        float half;
        float low;
        float high;
        long points;

        void add(Vector3f p) {
            float x = p.x - 0.5F;
            float z = p.z - 0.5F;
            half = Math.max(half, Math.max(Math.abs(x), Math.abs(z)));
            low = Math.min(low, p.y);
            high = Math.max(high, p.y);
            points++;
            assertTrue(Math.abs(x) <= PokerRig.WIDTH / 2 && Math.abs(z) <= PokerRig.WIDTH / 2, "outside the box: " + p);
            assertTrue(p.y >= -0.01F && p.y <= PokerRig.HEIGHT, "outside the box height: " + p);
        }
    }

    @Test
    void everythingStaysInsideTheCullingBox() {
        long phase = modPhase();
        Box box = new Box();
        for (Facing facing : Facing.values()) {
            for (int part = 0; part < PokerRig.PARTS; part++) {
                Transformation t = PokerRig.bodyTransform(facing, part);
                for (JsonElement e : model(PokerRig.bodyModel(part)).getAsJsonArray("elements")) {
                    for (double[] p : corners(e.getAsJsonObject(), true)) {
                        box.add(world(t, p[0], p[1], p[2]));
                    }
                }
            }
        }
        double[][] card = cardBox(0);
        hands((view, now) -> {
            DealerIdleAnimation.Pose p = PokerRig.dealerPose(view, (double) now, phase);
            for (Facing facing : Facing.values()) {
                Transformation[] t = BlackjackDealerRig.transforms(facing, p);
                for (int i = 0; i < t.length; i++) {
                    for (JsonElement e : model(BlackjackDealerRig.model(i)).getAsJsonArray("elements")) {
                        rigCorners(t[i], e.getAsJsonObject()).forEach(box::add);
                    }
                }
                List<Transformation> cards = new ArrayList<>();
                for (BlackjackCardMotion.Card c : view.cards) {
                    Transformation ct = PokerRig.cardTransform(facing, view, c, (double) now);
                    if (ct != null) {
                        cards.add(ct);
                    }
                }
                if (view.gesture == DealerPlayAnimation.Kind.SHUFFLE) {
                    List<BlackjackCardMotion.Pose> deck = new ArrayList<>();
                    BlackjackCardMotion.shuffleDeck((double) (now - view.gestureAt), deck);
                    deck.stream().filter(d -> d.visible).forEach(d -> cards.add(BlackjackCardRig.transform(facing, d)));
                }
                for (Transformation ct : cards) {
                    for (int k = 0; k < 4; k++) {
                        box.add(world(ct, card[k & 1][0], 8, card[k >> 1][2]));
                    }
                }
            }
        });
        System.out.printf("poker: %d points, |x|,|z| <= %.3f, y %.3f..%.3f (box %.2f x %.2f)%n", box.points,
                box.half, box.low, box.high, PokerRig.WIDTH, PokerRig.HEIGHT);
        assertTrue(box.points > 1_000_000, "points " + box.points);
    }

    /** render.md 2.5: poker ~22 entities idle / ~31 at the peak (12 table, 8 dealer, the panel, the cards). */
    @Test
    void entityBudget() {
        int[] peak = {0};
        hands((view, now) -> {
            int n = 0;
            for (BlackjackCardMotion.Card c : view.cards) {
                n += PokerRig.cardTransform(Facing.SOUTH, view, c, (double) now) != null ? 1 : 0;
            }
            if (view.gesture == DealerPlayAnimation.Kind.SHUFFLE) {
                List<BlackjackCardMotion.Pose> deck = new ArrayList<>();
                BlackjackCardMotion.shuffleDeck((double) (now - view.gestureAt), deck);
                n += (int) deck.stream().filter(d -> d.visible).count();
            }
            peak[0] = Math.max(peak[0], n);
        });
        int idle = PokerRig.PARTS + BlackjackDealerRig.PARTS.length;
        int top = idle + 1 + peak[0];
        System.out.printf("poker entities: idle %d, peak %d (cards %d)%n", idle, top, peak[0]);
        assertEquals(20, idle);
        assertEquals(9, peak[0]);
        assertTrue(top <= 31, "peak " + top);
    }

    // ---- panel and coins ----------------------------------------------------------------------------------------------

    @Test
    void panelAndCoinsStartWhereTheModDrawsAndEjects() {
        for (Facing facing : Facing.values()) {
            // PokerTableRenderer 47-48, 263-265: above toBlock(BOARD_X[2] = 32, 0, 16.8), the text grows up from 2.25
            Vector3f c = toBlock(32.0, 0.0, 16.8, facing);
            assertArrayEquals(new double[] {c.x, 2.25, c.z}, PokerRig.panelPoint(facing), 1e-6);
            for (int seat = 0; seat < 3; seat++) {
                // PokerTableBlockEntity.eject 644-645: the seat's bet spot (SEAT_X, BET_Z = 31.4) at 15.5 px
                Vector3f s = toBlock(new double[] {17.0, 32.0, 47.0}[seat], 15.5, 31.4, facing);
                assertArrayEquals(new double[] {s.x, s.y, s.z}, PokerRig.coinPoint(facing, seat), 1e-6);
            }
        }
    }
}
