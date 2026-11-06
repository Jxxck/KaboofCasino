package de.tigges.kaboofcasino.game.slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The spin plan (games.md 6 "Timing", port of {@code SlotMachineSpin}): reel motion, sound cues, lever, lamp,
 * bulbs and drum flash as functions of the ticks since the lever pull. Deterministic from (from, stops, seed); the
 * seed only drives the cruise jitter. Pure.
 */
public final class SlotSpin {

    public static final int REELS = 3;
    public static final int BULBS = 15;
    public static final double LEVER_REST = -14.0;
    public static final double LEVER_PULLED = 95.0;
    static final int[] START = {6, 8, 10};
    static final double SPEED = 0.85;
    private static final int[] CRUISE = {16, 30, 44};
    private static final int RESULT_DELAY = 10;
    private static final int[] CELEBRATION = {24, 30, 44, 60, 96};
    private static final int[] READY = {0, 0, 8, 16, 40};
    /** Payouts start this many ticks after the result ({@code SlotMachineBlockEntity.tick}). */
    public static final int PAYOUT_DELAY = 12;
    /** The floating bet panel disappears this many ticks after the result. */
    public static final int PANEL_AFTER_RESULT = 70;

    /** Sound cue kinds. */
    public enum Sound {
        LEVER, WHIRR, CLICK, STOP
    }

    /** One timed sound; {@code reel} is -1 for lever and whirr. */
    public record Cue(int tick, Sound sound, int reel, float volume, float pitch) {
    }

    private SlotSpin() {
    }

    public static Plan plan(int[] from, int[] stops, long seed) {
        return new Plan(from.clone(), stops.clone(), seed);
    }

    static double recoil(double dt) {
        return dt > 0 && dt < 12 ? -0.1 * StrictMath.exp(-dt / 2.2) * StrictMath.sin(2 * Math.PI * dt / 4.5) : 0;
    }

    private static double align(double x, int face) {
        double m = (face - x) % 8.0;
        return m < 0 ? m + 8.0 : m;
    }

    private static Reel build(int k, int from, int stop, int cruise, boolean tease) {
        Reel r = new Reel(START[k], from);
        r.add(6.0, 0.14166666666666666);
        r.add(cruise, 0.0);
        if (!tease) {
            double shortest = 3.4;
            double distance = shortest + align(r.turn + shortest, stop);
            double duration = 2.0 * distance / SPEED;
            r.add(duration, -SPEED / duration);
        } else {
            double fixed = 2.55;
            double perSpeed = 26.0;
            double shortest = fixed + 0.2 * perSpeed;
            double distance = shortest + align(r.turn + shortest, stop);
            double w = (distance - fixed) / perSpeed;
            r.add(6.0, (w - SPEED) / 6.0);
            r.add(20.0, 0.0);
            r.add(6.0, -w / 6.0);
        }
        r.finish();
        return r;
    }

    /** The plan of one spin. */
    public static final class Plan {
        private final int[] from;
        private final int[] stops;
        private final long seed;
        private final boolean tease;
        private final Reel[] reels = new Reel[REELS];
        private final SlotGame.Result result;
        private final List<Cue> cues;
        private final int resultTick;
        private final int endTick;

        private Plan(int[] from, int[] stops, long seed) {
            this.from = from;
            this.stops = stops;
            this.seed = seed;
            this.result = SlotGame.evaluate(stops);
            this.tease = SlotGame.symbol(0, stops[0]) == SlotGame.symbol(1, stops[1]);
            SplitMix rng = new SplitMix(seed);
            double previousLand = -1.0e9;
            for (int k = 0; k < REELS; k++) {
                int cruise = CRUISE[k] + rng.below(5);
                boolean slow = tease && k == 2;
                Reel r = build(k, Math.floorMod(from[k], 8), Math.floorMod(stops[k], 8), cruise, slow);
                while (r.land < previousLand + 8.0) {
                    r = build(k, Math.floorMod(from[k], 8), Math.floorMod(stops[k], 8), ++cruise, slow);
                }
                reels[k] = r;
                previousLand = r.land;
            }
            this.resultTick = (int) Math.ceil(reels[2].land) + RESULT_DELAY;
            this.endTick = resultTick + CELEBRATION[result.kind().ordinal()];
            this.cues = Collections.unmodifiableList(makeCues());
        }

