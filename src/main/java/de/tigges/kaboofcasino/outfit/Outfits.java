package de.tigges.kaboofcasino.outfit;

import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.wager.Chips;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The casino outfits (not in MineVegas): suits, the croupier's vest and an evening gown, four pieces each, sold in the
 * Chip-Shop's own tab and craftable from wool and one accent. Every piece is the leather armor item, so it protects and
 * wears out exactly like leather; the look comes from the pack: {@code item_model} for the icon and the {@code
 * equippable} asset {@code kaboofcasino:outfit_<style>} for the body. Hats have no asset, which makes the client draw
 * the item's own model on the head (a real 3D hat, the pack's {@code head} display context). Identity is the PDC tag
 * {@code kaboofcasino:item} like every casino item, so the crafting veto keeps them out of the dye and repair recipes.
 * Thread: MAIN.
 */
public final class Outfits {

    /** A piece of an outfit with its recipe shape ({@code W} the style's wool, {@code A} its accent). */
    public enum Piece {
        HAT("hat", List.of("WAW", "W W")),
        JACKET("jacket", List.of("W W", "WAW", "WWW")),
        TROUSERS("trousers", List.of("WAW", "W W", "W W")),
        SHOES("shoes", List.of("W W", "WAW"));

        private final String id;
        private final List<String> pattern;

        Piece(String id, List<String> pattern) {
            this.id = id;
            this.pattern = pattern;
        }

        public String id() {
            return id;
        }
    }

    /**
     * An outfit: its id (the pack's {@code OutfitArt} styles), recipe wool and accent, the names of the four pieces in
     * German and in English.
     */
    public enum Style {
        SMOKING("smoking", "minecraft:black_wool", "minecraft:white_wool",
                List.of("Zylinder", "Smoking-Jackett", "Smoking-Hose", "Lackschuhe"),
                List.of("Top Hat", "Tuxedo Jacket", "Tuxedo Trousers", "Patent Leather Shoes")),
        CROUPIER("croupier", "minecraft:green_wool", "minecraft:gold_nugget",
                List.of("Croupier-Schirm", "Croupier-Weste", "Croupier-Hose", "Croupier-Schuhe"),
                List.of("Croupier Visor", "Croupier Vest", "Croupier Trousers", "Croupier Shoes")),
        RUBIN("rubin", "minecraft:red_wool", "minecraft:black_wool",
                List.of("Rubin-Fedora", "Rubin-Samtjackett", "Rubin-Anzughose", "Rubin-Schuhe"),
                List.of("Ruby Fedora", "Ruby Velvet Jacket", "Ruby Suit Trousers", "Ruby Shoes")),
        SAPHIR("saphir", "minecraft:blue_wool", "minecraft:gold_nugget",
                List.of("Saphir-Fedora", "Saphir-Jackett", "Saphir-Anzughose", "Saphir-Schuhe"),
                List.of("Sapphire Fedora", "Sapphire Jacket", "Sapphire Suit Trousers", "Sapphire Shoes")),
        ELFENBEIN("elfenbein", "minecraft:white_wool", "minecraft:black_wool",
                List.of("Panama-Hut", "Dinner-Jackett", "Dinner-Hose", "Zweifarbige Schuhe"),
                List.of("Panama Hat", "Dinner Jacket", "Dinner Trousers", "Two-Tone Shoes")),
        HIGHROLLER("highroller", "minecraft:white_wool", "minecraft:gold_ingot",
                List.of("Goldener Zylinder", "High-Roller-Jackett", "High-Roller-Hose", "Goldene Schuhe"),
                List.of("Golden Top Hat", "High Roller Jacket", "High Roller Trousers", "Golden Shoes")),
        ABENDKLEID("abendkleid", "minecraft:red_wool", "minecraft:gold_nugget",
                List.of("Diadem", "Abendkleid", "Abendkleid-Rock", "Goldene Pumps"),
                List.of("Tiara", "Evening Gown", "Evening Gown Skirt", "Golden Pumps"));

        private final String id;
        private final String wool;
        private final String accent;
        private final List<String> german;
        private final List<String> english;

        Style(String id, String wool, String accent, List<String> german, List<String> english) {
            this.id = id;
            this.wool = wool;
            this.accent = accent;
            this.german = german;
            this.english = english;
        }

        /** The id in item ids and pack files (kept as first released: items in the world carry it). */
        public String id() {
            return id;
        }

        public String wool() {
            return wool;
        }

        public String accent() {
            return accent;
        }

        /** The piece's name in the casino's language. */
        public String name(Piece piece) {
            return de.tigges.kaboofcasino.config.Language.text(english.get(piece.ordinal()),
                    german.get(piece.ordinal()));
        }

        public String germanName(Piece piece) {
            return german.get(piece.ordinal());
        }

        public String englishName(Piece piece) {
            return english.get(piece.ordinal());
        }
    }

    private Outfits() {
    }

    /** The catalog id of a piece; the Chip-Shop files every {@code outfit_} item under its own tab. */
    public static String id(Style style, Piece piece) {
        return "casinodeco:outfit_" + style.id() + "_" + piece.id();
    }

    /** Every piece's catalog id, outfit by outfit. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Style s : Style.values()) {
            for (Piece p : Piece.values()) {
                out.add(id(s, p));
            }
        }
        return List.copyOf(out);
    }

    /** One shaped recipe per piece from vanilla ingredients only, so the recipe book shows and fills them correctly. */
    public static List<Recipes.Def> recipes() {
        List<String> unlock = new ArrayList<>();
        for (int d : Chips.DENOMINATIONS) {
            unlock.add(Chips.key(d));
        }
        List<Recipes.Def> out = new ArrayList<>();
        for (Style s : Style.values()) {
            for (Piece p : Piece.values()) {
                out.add(new Recipes.Def("outfit/" + s.id() + "_" + p.id(), false, p.pattern,
                        Map.of('W', s.wool(), 'A', s.accent()), List.of(), id(s, p), 1, unlock));
            }
        }
        return List.copyOf(out);
    }

    /** At enable, before {@code Recipes.register}: the pieces join the item catalog, their recipes the casino's. */
    public static void enable() {
        for (Style s : Style.values()) {
            for (Piece p : Piece.values()) {
                ItemCatalog.register(id(s, p), amount -> create(s, p, amount));
            }
        }
        Recipes.own("outfits", recipes());
    }

    static ItemStack create(Style style, Piece piece, int amount) {
        ItemStack stack = ItemStack.of(switch (piece) {
            case HAT -> Material.LEATHER_HELMET;
            case JACKET -> Material.LEATHER_CHESTPLATE;
            case TROUSERS -> Material.LEATHER_LEGGINGS;
            case SHOES -> Material.LEATHER_BOOTS;
        }, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("kaboofcasino", "outfit/" + style.id() + "_" + piece.id()));
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(style.name(piece)));
        Equippable.Builder equippable = Equippable.equippable(switch (piece) {
            case HAT -> EquipmentSlot.HEAD;
            case JACKET -> EquipmentSlot.CHEST;
            case TROUSERS -> EquipmentSlot.LEGS;
            case SHOES -> EquipmentSlot.FEET;
        }).equipSound(Key.key("minecraft", "item.armor.equip_leather"));
        if (piece != Piece.HAT) {
            equippable.assetId(Key.key("kaboofcasino", "outfit_" + style.id()));
        }
        stack.setData(DataComponentTypes.EQUIPPABLE, equippable.build());
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM, PersistentDataType.STRING, id(style, piece)));
        return stack;
    }
}
