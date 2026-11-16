package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Texas Hold'em screens (Jack 2026-11-10, not in MineVegas): the round view keeps the poker round panel
 * ({@link PokerGuiArt}: board, the player's box, the stake band, the timer bar) and only adds what changes, as items
 * like {@link PokerGui}'s (flat quads at fixed window positions from a fixed slot):
 * <ul>
 *   <li>three 52 px buttons over slots 45..47 / 48..50 / 51..53 at window y {@value PokerGui#BUTTON_Y}: PASSEN or
 *       GEHEN, CHECKEN or MITGEHEN, SETZEN, ERHÖHEN or ALL-IN ({@code hd_<word>_on|off});</li>
 *   <li>four 18 px buttons in row 2 for the size of a bet: the smallest (slot 19), one step less (20), one more (24),
 *       all-in (25) ({@code hd_min|minus|plus|max_on|off}), the size itself between them ({@code hd_amount}, slot 22,
 *       centred on window x 88);</li>
 *   <li>numbers ({@code select} per character on custom model data string k, digits and '+'/'-' of the blackjack GUI,
 *       tinted with colour 0 over the shadow in colour 1; a blank is no model): the pot ({@code hd_pot}, slot 9) and
 *       what the player has to call ({@code hd_call}, slot 18) in the box left of the board, the stacks next to the
 *       player heads in the stake band ({@code hd_stack_<i>}, slots 28 / 31 / 34);</li>
 *   <li>the rules button, a 12 px "?" badge at the left of slot 26's cell ({@code hd_help_on}, its tooltip explains
 *       the game), so the player's hand name ({@code hd_hand}: the poker GUI's pictures, cut to the
 *       {@value #HAND_CUT_W} px the names use, at the same place) comes from slot 8 instead;</li>
 *   <li>at the showdown the two cards each other player turned up, small ({@value #MINI_W}x{@value #MINI_H}, own
 *       faces {@code hd_mini_<id>}: the rank in the GUI font over a suit pip) above his stack ({@code hd_show_<i>} in
 *       slots 19 / 22 / 25, custom model data strings 0 and 1 = the card ids), where the size buttons are while
 *       betting;</li>
 *   <li>status lines ({@code hd_status_<key>}, slot 40) at the poker status position.</li>
 * </ul>
 * The words above the numbers (POT, ZU ZAHLEN, BLINDS, SPIELER) are title texts at the anchors of {@link #anchors()};
 * the buy-in view is the poker bet view with its own texts and the HINSETZEN button ({@code holdem/sit_on|off}).
 */
final class HoldemGui {

    static final int BUTTON_W = 52;
    static final int[] BUTTON_X = {9, 63, 117};
    static final int[] BUTTON_ART_SLOT = {46, 49, 52};
    static final int SIZE_ROW_Y = 54;
    static final int[] SIZE_SLOTS = {19, 20, 24, 25};
    static final String[] SIZE_NAMES = {"min", "minus", "plus", "max"};
    static final int AMOUNT_SLOT = 22;
    static final int AMOUNT_CHARS = 7;
    static final int AMOUNT_CENTRE = 88;
    /** The box left of the board (the poker dealer box): window -55..14, centre -20. */
    static final int BOX_CENTRE = PokerGuiArt.DEALER_X + PokerGuiArt.HAND_W / 2;
    static final int POT_SLOT = 9;
    static final int POT_TOP = 23;
    static final int CALL_SLOT = 18;
    static final int HELP_SLOT = 26;
    static final int HELP_SIZE = 12;
    static final int HAND_SLOT = 8;
    /** The part of a hand name picture ({@code PokerGui.NAME_W} wide) a name uses: from x 4, so it starts at 165. */
    static final int HAND_CUT_X = 4;
    static final int HAND_CUT_W = 61;
    static final int[] SHOW_SLOTS = {19, 22, 25};
    static final int MINI_W = 13;
    static final int MINI_H = 16;
    static final int MINI_Y = SIZE_ROW_Y + 1;
    static final int CALL_LABEL_TOP = 39;
    static final int CALL_TOP = 50;
    static final int BLINDS_TOP = 61;
    static final int NUMBER_CHARS = 7;
    static final int[] HEAD_SLOTS = {27, 30, 33};
    static final int[] STACK_SLOTS = {28, 31, 34};
    static final int STACK_CHARS = 5;
    static final int STACK_TOP = PokerGuiArt.STAKE_Y + 5;
    static final int STATUS_SLOT = 40;
    static final int GOLD = 0xFFFFAA00;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREEN = 0xFF55FF55;
    static final int RED = 0xFFFF5555;

    /** The status lines: key, text, colour, bold. */
    static final Map<String, Object[]> STATUSES = new LinkedHashMap<>();

    static {
        STATUSES.put("your_turn", new Object[] {"Du bist dran!", GOLD, true});
        STATUSES.put("waiting", new Object[] {"Die anderen sind dran", WHITE, false});
        STATUSES.put("dealing", new Object[] {"Es wird ausgeteilt", WHITE, false});
        STATUSES.put("all_in", new Object[] {"Du bist all-in", GOLD, true});
        STATUSES.put("showdown", new Object[] {"Showdown", WHITE, true});
        STATUSES.put("won", new Object[] {"Gewonnen!", GREEN, true});
        STATUSES.put("split", new Object[] {"Pot geteilt", WHITE, true});
        STATUSES.put("lost", new Object[] {"Verloren", RED, true});
        STATUSES.put("folded", new Object[] {"Ausgestiegen", RED, true});
        STATUSES.put("next", new Object[] {"Gleich kommt die nächste Hand", WHITE, false});
        STATUSES.put("lobby", new Object[] {"Warte auf Mitspieler", WHITE, false});
    }

    /** The status lines in English, by the same keys. */
    static final Map<String, String> STATUSES_EN = Map.ofEntries(Map.entry("your_turn", "Your move!"),
            Map.entry("waiting", "Waiting for the others"), Map.entry("dealing", "Dealing the cards"),
            Map.entry("all_in", "You are all-in"), Map.entry("showdown", "Showdown"), Map.entry("won", "You win!"),
            Map.entry("split", "Split pot"), Map.entry("lost", "You lose"), Map.entry("folded", "Folded"),
            Map.entry("next", "The next hand is coming up"), Map.entry("lobby", "Waiting for players"));

    /** The big buttons: name, German label, column, English label. */
    static final String[][] BUTTONS = {{"fold", "PASSEN", "0", "FOLD"}, {"leave", "GEHEN", "0", "LEAVE"},
            {"check", "CHECKEN", "1", "CHECK"}, {"call", "MITGEHEN", "1", "CALL"}, {"bet", "SETZEN", "2", "BET"},
            {"raise", "ERHÖHEN", "2", "RAISE"}, {"allin", "ALL-IN", "2", "ALL-IN"}};

    private HoldemGui() {
    }

    static List<GuiFont.Anchor> anchors() {
        List<GuiFont.Anchor> out = new ArrayList<>();
        out.add(GuiFont.Anchor.text("hd_call_label", BOX_CENTRE, CALL_LABEL_TOP, "center"));
        out.add(GuiFont.Anchor.text("hd_blinds", BOX_CENTRE, BLINDS_TOP, "center"));
        out.add(GuiFont.Anchor.text("hd_players", PokerGuiArt.DEALER_X + 4, STACK_TOP, "left"));
        return out;
    }

    static void add(PackTree tree, TextGlyphs text) {
        SlotMachineGui.button(tree, text, "holdem/sit_on", PackLang.text("SIT DOWN", "HINSETZEN"), true, 18, 4.5, 13.5);
        SlotMachineGui.button(tree, text, "holdem/sit_off", PackLang.text("SIT DOWN", "HINSETZEN"), false, 18, 4.5, 13.5);
        for (String[] b : BUTTONS) {
            int col = Integer.parseInt(b[2]);
            button(tree, text, "hd_" + b[0] + "_on", PackLang.text(b[3], b[1]), true, BUTTON_ART_SLOT[col], BUTTON_X[col]);
            button(tree, text, "hd_" + b[0] + "_off", PackLang.text(b[3], b[1]), false, BUTTON_ART_SLOT[col], BUTTON_X[col]);
        }
        for (int i = 0; i < SIZE_SLOTS.length; i++) {
            small(tree, "hd_" + SIZE_NAMES[i] + "_on", i, true, SIZE_SLOTS[i]);
            small(tree, "hd_" + SIZE_NAMES[i] + "_off", i, false, SIZE_SLOTS[i]);
        }
        help(tree, "hd_help_on");
        for (int id = 0; id < 52; id++) {
            PokerGuiItems.texture(tree, "hd_mini_" + id, mini(text, id));
        }
        for (int i = 0; i < SHOW_SLOTS.length; i++) {
            show(tree, "hd_show_" + i, SHOW_SLOTS[i], BlackjackGuiItems.slotX(STACK_SLOTS[i]));
        }
        number(tree, "hd_amount", AMOUNT_SLOT, AMOUNT_CENTRE - 3 * AMOUNT_CHARS, SIZE_ROW_Y + 5, AMOUNT_CHARS);
        number(tree, "hd_pot", POT_SLOT, BOX_CENTRE - 3 * NUMBER_CHARS, POT_TOP, NUMBER_CHARS);
        number(tree, "hd_call", CALL_SLOT, BOX_CENTRE - 3 * NUMBER_CHARS, CALL_TOP, NUMBER_CHARS);
        for (int i = 0; i < HEAD_SLOTS.length; i++) {
            number(tree, "hd_stack_" + i, STACK_SLOTS[i], BlackjackGuiItems.slotX(HEAD_SLOTS[i]) + 19, STACK_TOP,
                    STACK_CHARS);
        }
        for (Map.Entry<String, Object[]> e : STATUSES.entrySet()) {
            Object[] v = e.getValue();
            status(tree, text, "hd_status_" + e.getKey(), PackLang.text(STATUSES_EN.get(e.getKey()), (String) v[0]),
                    (Integer) v[1], (Boolean) v[2]);
        }
    }

    /** {@link PokerGui#button} at {@value #BUTTON_W} px. */
    static void button(PackTree tree, TextGlyphs text, String name, String label, boolean active, int slot, int x) {
        int w = BUTTON_W;
        int h = PokerGui.BUTTON_H;
        BufferedImage img = Images.blank(w, h);
        frame(img, w, h, active);
        int width = text.width(label);
        if (width > w - 4) {
            throw new IllegalStateException(label + " does not fit a " + w + " px button");
        }
        text.draw(img, label, (w - width) / 2, (h - 8) / 2, active ? WHITE : -8221566, active);
        PokerGuiItems.picture(tree, name, img, slot, x, PokerGui.BUTTON_Y);
    }

    private static void frame(BufferedImage img, int w, int h, boolean active) {
        Images.fill(img, 0, 0, w, h, -15325409);
        Images.fill(img, 1, 1, w - 2, h - 2, active ? -6460111 : -11908546);
        Images.fill(img, 2, 2, w - 4, h - 4, active ? -15185353 : -14535893);
        Images.fill(img, 3, 3, w - 6, 1, active ? -12616104 : -13811401);
        Images.fill(img, 3, h - 4, w - 6, 1, active ? -15123668 : -15063263);
    }

    /** The suit pips of the small cards (5x5, suit order of the card sheet: spades, hearts, diamonds, clubs). */
    static final String[][] PIPS = {
        {"..#..", ".###.", "#####", "..#..", ".###."},
        {".#.#.", "#####", "#####", ".###.", "..#.."},
        {"..#..", ".###.", "#####", ".###.", "..#.."},
        {".###.", "#####", "#####", "..#..", ".###."}};

    /** A small card face: rounded dark frame, cream face, the rank (A, 2..10, J, Q, K) over the suit pip. */
    static BufferedImage mini(TextGlyphs text, int id) {
        BufferedImage img = Images.blank(MINI_W, MINI_H);
        Images.fill(img, 1, 0, MINI_W - 2, MINI_H, 0xFF2B2B2B);
        Images.fill(img, 0, 1, MINI_W, MINI_H - 2, 0xFF2B2B2B);
        Images.fill(img, 1, 1, MINI_W - 2, MINI_H - 2, 0xFFF4F1E8);
        int rank = id % 13;
        int suit = id / 13;
        int ink = suit == 1 || suit == 2 ? 0xFFC62828 : 0xFF1B1B1B;
        String label = rank == 0 ? "A" : rank < 10 ? Integer.toString(rank + 1) : "JQK".substring(rank - 10, rank - 9);
        int w = text.width(label) - 1;
        if (w > MINI_W - 2) {
            throw new IllegalStateException(label + " does not fit a small card");
        }
        text.draw(img, label, (MINI_W - w) / 2, 2, ink, false);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (PIPS[suit][row].charAt(col) == '#') {
                    Images.fill(img, (MINI_W - 5) / 2 + col, 10 + row, 1, 1, ink);
                }
            }
        }
        return img;
    }

    /** The "?" of the rules button (6x8), '#' = drawn. */
    static final String[] QUESTION = {".####.", "##..##", "....##", "...##.", "..##..", "..##..", "......", "..##.."};

    /**
     * The two cards a player turned up: a {@code composite} of two {@code select}s on custom model data string 0 / 1
     * (card id 0..51, else nothing), {@value #MINI_W}x{@value #MINI_H} each, centred above the 36 px of his stack from
     * window x {@code left}. A clear quad over both gives every face the same (oversized) bounds.
     */
    static void show(PackTree tree, String name, int slot, int left) {
        int x0 = left + (36 - (2 * MINI_W + 1)) / 2;
        JsonArray parts = new JsonArray();
        for (int k = 0; k < 2; k++) {
            int x = x0 + k * (MINI_W + 1);
            List<String> values = new ArrayList<>();
            List<JsonObject> models = new ArrayList<>();
            for (int id = 0; id < 52; id++) {
                String model = name + "_" + k + "_" + id;
                PokerGuiItems.model(tree, model, PokerGuiItems.model(slot, List.of(
                        new BlackjackGuiItems.Quad(PokerGuiItems.BLACKJACK + "clear", x0, MINI_Y, 2 * MINI_W + 1,
                                MINI_H, -1, 7.8),
                        new BlackjackGuiItems.Quad("hd_mini_" + id, x, MINI_Y, MINI_W, MINI_H, -1, 8))));
                values.add(Integer.toString(id));
                models.add(PokerGuiItems.plain(model, 0));
            }
            parts.add(PokerGuiItems.select(k, values, models));
        }
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", parts);
        PokerGuiItems.item(tree, name, composite);
    }

    /**
     * The rules button: a {@value #HELP_SIZE} px badge (the buttons' active frame, a gold "?") at window x
     * slotX + 1, y slotY + 3 of {@link #HELP_SLOT}, left of the hand name (cut to start at x 165). A clear quad 17 px
     * down from it (above the stack numbers at y 76) makes it an oversized item.
     */
    static void help(PackTree tree, String name) {
        int s = HELP_SIZE;
        BufferedImage img = Images.blank(s, s);
        frame(img, s, s, true);
        for (int row = 0; row < QUESTION.length; row++) {
            for (int col = 0; col < QUESTION[row].length(); col++) {
                if (QUESTION[row].charAt(col) == '#') {
                    Images.fill(img, (s - 6) / 2 + col, (s - 8) / 2 + row, 1, 1, GOLD);
                }
            }
        }
        PokerGuiItems.texture(tree, name, img);
        int x = BlackjackGuiItems.slotX(HELP_SLOT);
        int y = BlackjackGuiItems.slotY(HELP_SLOT);
        PokerGuiItems.model(tree, name, PokerGuiItems.model(HELP_SLOT, List.of(
                new BlackjackGuiItems.Quad(PokerGuiItems.BLACKJACK + "clear", x + 1, y + 3, s, 17, -1, 7.8),
                new BlackjackGuiItems.Quad(name, x + 1, y + 3, s, s, -1, 8))));
        PokerGuiItems.item(tree, name, PokerGuiItems.plain(name, 0));
    }

    /**
     * {@code hand_player} of the poker GUI for Hold'em, from {@link #HAND_SLOT}: its pictures ({@code pictures}, by
     * category) cut to {@value #HAND_CUT_W} px from x {@value #HAND_CUT_X} at the same window place, tinted the same
     * way (colour 0 over the shadow in colour 1). A name outside the cut fails the build.
     */
    static void hands(PackTree tree, List<BufferedImage> pictures) {
        List<String> values = new ArrayList<>();
        List<JsonObject> models = new ArrayList<>();
        for (int category = 0; category < pictures.size(); category++) {
            BufferedImage full = pictures.get(category);
            for (int y = 0; y < full.getHeight(); y++) {
                for (int x = 0; x < full.getWidth(); x++) {
                    if ((full.getRGB(x, y) >>> 24) != 0 && (x < HAND_CUT_X || x >= HAND_CUT_X + HAND_CUT_W)) {
                        throw new IllegalStateException("hand name " + category + " is wider than " + HAND_CUT_W
                                + " px");
                    }
                }
            }
            String texture = "hd_hand_" + category;
            PokerGuiItems.texture(tree, texture, Images.crop(full, HAND_CUT_X, 0, HAND_CUT_W, full.getHeight()));
            int x = PokerGuiArt.PLAYER_X + 34 - PokerGui.NAME_W / 2 + HAND_CUT_X;
            PokerGuiItems.model(tree, texture, PokerGuiItems.model(HAND_SLOT, List.of(
                    new BlackjackGuiItems.Quad(texture, x + 1, PokerGui.NAME_TOP + 1, HAND_CUT_W, PokerGui.NAME_H, 1,
                            7.9),
                    new BlackjackGuiItems.Quad(texture, x, PokerGui.NAME_TOP, HAND_CUT_W, PokerGui.NAME_H, 0, 8))));
            values.add(Integer.toString(category));
            models.add(PokerGuiItems.plain(texture, 2));
        }
        PokerGuiItems.item(tree, "hd_hand", PokerGuiItems.select(0, values, models));
    }

    /**
     * An 18 px button over the whole slot with a drawn sign: |&lt; (smallest), - (less), + (more), &gt;| (all-in).
     */
    static void small(PackTree tree, String name, int kind, boolean active, int slot) {
        BufferedImage img = Images.blank(18, 18);
        frame(img, 18, 18, active);
        int c = active ? WHITE : -8221566;
        switch (kind) {
            case 0 -> { // a bar, then a triangle pointing left (tip at x 7, base at x 10)
                Images.fill(img, 5, 5, 1, 8, c);
                for (int i = 0; i < 4; i++) {
                    Images.fill(img, 7 + i, 8 - i, 1, 2 + 2 * i, c);
                }
            }
            case 1 -> Images.fill(img, 5, 8, 8, 2, c);
            case 2 -> {
                Images.fill(img, 5, 8, 8, 2, c);
                Images.fill(img, 8, 5, 2, 8, c);
            }
            default -> { // a triangle pointing right (base at x 7, tip at x 10), then a bar
                for (int i = 0; i < 4; i++) {
                    Images.fill(img, 10 - i, 8 - i, 1, 2 + 2 * i, c);
                }
                Images.fill(img, 12, 5, 1, 8, c);
            }
        }
        PokerGuiItems.picture(tree, name, img, slot, BlackjackGuiItems.slotX(slot) - 1,
                BlackjackGuiItems.slotY(slot) - 1);
    }

    /**
     * A number: {@code chars} positions of 6 px from window x {@code left}, cap top {@code top}; character k selected by
     * custom model data string k from {@link BlackjackGui#CHARS} (anything else, a blank, draws nothing).
     */
    static void number(PackTree tree, String name, int slot, int left, int top, int chars) {
        List<JsonObject> parts = new ArrayList<>();
        for (int k = 0; k < chars; k++) {
            int x = left + 6 * k;
            List<String> values = new ArrayList<>();
            List<JsonObject> models = new ArrayList<>();
            for (char c : BlackjackGui.CHARS.toCharArray()) {
                String model = name + "_" + k + "_" + BlackjackGui.charName(c);
                String texture = PokerGuiItems.BLACKJACK + "net_compact_" + BlackjackGui.charName(c);
                // the clear quad over the whole number gives every character the same (oversized) bounds
                PokerGuiItems.model(tree, model, PokerGuiItems.model(slot, List.of(
                        new BlackjackGuiItems.Quad(PokerGuiItems.BLACKJACK + "clear", left, top, 6 * chars + 1, 9, -1,
                                7.8),
                        new BlackjackGuiItems.Quad(texture, x + 1, top + 1, 6, 8, 1, 7.9),
                        new BlackjackGuiItems.Quad(texture, x, top, 6, 8, 0, 8))));
                values.add(Character.toString(c));
                models.add(PokerGuiItems.plain(model, 2));
            }
            parts.add(PokerGuiItems.select(k, values, models));
        }
        JsonArray models = new JsonArray();
        parts.forEach(models::add);
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", models);
        PokerGuiItems.item(tree, name, composite);
    }

    /** A status line centred on the poker status position (window x 88, cap top 93), with shadow. */
    static void status(PackTree tree, TextGlyphs text, String name, String label, int colour, boolean bold) {
        int w = PokerGui.STATUS_W;
        BufferedImage img = Images.blank(w, 12);
        if (BlackjackGuiText.width(text, label, bold) > w - 2) {
            throw new IllegalStateException(name + " is wider than " + (w - 2) + " px");
        }
        BlackjackGuiText.centred(img, text, label, w / 2, 3, colour, bold, true);
        PokerGuiItems.picture(tree, name, img, STATUS_SLOT, PokerGui.STATUS_CENTRE - w / 2, PokerGui.STATUS_TOP - 3);
    }
}
