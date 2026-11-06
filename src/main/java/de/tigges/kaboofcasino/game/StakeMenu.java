package de.tigges.kaboofcasino.game;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.Ledger;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * The three bet slots of the fortune wheel and Plinko (games.md 8/9 "Bets") as a selection: nothing moves until the
 * round is committed. One slot is active (gold frame when empty, glint when filled); clicks on the own inventory set
 * the active slot (left: that stack, right: +1, shift-left: all of that item); filling an empty slot moves the
 * marker to the next empty one. A slot click selects it, a left click on the active slot clears it, a right click
 * takes one away. Each slot holds at most {@code bets.max-stake} items (the mod: a stack per slot), or with
 * {@code bets.cap-per: round} all slots together. Each slot is its own stake, paid in its own item (the mod: one
 * ball per slot in Plinko).
 */
public abstract class StakeMenu extends GameMenu {

    /** The wheel's and Plinko's three bet slots. */
    public static final int SLOTS = 3;

    /** A filled slot: its index (the mod's slot number, which the fair-rounding roll uses) and the stake. */
    public record Stake(int slot, ItemAmount amount) {
    }

    protected final CasinoContext ctx;
    private final ItemAmount[] stakes;
    private int active;

    protected StakeMenu(UUID viewer, Component title, CasinoContext ctx) {
        this(viewer, title, ctx, SLOTS);
    }

    /** A menu with {@code slots} bet slots (the roulette: 9, three per colour). */
    protected StakeMenu(UUID viewer, Component title, CasinoContext ctx, int slots) {
        super(viewer, title);
        this.ctx = ctx;
        this.stakes = new ItemAmount[slots];
    }

    /** True if {@code slot} may be chosen now (the roulette: only the colour already staked). */
    protected boolean selectable(int slot) {
        return true;
    }

    /** Told when a slot that is not {@link #selectable} was clicked. */
    protected void refused(Player player, int slot) {
    }

    /** The icon of an empty slot that is not selectable (the roulette's veil), or null for none. */
    protected ItemStack lockedIcon() {
        return null;
    }

    /** Where the marker goes after an empty slot was filled: the next empty selectable slot after it. */
    protected int next(int filled) {
        for (int step = 1; step < stakes.length; step++) {
            int candidate = (filled + step) % stakes.length;
            if (stakes[candidate] == null && selectable(candidate)) {
                return candidate;
            }
        }
        return filled;
    }

    /** The current stake of a slot (null = empty). */
    protected ItemAmount stake(int slot) {
        return stakes[slot];
    }

    // ---- per game --------------------------------------------------------------------------------------------------

    /** Grid indices of the three stake slots (the mod's bet slots, render.md 5.2). */
    protected abstract int[] stakeSlots();

    /** Grid indices of the play button's click area; {@link #buttonArtSlot()} shows the button art. */
    protected abstract int[] buttonSlots();

    protected abstract int buttonArtSlot();

    /** Grid index of the bettable-items book. */
    protected abstract int betsSlot();

    /** Button model and texts: {@code kaboofcasino:gui/<game>_on|off}. */
    protected abstract String buttonModel(boolean enabled);

    protected abstract Msg buttonName();

    /** The play click (commit): the controller decides, commits and starts the round. */
    protected abstract void play(Player player);

    /** Extra display items (labels such as Plinko's "1 Kugel"). */
    protected void decorate() {
    }

    // ---- state -----------------------------------------------------------------------------------------------------

    /** The filled slots in slot order. */
    public List<Stake> stakes() {
        List<Stake> out = new ArrayList<>();
        for (int i = 0; i < stakes.length; i++) {
            if (stakes[i] != null) {
                out.add(new Stake(i, stakes[i]));
            }
        }
        return out;
    }

    public void clearStakes() {
        java.util.Arrays.fill(stakes, null);
        active = 0;
    }

    int active() {
        return active;
    }

    /** Sum of the other slots' counts. */
    private long others(int slot) {
        long n = 0;
        for (int i = 0; i < stakes.length; i++) {
            if (i != slot && stakes[i] != null) {
                n += stakes[i].count();
            }
        }
        return n;
    }

    /** Items of {@code item} already staked in the other slots. */
    private long othersOf(int slot, String item) {
        long n = 0;
        for (int i = 0; i < stakes.length; i++) {
            if (i != slot && stakes[i] != null && stakes[i].item().equals(item)) {
                n += stakes[i].count();
            }
        }
        return n;
    }

    /**
     * The new count of the active slot for a click on {@code owned} plain items of {@code item} (pure).
     *
     * @param current the active slot's stake (null if empty)
     * @param stack   the clicked stack's size
     * @param others  items in the other slots (all items) that count against {@code max} (0 with the cap per slot)
     * @param ownedFree owned plain items of this item minus those in the other slots
     */
    static long countFor(ClickType click, ItemAmount current, String item, int stack, long others, long ownedFree,
                         int max) {
        boolean same = current != null && current.item().equals(item);
        long want = switch (click) {
            case RIGHT -> same ? current.count() + 1 : 1;
            case SHIFT_LEFT -> ownedFree;
            default -> stack;
        };
        return Math.min(Math.min(want, max - others), ownedFree);
    }

    // ---- rendering -------------------------------------------------------------------------------------------------