        private List<Cue> makeCues() {
            List<Cue> out = new ArrayList<>();
            out.add(new Cue(0, Sound.LEVER, -1, 1.0f, 1.0f));
            int whirrEnd = (int) Math.floor(reels[2].land) - 12;
            int tick = START[0] + 2;
            for (int n = 0; tick < whirrEnd; n++) {
                out.add(new Cue(tick, Sound.WHIRR, -1, 0.5f, n % 2 == 0 ? 1.0f : 0.96f));
                tick += 7;
            }
            for (int k = 0; k < REELS; k++) {
                Reel r = reels[k];
                boolean slow = tease && k == 2;
                for (int i = 2; i < r.count; i++) {
                    double duration = (i + 1 < r.count ? r.t0[i + 1] : r.land) - r.t0[i];
                    double turnEnd = r.turn0[i] + r.v0[i] * duration + 0.5 * r.acc[i] * duration * duration;
                    for (long m = (long) Math.floor(r.turn0[i]) + 1; m < turnEnd - 1.0e-6; m++) {
                        if (slow || !(m <= r.finalTurn - 6.0)) {
                            double dt;
                            if (Math.abs(r.acc[i]) < 1.0e-12) {
                                dt = (m - r.turn0[i]) / r.v0[i];
                            } else {
                                dt = (-r.v0[i] + Math.sqrt(Math.max(0.0, r.v0[i] * r.v0[i]
                                        + 2.0 * r.acc[i] * (m - r.turn0[i])))) / r.acc[i];
                            }
                            out.add(new Cue((int) Math.round(r.t0[i] + dt), Sound.CLICK, k, 0.42f,
                                    0.88f + 0.1f * k + 0.03f * (m % 3)));
                        }
                    }
                }
                out.add(new Cue((int) Math.ceil(r.land), Sound.STOP, k, 0.9f, 0.94f + 0.05f * k));
            }
            out.sort((a, b) -> a.tick() != b.tick() ? Integer.compare(a.tick(), b.tick())
                    : a.sound() != b.sound() ? a.sound().compareTo(b.sound()) : Integer.compare(a.reel(), b.reel()));
            return out;
        }

        public int[] from() {
            return from.clone();
        }

        public int[] stops() {
            return stops.clone();
        }

        public long seed() {
            return seed;
        }

        public SlotGame.Result result() {
            return result;
        }

        public boolean tease() {
            return tease;
        }

        public List<Cue> cues() {
            return cues;
        }

        /** Reel position in faces (fractional) at {@code elapsed} ticks. */
        public double turn(int reel, double elapsed) {
            return reels[reel].turnAt(elapsed);
        }

        public double speed(int reel, double elapsed) {
            return reels[reel].speedAt(elapsed);
        }

        public int landTick(int reel) {
            return (int) Math.ceil(reels[reel].land);
        }

        public double land(int reel) {
            return reels[reel].land;
        }

        public int resultTick() {
            return resultTick;
        }

        public int endTick() {
            return endTick;
        }

        public int readyTick() {
            return resultTick + READY[result.kind().ordinal()];
        }

        public int payoutTick() {
            return resultTick + PAYOUT_DELAY;
        }

        /** Lever angle in degrees (smoothstep down 8 ticks, hold 2, cubic ease-out back over 16). */
        public double lever(double elapsed) {
            if (elapsed <= 0) {
                return LEVER_REST;
            }
            if (elapsed < 8.0) {
                double x = elapsed / 8.0;
                return LEVER_REST + 109.0 * x * x * (3.0 - 2.0 * x);
            }
            if (elapsed < 10.0) {
                return LEVER_PULLED;
            }
            double x = (elapsed - 8.0 - 2.0) / 16.0;
            if (x >= 1.0) {
                return LEVER_REST;
            }
            double y = 1.0 - (1.0 - x) * (1.0 - x) * (1.0 - x);
            return LEVER_PULLED - 109.0 * y;
        }

