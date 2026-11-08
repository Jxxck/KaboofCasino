package de.tigges.kaboofcasino.game;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * The bet slots' chip selection (PLAN-2 C2), shared by every game menu: a slot holds a {@link Chips.Selection}
 * picked from the player's own chips (nothing moves until the round is committed), its stake is the selection's chip
 * value. A click on a chip stack in the inventory adds chips of that denomination to the active slot: left = the
 * clicked stack, right = one, shift-left = all of them; never more than the player has unselected and never past
 * {@code bets.max-stake} (a chip value per slot, or per round with {@code bets.cap-per: round}).
 */
public final class ChipStakes {

    private ChipStakes() {
    }

    /**
     * How many chips of {@code denomination} a click adds (pure).
     *
     * @param stack     the clicked stack's size
     * @param ownedFree the player's chips of this denomination not selected in any slot yet
     * @param current   the active slot's value
     * @param others    the value of the other slots that counts against {@code max} (0 with the cap per slot)
     */
    public static long add(ClickType click, int denomination, int stack, long ownedFree, long current, long others,
                           long max) {
        long want = switch (click) {
            case RIGHT -> 1;
            case SHIFT_LEFT -> ownedFree;
            default -> stack;
        };
        long room = Math.max(0, max - others - current) / denomination;
        return Math.max(0, Math.min(Math.min(want, ownedFree), room));
    }

    /** True if the click wanted more chips than {@link #add} allowed because of the maximum (pure). */
    public static boolean capped(ClickType click, int denomination, int stack, long ownedFree, long current,
                                 long others, long max) {
        long want = switch (click) {
            case RIGHT -> 1;
            case SHIFT_LEFT -> ownedFree;
            default -> stack;
        };
        long room = Math.max(0, max - others - current) / denomination;
        return Math.min(want, ownedFree) > room;
    }

    /**
     * The icon of a filled bet slot: the slot's largest chip (a display copy, never a real chip), named with the
     * value, one lore line per chip kind, then {@code lore}; glint if it is the active slot.
     */
    public static ItemStack icon(CasinoContext ctx, Chips.Selection selection, List<Component> lore, boolean glint) {
        Messages m = ctx.messages().get();
        List<Component> lines = new ArrayList<>();
        for (ItemAmount a : selection.amounts()) {
            int d = Chips.denomination(a.item()).orElseThrow();
            lines.add(m.render(Msg.CHIPS_STAKE_LINE, Messages.text("count", Long.toString(a.count())),
                    Messages.component("chip", ChipItems.name(d))));
        }
        lines.addAll(lore);
        ItemStack icon = DisplayCopies.model(Chips.key(selection.largest()), m.render(Msg.CHIPS_STAKE,
                Messages.text("value", ItemNames.value(selection.value()))), lines);
        if (glint) {
            icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return icon;
    }

    /** The icon of a chip value (a confirmed derby stake, a round menu's stake cell): the largest chip it holds. */
    public static ItemStack valueIcon(long value, Component name, List<Component> lore) {
        List<ItemAmount> parts = Chips.breakdown(Math.max(1, value));
        int largest = Chips.denomination(parts.getFirst().item()).orElseThrow();
        return DisplayCopies.model(Chips.key(largest), name, lore);
    }

    /** What a round menu's stake cell shows of a stake: the largest chip of a chip value, or the ore itself. */
    public static String cellItem(ItemAmount stake) {
        if (!Chips.isValue(stake.item())) {
            return stake.item();
        }
        return Chips.breakdown(Math.max(1, stake.count())).getFirst().item();
    }

    /**
     * A round menu's stake cell: a chip's look (named with {@code chips}, the chip value, if above 0) or a plain ore
     * stack, {@code amount} as the stack count (1..99).
     */
    public static ItemStack cellIcon(String item, int amount, long chips) {
        if (Chips.denomination(item).isEmpty()) {
            return DisplayCopies.of(de.tigges.kaboofcasino.wager.PlayerAccount.material(item), amount, null,
                    List.of());
        }
        Component name = chips > 0 ? Component.text(ItemNames.value(chips) + " Chips",
                net.kyori.adventure.text.format.NamedTextColor.GOLD) : null;
        ItemStack icon = DisplayCopies.model(item, name, List.of());
        int n = Math.max(1, Math.min(99, amount));
        if (n > 1) {
            icon.setData(DataComponentTypes.MAX_STACK_SIZE, 99);
            icon.setAmount(n);
        }
        return icon;
    }

    /** The "play with chips" book (the mod's bet list, drawn left of its window): the six chips. */
    public static ItemStack book(CasinoContext ctx) {
        Messages m = ctx.messages().get();
        List<Component> lore = new ArrayList<>();
        for (int d : Chips.DENOMINATIONS) {
            lore.add(m.render(Msg.CHIPS_BOOK_LINE, Messages.component("chip", ChipItems.name(d))));
        }
        lore.addAll(m.lines(Msg.CHIPS_BOOK_LORE));
        return DisplayCopies.of(Material.BOOK, 1, m.render(Msg.CHIPS_BOOK_TITLE), lore);
    }

    /** The max-stake message for a capped click. */
    public static void tellMax(CasinoContext ctx, org.bukkit.entity.Player player) {
        boolean round = ctx.config().get().bets().capPerRound();
        ctx.messages().get().actionBar(player, round ? Msg.CHIPS_MAX_ROUND : Msg.CHIPS_MAX,
                Messages.text("max", ItemNames.value(ctx.config().get().bets().maxStake())));
    }
}
