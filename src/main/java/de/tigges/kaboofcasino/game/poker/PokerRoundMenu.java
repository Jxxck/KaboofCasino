package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCards;
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
import java.util.Collections;
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
import org.bukkit.inventory.ItemStack;

/**
 * The poker round view as a 9x6 chest (render.md 5.2; the mod's 160x196 side panel, {@code PokerScreen} while a hand
 * runs). The title is the panel (re-built 304 px wide around the chest, see the pack tool's {@code PokerGuiArt}) with
 * "POKER" and the labels CROUPIER / DU above the hand boxes; everything that changes is a display item whose pack
 * model draws at a fixed window position (see the pack tool's {@code PokerGui}):
 * <pre>
 *  row 0  slots 0 / 2 / 3 / 5 / 6  the board (window x 21 + 27 i, y 15)
 *  row 1  slots 9 / 10             the dealer's cards (x -47 / -19)      slots 16 / 17  the player's cards (169 / 197)
 *  row 2  slot 18 the dealer's hand name, 19 EINSATZ / BILANZ, 22 status / result line, 26 the player's hand name
 *  row 3  slots 27..32             the ante items, at the result the NET items (one per item kind)
 *  row 4  the NET labels under their items, slot 39 the timer bar, slot 43 "x3"
 *  row 5  MITGEHEN (45..48), AUSSTEIGEN (50..53); slot 49 is the felt between them
 * </pre>
 * The view is the mod's client logic on game ticks: a card appears {@value #APPEAR_DELAY} ticks after it was dealt
 * (at once for cards already on the table during the first {@value #INSTANT_TICKS} ticks after opening), a dealer card
 * shows its face {@value #FACE_DELAY} ticks after it was turned, a button press locks both buttons for
 * {@value #PRESS_LOCK} ticks; MITGEHEN is greyed unless the round can call and the player has twice the ante. The hand
 * names are those of the cards on show (the player's with the board, the dealer's once both his cards are face up,
 * red if he does not qualify). The controller calls {@link #refresh} every tick while the menu is open; only slots
 * whose content changed are re-sent. No slot holds a real item; clicks outside the two buttons do nothing. The menu
 * never closes for idleness (the controller closes it when the hand is over).
 */
public final class PokerRoundMenu extends GameMenu {

    static final int[] BOARD = {0, 2, 3, 5, 6};
    static final String[] BOARD_ITEMS = {"card_board_a", "card_board_b", "card_board_a", "card_board_b",
            "card_board_a"};
    static final int[] DEALER = {9, 10};
    static final String[] DEALER_ITEMS = {"card_dealer_0", "card_dealer_1"};
    static final int[] PLAYER = {16, 17};
    static final String[] PLAYER_ITEMS = {"card_player_0", "card_player_1"};
    static final int DEALER_NAME = 18;
    static final int STAKE_LABEL = 19;
    static final int STATUS = 22;
    static final int PLAYER_NAME = 26;
    static final int STAKE_ROW = 27;
    /** A NET label lies in the slot under its item. */
    static final int LABEL_BELOW = 9;
    static final int BAR = 39;
    static final int TRIPLED = 43;
    static final int[][] BUTTONS = {{45, 46, 47, 48}, {50, 51, 52, 53}};
    static final int[] BUTTON_ART = {46, 51};
    /** {@code PokerScreen}: the hands' APPEAR_DELAY and FACE_DELAY, the 6-tick press lock, {@code instant}. */
    static final int APPEAR_DELAY = 22;
    static final int FACE_DELAY = 8;
    static final int PRESS_LOCK = 6;
    static final int INSTANT_TICKS = 4;
    static final int HIDDEN = BlackjackCards.HIDDEN;
    /** {@code PokerMenu}: at most 2 player, 2 dealer and 5 board cards, 6 NET lines; the label's 7 characters. */
    static final int MAX_HAND = 2;
    static final int MAX_BOARD = 5;
    static final int MAX_NET = 6;
    static final int NET_CHARS = 6;
    static final int COMPACT_CHARS = 5;
    /** The timer bar: 136 px for 150 quarter seconds ({@code D_TURN_LEFT}), red at 38 and below. */
    static final int BAR_W = 136;
    static final int BAR_FULL = 150;
    static final int BAR_LOW = 38;
    static final int BAR_COLOUR = 0xE0A030;
    static final int BAR_LOW_COLOUR = 0xD04040;
    static final int GREEN = 0x55FF55;
    /** {@code PokerScreen}: the player's hand name -171. */
    static final int YELLOW = 0xFFFF55;
    static final int WHITE = 0xFFFFFF;
    static final int RED = 0xFF5555;
    static final String MODEL = "kaboofcasino:gui/poker/";
    /** The blackjack GUI's timer bar (same place, same 136 px). */
    static final String BAR_MODEL = "kaboofcasino:gui/blackjack/bar";
    static final String BLANK = "kaboofcasino:gui/blank";
    /** {@code extractPanel}: the title -22016 bold with shadow, the labels -3614776 with shadow. */
    static final TextColor GOLD = TextColor.color(0xFFAA00);
    static final TextColor PALE = TextColor.color(0xC8D7C8);