        private boolean spinning(double elapsed) {
            return elapsed >= 0 && elapsed < reels[2].land;
        }

        private double sinceResult(double elapsed) {
            return elapsed - resultTick;
        }

        private boolean celebrating(double elapsed) {
            double s = sinceResult(elapsed);
            return s >= 0 && s < CELEBRATION[result.kind().ordinal()]
                    && result.kind().ordinal() >= SlotGame.Kind.WIN.ordinal();
        }

        public double lamp(double elapsed) {
            if (spinning(elapsed)) {
                return (int) Math.floor(elapsed / 4.0) % 2 == 0 ? 1.0 : 0.4;
            }
            if (celebrating(elapsed)) {
                int period = result.kind() == SlotGame.Kind.JACKPOT ? 2 : 4;
                return (int) Math.floor(sinceResult(elapsed) / period) % 2 == 0 ? 1.0 : 0.3;
            }
            return 1.0;
        }

        public double bulb(int i, double elapsed) {
            if (spinning(elapsed)) {
                return ((int) Math.floor(elapsed / 2.0) + i) % 3 == 0 ? 1.0 : 0.22;
            }
            if (celebrating(elapsed)) {
                boolean jackpot = result.kind() == SlotGame.Kind.JACKPOT;
                int step = (int) Math.floor(sinceResult(elapsed) / (jackpot ? 3.0 : 4.0));
                return jackpot ? ((step + i) % 2 == 0 ? 1.0 : 0.22) : (step % 2 == 0 ? 1.0 : 0.22);
            }
            return 1.0;
        }

        /** Drum brightness during the celebration (1.0 / 0.62 every 4 ticks). */
        public double flash(double elapsed) {
            if (!celebrating(elapsed)) {
                return 1.0;
            }
            return (int) Math.floor(sinceResult(elapsed) / 4.0) % 2 == 0 ? 1.0 : 0.62;
        }
    }

    /** Piecewise constant-acceleration reel motion. */
    private static final class Reel {
        final double[] t0 = new double[8];
        final double[] turn0 = new double[8];
        final double[] v0 = new double[8];
        final double[] acc = new double[8];
        int count;
        double land;
        double finalTurn;
        private double t;
        private double turn;
        private double v;

        Reel(double start, double from) {
            this.t = start;
            this.turn = from;
            this.t0[0] = start;
            this.turn0[0] = from;
        }

        void add(double duration, double a) {
            t0[count] = t;
            turn0[count] = turn;
            v0[count] = v;
            acc[count] = a;
            count++;
            turn = turn + v * duration + 0.5 * a * duration * duration;
            v += a * duration;
            t += duration;
        }

        void finish() {
            land = t;
            finalTurn = Math.rint(turn);
        }

        double turnAt(double e) {
            if (e <= t0[0]) {
                return turn0[0];
            }
            if (e >= land) {
                return finalTurn + recoil(e - land);
            }
            int i = count - 1;
            while (e < t0[i]) {
                i--;
            }
            double dt = e - t0[i];
            return turn0[i] + v0[i] * dt + 0.5 * acc[i] * dt * dt;
        }

        double speedAt(double e) {
            if (e <= t0[0] || e >= land) {
                return 0;
            }
            int i = count - 1;
            while (e < t0[i]) {
                i--;
            }
            return v0[i] + acc[i] * (e - t0[i]);
        }
    }

    /** SplitMix64 of the mod ({@code SlotMachineSpin.Rng}). */
    private static final class SplitMix {
        private long state;

        SplitMix(long seed) {
            this.state = seed;
        }

        long nextLong() {
            long z = state += -7046029254386353131L;
            z = (z ^ z >>> 30) * -4658895280553007687L;
            z = (z ^ z >>> 27) * -7723592293110705685L;
            return z ^ z >>> 31;
        }

        double next() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        int below(int n) {
            return (int) (next() * n);
        }
    }
}
