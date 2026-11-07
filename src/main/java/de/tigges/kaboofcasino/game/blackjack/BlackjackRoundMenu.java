package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * The blackjack round view as a 9x6 chest (render.md 5.2; the mod's 160x196 side panel, {@code BlackjackScreen}
 * while a hand runs). The title is the panel (re-built 288 px wide around the chest) with "BLACKJACK" and the labels
 * CROUPIER / DU / EINSATZ in the bands' left part; everything that changes is a display item whose pack model draws at
 * a fixed window position (see the pack tool's {@code BlackjackGui}):
 * <pre>
 *  row 0 (slots 0..8)    dealer cards (top at window y 2)            slot 17 dealer total (right of the band)
 *  row 2 (slots 18..26)  player cards (y 38)                         slot 35 player total
 *  row 3 (slots 27..32)  the stake items, at the result with their +n / 0 / -n labels; slot 34 "x2"
 *  row 4                 slot 39 timer bar, slot 40 status / result line
 *  row 5 (45..53)        KARTE (45..47), HALTEN (48..50), DOPPELN (51..53)
 * </pre>
 * Cards: up to five at the mod's 27 px step (columns 0, 1, 3, 4, 6; every second one with the 9 px model shift),
 * six to nine at the 18 px slot pitch, more (never seen in practice) as the first eight and the newest. The view is
 * the mod's client logic on game ticks: a card appears {@value #APPEAR_DELAY} ticks after it was dealt (at once for
 * cards already on the table during the first {@value #INSTANT_TICKS} ticks after opening), the hole card shows its
 * face {@value #FACE_DELAY} ticks after it was revealed, a button press locks the buttons for {@value #PRESS_LOCK}
 * ticks; DOPPELN is greyed when the hand cannot double or the player lacks the items. The controller calls
 * {@link #refresh} every tick while the menu is open; only slots whose content changed are re-sent. No slot holds a
 * real item; clicks outside the three buttons do nothing. The menu never closes for idleness (the controller closes it
 * when the hand is over).
 */
public final class BlackjackRoundMenu extends GameMenu {

    static final int DEALER_ROW = 0;
    static final int PLAYER_ROW = 18;
    static final int STAKE_ROW = 27;
    static final int DEALER_BADGE = 17;
    static final int PLAYER_BADGE = 35;
    static final int DOUBLED = 34;
    static final int BAR = 39;
    static final int STATUS = 40;
    static final int[][] BUTTONS = {{45, 46, 47}, {48, 49, 50}, {51, 52, 53}};
    static final int[] BUTTON_ART = {46, 49, 52};
    /** {@code BlackjackScreen}: APPEAR_DELAY, FACE_DELAY, the 6-tick press lock, {@code instant = ticks < 4}. */
    static final int APPEAR_DELAY = 22;
    static final int FACE_DELAY = 8;
    static final int PRESS_LOCK = 6;
    static final int INSTANT_TICKS = 4;
    static final int MAX_CARDS = 12;
    /** The timer bar: 136 px for 150 quarter seconds ({@code D_TURN_LEFT}), red at 38 and below. */
    static final int BAR_W = 136;
    static final int BAR_FULL = 150;
    static final int BAR_LOW = 38;
    static final int BAR_COLOUR = 0xE0A030;
    static final int BAR_LOW_COLOUR = 0xD04040;
    static final int GREEN = 0x55FF55;
    static final int WHITE = 0xFFFFFF;
    static final int RED = 0xFF5555;
    static final String MODEL = "kaboofcasino:gui/blackjack/";
    static final String BLANK = "kaboofcasino:gui/blank";
    /** {@code extractPanel}: the title -22016 bold with shadow, the labels -3614776. */
    static final TextColor GOLD = TextColor.color(0xFFAA00);
    static final TextColor PALE = TextColor.color(0xC8D7C8);

    /** The three panel buttons ({@code BlackjackMenu} buttons 1..3). */
    enum Action {
        HIT("hit", Msg.GUI_BLACKJACK_HIT, Msg.GUI_BLACKJACK_HIT_LORE),
        STAND("stand", Msg.GUI_BLACKJACK_STAND, Msg.GUI_BLACKJACK_STAND_LORE),
        DOUBLE("double", Msg.GUI_BLACKJACK_DOUBLE, Msg.GUI_BLACKJACK_DOUBLE_LORE);

        final String model;
        final Msg name;
        final Msg lore;

        Action(String model, Msg name, Msg lore) {
            this.model = model;
            this.name = name;
            this.lore = lore;
        }
    }

    /**
     * What one slot shows: a pack model (with custom model data) or a vanilla stake item, and for button slots the
     * action and whether it is clickable. Equal cells are not sent again.
     */
    record Cell(String model, String item, int amount, List<String> strings, List<Integer> colors, List<Float> floats,
                Action action, boolean enabled) {

        Cell {
            strings = List.copyOf(strings);
            colors = List.copyOf(colors);
            floats = List.copyOf(floats);
        }

        static Cell model(String name) {
            return new Cell(MODEL + name, null, 1, List.of(), List.of(), List.of(), null, false);
        }

        static Cell stake(ItemAmount stake, int amount) {
            return new Cell(null, stake.item(), amount, List.of(), List.of(), List.of(), null, false);
        }
    }

    /** {@code BlackjackScreen.Hand} on game ticks. */
    static final class Hand {
        private final int[] value = new int[MAX_CARDS];
        private final long[] shownAt = new long[MAX_CARDS];
        private final long[] faceAt = new long[MAX_CARDS];
        private int count;

        void update(List<Integer> cards, List<Long> dealtAt, long now, boolean instant) {
            int n = Math.min(MAX_CARDS, cards.size());
            if (n < count) {
                count = 0;
            }
            for (int i = 0; i < n; i++) {
                int card = cards.get(i);
                if (i >= count) {
                    value[i] = card;
                    shownAt[i] = instant ? Long.MIN_VALUE : (i < dealtAt.size() ? dealtAt.get(i) : now) + APPEAR_DELAY;
                    faceAt[i] = Long.MIN_VALUE;
                } else if (value[i] == BlackjackCards.HIDDEN && card != BlackjackCards.HIDDEN) {
                    value[i] = card;
                    faceAt[i] = instant ? Long.MIN_VALUE : now + FACE_DELAY;
                }
            }
            count = n;
        }

        int visible(long now) {
            int n = count;
            while (n > 0 && shownAt[n - 1] > now) {
                n--;
            }
            return n;
        }

        List<Integer> values(int shown, long now) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < shown; i++) {
                out.add(value[i] != BlackjackCards.HIDDEN && now >= faceAt[i] ? value[i] : BlackjackCards.HIDDEN);
            }
            return out;
        }
    }

    /** The menu's state between ticks and the cells of one tick (pure: no server needed). */
    static final class View {
        final Hand dealer = new Hand();
        final Hand player = new Hand();
        int ticks;
        int pressLock;

        /** One step: {@code tick} = a game tick passed ({@code containerTick}), else a redraw in the same tick. */
        Cell[] cells(BlackjackSnapshot s, boolean tick) {
            if (tick) {
                ticks++;
                if (pressLock > 0) {
                    pressLock--;
                }
            }
            boolean instant = ticks < INSTANT_TICKS;
            long now = s.gameTime();
            dealer.update(s.dealer(), s.dealerDealtAt(), now, instant);
            player.update(s.player(), s.playerDealtAt(), now, instant);
            Cell[] out = new Cell[54];
            int dealerShown = dealer.visible(now);
            int playerShown = player.visible(now);
            List<Integer> dealerValues = dealer.values(dealerShown, now);
            List<Integer> playerValues = player.values(playerShown, now);
            cards(out, DEALER_ROW, dealerValues);
            cards(out, PLAYER_ROW, playerValues);
            if (dealerShown > 0) {
                out[DEALER_BADGE] = Cell.model("badge_" + Math.min(31, BlackjackCards.total(dealerValues)));
            }
            if (playerShown > 0) {
                out[PLAYER_BADGE] = Cell.model("badge_" + Math.min(31, BlackjackCards.total(playerValues)));
            }
            stakes(out, s);
            if (s.doubled()) {
                out[DOUBLED] = Cell.model("doubled");
            }
            boolean decided = s.phase() == BlackjackSnapshot.PHASE_RESULT && s.result() != 0;
            if (decided) {
                out[STATUS] = Cell.model("result_" + s.result());
            } else if (s.phase() >= BlackjackSnapshot.PHASE_DEALING && s.phase() <= BlackjackSnapshot.PHASE_RESULT) {
                out[STATUS] = Cell.model("status_" + s.phase());
            }
            int left = (s.turnTicksLeft() + 3) / 4;
            if (s.phase() == BlackjackSnapshot.PHASE_TURN && left > 0) {
                int fill = Math.min(BAR_W, BAR_W * left / BAR_FULL);
                if (fill > 0) {
                    out[BAR] = new Cell(MODEL + "bar", null, 1, List.of(), List.of(left > BAR_LOW ? BAR_COLOUR
                            : BAR_LOW_COLOUR), List.of((float) fill), null, false);
                }
            }
            boolean turn = s.phase() == BlackjackSnapshot.PHASE_TURN && pressLock == 0;
            boolean[] enabled = {turn && s.canHit(), turn && s.canStand(), turn && s.canDouble() && s.affordDouble()};
            Action[] actions = Action.values();
            for (int b = 0; b < actions.length; b++) {
                for (int slot : BUTTONS[b]) {
                    String model = slot == BUTTON_ART[b] ? MODEL + actions[b].model + (enabled[b] ? "_on" : "_off")
                            : BLANK;
                    out[slot] = new Cell(model, null, 1, List.of(), List.of(), List.of(), actions[b], enabled[b]);
                }
            }
            return out;
        }
    }

    /** One placed card: index in the hand, grid column, 9 px model shift. */
    record Place(int card, int column, boolean shifted) {
    }

    /**
     * Where the visible cards of a hand go: up to five at the mod's step 27 (it uses 28 up to four cards, 27 for
     * five), six to nine at 18 px, more as the first eight and the newest in the last column.
     */
    static List<Place> places(int n) {
        List<Place> out = new ArrayList<>();
        if (n <= 5) {
            int[] columns = {0, 1, 3, 4, 6};
            for (int i = 0; i < n; i++) {
                out.add(new Place(i, columns[i], i % 2 == 1));
            }
        } else if (n <= 9) {
            for (int i = 0; i < n; i++) {
                out.add(new Place(i, i, false));
            }
        } else {
            for (int i = 0; i < 8; i++) {
                out.add(new Place(i, i, false));
            }
            out.add(new Place(n - 1, 8, false));
        }
        return out;
    }

    static void cards(Cell[] out, int row, List<Integer> values) {
        for (Place p : places(values.size())) {
            int card = values.get(p.card());
            String name = card == BlackjackCards.HIDDEN ? "card_back" : "card_" + card;
            out[row + p.column()] = Cell.model(p.shifted() ? name + "_9" : name);
        }
    }

    /** The count a stake slot shows: the (doubled) count up to 99; a doubled count above 99 as its base count. */
    static int shownCount(long count, boolean doubled) {
        if (count <= 99) {
            return (int) count;
        }
        return (int) Math.min(99, doubled ? count / 2 : count);
    }

    /**
     * The stake row ({@code extractPanel}): before the result the items with their counts (doubled: the doubled
     * count and "x2" like the mod, while it fits a slot's 99; above that the base count next to the "x2"), at the
     * result the mod's net labels: two stakes or fewer and not doubled bold to the right of the item (columns 0 / 3,
     * labels 1 / 4), else plain over the item's lower right (columns 0 / 2 / 4).
     */
    static void stakes(Cell[] out, BlackjackSnapshot s) {
        List<ItemAmount> stakes = s.stakes().subList(0, Math.min(3, s.stakes().size()));
        boolean decided = s.phase() == BlackjackSnapshot.PHASE_RESULT && s.result() != 0;
        if (!decided) {
            for (int i = 0; i < stakes.size(); i++) {
                out[STAKE_ROW + i] = Cell.stake(stakes.get(i), shownCount(stakes.get(i).count(), s.doubled()));
            }
            return;
        }
        int halves = BlackjackRound.Result.byCode(s.result()).halves;
        boolean compact = stakes.size() > 2 || s.doubled();
        for (int i = 0; i < stakes.size(); i++) {
            long count = stakes.get(i).count();
            long net = count * halves / 2 - count;
            String label = net > 0 ? "+" + net : Long.toString(net);
            int colour = net > 0 ? GREEN : net == 0 ? WHITE : RED;
            int slot = STAKE_ROW + (compact ? 2 * i : 3 * i);
            out[slot] = Cell.stake(stakes.get(i), 1);
            List<String> chars = new ArrayList<>();
            for (int c = 0; c < label.length(); c++) {
                chars.add(label.substring(c, c + 1));
            }
            if (compact) {
                java.util.Collections.reverse(chars);
            }
            out[slot + 1] = new Cell(MODEL + (compact ? "net_compact" : "net_wide"), null, 1, chars,
                    List.of(colour, (colour & 0xFCFCFC) >> 2), List.of(), null, false);
        }
    }

    private final BlackjackController controller;
    private final CasinoContext ctx;
    private final View view = new View();
    private final Cell[] shown = new Cell[54];

    public BlackjackRoundMenu(UUID viewer, BlackjackController controller, CasinoContext ctx) {
        super(viewer, title(ctx));
        this.controller = controller;
        this.ctx = ctx;
    }

    static Component title(CasinoContext ctx) {
        return title(ctx.layout(), ctx.messages().get()::raw);
    }

    /** The round view's title from the pack's layout and the message texts ({@code raw}). */
    public static Component title(GuiLayout layout, Function<Msg, String> raw) {
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("bj_round_bg_" + col + "_" + row);
            }
        }
        t.text("bj_round_title", raw.apply(Msg.GUI_BLACKJACK_TITLE), GOLD, true, true);
        t.text("bj_round_dealer", raw.apply(Msg.GUI_BLACKJACK_DEALER), PALE);
        t.text("bj_round_you", raw.apply(Msg.GUI_BLACKJACK_YOU), PALE);
        t.text("bj_round_stake", raw.apply(Msg.GUI_BLACKJACK_STAKE), PALE);
        return t.build();
    }

    @Override
    public BlackjackController controller() {
        return controller;
    }

    /** The round menu stays open for the whole hand; the controller closes it when the hand is over. */
    @Override
    public boolean closesWhenIdle() {
        return false;
    }

    @Override
    public void render() {
        clear();
        Arrays.fill(shown, null);
        Player p = Bukkit.getPlayer(viewer());
        if (p != null) {
            apply(view.cells(controller.snapshot(p), false));
        }
    }

    /** Called by the controller every tick while this menu is open: re-sends only the slots that changed. */
    public void refresh(BlackjackSnapshot s) {
        apply(view.cells(s, true));
    }

    private void apply(Cell[] cells) {
        for (int slot = 0; slot < cells.length; slot++) {
            if (Objects.equals(cells[slot], shown[slot])) {
                continue;
            }
            Cell c = cells[slot];
            shown[slot] = c;
            if (c == null) {
                set(slot, null);
            } else if (c.action() != null && c.enabled()) {
                Action a = c.action();
                set(slot, Button.commit(icon(c), (p, click) -> press(p, a)));
            } else {
                set(slot, Button.display(icon(c)));
            }
        }
    }

    /**
     * {@code BlackjackScreen.keyPressed} (H/T/1 = KARTE, S/R/2 = HALTEN, D/3 = DOPPELN, only while that button is
     * active): in a chest the number keys 1, 2 and 3 over any top slot; the letters have no inventory binding.
     */
    @Override
    public Button keyButton(ClickType click, int hotbarButton) {
        Action a = keyAction(click, hotbarButton);
        return a == null ? null : activeButton(a);
    }

    static Action keyAction(ClickType click, int hotbarButton) {
        if (click != ClickType.NUMBER_KEY) {
            return null;
        }
        return switch (hotbarButton) {
            case 0 -> Action.HIT;
            case 1 -> Action.STAND;
            case 2 -> Action.DOUBLE;
            default -> null;
        };
    }

    /** The registered button of {@code action}, if it is shown and clickable. */
    private Button activeButton(Action action) {
        for (int slot = 0; slot < shown.length; slot++) {
            Cell c = shown[slot];
            if (c != null && c.action() == action && c.enabled()) {
                return button(slot);
            }
        }
        return null;
    }

    /** {@code BlackjackScreen.press}: one press per 6 ticks, the vanilla button click sound. */
    private void press(Player player, Action action) {
        if (view.pressLock > 0) {
            return;
        }
        view.pressLock = PRESS_LOCK;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.25f, 1.0f);
        switch (action) {
            case HIT -> controller.hit(player);
            case STAND -> controller.stand(player);
            case DOUBLE -> controller.doubleDown(player);
        }
        if (isOpenFor(player)) {
            apply(view.cells(controller.snapshot(player), false));
        }
    }

    private ItemStack icon(Cell c) {
        var m = ctx.messages().get();
        ItemStack stack;
        if (c.item() != null) {
            stack = DisplayCopies.of(PlayerAccount.material(c.item()), c.amount(), null, List.of());
        } else if (c.action() != null) {
            stack = DisplayCopies.model(c.model(), m.render(c.action().name), m.lines(c.action().lore));
        } else {
            stack = DisplayCopies.model(c.model(), null, List.of());
        }
        if (!c.strings().isEmpty() || !c.colors().isEmpty() || !c.floats().isEmpty()) {
            CustomModelData.Builder data = CustomModelData.customModelData();
            c.strings().forEach(data::addString);
            c.colors().forEach(rgb -> data.addColor(Color.fromRGB(rgb)));
            c.floats().forEach(data::addFloat);
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, data.build());
        }
        if (c.action() == null) {
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        }
        return stack;
    }
}
