package de.tigges.kaboofcasino.deco.full;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The live read of Paper's switch, on a class with GlobalConfiguration's shape. */
class PaperSwitchTest {

    private final ClassLoader loader = getClass().getClassLoader();

    @AfterEach
    void reset() {
        FakeGlobalConfiguration.instance = new FakeGlobalConfiguration();
    }

    @Test
    void readsTheFlagLiveOnEveryCall() {
        Optional<PaperSwitch.Access> access = PaperSwitch.lookup(loader, FakeGlobalConfiguration.class.getName());
        assertTrue(access.isPresent());
        assertEquals(Optional.of(false), access.get().read());
        FakeGlobalConfiguration.instance.blockUpdates.disableMushroomBlockUpdates = true;
        assertEquals(Optional.of(true), access.get().read(), "a changed value is seen without a new lookup");
        FakeGlobalConfiguration replaced = new FakeGlobalConfiguration(); // like /paper reload: a new instance
        FakeGlobalConfiguration.instance = replaced;
        assertEquals(Optional.of(false), access.get().read());
        replaced.blockUpdates = null;
        assertEquals(Optional.empty(), access.get().read(), "half-built config: unknown, not false");
        FakeGlobalConfiguration.instance = null;
        assertEquals(Optional.empty(), access.get().read());
    }

    @Test
    void anotherShapeIsNotRead() {
        assertTrue(PaperSwitch.lookup(loader, "io.papermc.paper.configuration.GlobalConfiguration").isEmpty(),
                "not on the test classpath: falls back to the rotate probe");
        assertTrue(PaperSwitch.lookup(loader, FakeGlobalConfiguration.Renamed.class.getName()).isEmpty(),
                "a non-boolean flag is not taken");
        assertTrue(PaperSwitch.lookup(loader, String.class.getName()).isEmpty());
    }
}