    /** The two panel buttons ({@code PokerMenu} buttons 1 and 2). */
    enum Action {
        CALL("call", Msg.GUI_POKER_CALL, Msg.GUI_POKER_CALL_LORE),
        FOLD("fold", Msg.GUI_POKER_FOLD, Msg.GUI_POKER_FOLD_LORE);

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
     * What one slot shows: a pack model (with custom model data) or a vanilla item, and for button slots the action
     * and whether it is clickable. Equal cells are not sent again.
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

        static Cell card(String name, int card) {
            return new Cell(MODEL + name, null, 1, List.of(card == HIDDEN ? "back" : Integer.toString(card)),
                    List.of(), List.of(), null, false);
        }

        /** A hand name: category 0..9, text colour and the client's shadow of it. */
        static Cell hand(String name, int category, int rgb) {
            return new Cell(MODEL + name, null, 1, List.of(Integer.toString(category)), List.of(rgb, shadow(rgb)),
                    List.of(), null, false);
        }

        static Cell stake(String item, int amount) {
            return new Cell(null, item, amount, List.of(), List.of(), List.of(), null, false);
        }
    }

    /** {@code BlackjackScreen.Hand} on game ticks, with the server's reveal times for the face delay. */
    static final class Hand {
        private final int[] value = new int[MAX_BOARD];
        private final long[] shownAt = new long[MAX_BOARD];
        private final long[] faceAt = new long[MAX_BOARD];
        private int count;

