package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.gui.Button;
import de.tigges.kaboofcasino.gui.DisplayCopies;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.TitleBuilder;
import de.tigges.kaboofcasino.util.ChipText;
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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The Texas Hold'em view of a seated player (9x6 chest over the poker round panel, see the pack tool's
 * {@code HoldemGui}):
 * <pre>
 *  row 0  slots 0 / 2 / 3 / 5 / 6  the board                     row 1  slots 16 / 17  his two cards
 *  slot 9 the pot, slot 18 what he has to call (both drawn in the box left of the board), slot 8 his hand's name
 *  row 2  slots 19 / 20 / 24 / 25 smallest bet, less, more, all-in; slot 22 the size of the bet; slot 26 the rules
 *         (a small ?); at the showdown instead the cards the others turned up, small above their stacks (19 / 22 /
 *         25; the ? gives way to the third player's)
 *  row 3  slots 27 / 30 / 33       the players (heads; tooltip: stack, bet, last action), their stacks beside them
 *  row 4  slot 39 the timer bar, slot 40 the status line
 *  row 5  PASSEN or GEHEN (45..47), CHECKEN or MITGEHEN (48..50), SETZEN / ERHÖHEN / ALL-IN (51..53)
 * </pre>
 * Number keys: 1 = check or call, 2 = fold, 3 = bet or raise; F = fold. The size of a bet starts at the smallest legal
 * one with every turn; a click on less or more moves it by the big blind (shift: ten big blinds). The table refreshes
 * the view every tick; only changed slots are sent. The menu stays open between hands; GEHEN stands up.
 */
public final class HoldemMenu extends GameMenu {

    static final int[] BOARD = PokerRoundMenu.BOARD;
    static final String[] BOARD_ITEMS = PokerRoundMenu.BOARD_ITEMS;
    static final int[] PLAYER = PokerRoundMenu.PLAYER;
    static final String[] PLAYER_ITEMS = PokerRoundMenu.PLAYER_ITEMS;
    /** His hand's name: the poker round's picture, from slot 8 (slot 26 holds the rules button). */
    static final int PLAYER_NAME = 8;
    static final int POT = 9;
    static final int CALL = 18;
    static final int HELP = 26;
    /** Per head the slot of the small cards that player turned up at the showdown (above his stack). */
    static final int[] SHOW = {19, 22, 25};
    static final String SUITS = "♠♥♦♣";
    static final int AMOUNT = 22;
    static final int AMOUNT_CHARS = 7;
    static final int NUMBER_CHARS = 7;
    static final int STACK_CHARS = 5;
    static final int[] SIZE = {19, 20, 24, 25};
    static final int[] HEADS = {27, 30, 33};
    static final int[] STACKS = {28, 31, 34};
    static final int BAR = PokerRoundMenu.BAR;
    static final int STATUS = 40;
    static final int[][] BUTTONS = {{45, 46, 47}, {48, 49, 50}, {51, 52, 53}};
    static final int[] BUTTON_ART = {46, 49, 52};
    static final String MODEL = PokerRoundMenu.MODEL;
    static final String BLANK = PokerRoundMenu.BLANK;
    static final int GOLD = 0xFFAA00;
    static final int WHITE = 0xFFFFFF;
    static final int GREY = 0xA0A0A0;
    static final int GREEN = 0x55FF55;
    static final int YELLOW = PokerRoundMenu.YELLOW;
    static final TextColor TITLE_GOLD = PokerRoundMenu.GOLD;
    static final TextColor PALE = PokerRoundMenu.PALE;

    enum Action {
        FOLD("fold", Msg.GUI_HOLDEM_FOLD, Msg.GUI_HOLDEM_FOLD_LORE),
        LEAVE("leave", Msg.GUI_HOLDEM_LEAVE, Msg.GUI_HOLDEM_LEAVE_LORE),
        CHECK("check", Msg.GUI_HOLDEM_CHECK, Msg.GUI_HOLDEM_KEY_1),
        CALL("call", Msg.GUI_HOLDEM_CALL, Msg.GUI_HOLDEM_KEY_1),
        BET("bet", Msg.GUI_HOLDEM_BET, Msg.GUI_HOLDEM_KEY_3),
        RAISE("raise", Msg.GUI_HOLDEM_RAISE, Msg.GUI_HOLDEM_KEY_3),
        ALLIN("allin", Msg.GUI_HOLDEM_ALLIN, Msg.GUI_HOLDEM_KEY_3),
        MIN("min", Msg.GUI_HOLDEM_MIN, Msg.GUI_HOLDEM_MIN_LORE),
        MINUS("minus", Msg.GUI_HOLDEM_MINUS, Msg.GUI_HOLDEM_SIZE_LORE),
        PLUS("plus", Msg.GUI_HOLDEM_PLUS, Msg.GUI_HOLDEM_SIZE_LORE),
        MAX("max", Msg.GUI_HOLDEM_MAX, Msg.GUI_HOLDEM_MAX_LORE),
        HELP("help", Msg.GUI_HOLDEM_HELP, Msg.GUI_HOLDEM_HELP_LORE);

        final String model;
        final Msg name;
        final Msg lore;

        Action(String model, Msg name, Msg lore) {
            this.model = model;
            this.name = name;
            this.lore = lore;
        }

        boolean sizes() {
            return this == MIN || this == MINUS || this == PLUS || this == MAX;
        }
    }

    /** A player head in the stake band: who, tooltip lines, glint for the player to act. */
    record Head(UUID player, String name, List<String> lore, boolean glint, boolean folded) {
        Head {
            lore = List.copyOf(lore);
        }
    }

    /** What one slot shows; {@code chips} is the amount an action's name shows. Equal cells are not sent again. */
    record Cell(String model, List<String> strings, List<Integer> colors, List<Float> floats, Action action,
                boolean enabled, int chips, Head head) {
        Cell {
            strings = List.copyOf(strings);
            colors = List.copyOf(colors);
            floats = List.copyOf(floats);
        }

        static Cell model(String name) {
            return new Cell(MODEL + name, List.of(), List.of(), List.of(), null, false, 0, null);
        }

        static Cell card(String name, int card) {
            return new Cell(MODEL + name, List.of(card < 0 ? "back" : Integer.toString(card)), List.of(), List.of(),
                    null, false, 0, null);
        }

        /** A number: its characters (a blank draws nothing) in {@code rgb} over the client's shadow of it. */
        static Cell number(String name, String text, int rgb) {
            List<String> chars = new ArrayList<>();
            for (int i = 0; i < text.length(); i++) {
                chars.add(text.substring(i, i + 1));
            }
            return new Cell(MODEL + name, chars, List.of(rgb, PokerRoundMenu.shadow(rgb)), List.of(), null, false, 0,
                    null);
        }

        static Cell button(Action a, boolean art, boolean enabled, int chips) {
            String model = art ? MODEL + "hd_" + a.model + (enabled ? "_on" : "_off") : BLANK;
            return new Cell(model, List.of(), List.of(), List.of(), a, enabled, chips, null);
        }
    }

    /** {@code text} centred in {@code width} characters (left blank first). */
    static String centred(String text, int width) {
        if (text.length() >= width) {
            return text.substring(text.length() - width);
        }
        int left = (width - text.length()) / 2;
        return " ".repeat(left) + text + " ".repeat(width - left - text.length());
    }

    /** A chip count for the 6 px digits: at most {@code width} characters (larger counts are capped). */
    static String digits(long n, int width) {
        long max = 1;
        for (int i = 0; i < width; i++) {
            max *= 10;
        }
        return Long.toString(Math.max(0, Math.min(max - 1, n)));
    }

    /** The menu's state between ticks: the card animations and the chosen bet size. */
    static final class View {
        final PokerRoundMenu.Hand player = new PokerRoundMenu.Hand();
        final PokerRoundMenu.Hand board = new PokerRoundMenu.Hand();
        int ticks;
        int pressLock;
        int amount;
        int turnSeen = Integer.MIN_VALUE;

        /** The chosen bet size, within the legal range of the view. */
        int amount(HoldemView v) {
            if (v.turn() != turnSeen) {
                turnSeen = v.turn();
                amount = v.minRaiseTo();
            }
            amount = Math.max(v.minRaiseTo(), Math.min(v.maxRaiseTo(), amount));
            return amount;
        }

        void size(HoldemView v, Action a, boolean shift, int bigBlind) {
            int step = bigBlind * (shift ? 10 : 1);
            amount(v);
            amount = switch (a) {
                case MIN -> v.minRaiseTo();
                case MINUS -> amount - step;
                case PLUS -> amount + step;
                case MAX -> v.maxRaiseTo();
                default -> amount;
            };
            amount(v);
        }

        Cell[] cells(HoldemView v, boolean tick, int turnTicks) {
            if (tick) {
                ticks++;
                if (pressLock > 0) {
                    pressLock--;
                }
            }
            boolean instant = ticks < PokerRoundMenu.INSTANT_TICKS;
            long now = v.gameTime();
            player.update(PokerRoundMenu.first(v.hole(), 2), v.holeAt(), List.of(), now, instant);
            board.update(PokerRoundMenu.first(v.board(), 5), v.boardAt(), List.of(), now, instant);
            List<Integer> mine = player.values(now);
            List<Integer> shownBoard = board.values(now);
            Cell[] out = new Cell[54];
            for (int i = 0; i < mine.size(); i++) {
                out[PLAYER[i]] = Cell.card(PLAYER_ITEMS[i], mine.get(i));
            }
            for (int i = 0; i < shownBoard.size(); i++) {
                out[BOARD[i]] = Cell.card(BOARD_ITEMS[i], shownBoard.get(i));
            }
            if (!mine.isEmpty()) {
                List<Integer> all = new ArrayList<>(mine);
                all.addAll(shownBoard);
                out[PLAYER_NAME] = new Cell(MODEL + "hd_hand", List.of(Integer.toString(PokerHands.category(
                        PokerHands.score(all)))), List.of(YELLOW, PokerRoundMenu.shadow(YELLOW)), List.of(), null,
                        false, 0, null);
            }
            if (v.pot() > 0) {
                out[POT] = Cell.number("hd_pot", centred(digits(v.pot(), NUMBER_CHARS), NUMBER_CHARS), GOLD);
            }
            if (v.myTurn()) {
                out[CALL] = Cell.number("hd_call", centred(digits(v.toCall(), NUMBER_CHARS), NUMBER_CHARS), WHITE);
            }
            // the showdown: the others' turned-up cards take the place of the bet size controls
            boolean showdown = v.seats().stream().anyMatch(s -> !s.me() && s.shown().size() == 2);
            for (int i = 0; i < v.seats().size() && i < HEADS.length; i++) {
                HoldemView.Seat s = v.seats().get(i);
                // the tooltip's facts are part of the cell, so a changed tooltip is sent again
                out[HEADS[i]] = new Cell(null, List.of(), List.of(), List.of(), null, false, 0,
                        new Head(s.player(), s.name(), List.of(Integer.toString(s.stack()), Integer.toString(s.bet()),
                                s.action(), Boolean.toString(s.dealer()), s.shown().toString(),
                                Integer.toString(s.category())), s.acting(), s.folded()));
                int colour = s.winner() ? GREEN : s.folded() ? GREY : s.acting() ? GOLD : WHITE;
                out[STACKS[i]] = Cell.number("hd_stack_" + i, digits(s.stack(), STACK_CHARS), colour);
                if (showdown && !s.me() && s.shown().size() == 2) {
                    out[SHOW[i]] = new Cell(MODEL + "hd_show_" + i, List.of(Integer.toString(s.shown().get(0)),
                            Integer.toString(s.shown().get(1))), List.of(), List.of(), null, false, 0, null);
                }
            }
            // the rules, unless the third player's turned-up cards are where the ? is
            if (out[SHOW[SHOW.length - 1]] == null) {
                out[HELP] = Cell.button(Action.HELP, true, true, 0);
            }
            out[STATUS] = Cell.model("hd_status_" + v.status());
            if (v.myTurn() && v.ticksLeft() > 0 && turnTicks > 0) {
                float fill = Math.min(PokerRoundMenu.BAR_W, (float) PokerRoundMenu.BAR_W * v.ticksLeft() / turnTicks);
                int colour = v.ticksLeft() * 4 > turnTicks ? PokerRoundMenu.BAR_COLOUR : PokerRoundMenu.BAR_LOW_COLOUR;
                out[BAR] = new Cell(PokerRoundMenu.BAR_MODEL, List.of(), List.of(colour), List.of(fill), null, false,
                        0, null);
            }
            boolean live = pressLock == 0;
            // left: PASSEN while he plays the hand, else GEHEN (stand up)
            Action left = v.inHand() ? Action.FOLD : Action.LEAVE;
            boolean leftOn = v.inHand() ? live && v.canFold() : live;
            Action middle = v.canCheck() || !v.canCall() ? Action.CHECK : Action.CALL;
            boolean middleOn = live && (v.canCheck() || v.canCall());
            int size = v.canRaise() ? amount(v) : 0;
            Action right = v.canRaise() && size >= v.maxRaiseTo() ? Action.ALLIN : v.opening() ? Action.BET
                    : Action.RAISE;
            boolean rightOn = live && v.canRaise();
            Action[] actions = {left, middle, right};
            boolean[] on = {leftOn, middleOn, rightOn};
            int[] chips = {0, v.toCall(), size};
            for (int b = 0; b < 3; b++) {
                for (int slot : BUTTONS[b]) {
                    out[slot] = Cell.button(actions[b], slot == BUTTON_ART[b], on[b], chips[b]);
                }
            }
            if (!showdown) {
                Action[] sizes = {Action.MIN, Action.MINUS, Action.PLUS, Action.MAX};
                for (int i = 0; i < SIZE.length; i++) {
                    out[SIZE[i]] = Cell.button(sizes[i], true, v.canRaise(), size);
                }
                if (v.canRaise()) {
                    out[AMOUNT] = Cell.number("hd_amount", centred(digits(size, AMOUNT_CHARS), AMOUNT_CHARS), GOLD);
                }
            }
            return out;
        }
    }

    private final PokerController controller;
    private final CasinoContext ctx;
    private final View view = new View();
    private final Cell[] shown = new Cell[54];
    private HoldemView last;

    public HoldemMenu(UUID viewer, PokerController controller, CasinoContext ctx) {
        super(viewer, title(ctx));
        this.controller = controller;
        this.ctx = ctx;
    }

    static Component title(CasinoContext ctx) {
        var h = ctx.config().get().poker().holdem();
        return title(ctx.layout(), ctx.messages().get()::raw, h.smallBlind(), h.bigBlind());
    }

    /** The round panel with TEXAS HOLD'EM, POT, DU, ZU ZAHLEN, the blinds and SPIELER. */
    public static Component title(GuiLayout layout, Function<Msg, String> raw, int smallBlind, int bigBlind) {
        TitleBuilder t = new TitleBuilder(layout);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                t.art("pk_round_bg_" + col + "_" + row);
            }
        }
        t.text("pk_round_title", raw.apply(Msg.GUI_HOLDEM_TITLE), TITLE_GOLD, true, true);
        t.text("pk_round_dealer", raw.apply(Msg.GUI_HOLDEM_POT), PALE, false, true);
        t.text("pk_round_you", raw.apply(Msg.GUI_BLACKJACK_YOU), PALE, false, true);
        t.text("hd_call_label", raw.apply(Msg.GUI_HOLDEM_TO_CALL), PALE, false, true);
        t.text("hd_blinds", raw.apply(Msg.GUI_HOLDEM_BLINDS).replace("<sb>", ChipText.number(smallBlind))
                .replace("<bb>", ChipText.number(bigBlind)), PALE, false, true);
        t.text("hd_players", raw.apply(Msg.GUI_HOLDEM_PLAYERS), PALE, false, true);
        return t.build();
    }

    @Override
    public PokerController controller() {
        return controller;
    }

    /** Open between hands too; the table closes it when the player leaves the table. */
    @Override
    public boolean closesWhenIdle() {
        return false;
    }

    private int turnTicks() {
        return ctx.config().get().poker().holdem().turnSeconds() * 20;
    }

    @Override
    public void render() {
        clear();
        Arrays.fill(shown, null);
        Player p = Bukkit.getPlayer(viewer());
        HoldemView v = p == null ? null : controller.holdemView(p);
        if (v != null) {
            last = v;
            apply(view.cells(v, false, turnTicks()));
        }
    }

    /** Called by the table every tick while this menu is open. */
    public void refresh(HoldemView v) {
        last = v;
        apply(view.cells(v, true, turnTicks()));
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
            } else if (c.action() == Action.HELP) {
                set(slot, Button.of(icon(c), (p, click) -> {
                    p.playSound(p, Sound.UI_BUTTON_CLICK, 0.2f, 1.4f);
                    ctx.messages().get().send(p, Msg.POKER_HOLDEM_RULES);
                }));
            } else if (c.action() != null && c.enabled()) {
                Action a = c.action();
                ItemStack icon = icon(c);
                set(slot, a.sizes() ? Button.commitWithShift(icon, (p, click) -> press(p, a, click.isShiftClick()))
                        : Button.commit(icon, (p, click) -> press(p, a, false)));
            } else {
                set(slot, Button.display(icon(c)));
            }
        }
    }

    @Override
    public Button keyButton(ClickType click, int hotbarButton) {
        Action[] wanted = keyActions(click, hotbarButton);
        for (Action a : wanted) {
            for (int slot = 0; slot < shown.length; slot++) {
                Cell c = shown[slot];
                if (c != null && c.action() == a && c.enabled()) {
                    return button(slot);
                }
            }
        }
        return null;
    }

    static Action[] keyActions(ClickType click, int hotbarButton) {
        if (click == ClickType.NUMBER_KEY) {
            return switch (hotbarButton) {
                case 0 -> new Action[] {Action.CHECK, Action.CALL};
                case 1 -> new Action[] {Action.FOLD};
                case 2 -> new Action[] {Action.BET, Action.RAISE, Action.ALLIN};
                default -> new Action[0];
            };
        }
        return click == ClickType.SWAP_OFFHAND ? new Action[] {Action.FOLD} : new Action[0];
    }

    private void press(Player player, Action action, boolean shift) {
        HoldemView v = last;
        if (v == null) {
            return;
        }
        if (action.sizes()) {
            view.size(v, action, shift, ctx.config().get().poker().holdem().bigBlind());
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.2f, 1.4f);
            apply(view.cells(v, false, turnTicks()));
            return;
        }
        if (view.pressLock > 0) {
            return;
        }
        view.pressLock = PokerRoundMenu.PRESS_LOCK;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.25f, 1.0f);
        switch (action) {
            case FOLD -> controller.holdemFold(player);
            case LEAVE -> controller.holdemLeave(player);
            case CHECK, CALL -> controller.holdemCheckOrCall(player);
            case BET, RAISE, ALLIN -> controller.holdemRaiseTo(player, view.amount(v));
            default -> {
            }
        }
        if (isOpenFor(player)) {
            HoldemView next = controller.holdemView(player);
            if (next != null) {
                last = next;
                apply(view.cells(next, false, turnTicks()));
            }
        }
    }

    private ItemStack icon(Cell c) {
        Messages m = ctx.messages().get();
        if (c.head() != null) {
            return head(c.head());
        }
        ItemStack stack;
        if (c.action() != null) {
            var chips = Messages.text("chips", ChipText.chips(c.chips()));
            // a size button without a bet to size says so (not "Einsatz jetzt: 0 Chips")
            List<Component> lore = c.action().sizes() && !c.enabled()
                    ? m.lines(Msg.GUI_HOLDEM_SIZE_WAIT) : m.lines(c.action().lore, chips);
            stack = DisplayCopies.model(c.model(), m.render(c.action().name, chips), lore);
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

    /** "A♠", "10♥": a card as text (suit order of the card sheet: spades, hearts, diamonds, clubs). */
    static String cardText(int card) {
        return de.tigges.kaboofcasino.game.blackjack.BlackjackCards.rankName(card)
                + SUITS.charAt(de.tigges.kaboofcasino.game.blackjack.BlackjackCards.suit(card));
    }

    /** A player's head: name, stack, bet, last action; the player to act glints. */
    private ItemStack head(Head h) {
        HoldemView v = last;
        HoldemView.Seat seat = v == null ? null : v.seats().stream().filter(s -> s.player().equals(h.player()))
                .findFirst().orElse(null);
        Messages m = ctx.messages().get();
        List<Component> lore = new ArrayList<>();
        if (seat != null) {
            lore.add(m.render(Msg.GUI_HOLDEM_HEAD_STACK, Messages.text("chips", ChipText.chips(seat.stack()))));
            if (seat.bet() > 0) {
                lore.add(m.render(Msg.GUI_HOLDEM_HEAD_BET, Messages.text("chips", ChipText.chips(seat.bet()))));
            }
            if (!seat.action().isEmpty()) {
                lore.add(Component.text(seat.action(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            if (seat.shown().size() == 2) {
                lore.add(m.render(Msg.GUI_HOLDEM_HEAD_CARDS, Messages.text("cards", cardText(seat.shown().get(0))
                        + " " + cardText(seat.shown().get(1)))));
                if (seat.category() >= 0) {
                    lore.add(m.render(Msg.GUI_HOLDEM_HEAD_HAND, Messages.text("hand",
                            PokerPanel.HANDS[Math.min(PokerPanel.HANDS.length - 1, seat.category())])));
                }
            }
            if (seat.dealer()) {
                lore.add(m.render(Msg.GUI_HOLDEM_HEAD_DEALER));
            }
            if (seat.acting()) {
                lore.add(m.render(Msg.GUI_HOLDEM_HEAD_ACTING));
            }
        }
        ItemStack stack = DisplayCopies.of(Material.PLAYER_HEAD, 1, Component.text(h.name(), h.folded()
                ? NamedTextColor.GRAY : NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false), lore);
        // only a profile that already has its skin: anything else would make the server look it up at Mojang
        Player online = Bukkit.getPlayer(h.player());
        if (online != null && online.getPlayerProfile().hasTextures()) {
            stack.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(online.getPlayerProfile()));
        }
        if (h.glint()) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }
}
