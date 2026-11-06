package de.tigges.kaboofcasino.game.slot;

/**
 * Idle light patterns of the machines ({@code CasinoIdleLights}): 4 chase patterns of 120 ticks each with a 14-tick
 * cross-fade, a slow beacon for lamps, and the 14-tick blend from game lights back to idle. Pure.
 */
public final class IdleLights {

    private IdleLights() {
    }

    /** Per-machine phase offset so neighbouring machines do not blink in sync. */
    public static double clock(double worldTime, int x, int y, int z) {
        return worldTime + Math.floorMod((long) x * 31L + (long) y * 17L + (long) z * 47L, 480);
    }

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private static double wave(double turns) {
        return 0.5 + 0.5 * StrictMath.cos(2 * Math.PI * turns);
    }

    private static double pattern(int mode, double position, double time) {
        double glow = switch (mode) {
            case 0 -> Math.pow(wave(position - time / 64.0), 6.0);
            case 1 -> Math.pow(wave(position * 2.0 + time / 80.0), 4.0);
            case 2 -> wave(time / 48.0) * (0.4 + 0.6 * wave(position * 3.0));
            default -> Math.pow(wave(position * 5.0 - time / 40.0), 3.0);
        };
        return 0.25 + 0.75 * glow;
    }

    public static double bulb(int index, int count, double time) {
        double cycle = time - Math.floor(time / 480.0) * 480.0;
        int mode = (int) (cycle / 120.0);
        double into = cycle - mode * 120;
        double position = (double) Math.floorMod(index, count) / count;
        double now = pattern(mode, position, time);
        double before = pattern(Math.floorMod(mode - 1, 4), position, time);
        double w = smooth(into / 14.0);
        return before * (1.0 - w) + now * w;
    }

    public static double beacon(double time) {
        return 0.62 + 0.38 * wave(time / 64.0);
    }

    public static double afterGame(double game, double idle, double sinceEnd) {
        double w = smooth(sinceEnd / 14.0);
        return game * (1.0 - w) + idle * w;
    }
}