        void update(List<Integer> cards, List<Long> dealtAt, List<Long> revealedAt, long now, boolean instant) {
            int n = Math.min(MAX_BOARD, cards.size());
            if (n < count) {
                count = 0;
            }
            for (int i = 0; i < n; i++) {
                int card = cards.get(i);
                if (i >= count) {
                    value[i] = card;
                    shownAt[i] = instant ? Long.MIN_VALUE : (i < dealtAt.size() ? dealtAt.get(i) : now) + APPEAR_DELAY;
                    faceAt[i] = Long.MIN_VALUE;
                } else if (value[i] == HIDDEN && card != HIDDEN) {
                    value[i] = card;
                    long turned = i < revealedAt.size() && revealedAt.get(i) >= 0 ? revealedAt.get(i) : now;
                    faceAt[i] = instant ? Long.MIN_VALUE : turned + FACE_DELAY;
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

        /** The cards on show: face up once their face delay is over, else {@link #HIDDEN}. */
        List<Integer> values(long now) {
            List<Integer> out = new ArrayList<>();
            int shown = visible(now);
            for (int i = 0; i < shown; i++) {
                out.add(value[i] != HIDDEN && now >= faceAt[i] ? value[i] : HIDDEN);
            }
            return out;
        }
    }

    /** The menu's state between ticks and the cells of one tick (pure: no server needed). */
    static final class View {
        final Hand dealer = new Hand();
        final Hand player = new Hand();
        final Hand board = new Hand();
        int ticks;
        int pressLock;

        /** One step: {@code tick} = a game tick passed ({@code containerTick}), else a redraw in the same tick. */
        Cell[] cells(PokerSnapshot s, boolean tick) {
            if (tick) {
                ticks++;
                if (pressLock > 0) {
                    pressLock--;
                }
            }
            boolean instant = ticks < INSTANT_TICKS;
            long now = s.gameTime();
            dealer.update(first(s.dealer(), MAX_HAND), s.dealerDealtAt(), s.dealerRevealedAt(), now, instant);
            player.update(first(s.player(), MAX_HAND), s.playerDealtAt(), List.of(), now, instant);
            board.update(first(s.board(), MAX_BOARD), s.boardDealtAt(), List.of(), now, instant);
            List<Integer> dealerCards = dealer.values(now);
            List<Integer> playerCards = player.values(now);
            List<Integer> boardCards = board.values(now);
            Cell[] out = new Cell[54];
            cards(out, DEALER, DEALER_ITEMS, dealerCards);
            cards(out, PLAYER, PLAYER_ITEMS, playerCards);
            cards(out, BOARD, BOARD_ITEMS, boardCards);
            if (!playerCards.isEmpty()) {
                List<Integer> mine = new ArrayList<>(playerCards);
                mine.addAll(boardCards);
                out[PLAYER_NAME] = Cell.hand("hand_player", PokerHands.category(PokerHands.score(mine)), YELLOW);
            }
            if (dealerCards.size() == 2 && !dealerCards.contains(HIDDEN)) {
                List<Integer> his = new ArrayList<>(dealerCards);
                his.addAll(boardCards);
                int score = PokerHands.score(his);
                out[DEALER_NAME] = Cell.hand("hand_dealer", PokerHands.category(score),
                        PokerHands.qualifies(score) ? WHITE : RED);
            }
            stakes(out, s);
            if (decided(s)) {
                out[STATUS] = Cell.model("result_" + s.result());
            } else {
                out[STATUS] = Cell.model("status_" + Math.max(1, Math.min(PokerSnapshot.PHASE_RESULT, s.phase())));
            }
            int left = (s.turnTicksLeft() + 3) / 4;
            if (s.phase() == PokerSnapshot.PHASE_DECISION && left > 0) {
                int fill = Math.min(BAR_W, BAR_W * left / BAR_FULL);
                if (fill > 0) {
                    out[BAR] = new Cell(BAR_MODEL, null, 1, List.of(), List.of(left > BAR_LOW ? BAR_COLOUR
                            : BAR_LOW_COLOUR), List.of((float) fill), null, false);
                }
            }
            boolean decision = s.phase() == PokerSnapshot.PHASE_DECISION && pressLock == 0;
            boolean[] enabled = {decision && s.canCall() && s.affordCall(), decision && s.canFold()};
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

    static List<Integer> first(List<Integer> cards, int max) {
        return cards.subList(0, Math.min(max, cards.size()));
    }

    /** {@code PokerScreen}: the result is shown once the phase is RESULT and a result is known. */
    static boolean decided(PokerSnapshot s) {
        return s.phase() == PokerSnapshot.PHASE_RESULT && s.result() >= 1 && s.result() <= 5;
    }

    static void cards(Cell[] out, int[] slots, String[] items, List<Integer> values) {
        for (int i = 0; i < values.size() && i < slots.length; i++) {
            out[slots[i]] = Cell.card(items[i], values.get(i));
        }
    }

    /** Grid columns of the NET items: the mod's 44 px step for one or two (the label right of the item), else 28. */
    static int[] netColumns(int n) {
        return switch (n) {
            case 0 -> new int[0];
            case 1 -> new int[] {0};
            case 2 -> new int[] {0, 3};
            case 3 -> new int[] {0, 2, 4};
            case 4 -> new int[] {0, 2, 4, 6};
            case 5 -> new int[] {0, 2, 4, 6, 8};
            default -> new int[] {0, 1, 2, 3, 4, 5};
        };
    }

    /** The client's text shadow colour ({@code ARGB.scaleRGB(colour, 0.25F)}). */
    static int shadow(int rgb) {
        return (rgb & 0xFCFCFC) >> 2;
    }

    /**
     * The NET label of {@code delta}: "+n", "0" or "-n" in at most {@code chars} characters (the mod shrinks a longer
     * one; with 64 antes per slot the wide labels reach +19584, the compact ones +6528; beyond that it is capped).
     */
    static String netLabel(long delta, int chars) {
        long max = 1;
        for (int i = 1; i < chars; i++) {
            max *= 10;
        }
        long shown = Math.max(1 - max, Math.min(max - 1, delta));
        return shown > 0 ? "+" + shown : Long.toString(shown);
    }

    /**
     * The count an ante slot shows ({@code PokerTableBlockEntity.anteStakes}): the stake, after the call a third of
     * it (the snapshot's stakes are the mod's {@code shownWager}, 3 x the ante then), at most a slot's 99.
     */
    static int anteShown(long count, boolean called) {
        return (int) Math.max(1, Math.min(99, called ? count / 3 : count));
    }

    /**
     * The stake band ({@code extractPanel}): before the result EINSATZ, the ante items with their counts (one per
     * filled bet slot) and "x3" once called; at the result BILANZ (not with more than three kinds) and per item kind
     * the item and its net: one or two kinds with the label right of the item, more with the label over the item's
     * lower right like a count. Colours: green above 0, white 0, red below.
     */
    static void stakes(Cell[] out, PokerSnapshot s) {
        if (!decided(s)) {
            out[STAKE_LABEL] = Cell.model("label_stake");
            List<ItemAmount> stakes = s.stakes();
            for (int i = 0; i < stakes.size() && i < 3; i++) {
                out[STAKE_ROW + i] = Cell.stake(stakes.get(i).item(), anteShown(stakes.get(i).count(), s.called()));
            }
            if (s.called()) {
                out[TRIPLED] = Cell.model("tripled");
            }
            return;
        }
        List<PokerSnapshot.Net> net = s.net().subList(0, Math.min(MAX_NET, s.net().size()));
        boolean compact = net.size() > 3;
        if (!compact) {
            out[STAKE_LABEL] = Cell.model("label_net");
        }
        boolean wide = net.size() <= 2;
        int[] columns = netColumns(net.size());
        for (int i = 0; i < net.size(); i++) {
            long delta = net.get(i).count();
            String label = netLabel(delta, wide ? NET_CHARS : COMPACT_CHARS);
            int colour = delta > 0 ? GREEN : delta == 0 ? WHITE : RED;
            int slot = STAKE_ROW + columns[i];
            out[slot] = Cell.stake(net.get(i).item(), 1);
            List<String> chars = new ArrayList<>();
            for (int c = 0; c < label.length(); c++) {
                chars.add(label.substring(c, c + 1));
            }
            if (!wide) {
                Collections.reverse(chars);
            }
            out[slot + LABEL_BELOW] = new Cell(MODEL + (wide ? "net_wide" : "net_compact"), null, 1, chars,
                    List.of(colour, shadow(colour)), List.of(), null, false);
        }
    }

    private final PokerController controller;
    private final CasinoContext ctx;
    private final View view = new View();
    private final Cell[] shown = new Cell[54];

    public PokerRoundMenu(UUID viewer, PokerController controller, CasinoContext ctx) {
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
                t.art("pk_round_bg_" + col + "_" + row);
            }
        }
        t.text("pk_round_title", raw.apply(Msg.GUI_POKER_TITLE), GOLD, true, true);
        t.text("pk_round_dealer", raw.apply(Msg.GUI_BLACKJACK_DEALER), PALE, false, true);
        t.text("pk_round_you", raw.apply(Msg.GUI_BLACKJACK_YOU), PALE, false, true);
        return t.build();
    }

    @Override
    public PokerController controller() {
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
    public void refresh(PokerSnapshot s) {
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

    /** {@code PokerScreen.press}: one press per 6 ticks, the vanilla button click sound. */
    private void press(Player player, Action action) {
        if (view.pressLock > 0) {
            return;
        }
        view.pressLock = PRESS_LOCK;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.25f, 1.0f);
        switch (action) {
            case CALL -> controller.call(player);
            case FOLD -> controller.fold(player);
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
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        }
        return stack;
    }
}
