package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.visual.Displays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * The mod's floating machine names ({@code CasinoMachineNames}): a player who may use the croupier's hand and holds
 * it sees, over every machine within 8 blocks, its name or "{@code <Type> #<n>}". Here a text display per machine
 * that someone sees, hidden from everybody by default ({@code setVisibleByDefault(false)}) and shown only to those
 * players ({@code showEntity}); it goes when nobody sees it any more. Non-persistent and tagged like the rigs
 * ({@link Displays#text}). Height and anchor point per game as in the mod, its colours (gold text with shadow on the
 * dark green box), its size (0.011 blocks per pixel, at most 4 blocks wide). Thread: MAIN.
 */
final class AdminLabels {

    /** {@code distanceToSqr(atCenterOf(machine)) > 64}: 8 blocks. */
    static final double RANGE_SQ = 64.0;
    static final TextColor TEXT = TextColor.color(0xFFD675);
    static final Color BACKGROUND = Color.fromARGB(0xF0, 0x22, 0x49, 0x38);
    /** A text display draws 0.025 blocks per pixel; the mod 0.011, at most 4 blocks for the whole label. */
    static final float MAX_SCALE = 0.011f / 0.025f;

    private static final class Label {
        final TextDisplay entity;
        String text;
        final Set<UUID> viewers = new HashSet<>();

        Label(TextDisplay entity, String text) {
            this.entity = entity;
            this.text = text;
        }
    }

    private final CasinoContext ctx;
    private final Predicate<Player> sees;
    private final Map<UUID, Label> labels = new HashMap<>();

    AdminLabels(CasinoContext ctx, Predicate<Player> sees) {
        this.ctx = ctx;
        this.sees = sees;
    }

    /** The mod's label position over the machine's block-entity block (our anchor) per game. */
    static double[] offset(MachineType type) {
        return switch (type) {
            case ROULETTE_TABLE -> new double[] {1.0, 2.8, 1.0};
            case BLACKJACK_TABLE, POKER_TABLE -> new double[] {0.5, 3.4, 0.5};
            case SLOT_MACHINE -> new double[] {0.5, 4.1, 0.5};
            case FORTUNE_WHEEL, PLINKO, DUCK_DERBY -> new double[] {0.5, 3.5, 0.5};
        };
    }

    /** The label's text as the default font measures it (German names; approximate advances). */
    static String plain(MachineType type, String name, int number) {
        return !name.isBlank() ? name : AdminTexts.GERMAN.get("block.roulettecasino." + type.id()) + " #" + number;
    }

    /** Approximate default-font width in pixels (most glyphs 6, narrow ones less). */
    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += switch (text.charAt(i)) {
                case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
                case 'l', '`' -> 3;
                case 'I', 't', ' ', '(', ')', '[', ']', '{', '}', '"', '*' -> 4;
                case 'f', 'k', '<', '>' -> 5;
                case '@', '~' -> 7;
                default -> 6;
            };
        }
        return w;
    }

    /** {@code min(0.011, 4 / width)} per pixel as a text display scale. */
    static float scale(String text) {
        return Math.min(MAX_SCALE, 4.0f / Math.max(1, width(text)) / 0.025f);
    }

    void tick() {
        Map<UUID, Set<Player>> wanted = new HashMap<>();
        Collection<Machine> all = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sees.test(player)) {
                continue;
            }
            if (all == null) {
                all = ctx.machines().all();
            }
            Location at = player.getLocation();
            String world = player.getWorld().getKey().asString();
            for (Machine m : all) {
                Pos a = m.anchor();
                if (m.record().number() <= 0 || !a.world().equals(world)) {
                    continue;
                }
                double dx = a.x() + 0.5 - at.getX();
                double dy = a.y() + 0.5 - at.getY();
                double dz = a.z() + 0.5 - at.getZ();
                if (dx * dx + dy * dy + dz * dz <= RANGE_SQ) {
                    wanted.computeIfAbsent(m.id(), id -> new HashSet<>()).add(player);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, Label>> it = labels.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Label> e = it.next();
            if (!wanted.containsKey(e.getKey()) || !e.getValue().entity.isValid()) {
                remove(e.getValue());
                it.remove();
            }
        }
        for (Map.Entry<UUID, Set<Player>> e : wanted.entrySet()) {
            Machine m = ctx.machines().byId(e.getKey()).orElse(null);
            if (m == null) {
                continue;
            }
            String text = plain(m.type(), m.record().own().name(), m.record().number());
            Label label = labels.get(m.id());
            if (label == null) {
                label = spawn(m, text);
                if (label == null) {
                    continue;
                }
                labels.put(m.id(), label);
            } else if (!label.text.equals(text)) {
                label.text = text;
                label.entity.text(component(m));
                label.entity.setTransformation(transformation(text));
            }
            Set<UUID> now = new HashSet<>();
            for (Player viewer : e.getValue()) {
                now.add(viewer.getUniqueId());
                if (label.viewers.add(viewer.getUniqueId())) {
                    viewer.showEntity(ctx.plugin(), label.entity);
                }
            }
            for (Iterator<UUID> it = label.viewers.iterator(); it.hasNext(); ) {
                UUID id = it.next();
                if (!now.contains(id)) {
                    Player gone = Bukkit.getPlayer(id);
                    if (gone != null) {
                        gone.hideEntity(ctx.plugin(), label.entity);
                    }
                    it.remove();
                }
            }
        }
    }

    private Label spawn(Machine m, String text) {
        World world = Bukkit.getWorld(org.bukkit.NamespacedKey.fromString(m.anchor().world()));
        if (world == null) {
            return null;
        }
        double[] o = offset(m.type());
        // the mod's box spans 3 px above to 12 px below its point; a text display grows upwards from its own
        Location at = new Location(world, m.anchor().x() + o[0], m.anchor().y() + o[1] - 0.11, m.anchor().z() + o[2]);
        TextDisplay display = Displays.text(world, at, m.id(), d -> {
            d.setVisibleByDefault(false);
            d.text(component(m));
            d.setBackgroundColor(BACKGROUND);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setLineWidth(1000);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(transformation(text));
        });
        return new Label(display, text);
    }

    private static Component component(Machine m) {
        return AdminTexts.displayName(m.type(), m.record().own().name(), m.record().number()).color(TEXT);
    }

    private static Transformation transformation(String text) {
        float s = scale(text);
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    private void remove(Label label) {
        if (label.entity.isValid()) {
            for (UUID id : label.viewers) {
                Player viewer = Bukkit.getPlayer(id);
                if (viewer != null) {
                    viewer.hideEntity(ctx.plugin(), label.entity);
                }
            }
        }
        Displays.remove(label.entity);
    }

    /** Removes every label (plugin disable). */
    void clear() {
        for (Label label : labels.values()) {
            remove(label);
        }
        labels.clear();
    }
}