    @Override
    public void render() {
        clear();
        var m = ctx.messages().get();
        int[] slots = stakeSlots();
        for (int i = 0; i < stakes.length; i++) {
            final int slot = i;
            ItemStack icon;
            if (stakes[i] != null) {
                icon = DisplayCopies.of(PlayerAccount.material(stakes[i].item()), (int) stakes[i].count(), null,
                        m.lines(Msg.GUI_STAKE_LORE));
                if (i == active) {
                    icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
            } else if (!selectable(i) && lockedIcon() != null) {
                icon = lockedIcon();
            } else if (i == active) {
                icon = DisplayCopies.model("kaboofcasino:gui/stake_active", m.render(Msg.GUI_STAKE_ACTIVE),
                        m.lines(ctx.config().get().bets().capPerRound() ? Msg.GUI_STAKE_ACTIVE_LORE_ROUND
                                : Msg.GUI_STAKE_ACTIVE_LORE, Messages.text("max",
                                Integer.toString(ctx.config().get().bets().maxStake()))));
            } else {
                icon = DisplayCopies.model("kaboofcasino:gui/blank", m.render(Msg.GUI_STAKE_EMPTY),
                        m.lines(Msg.GUI_STAKE_EMPTY_LORE));
            }
            set(slots[i], Button.of(icon, (p, click) -> slotClicked(p, slot, click)));
        }
        boolean owed = !owedNow().isEmpty();
        ItemStack button;
        if (owed) {
            button = DisplayCopies.model("kaboofcasino:gui/collect_low", m.render(Msg.GUI_SLOT_COLLECT),
                    m.lines(Msg.GUI_SLOT_COLLECT_LORE));
        } else {
            button = DisplayCopies.model(buttonModel(!stakes().isEmpty()), m.render(buttonName()),
                    m.lines(ctx.config().get().bets().capPerRound() ? Msg.GUI_STAKES_PLAY_LORE_ROUND
                            : Msg.GUI_STAKES_PLAY_LORE, Messages.text("max",
                            Integer.toString(ctx.config().get().bets().maxStake()))));
        }
        ItemStack blank = DisplayCopies.model("kaboofcasino:gui/blank", m.render(owed ? Msg.GUI_SLOT_COLLECT
                : buttonName()), List.of());
        for (int slot : buttonSlots()) {
            ItemStack icon = slot == buttonArtSlot() ? button : blank;
            set(slot, owed ? Button.of(icon, (p, click) -> {
                ctx.ledger().collect(p);
                render();
            }) : Button.commit(icon, (p, click) -> play(p)));
        }
        set(betsSlot(), Button.display(betsBook(ctx)));
        decorate();
    }

    /** The bettable-items list (the mod's {@code CasinoBetList}, drawn left of its window) as a book tooltip. */
    public static ItemStack betsBook(CasinoContext ctx) {
        var m = ctx.messages().get();
        List<Component> lore = new ArrayList<>();
        for (String item : ctx.config().get().bets().items()) {
            lore.add(Component.text("• ", NamedTextColor.GRAY).append(ItemNames.name(item)
                    .color(NamedTextColor.YELLOW)));
        }
        lore.addAll(m.lines(Msg.GUI_BETS_LORE));
        return DisplayCopies.of(Material.BOOK, 1, m.render(Msg.GUI_BETS_TITLE), lore);
    }

    private List<ItemAmount> owedNow() {
        Player p = org.bukkit.Bukkit.getPlayer(viewer());
        if (p == null) {
            return List.of();
        }
        try {
            Ledger l = new PlayerAccount(p).ledger();
            return l.owed();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // ---- clicks ----------------------------------------------------------------------------------------------------

    private void slotClicked(Player player, int slot, ClickType click) {
        if (!selectable(slot)) {
            refused(player, slot);
            return;
        }
        if (click == ClickType.RIGHT) {
            if (stakes[slot] != null) {
                stakes[slot] = stakes[slot].count() > 1
                        ? new ItemAmount(stakes[slot].item(), stakes[slot].count() - 1) : null;
            }
        } else if (click == ClickType.SHIFT_LEFT || slot == active) {
            stakes[slot] = null;
        }
        active = slot;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        render();
    }

    @Override
    public void onBottomClick(Player player, int index, ClickType click, ItemStack clicked) {
        if (clicked == null || clicked.isEmpty()) {
            return;
        }
        var m = ctx.messages().get();
        String key = clicked.getType().getKey().asString();
        if (!ctx.config().get().bets().items().contains(key)) {
            m.actionBar(player, Msg.BET_NOT_ALLOWED, Messages.component("item",
                    Component.translatable(clicked.getType())));
            return;
        }
        if (!PlayerAccount.isPlain(clicked)) {
            m.actionBar(player, Msg.BET_NOT_PLAIN);
            return;
        }
        if (!selectable(active)) {
            refused(player, active);
            return;
        }
        int max = ctx.config().get().bets().maxStake();
        long others = ctx.config().get().bets().capPerRound() ? others(active) : 0;
        long ownedFree = new PlayerAccount(player).countPlain(key) - othersOf(active, key);
        long count = countFor(click, stakes[active], key, clicked.getAmount(), others, ownedFree, max);
        if (count < (click == ClickType.SHIFT_LEFT ? ownedFree : 1) && others + count >= max) {
            m.actionBar(player, Msg.BET_MAX, Messages.text("max", Integer.toString(max)));
        }
        if (count >= 1) {
            boolean wasEmpty = stakes[active] == null;
            stakes[active] = new ItemAmount(key, count);
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            if (wasEmpty && click != ClickType.RIGHT) {
                active = next(active);
            }
        }
        render();
    }
}
