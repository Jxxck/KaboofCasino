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

    /** The big buttons: name, label, column. */
    static final String[][] BUTTONS = {{"fold", "PASSEN", "0"}, {"leave", "GEHEN", "0"}, {"check", "CHECKEN", "1"},
            {"call", "MITGEHEN", "1"}, {"bet", "SETZEN", "2"}, {"raise", "ERHÖHEN", "2"}, {"allin", "ALL-IN", "2"}};

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
        SlotMachineGui.button(tree, text, "holdem/sit_on", "HINSETZEN", true, 18, 4.5, 13.5);
        SlotMachineGui.button(tree, text, "holdem/sit_off", "HINSETZEN", false, 18, 4.5, 13.5);
        for (String[] b : BUTTONS) {
            int col = Integer.parseInt(b[2]);
            button(tree, text, "hd_" + b[0] + "_on", b[1], true, BUTTON_ART_SLOT[col], BUTTON_X[col]);
            button(tree, text, "hd_" + b[0] + "_off", b[1], false, BUTTON_ART_SLOT[col], BUTTON_X[col]);
        }
        for (int i = 0; i < SIZE_SLOTS.length; i++) {
            small(tree, "hd_" + SIZE_NAMES[i] + "_on", i, true, SIZE_SLOTS[i]);
            small(tree, "hd_" + SIZE_NAMES[i] + "_off", i, false, SIZE_SLOTS[i]);
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
            status(tree, text, "hd_status_" + e.getKey(), (String) v[0], (Integer) v[1], (Boolean) v[2]);
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

    /** An 18 px button over the whole slot with a drawn sign: |&lt; (smallest), - (less), + (more), &gt;| (all-in). */
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
