package de.tigges.kaboofcasino.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * {@code config.yml} -> {@link CasinoConfig}. The live file is created from the jar copy, missing keys are merged in
 * (a new jar never replaces the live file and {@code getString(path, def)} ignores jar defaults), every value is
 * type- and range-checked; a bad value gives an English warning and the default. Thread: MAIN.
 */
public final class ConfigLoader {

    public static final String FILE = "config.yml";
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");

    /** Result: the config plus warnings for the console. */
    public record Loaded(CasinoConfig config, List<String> warnings, int added) {
    }

    private ConfigLoader() {
    }

    public static Loaded load(Path file, Supplier<java.io.InputStream> jarDefaults) {
        YamlFiles.Merged merged = YamlFiles.loadMerged(file, jarDefaults, FILE);
        List<String> warnings = new ArrayList<>(merged.warnings());
        CasinoConfig config = read(merged.yaml(), warnings);
        return new Loaded(config, warnings, merged.added());
    }

    static CasinoConfig read(YamlConfiguration y, List<String> warnings) {
        CasinoConfig d = CasinoConfig.DEFAULTS;
        Reader r = new Reader(y, warnings);
        CasinoConfig.Pack pack = new CasinoConfig.Pack(
                r.bool("pack.enabled", d.pack().enabled()),
                r.bool("pack.enforce", d.pack().enforce()),
                r.string("pack.serve", d.pack().serve(), s -> s.equals(CasinoConfig.Pack.MINECRAFT_PORT)
                        || s.equals(CasinoConfig.Pack.HTTP_PORT)),
                r.string("pack.url-host", d.pack().urlHost(), s -> !s.isBlank() && !s.contains("/")),
                r.integer("pack.url-port", d.pack().urlPort(), 1, 65535),
                r.integer("pack.port", d.pack().port(), 1, 65535),
                r.string("pack.bind", d.pack().bind(), s -> !s.isBlank()),
                r.integer("pack.timeout-seconds", d.pack().timeoutSeconds(), 5, 3600));
        List<String> items = r.keys("bets.items", d.bets().items());
        if (items.isEmpty()) {
            warnings.add(FILE + ": bets.items is empty; using the mod's 18 bet items");
            items = d.bets().items();
        }
        CasinoConfig.Bets bets = new CasinoConfig.Bets(r.integer("bets.max-stake", d.bets().maxStake(), 1, 64), items,
                r.string("bets.cap-per", d.bets().capPer(), s -> s.equals(CasinoConfig.Bets.PER_SLOT)
                        || s.equals(CasinoConfig.Bets.PER_ROUND)));
        String mode = r.string("machines.break", d.machines().breakMode(),
                s -> s.equals("owner") || s.equals("anyone"));
        return new CasinoConfig(pack, bets,
                new CasinoConfig.Rounds(r.integer("rounds.max-open", d.rounds().maxOpen(), 1, 16)),
                new CasinoConfig.Owed(r.integer("owed.delivery-ticks", d.owed().deliveryTicks(), 1, 1200)),
                new CasinoConfig.Machines(mode, r.integer("machines.verify-ticks", d.machines().verifyTicks(), 20,
                        12_000)),
                new CasinoConfig.Safety(r.integer("safety.combat-seconds", d.safety().combatSeconds(), 0, 600),
                        r.integer("safety.monster-radius", d.safety().monsterRadius(), 0, 64),
                        r.integer("safety.tnt-radius", d.safety().tntRadius(), 0, 64),
                        r.integer("safety.sweep-ticks", d.safety().sweepTicks(), 1, 20)),
                new CasinoConfig.Gui(r.integer("gui.click-guard-ticks", d.gui().clickGuardTicks(), 0, 40),
                        r.integer("gui.idle-close-seconds", d.gui().idleCloseSeconds(), 0, 3600)),
                game(r, "slot", d.slot()), game(r, "wheel", d.wheel()), game(r, "plinko", d.plinko()),
                new CasinoConfig.Lights(r.integer("lights.max-updates-per-tick", d.lights().maxUpdatesPerTick(), 1,
                        100)));
    }

    private static CasinoConfig.Slot game(Reader r, String section, CasinoConfig.Slot d) {
        return new CasinoConfig.Slot(r.integer(section + ".win-chance", d.winChance(), -1, 100),
                r.integer(section + ".idle-light-ticks", d.idleLightTicks(), 0, 200),
                r.integer(section + ".idle-light-range", d.idleLightRange(), 0, 128));
    }

    /** Typed, range-checked reads with warnings. */
    private record Reader(YamlConfiguration y, List<String> warnings) {

        int integer(String path, int def, int min, int max) {
            Object v = y.get(path);
            if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    && n.doubleValue() >= min && n.doubleValue() <= max) {
                return n.intValue();
            }
            warnings.add(FILE + ": " + path + " must be a whole number " + min + ".." + max + " (got " + v
                    + "); using " + def);
            return def;
        }

        boolean bool(String path, boolean def) {
            Object v = y.get(path);
            if (v instanceof Boolean b) {
                return b;
            }
            warnings.add(FILE + ": " + path + " must be true or false (got " + v + "); using " + def);
            return def;
        }

        String string(String path, String def, java.util.function.Predicate<String> valid) {
            Object v = y.get(path);
            if (v instanceof String s && valid.test(s.strip())) {
                return s.strip();
            }
            warnings.add(FILE + ": " + path + " is invalid (got " + v + "); using " + def);
            return def;
        }

        List<String> keys(String path, List<String> def) {
            Object v = y.get(path);
            if (!(v instanceof List<?> list)) {
                warnings.add(FILE + ": " + path + " must be a list; using the defaults");
                return def;
            }
            Set<String> out = new LinkedHashSet<>();
            for (Object o : list) {
                String s = String.valueOf(o).strip().toLowerCase(Locale.ROOT);
                if (!s.contains(":")) {
                    s = "minecraft:" + s;
                }
                if (KEY.matcher(s).matches()) {
                    out.add(s);
                } else {
                    warnings.add(FILE + ": " + path + " entry '" + o + "' is not an item key; dropped");
                }
            }
            return List.copyOf(out);
        }
    }
}
