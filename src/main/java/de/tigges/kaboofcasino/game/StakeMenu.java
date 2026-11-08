package de.tigges.kaboofcasino.game;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.wager.ChipItems;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.Ledger;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * The bet slots of a game (games.md "Bets") as a chip selection (PLAN-2 C2, {@link ChipStakes}): nothing moves until
 * the round is committed. One slot is active (gold frame when empty, glint when filled); clicks on chips in the own
 * inventory add to the active slot (left: that stack, right: one chip, shift-left: all chips of that kind); filling
 * an empty slot moves the marker to the next empty one. A slot click selects it, a left click on the active slot
 * clears it, a right click takes one chip (the smallest) away. Each slot holds at most {@code bets.max-stake} in chip
 * value, or with {@code bets.cap-per: round} all slots together. Each slot is its own stake (the mod: one ball per
 * slot in Plinko), paid in chips.
 */
public abstract class StakeMenu extends GameMenu {

    /** The wheel's and Plinko's three bet slots. */
    public static final int SLOTS = 3;

    /**
     * A filled slot: its index (the mod's slot number, which the fair-rounding roll uses), the stake (a chip value,
     * {@link Chips#VALUE}) and the chips that leave the inventory for it (denomination keys).
     */
    public record Stake(int slot, ItemAmount amount, List<ItemAmount> chips) {
        public Stake {
            chips = List.copyOf(chips);
        }
    }

    protected final CasinoContext ctx;
    private final Chips.Selection[] stakes;
    private int active;

    protected StakeMenu(UUID viewer, Component title, CasinoContext ctx) {
        this(viewer, title, ctx, SLOTS);
    }

    /** A menu with {@code slots} bet slots (the roulette: 9, three per colour). */
    protected StakeMenu(UUID viewer, Component title, CasinoContext ctx, int slots) {
        super(viewer, title);
        this.ctx = ctx;
        this.stakes = new Chips.Selection[slots];
    }

    /** True if {@code slot} may be chosen now (the roulette: only the colour already staked). */
    protected boolean selectable(int slot) {
        return true;
    }

    /** Told when a slot that is not {@link #selectable} was clicked. */
    protected void refused(Player player, int slot) {
    }

