package de.tigges.kaboofcasino.config;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /** A messages file of a version up to 0.19.0: renamed keys keep their texts, the Discord texts go. */
    static int migrate(YamlConfiguration live) {
        int n = 0;
        for (String[] r : RENAMES) {
            n += YamlFiles.rename(live, r[0], r[1]);
        }
        n += YamlFiles.remove(live, "discord");
        return n;
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
