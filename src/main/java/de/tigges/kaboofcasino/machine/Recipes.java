package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.util.Keys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * The mod's crafting recipes for the shipped machine (games.md 13): reel -> mechanism -> slot machine.
 * Casino ingredients are paper, matched as {@code MaterialChoice(PAPER)}; {@link ItemVetoListener} checks the PDC tag
 * of every paper in the grid against {@link #paperRule}, so stale item components never break crafting.
 */
public final class Recipes {

    /** What the paper slots of a recipe must hold: a casino item, or {@code null} for plain paper. */
    public record PaperRule(CasinoItem expected) {
    }

    public static final NamespacedKey REEL = Keys.key("slot_machine_reel");
    public static final NamespacedKey MECHANISM = Keys.key("slot_machine_mechanism");
    public static final NamespacedKey SLOT_MACHINE = Keys.key("slot_machine");
    public static final List<NamespacedKey> ALL = List.of(REEL, MECHANISM, SLOT_MACHINE);

    private static final Map<NamespacedKey, PaperRule> RULES = Map.of(
            REEL, new PaperRule(null),
            MECHANISM, new PaperRule(CasinoItem.SLOT_MACHINE_REEL),
            SLOT_MACHINE, new PaperRule(CasinoItem.SLOT_MACHINE_MECHANISM));

    private Recipes() {
    }

    public static Optional<PaperRule> paperRule(NamespacedKey key) {
        return Optional.ofNullable(RULES.get(key));
    }

    /** Registers (or re-registers after /reload) the three recipes. */
    public static void register(Messages messages) {
        unregister();
        ShapedRecipe reel = new ShapedRecipe(REEL, CasinoItems.create(CasinoItem.SLOT_MACHINE_REEL, messages, 1));
        reel.shape(" P ", "ID ", " W ");
        reel.setIngredient('P', new RecipeChoice.MaterialChoice(Material.PAPER));
        reel.setIngredient('I', Material.IRON_INGOT);
        reel.setIngredient('D', Material.DIAMOND);
        reel.setIngredient('W', Material.RED_WOOL);
        Bukkit.addRecipe(reel);

        ShapedRecipe mechanism = new ShapedRecipe(MECHANISM,
                CasinoItems.create(CasinoItem.SLOT_MACHINE_MECHANISM, messages, 1));
        mechanism.shape("TTT", " P ", " R ");
        mechanism.setIngredient('T', new RecipeChoice.MaterialChoice(Material.PAPER));
        mechanism.setIngredient('P', Material.PISTON);
        mechanism.setIngredient('R', Material.REDSTONE);
        Bukkit.addRecipe(mechanism);

        ShapedRecipe machine = new ShapedRecipe(SLOT_MACHINE, CasinoItems.create(CasinoItem.SLOT_MACHINE, messages, 1));
        machine.shape("VWV", "GMG", "PRP");
        machine.setIngredient('V', Material.GLASS);
        machine.setIngredient('W', Material.RED_WOOL);
        machine.setIngredient('G', Material.GOLD_BLOCK);
        machine.setIngredient('M', new RecipeChoice.MaterialChoice(Material.PAPER));
        machine.setIngredient('P', new RecipeChoice.MaterialChoice(Tag.PLANKS));
        machine.setIngredient('R', Material.REDSTONE);
        Bukkit.addRecipe(machine);
    }

    public static void unregister() {
        for (NamespacedKey key : ALL) {
            Bukkit.removeRecipe(key);
        }
    }

    /** True if every paper in {@code matrix} satisfies the recipe's paper rule. */
    public static boolean papersValid(PaperRule rule, ItemStack[] matrix) {
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType() != Material.PAPER) {
                continue;
            }
            Optional<CasinoItem> item = CasinoItems.of(stack);
            if (rule.expected() == null ? CasinoItems.isCasinoItem(stack) : item.orElse(null) != rule.expected()) {
                return false;
            }
        }
        return true;
    }
}
