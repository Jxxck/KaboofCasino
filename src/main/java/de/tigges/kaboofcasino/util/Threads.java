package de.tigges.kaboofcasino.util;

import java.util.function.BooleanSupplier;

/** Thread assertions: everything Bukkit on MAIN, JDBC only on the DB thread. Off until enabled (unit tests). */
public final class Threads {

    private static volatile BooleanSupplier mainThread;

    private Threads() {
    }

    public static void enableChecks(BooleanSupplier isMainThread) {
        mainThread = isMainThread;
    }

    public static void disableChecks() {
        mainThread = null;
    }

    public static void requireMain() {
        BooleanSupplier check = mainThread;
        if (check != null && !check.getAsBoolean()) {
            throw new IllegalStateException("KaboofCasino: must run on the server main thread, not "
                    + Thread.currentThread().getName());
        }
    }

    public static void requireNotMain() {
        BooleanSupplier check = mainThread;
        if (check != null && check.getAsBoolean()) {
            throw new IllegalStateException("KaboofCasino: blocking work on the server main thread");
        }
    }
}
