package de.tigges.kaboofcasino.deco.full;

/** Stand-in with the shape of Paper's {@code io.papermc.paper.configuration.GlobalConfiguration} (PaperSwitchTest). */
public class FakeGlobalConfiguration {

    public static FakeGlobalConfiguration instance = new FakeGlobalConfiguration();

    public BlockUpdates blockUpdates = new BlockUpdates();

    public static FakeGlobalConfiguration get() {
        return instance;
    }

    /** {@code GlobalConfiguration$BlockUpdates} */
    public static class BlockUpdates {
        public boolean disableMushroomBlockUpdates;
    }

    /** A config whose flag is not a boolean (a changed Paper): must not be read. */
    public static class Renamed {
        public static Renamed get() {
            return new Renamed();
        }

        public Wrong blockUpdates = new Wrong();
    }

    /** A {@code BlockUpdates} with the flag as a String. */
    public static class Wrong {
        public String disableMushroomBlockUpdates = "true";
    }
}