    /** The icon of an empty slot that is not selectable (the roulette's veil), or null for a blank. */
    protected ItemStack lockedIcon(int slot) {
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

    /** The current stake of a slot as a chip value (null = empty). */
    protected ItemAmount stake(int slot) {
        return stakes[slot] == null ? null : stakes[slot].asValue();
    }

    // ---- per game --------------------------------------------------------------------------------------------------

    /** Grid indices of the three stake slots (the mod's bet slots, render.md 5.2). */
    protected abstract int[] stakeSlots();

    /** Grid indices of the play button's click area; {@link #buttonArtSlot()} shows the button art. */
    protected abstract int[] buttonSlots();

    protected abstract int buttonArtSlot();

    /** Grid index of the "play with chips" book (the mod's bettable-items list). */
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
                out.add(new Stake(i, stakes[i].asValue(), stakes[i].amounts()));
            }
        }
        return out;
    }

    public void clearStakes() {
        java.util.Arrays.fill(stakes, null);
        active = 0;
    }

    /** The active slot (the marker, or the glint of a filled slot). */
    protected int active() {
        return active;
    }

    /** Value of the other slots. */
    private long others(int slot) {
        long n = 0;
        for (int i = 0; i < stakes.length; i++) {
            if (i != slot && stakes[i] != null) {
                n += stakes[i].value();
            }
        }
        return n;
    }

    /** Chips of {@code denomination} already selected in any slot. */
    private long selected(int denomination) {
        long n = 0;
        for (Chips.Selection sel : stakes) {
            if (sel != null) {
                n += sel.count(denomination);
            }
        }
        return n;
    }

    // ---- rendering -------------------------------------------------------------------------------------------------

    @Override
    public void render() {
        clear();
        var m = ctx.messages().get();
        int[] slots = stakeSlots();
        for (int i = 0; i < stakes.length; i++) {
            final int slot = i;
            set(slots[i], Button.of(stakeIcon(i), (p, click) -> slotClicked(p, slot, click)));
        }
        boolean owed = !owedNow().isEmpty();
        ItemStack button;
        if (owed) {
            button = DisplayCopies.model("kaboofcasino:gui/collect_low", m.render(Msg.GUI_SLOT_COLLECT),
                    m.lines(Msg.GUI_SLOT_COLLECT_LORE));
        } else {
            button = DisplayCopies.model(buttonModel(!stakes().isEmpty()), m.render(buttonName()),
                    m.lines(ctx.config().get().bets().capPerRound() ? Msg.CHIPS_PLAY_LORE_ROUND
                            : Msg.CHIPS_PLAY_LORE, Messages.text("max",
                            de.tigges.kaboofcasino.util.ChipText.chips(ctx.config().get().bets().maxStake()))));
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
        set(betsSlot(), Button.display(ChipStakes.book(ctx)));
        decorate();
    }

    /** The icon of bet slot {@code i}: its chips (glint if active), the active marker, an empty or a locked slot. */
    protected ItemStack stakeIcon(int i) {
        var m = ctx.messages().get();
        if (stakes[i] != null) {
            return ChipStakes.icon(ctx, stakes[i], m.lines(Msg.CHIPS_STAKE_LORE), i == active);
        }
        if (!selectable(i)) {
            ItemStack locked = lockedIcon(i);
            return locked != null ? locked : DisplayCopies.model("kaboofcasino:gui/blank",
                    m.render(Msg.GUI_STAKE_EMPTY), List.of());
        }
        if (i == active) {
            return DisplayCopies.model("kaboofcasino:gui/stake_active", m.render(Msg.GUI_STAKE_ACTIVE),
                    m.lines(ctx.config().get().bets().capPerRound() ? Msg.CHIPS_ACTIVE_LORE_ROUND
                            : Msg.CHIPS_ACTIVE_LORE, Messages.text("max",
                            de.tigges.kaboofcasino.util.ChipText.chips(ctx.config().get().bets().maxStake()))));
        }
        return DisplayCopies.model("kaboofcasino:gui/blank", m.render(Msg.GUI_STAKE_EMPTY),
                m.lines(Msg.GUI_STAKE_EMPTY_LORE));
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

    /** A click on bet slot {@code slot}: select it, clear it (again / shift) or take one away (right). */
    protected void slotClicked(Player player, int slot, ClickType click) {
        if (!selectable(slot)) {
            refused(player, slot);
            return;
        }
        if (click == ClickType.RIGHT) {
            if (stakes[slot] != null) {
                Chips.Selection less = stakes[slot].minusSmallest();
                stakes[slot] = less.isEmpty() ? null : less;
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
        OptionalInt chip = ChipItems.denomination(clicked);
        if (chip.isEmpty()) {
            m.actionBar(player, Msg.CHIPS_ONLY);
            return;
        }
        if (!selectable(active)) {
            refused(player, active);
            return;
        }
        int d = chip.getAsInt();
        long max = ctx.config().get().bets().maxStake();
        long others = ctx.config().get().bets().capPerRound() ? others(active) : 0;
        long ownedFree = new PlayerAccount(player).countPlain(Chips.key(d)) - selected(d);
        long current = stakes[active] == null ? 0 : stakes[active].value();
        long add = ChipStakes.add(click, d, clicked.getAmount(), ownedFree, current, others, max);
        if (ChipStakes.capped(click, d, clicked.getAmount(), ownedFree, current, others, max)) {
            ChipStakes.tellMax(ctx, player);
        }
        if (add >= 1) {
            boolean wasEmpty = stakes[active] == null;
            stakes[active] = (wasEmpty ? Chips.Selection.EMPTY : stakes[active]).plus(d, add);
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            if (wasEmpty && click != ClickType.RIGHT) {
                active = next(active);
            }
        }
        render();
    }
}
