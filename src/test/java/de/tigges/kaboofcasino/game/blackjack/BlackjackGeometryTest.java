package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.pack.PackFiles;
import de.tigges.kaboofcasino.visual.RigMath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The rig against an independent evaluation of the mod's renderer: the dealer's parts (pack models placed by the
 * rig's transformations) land on the mod's {@code ModelPart} cubes under {@code DealerPlacement} + the part chain,
 * the cards on {@code BlackjackCardGeometry.quads} at {@code BlackjackLayout.toBlock}, for idle, every gesture, the
 * watch, card flights, flips, peeks, clearing sweeps, the shuffle deck and all four facings. World positions are
 * blocks from the anchor block's corner, like the mod's block-entity pose.
 */
class BlackjackGeometryTest {

    private static final double EPS = 2e-4;

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

        /** Right-multiplies the 3x3 part by r. */
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

    /** {@code DealerPlacement.apply} then the {@code ModelPart} chain of display part {@code i}. */
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
        m.rotateY(Math.toRadians(180.0F - facing.turns() * 90.0F));   // Axis.YP.rotationDegrees
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

    // ---- the rig + the built pack ------------------------------------------------------------------------------------

    private static JsonObject model(String key) {
        String path = key.substring(key.indexOf(':') + 1);
        byte[] json = PackFiles.all().get("assets/kaboofcasino/models/" + path + ".json");
        return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static double[] vec(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    /** Where model point m ends up, from the anchor's corner (the rig stands at the anchor's bottom centre). */
    private static Vector3f world(Transformation t, double x, double y, double z) {
        return RigMath.apply(t, x, y, z).add(0.5F, 0F, 0.5F);
    }

    private static List<Vector3f> rigCorners(Transformation t, JsonObject element) {
        double[] f = vec(element.getAsJsonArray("from"));
        double[] to = vec(element.getAsJsonArray("to"));
        List<Vector3f> out = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            out.add(world(t, (k & 1) == 0 ? f[0] : to[0], (k & 2) == 0 ? f[1] : to[1], (k & 4) == 0 ? f[2] : to[2]));
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

    // ---- sample poses -------------------------------------------------------------------------------------------------

    private static List<DealerIdleAnimation.Pose> dealerPoses() {
        List<DealerIdleAnimation.Pose> out = new ArrayList<>();
        for (int t = 0; t < 480; t += 5) {
            DealerIdleAnimation.Pose p = new DealerIdleAnimation.Pose();
            DealerIdleAnimation.evaluate(t + 0.5F, p);
            out.add(p);
        }
        for (DealerPlayAnimation.Kind kind : DealerPlayAnimation.Kind.values()) {
            if (kind == DealerPlayAnimation.Kind.NONE) {
                continue;
            }
            for (float t = 0; t <= DealerPlayAnimation.duration(kind); t += 1.5F) {
                for (float bearing : new float[] {-1.3F, 0F, 0.8F}) {
                    DealerIdleAnimation.Pose p = new DealerIdleAnimation.Pose();
                    DealerIdleAnimation.evaluate(t * 7 + 100, p);
                    DealerPlayAnimation.Input in = new DealerPlayAnimation.Input();
                    in.kind = kind;
                    in.time = t;
                    in.bearing = bearing;
                    in.watch = bearing == 0F ? 0F : 0.6F;
                    in.watchBearing = bearing;
                    in.watchTime = t * 9;
                    DealerPlayAnimation.apply(in, p);
                    out.add(p);
                }
            }
        }
        return out;
    }

    @Test
    void dealerPartsLandOnTheModsCubes() {
        int checked = 0;
        for (Facing facing : Facing.values()) {
            for (DealerIdleAnimation.Pose p : dealerPoses()) {
                Transformation[] t = BlackjackDealerRig.transforms(facing, p);
                for (int i = 0; i < BlackjackDealerRig.PARTS.length; i++) {
                    JsonArray elements = model(BlackjackDealerRig.model(i)).getAsJsonArray("elements");
                    assertEquals(CUBES[i].length, elements.size());
                    for (int c = 0; c < CUBES[i].length; c++) {
                        assertSameSet(modCorners(facing, p, i, c), rigCorners(t[i], elements.get(c).getAsJsonObject()),
                                facing + " " + BlackjackDealerRig.PARTS[i] + " cube " + c);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 10_000, "checked " + checked);
    }

    @Test
    void theFingerTipIsDealerKinematicsTip() {
        // DealerKinematics (ported verbatim) is the mod's own forward kinematics for the card hand; it drops the
        // placement's 0.001 block lift (0.015 px)
        for (DealerIdleAnimation.Pose p : dealerPoses()) {
            Transformation[] t = BlackjackDealerRig.transforms(Facing.SOUTH, p);
            for (int side = 0; side < 2; side++) {
                // forearm tip = ModelPart (0, 6, 0) = model (8, 2, 8)
                Vector3f w = world(t[3 + 2 * side], 8, 2, 8);
                DealerKinematics.Point tip = DealerKinematics.hand(p, side).tip();
                double[] b = BlackjackLayout.toBlock(tip.x(), tip.y(), tip.z(), 0);
                assertEquals(b[0], w.x, EPS);
                assertEquals(b[1] + 0.9375 * 0.001, w.y, 1e-4);
                assertEquals(b[2], w.z, EPS);
            }
        }
    }

    private static List<BlackjackCardMotion.Card> sampleCards() {
        List<BlackjackCardMotion.Card> out = new ArrayList<>();
        for (int spot = 0; spot < 5; spot++) {
            for (int index = 0; index < 4; index++) {
                BlackjackCardMotion.Card c = new BlackjackCardMotion.Card();
                c.spot = spot;
                c.index = index;
                c.value = (spot * 11 + index * 7) % 52;
                c.dealtAt = 1000;
                c.clearAt = index == 2 ? 1040 : -1;
                out.add(c);
            }
        }
        for (int index = 0; index < 5; index++) {
            BlackjackCardMotion.Card c = new BlackjackCardMotion.Card();
            c.toDealer = true;
            c.index = index;
            c.value = index == 1 ? -1 : 13 + index;
            c.faceUp = index != 1;
            c.dealtAt = 1000;
            if (index == 1) {
                c.peekAt = 1030;
                c.flipAt = 1062;
                c.clearAt = 1090;
            }
            out.add(c);
        }
        return out;
    }

    private static List<BlackjackCardMotion.Pose> deckPoses() {
        List<BlackjackCardMotion.Pose> out = new ArrayList<>();
        for (double t = 0; t <= 40; t += 0.5) {
            List<BlackjackCardMotion.Pose> deck = new ArrayList<>();
            BlackjackCardMotion.shuffleDeck(t, deck);
            for (BlackjackCardMotion.Pose p : deck) {
                if (p.visible) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** The card model's up (face) and down (back) corners vs the mod's two quads. */
    private static void assertCard(Facing facing, BlackjackCardMotion.Pose pose, int value, String what) {
        List<BlackjackCardGeometry.Quad> quads = new ArrayList<>();
        BlackjackCardGeometry.quads(pose, value, quads::add);
        Transformation t = BlackjackCardRig.transform(facing, pose);
        for (int side = 0; side < 2; side++) {
            List<Vector3f> want = new ArrayList<>();
            for (double[] v : quads.get(side).v) {
                double[] b = BlackjackLayout.toBlock(v[0], v[1], v[2], facing.turns());
                want.add(new Vector3f((float) b[0], (float) b[1], (float) b[2]));
            }
            double y = side == 0 ? 8.03 : 7.97;
            List<Vector3f> got = List.of(world(t, 5.5, y, 4.8), world(t, 5.5, y, 11.2), world(t, 10.5, y, 4.8),
                    world(t, 10.5, y, 11.2));
            assertSameSet(want, got, what + " side " + side);
        }
    }

    @Test
    void cardsLandOnTheModsQuads() {
        int checked = 0;
        for (Facing facing : Facing.values()) {
            for (BlackjackCardMotion.Card c : sampleCards()) {
                BlackjackCardMotion.Pose pose = new BlackjackCardMotion.Pose();
                for (double now = 1000; now <= 1120; now += 0.5) {
                    if (BlackjackCardMotion.pose(c, now, pose)) {
                        assertCard(facing, pose, c.value, facing + " card " + c.toDealer + c.index + " at " + now);
                        checked++;
                    }
                }
            }
            for (BlackjackCardMotion.Pose p : deckPoses()) {
                assertCard(facing, p, -1, facing + " deck");
                checked++;
            }
        }
        assertTrue(checked > 5_000, "checked " + checked);
    }

    @Test
    void cardTexelsAreTheModsAfterPadding() {
        // canonical pose: yaw -pi/2, flip 0 at the origin; the sheet is padded from 350 to 352 px
        BlackjackCardMotion.Pose pose = new BlackjackCardMotion.Pose();
        pose.yaw = -Math.PI / 2;
        pose.visible = true;
        int[][] faceInfo = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}, // up
            {0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};                  // down
        for (int value = -1; value < 52; value++) {
            List<BlackjackCardGeometry.Quad> quads = new ArrayList<>();
            BlackjackCardGeometry.quads(pose, value, quads::add);
            String key = BlackjackCardRig.model(value);
            JsonObject element = model(key).getAsJsonArray("elements").get(0).getAsJsonObject();
            double[] f = vec(element.getAsJsonArray("from"));
            double[] to = vec(element.getAsJsonArray("to"));
            for (int side = 0; side < 2; side++) {
                JsonObject face = element.getAsJsonObject("faces").getAsJsonObject(side == 0 ? "up" : "down");
                JsonArray uv = face.getAsJsonArray("uv");
                int shift = face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0;
                for (int i = 0; i < 4; i++) {
                    int[] pick = faceInfo[side * 4 + i];
                    double mx = pick[0] == 1 ? to[0] : f[0];
                    double mz = pick[2] == 1 ? to[2] : f[2];
                    int j = (i + shift) % 4;
                    double u = (j == 0 || j == 1 ? uv.get(0) : uv.get(2)).getAsDouble() / 16;
                    double v = (j == 0 || j == 3 ? uv.get(1) : uv.get(3)).getAsDouble() / 16;
                    BlackjackCardGeometry.Quad q = quads.get(side);
                    boolean found = false;
                    for (int k = 0; k < 4; k++) {
                        found |= Math.abs(q.v[k][0] - (mx - 8)) < 1e-4 && Math.abs(q.v[k][2] - (mz - 8)) < 1e-4
                                && Math.abs(q.uv[k][0] * 350 / 352 - u) < 1e-6 && Math.abs(q.uv[k][1] - v) < 1e-6;
                    }
                    assertTrue(found, key + " side " + side + " corner " + i);
                }
            }
        }
    }

    // ---- client-side limits -----------------------------------------------------------------------------------------

    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.dot(b) / Math.sqrt((double) a.lengthSquared() * b.lengthSquared()));
        return Math.toDegrees(2 * Math.acos(Math.min(1, dot)));
    }

    @Test
    void everyInterpolatedRotationStepIsSmall() {
        long phase = BlackjackDealerRig.phase(10, 64, -7);
        double worst = 0;
        for (Facing facing : Facing.values()) {
            BlackjackTableState view = demoView();
            for (long now = 990; now < 1400; now++) {
                int step = BlackjackDealerRig.fast(BlackjackDealerRig.input(view, now)) ? BlackjackRig.FAST_DURATION
                        : BlackjackRig.idleEvery(now, phase) + 1;
                Transformation[] a = BlackjackDealerRig.transforms(facing, BlackjackDealerRig.pose(view, now, phase));
                Transformation[] b = BlackjackDealerRig.transforms(facing,
                        BlackjackDealerRig.pose(view, now + step, phase));
                for (int i = 0; i < a.length; i++) {
                    worst = Math.max(worst, angle(a[i].getLeftRotation(), b[i].getLeftRotation()));
                }
            }
            for (BlackjackCardMotion.Card c : sampleCards()) {
                BlackjackCardMotion.Pose p = new BlackjackCardMotion.Pose();
                BlackjackCardMotion.Pose q = new BlackjackCardMotion.Pose();
                for (long now = 1000; now < 1120; now++) {
                    if (BlackjackCardMotion.pose(c, now, p)
                            && BlackjackCardMotion.pose(c, now + BlackjackRig.CARD_DURATION, q)) {
                        worst = Math.max(worst, angle(BlackjackCardRig.transform(facing, p).getLeftRotation(),
                                BlackjackCardRig.transform(facing, q).getLeftRotation()));
                    }
                }
            }
        }
        assertTrue(worst < 90, "largest step " + worst + " degrees");
    }

    /** A whole hand through the real listener: shuffle, deal, peek, turn, hit, reveal, dealer hit, pay, clear. */
    static BlackjackTableState demoView() {
        BlackjackTableState v = new BlackjackTableState(1000L, 3);
        v.phaseChanged(BlackjackRound.Phase.DEALING, 0);
        v.shuffled(0);
        v.cardDealt(false, 0, 5, true, 50);
        v.cardDealt(true, 0, 12, true, 72);
        v.cardDealt(false, 1, 20, true, 94);
        v.cardDealt(true, 1, -1, false, 116);
        v.dealerPeek(false, 143);
        v.phaseChanged(BlackjackRound.Phase.PLAYER_TURN, 171);
        v.turnOpened(771, 171);
        v.phaseChanged(BlackjackRound.Phase.PLAYER_DRAW, 230);
        v.cardDealt(false, 2, 2, true, 230);
        v.phaseChanged(BlackjackRound.Phase.PLAYER_TURN, 257);
        v.turnOpened(857, 257);
        v.phaseChanged(BlackjackRound.Phase.DEALER_TURN, 300);
        v.holeCardRevealed(30, 300);
        v.cardDealt(true, 2, 40, true, 324);
        v.phaseChanged(BlackjackRound.Phase.RESULT, 353);
        v.decided(BlackjackRound.Result.WIN, 353);
        v.reaction(BlackjackRound.Reaction.PAY, 353);
        v.phaseChanged(BlackjackRound.Phase.CLEARING, 389);
        v.cleared(389);
        return v;
    }

    @Test
    void everythingStaysInsideTheCullingBox() {
        float half = BlackjackRig.WIDTH / 2;
        List<Vector3f> points = new ArrayList<>();
        for (Facing facing : Facing.values()) {
            for (DealerIdleAnimation.Pose p : dealerPoses()) {
                Transformation[] t = BlackjackDealerRig.transforms(facing, p);
                for (int i = 0; i < t.length; i++) {
                    for (JsonElement e : model(BlackjackDealerRig.model(i)).getAsJsonArray("elements")) {
                        points.addAll(rigCorners(t[i], e.getAsJsonObject()));
                    }
                }
            }
            for (BlackjackCardMotion.Card c : sampleCards()) {
                BlackjackCardMotion.Pose pose = new BlackjackCardMotion.Pose();
                for (double now = 1000; now <= 1120; now += 1) {
                    if (BlackjackCardMotion.pose(c, now, pose)) {
                        Transformation t = BlackjackCardRig.transform(facing, pose);
                        points.add(world(t, 5.5, 8, 4.8));
                        points.add(world(t, 10.5, 8, 11.2));
                    }
                }
            }
            for (int part = 0; part < 8; part++) {
                Transformation t = BlackjackRig.bodyTransform(facing, part);
                points.add(world(t, 0, 0, 0));
                points.add(world(t, 16, 16, 16));
            }
        }
        for (Vector3f p : points) {
            // relative to the rig entity at the anchor's bottom centre
            float x = p.x - 0.5F;
            float z = p.z - 0.5F;
            assertTrue(Math.abs(x) <= half && Math.abs(z) <= half, "outside the box: " + p);
            assertTrue(p.y >= -0.01F && p.y <= BlackjackRig.HEIGHT, "outside the box height: " + p);
        }
    }

    @Test
    void theTableCellsAreTheFootprint() {
        // part p's block model fills the cell at (p % 4 - 1, 0, p / 4 - 1), rotated with the facing like the carriers
        for (Facing facing : Facing.values()) {
            List<de.tigges.kaboofcasino.util.Pos> carriers = de.tigges.kaboofcasino.machine.MachineType.BLACKJACK_TABLE
                    .carriers(new de.tigges.kaboofcasino.util.Pos("w", 0, 0, 0), facing);
            for (int part = 0; part < 8; part++) {
                Vector3f centre = world(BlackjackRig.bodyTransform(facing, part), 8, 8, 8);
                de.tigges.kaboofcasino.util.Pos cell = carriers.get(part);
                assertEquals(cell.x() + 0.5, centre.x, 1e-5, facing + " part " + part);
                assertEquals(0.5, centre.y, 1e-5);
                assertEquals(cell.z() + 0.5, centre.z, 1e-5, facing + " part " + part);
            }
            // the model's north-west corner turns with the cell like the blockstate's "y": 90 * turns
            Vector3f nw = world(BlackjackRig.bodyTransform(facing, 5), 0, 0, 0);
            double[] want = {0, 0};
            for (int k = 0; k < facing.turns(); k++) {
                want = new double[] {1 - want[1], want[0]};
            }
            assertEquals(want[0], nw.x, 1e-5, facing.toString());
            assertEquals(want[1], nw.z, 1e-5, facing.toString());
        }
    }
}
