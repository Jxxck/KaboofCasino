package de.tigges.kaboofcasino.deco.full;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * Paper's {@code block-updates.disable-mushroom-block-updates}, read live on every call (never cached), so a
 * {@code /paper reload} or a changed config after a restart is seen at once.
 *
 * <p>Source: {@code io.papermc.paper.configuration.GlobalConfiguration.get().blockUpdates.disableMushroomBlockUpdates}
 * (javap of the 26.3-205 server jar: {@code public static GlobalConfiguration get()}, {@code public BlockUpdates
 * blockUpdates}, {@code public boolean disableMushroomBlockUpdates}). That class is server internals, not API, so it
 * is reached by reflection (members looked up once, read on every call). If the lookup fails (a future Paper), the
 * same flag is read through its effect instead: {@code HugeMushroomBlock.rotate} returns the state unchanged exactly
 * when it is set (public API {@code BlockData.rotate}, no side effects). The config file is never read.
 */
final class PaperSwitch {

    private static final String CLASS = "io.papermc.paper.configuration.GlobalConfiguration";

    /** The reflective path to the flag (looked up once). */
    record Access(Method get, Field blockUpdates, Field disable) {

        /** Reads the flag now; empty if the configuration is not there (yet). */
        Optional<Boolean> read() {
            try {
                Object config = get.invoke(null);
                if (config == null) {
                    return Optional.empty();
                }
                Object updates = blockUpdates.get(config);
                return updates == null ? Optional.empty() : Optional.of(disable.getBoolean(updates));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    private static volatile Optional<Access> access;

    private PaperSwitch() {
    }

    /** True if mushroom block updates are disabled (the full deco may work). */
    static boolean on() {
        return globalConfiguration().orElseGet(PaperSwitch::probe);
    }

    /** {@code GlobalConfiguration} or {@code rotate probe}: where {@link #on()} reads the flag from. */
    static String source() {
        return access().isPresent() ? "GlobalConfiguration" : "rotate probe";
    }

    /** The live config value, empty if Paper's class cannot be reached. */
    static Optional<Boolean> globalConfiguration() {
        return access().flatMap(Access::read);
    }

    /** The flag through its effect: with it set, a cap with only its north face does not turn. */
    static boolean probe() {
        BlockData probe = Bukkit.createBlockData(
                "minecraft:red_mushroom_block[down=false,east=false,north=true,south=false,up=false,west=false]");
        BlockData turned = probe.clone();
        turned.rotate(StructureRotation.CLOCKWISE_90);
        return turned.equals(probe);
    }

    private static Optional<Access> access() {
        Optional<Access> a = access;
        if (a == null) {
            a = lookup(Bukkit.getServer().getClass().getClassLoader(), CLASS);
            access = a;
        }
        return a;
    }

    /** {@code className.get().blockUpdates.disableMushroomBlockUpdates}, all public; empty if anything is missing. */
    static Optional<Access> lookup(ClassLoader loader, String className) {
        try {
            Class<?> global = Class.forName(className, false, loader);
            Method get = global.getMethod("get");
            Field blockUpdates = global.getField("blockUpdates");
            Field disable = blockUpdates.getType().getField("disableMushroomBlockUpdates");
            if (disable.getType() != boolean.class) {
                return Optional.empty();
            }
            return Optional.of(new Access(get, blockUpdates, disable));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return Optional.empty();
        }
    }
}
