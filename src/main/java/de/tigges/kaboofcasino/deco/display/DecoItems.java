package de.tigges.kaboofcasino.deco.display;

import de.tigges.kaboofcasino.machine.ItemCatalog;
import de.tigges.kaboofcasino.machine.Recipes;
import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The placing items of the display decoration. Stairs and slabs are the vanilla block item of their carrier, so vanilla
 * places them with all its rules (render.md 4.4); everything on barrier or light carriers is paper (placed by the
 * plugin, like the machines; never a barrier or light item, which would be an unbreakable block for anybody holding
 * it). All carry {@code item_model = <mod id>} (the mod's own item definition in the pack), the translated block name
 * ({@code block.<ns>.<id>}, German fallback) and the PDC tag {@code kaboofcasino:item = <mod id>} (the identity).
 * Thread: MAIN.
 */
public final class DecoItems {

    private DecoItems() {
    }

    /**
     * The stairs and slabs the mod does not have, from their block, shaped and counted like its marble ones, and the
     * square signs: four small signs make a 2 x 2, four of those a 4 x 4.
     */
    public static List<Recipes.Def> ownRecipes() {
        List<Recipes.Def> out = new ArrayList<>();
        for (DecoType t : DecoType.values()) {
            if (t.own() && t.family() != null) {
                String block = "casinodeco:" + t.family();
                boolean stairs = t.carrier() == DecoType.Carrier.STAIRS;
                out.add(new Recipes.Def("casinodeco/" + t.path(), false,
                        stairs ? List.of("B  ", "BB ", "BBB") : List.of("BBB"), Map.of('B', block), List.of(), t.id(),
                        stairs ? 4 : 6, List.of(block)));
            }
        }
        out.add(new Recipes.Def("casinodeco/casino_sign_2x2", false, List.of("SS", "SS"),
                Map.of('S', DecoType.SMALL_CASINO_SIGN.id()), List.of(), DecoType.CASINO_SIGN_2X2.id(), 1,
                List.of(DecoType.SMALL_CASINO_SIGN.id())));
        out.add(new Recipes.Def("casinodeco/casino_sign_4x4", false, List.of("SS", "SS"),
                Map.of('S', DecoType.CASINO_SIGN_2X2.id()), List.of(), DecoType.CASINO_SIGN_4X4.id(), 1,
                List.of(DecoType.CASINO_SIGN_2X2.id())));
        return List.copyOf(out);
    }

    /** The item of {@code type}, {@code amount} of it. */
    public static ItemStack create(DecoType type, int amount) {
        ItemStack stack = ItemStack.of(type.vanillaCarrier() ? type.vanilla() : Material.PAPER, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(type.id()));
        stack.setData(DataComponentTypes.ITEM_NAME, Component.translatable(type.translationKey(), type.german()));
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM, PersistentDataType.STRING, type.id()));
        return stack;
    }

    /** The decoration type of a stack (by its PDC tag only), empty for anything else. */
    public static Optional<DecoType> of(ItemStack stack) {
        return ItemCatalog.idOf(stack).flatMap(DecoType::byId).filter(t -> t.vanillaCarrier()
                ? stack.getType() == t.vanilla() : stack.getType() == Material.PAPER);
    }

    /** Registers every type in the item catalog ({@code /casino give}, the mod's recipes). */
    public static void register() {
        for (DecoType t : DecoType.values()) {
            ItemCatalog.register(t.id(), amount -> create(t, amount));
        }
        Recipes.own("deco", ownRecipes());
    }
}
