package de.tigges.kaboofcasino.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The texts file of the casino's language ({@code messages_en.yml} or {@code messages_de.yml},
 * {@link Language#messagesFile()}): MiniMessage templates by {@link Msg}. Player names and other player input only
 * ever go in through {@link #text} (unparsed). Thread: MAIN.
 */
public final class Messages {

    /** Keys of versions up to 0.19.0 and their names now. */
    static final String[][] RENAMES = {
        {"board.category.gewinn", "board.category.win"},
        {"board.category.multiplikator", "board.category.multiplier"},
        {"board.category.netto", "board.category.net"},
        {"board.category.einsatz", "board.category.staked"},
        {"board.category.runden", "board.category.rounds"},
        {"board.category.spielautomat", "board.category.slots"},
        {"board.category.gluecksrad", "board.category.wheel"},
        {"board.category.entenrennen", "board.category.derby"},
        {"board.period.woche", "board.period.week"},
        {"board.period.gesamt", "board.period.total"},
        {"chipshop.category.boeden", "chipshop.category.floors"},
        {"chipshop.category.moebel", "chipshop.category.furniture"},
        {"chipshop.category.licht", "chipshop.category.lights"},
        {"chipshop.category.technik", "chipshop.category.tech"},
        {"chipshop.category.maschinen", "chipshop.category.machines"},
    };
    /**
     * The author and credit texts were reworded in 0.20.1. A live file still holds the wording it was created with, so
     * a text that is one of the earlier defaults (German or English, any version) gives way to the new default, while a
     * text an admin wrote himself stays. Earlier defaults are listed by {@link #fingerprint}.
     */
    static final Map<String, Set<String>> REWORDED = Map.of(
            "guide.credits", Set.of("4317a60c4f618ee1", "868dc38aa0bfd1d8"),
            "pack.prompt", Set.of("21bbb3c8511f676c", "c49c22e288c72856"),
            "command.info", Set.of("8ba211828b71830b", "d9bb31e2809334ad", "da665035846ba0a0", "e85389d15711d8aa"),
            "command.credits", Set.of("37ab9ada07fb7604", "7bc280e06401eb6c", "9ece1010fa2b0d20", "c5ce719adc4d07fd",
                    "eb1395295887b589"),
            "item.slot-machine-lore", Set.of("c26c6a04e1b2860d"));
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<Msg, String> templates;

    private Messages(Map<Msg, String> templates) {
        this.templates = templates;
    }

    /** Loaded file plus warnings. */
    public record Loaded(Messages messages, List<String> warnings, int added) {
    }

    /** @param file the live file; its name ({@code messages_<language>.yml}) is the one named in warnings */
    public static Loaded load(Path file, Supplier<InputStream> jarDefaults) {
        YamlFiles.Merged merged = YamlFiles.loadMerged(file, jarDefaults, file.getFileName().toString(),
                Messages::migrate);
        return new Loaded(of(merged.yaml()), merged.warnings(), merged.added());
    }

    /**
     * A messages file of an older version: renamed keys keep their texts, the Discord texts go, and the reworded
     * author and credit texts ({@link #REWORDED}) are dropped so that the merge puts the new defaults in their place.
     */
    static int migrate(YamlConfiguration live) {
        int n = 0;
        for (String[] r : RENAMES) {
            n += YamlFiles.rename(live, r[0], r[1]);
        }
        n += YamlFiles.remove(live, "discord");
        for (Map.Entry<String, Set<String>> e : REWORDED.entrySet()) {
            String text = live.getString(e.getKey());
            if (text != null && e.getValue().contains(fingerprint(text))) {
                live.set(e.getKey(), null); // not counted: the merge counts the default it adds here
            }
        }
        return n;
    }

    /** The first 16 hex digits of a text's SHA-256 (UTF-8). */
    static String fingerprint(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Messages of(YamlConfiguration yaml) {
        Map<Msg, String> map = new EnumMap<>(Msg.class);
        for (Msg m : Msg.values()) {
            map.put(m, yaml.getString(m.key(), "<red>?" + m.key() + "?</red>"));
        }
        return new Messages(map);
    }

    /** An unparsed placeholder (player names, item names typed by players). */
    public static TagResolver text(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public String raw(Msg msg) {
        return templates.get(msg);
    }

    public Component render(Msg msg, TagResolver... resolvers) {
        return MM.deserialize(templates.get(msg), resolvers).decoration(TextDecoration.ITALIC, false);
    }

    /** Chat line with the prefix. */
    public void send(Audience to, Msg msg, TagResolver... resolvers) {
        to.sendMessage(render(Msg.PREFIX).append(render(msg, resolvers)));
    }

    public void actionBar(Audience to, Msg msg, TagResolver... resolvers) {
        to.sendActionBar(render(msg, resolvers));
    }

    /** Multi-line templates: one component per line ({@code \n} in the template). */
    public List<Component> lines(Msg msg, TagResolver... resolvers) {
        return java.util.Arrays.stream(templates.get(msg).split("\n"))
                .map(line -> MM.deserialize(line, resolvers).decoration(TextDecoration.ITALIC, false))
                .toList();
    }
}
