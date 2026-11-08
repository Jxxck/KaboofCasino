package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * The duck derby menu as a 9x6 chest (render.md 5.2; mod {@code DuckDerbyScreen} / {@code DuckDerbyMenu}). The title is
 * the mod's window re-built around the chest (the pack's {@code DerbyGuiArt}: the left panel with the four duck cards
 * over the bet columns, the right panel with "DUCK DERBY"); what changes is a display item per slot, see
 * {@link DerbyMenuView} for the layout. The twelve bets are a selection (see {@link StakeMenu}): bet i is lane
 * {@code i % 4}, row {@code i / 4}, and only one lane may hold stakes ({@code DuckDerbyMenu.open}): once a lane is
 * staked the other columns are veiled and refuse clicks. While the viewer is a confirmed entrant the column shows his
 * escrow read-only and GROUP becomes WITHDRAW. The controller calls {@link #refresh} every tick; only changed slots are
 * sent. Nothing real ever lies in this menu; every click outside the buttons and bets does nothing.
 */
public final class DerbyBetMenu extends StakeMenu {

    /** {@code extractLabels}: "DUCK DERBY" -1389181 with shadow. */
    static final TextColor BRAND = TextColor.color(0xEACD83);
    static final Msg[] DUCKS = {Msg.GUI_DERBY_DUCK_0, Msg.GUI_DERBY_DUCK_1, Msg.GUI_DERBY_DUCK_2,
            Msg.GUI_DERBY_DUCK_3};

    private final DerbyController controller;
    private final DerbyMenuView.Cell[] shown = new DerbyMenuView.Cell[DerbyMenuView.SLOTS];
    private DerbySnapshot snapshot;
    private long ticks;
    private long severalUntil = Long.MIN_VALUE;
    private boolean help;
    private int page;

    public DerbyBetMenu(UUID viewer, DerbyController controller, CasinoContext ctx) {
        super(viewer, title(ctx), ctx, DerbyMenuView.BETS);
        this.controller = controller;
    }

    static Component title(CasinoContext ctx) {
        return title(ctx.layout(), ctx.messages().get()::raw);
    }

    /** The title from the pack's layout and the message texts ({@code raw}). */
    public static Component title(GuiLayout layout, Function<Msg, String> raw) {
        TitleBuilder t = new TitleBuilder(layout);
        t.art("derby_bg_0");
        t.art("derby_bg_1");
        for (int lane = 0; lane < 4; lane++) {
            t.art("derby_card_" + lane);
        }
        t.text("derby_brand", raw.apply(Msg.GUI_DERBY_TITLE), BRAND, false, true);
        return t.build();
    }

    @Override
    public MachineController controller() {
        return controller;
    }

    /** {@code stakedLane()}: -1 none, the lane, or -2 several. Slot index i: lane i % 4, row i / 4. */
    public int stakedLane() {
        int lane = -1;
        for (Stake s : stakes()) {
            int l = s.slot() % 4;
            if (lane == -1) {
                lane = l;
            } else if (lane != l) {
                return -2;
            }
        }
        return lane;
    }

    /** Called by the controller every tick while open: re-sends the slots whose content changed. */
    public void refresh(DerbySnapshot s) {
        if (s.confirmed() && (snapshot == null || !snapshot.confirmed())) {
            clearStakes(); // the draft became the escrow (DuckDerbyBlockEntity.joinGroup)
        }
        snapshot = s;
        ticks++;
        apply();
    }

    @Override
    public void render() {
        if (snapshot == null) {
            Player p = Bukkit.getPlayer(viewer());
            if (p == null) {
                return;
            }
            snapshot = controller.snapshot(p);
        }
        apply();
    }

    private ItemAmount[] draft() {
        ItemAmount[] out = new ItemAmount[DerbyMenuView.BETS];
        for (Stake s : stakes()) {
            out[s.slot()] = s.amount();
        }
        return out;
    }

    private void apply() {
        if (snapshot == null) {
            return;
        }
        DerbyMenuView.Cell[] cells = DerbyMenuView.cells(snapshot, viewer(), draft(), active(), page, help,
                ticks < severalUntil);
        for (int slot = 0; slot < cells.length; slot++) {
            DerbyMenuView.Cell c = cells[slot];
            if (Objects.equals(c, shown[slot])) {
                continue;
            }
            shown[slot] = c;
            set(slot, c == null ? null : button(c));
        }
    }

    // ---- items -------------------------------------------------------------------------------------------------------

    private Button button(DerbyMenuView.Cell c) {
        var m = ctx.messages().get();
        return switch (c.kind()) {
            case PICTURE -> Button.display(picture(c.model(), c.strings()));
            case BET -> Button.of(stakeIcon(c.index()), (p, click) -> slotClicked(p, c.index(), click));
            case ESCROW -> Button.display(escrowIcon(c.stake(), m));
            case HEAD -> Button.display(head(c.member()));
            case BUTTON -> actionButton(c);
        };
    }

    /** A confirmed group stake: chips (a chip value, the largest chip shown) or, from an older record, ores. */
    private static ItemStack escrowIcon(de.tigges.kaboofcasino.wager.ItemAmount stake,
                                        de.tigges.kaboofcasino.config.Messages m) {
        if (de.tigges.kaboofcasino.wager.Chips.isValue(stake.item())) {
            return de.tigges.kaboofcasino.game.ChipStakes.valueIcon(stake.count(), m.render(Msg.CHIPS_ESCROW,
                    de.tigges.kaboofcasino.config.Messages.text("value",
                            de.tigges.kaboofcasino.util.ItemNames.value(stake.count()))), List.of());
        }
        return DisplayCopies.of(PlayerAccount.material(stake.item()), (int) Math.min(99, stake.count()), null,
                m.lines(Msg.GUI_DERBY_CONFIRMED));
    }

    private static ItemStack picture(String model, List<String> strings) {
        ItemStack stack = DisplayCopies.model(model, null, List.of());
        if (!strings.isEmpty()) {
            CustomModelData.Builder data = CustomModelData.customModelData();
            strings.forEach(data::addString);
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        }
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return stack;
    }

    /** A member's head: the player head with his profile, his name, his duck and "Einsatz bestätigt" (the mod's tooltip). */
    private ItemStack head(DerbyMember member) {
        var m = ctx.messages().get();
        List<Component> lore = List.of(m.render(DUCKS[Math.max(0, Math.min(3, member.lane()))]),
                m.render(Msg.GUI_DERBY_CONFIRMED));
        ItemStack stack = DisplayCopies.of(Material.PLAYER_HEAD, 1, Component.text(member.name(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false), lore);
        Player online = Bukkit.getPlayer(member.id());
        ResolvableProfile profile = online != null ? ResolvableProfile.resolvableProfile(online.getPlayerProfile())
                : ResolvableProfile.resolvableProfile().uuid(member.id()).name(member.name()).build();
        stack.setData(DataComponentTypes.PROFILE, profile);
        return stack;
    }

    private Button actionButton(DerbyMenuView.Cell c) {
        var m = ctx.messages().get();
        Msg name;
        List<Component> lore = new ArrayList<>();
        switch (c.action()) {
            case SOLO -> {
                name = Msg.GUI_DERBY_SOLO;
                lore.addAll(m.lines(Msg.GUI_DERBY_SOLO_LORE));
            }
            case CLAIM -> {
                name = Msg.GUI_DERBY_CLAIM;
                lore.addAll(m.lines(Msg.GUI_DERBY_CLAIM_LORE));
            }
            case GROUP, CONFIRM, WITHDRAW -> {
                name = c.action() == DerbyMenuView.Action.GROUP ? Msg.GUI_DERBY_GROUP
                        : c.action() == DerbyMenuView.Action.CONFIRM ? Msg.GUI_DERBY_CONFIRM : Msg.GUI_DERBY_WITHDRAW;
                if (c.state().equals("unavailable")) {
                    lore.addAll(m.lines(Msg.DERBY_GROUP_UNAVAILABLE));
                } else {
                    lore.addAll(m.lines(c.action() == DerbyMenuView.Action.GROUP ? Msg.GUI_DERBY_GROUP_LORE
                            : c.action() == DerbyMenuView.Action.CONFIRM ? Msg.GUI_DERBY_CONFIRM_LORE
                            : Msg.GUI_DERBY_WITHDRAW_LORE));
                }
            }
            case HELP -> {
                name = Msg.GUI_DERBY_HELP;
                lore.addAll(m.lines(Msg.GUI_DERBY_HELP_LORE));
                lore.add(Component.empty());
                lore.add(m.render(Msg.CHIPS_BOOK_TITLE));
                ItemStack book = de.tigges.kaboofcasino.game.ChipStakes.book(ctx);
                var bookLore = book.getData(DataComponentTypes.LORE);
                if (bookLore != null) {
                    lore.addAll(bookLore.lines());
                }
            }
            case PAGER -> {
                name = Msg.GUI_DERBY_PAGE;
                lore.addAll(m.lines(Msg.GUI_DERBY_PAGE_LORE));
            }
            default -> name = Msg.GUI_DERBY_HELP_CLOSE;
        }
        Component title = c.action() == DerbyMenuView.Action.PAGER ? m.render(name, Messages.text("page",
                Integer.toString(c.index() + 1)), Messages.text("pages", Integer.toString(c.pages()))) : m.render(name);
        ItemStack icon = DisplayCopies.model(c.model(), title, lore);
        if (!c.active()) {
            return Button.display(icon);
        }
        DerbyMenuView.Action a = c.action();
        return switch (a) {
            case SOLO, CLAIM, GROUP, CONFIRM, WITHDRAW -> Button.commit(icon, (p, click) -> press(p, a));
            case PAGER -> Button.of(icon, (p, click) -> turn(p, click));
            default -> Button.of(icon, (p, click) -> toggleHelp(p));
        };
    }

    // ---- clicks ------------------------------------------------------------------------------------------------------

    private void press(Player player, DerbyMenuView.Action action) {
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.25f, 1.0f);
        switch (action) {
            case SOLO -> controller.solo(player, this);
            case GROUP, CONFIRM -> controller.group(player, this);
            case WITHDRAW -> controller.withdraw(player);
            case CLAIM -> controller.claim(player);
            default -> {
            }
        }
        if (isOpenFor(player)) {
            snapshot = controller.snapshot(player);
            apply();
        }
    }

    /** The pager: left click the next page, right click the previous one (both wrap around). */
    private void turn(Player player, ClickType click) {
        int pages = DerbyMenuView.pages(snapshot == null ? 0 : snapshot.members().size());
        boolean back = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        page = Math.floorMod(Math.min(page, pages - 1) + (back ? -1 : 1), pages);
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.25f, 1.0f);
        apply();
    }

    private void toggleHelp(Player player) {
        help = !help;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.25f, 1.0f);
        apply();
    }

    @Override
    public void onBottomClick(Player player, int index, ClickType click, ItemStack clicked) {
        if (help || snapshot != null && snapshot.confirmed()) {
            return;
        }
        super.onBottomClick(player, index, click, clicked);
    }

    // ---- the one-lane rule ({@code DuckDerbyMenu.open}) ------------------------------------------------------------

    @Override
    protected boolean selectable(int slot) {
        int lane = stakedLane();
        return lane < 0 || slot % 4 == lane;
    }

    /** {@code stakedLane() == -2}: the mod's hint "Eine Ente pro Rennen!" for a moment, and the refusal sound. */
    @Override
    protected void refused(Player player, int slot) {
        severalUntil = ticks + DerbyMenuView.SEVERAL_TICKS;
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.8f);
        apply();
    }

    /** The marker moves down the chosen lane. */
    @Override
    protected int next(int filled) {
        for (int step = 1; step < 3; step++) {
            int candidate = (filled + 4 * step) % DerbyMenuView.BETS;
            if (stake(candidate) == null) {
                return candidate;
            }
        }
        return filled;
    }

    // ---- StakeMenu ---------------------------------------------------------------------------------------------------

    @Override
    protected int[] stakeSlots() {
        int[] out = new int[DerbyMenuView.BETS];
        for (int i = 0; i < out.length; i++) {
            out[i] = DerbyMenuView.betSlot(i);
        }
        return out;
    }

    @Override
    protected int[] buttonSlots() {
        return Arrays.copyOf(DerbyMenuView.SOLO, DerbyMenuView.SOLO.length);
    }

    @Override
    protected int buttonArtSlot() {
        return DerbyMenuView.SOLO_ART;
    }

    /** The bettable items are in the help button's tooltip (this layout has no slot for the book). */
    @Override
    protected int betsSlot() {
        return DerbyMenuView.HELP;
    }

    @Override
    protected String buttonModel(boolean enabled) {
        return DerbyMenuView.MODEL + (enabled ? "solo_on" : "solo_off");
    }

    @Override
    protected Msg buttonName() {
        return Msg.GUI_DERBY_SOLO;
    }

    @Override
    protected void play(Player player) {
        controller.solo(player, this);
    }
}
