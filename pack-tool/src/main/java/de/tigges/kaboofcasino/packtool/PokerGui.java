package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The poker chest GUIs (render.md 5.2; mod {@code PokerScreen}): the bet view (the mod's 366x266 window, see
 * {@link PokerGuiArt}) and the round view (the mod's 160x196 side panel re-built around the chest). Static texts are
 * title anchors; everything that changes during a hand is an {@code oversized_in_gui} item below {@code gui/poker/}
 * whose model draws at a fixed window position from a fixed slot of the round menu ({@code PokerRoundMenu} uses the
 * same slots):
 * <ul>
 *   <li>cards ({@code card_<position>}, custom model data string 0 = card id 0..51 or {@code back}): the mod's 25x32
 *       faces (the blackjack GUI's textures), tops at window y 15; the board at x 21 + 27 i from slots 0 / 2 / 3 / 5
 *       / 6 ({@code board_a}: 13 px right of the slot, {@code board_b}: 4 px), the dealer's at -47 / -19 from slots
 *       9 / 10, the player's at 169 / 197 from slots 16 / 17 (the mod's 28 px step);</li>
 *   <li>hand names ({@code hand_dealer} from slot 18, {@code hand_player} from slot 26; custom model data string 0
 *       = category 0..9, colours 0 / 1 = text / shadow): centred under the box's cards, one line at cap top 54 or two
 *       (the mod's {@code split} at 66 px) at 50 / 59;</li>
 *   <li>the stake band's word ({@code label_stake} EINSATZ, {@code label_net} BILANZ, slot 19) at window (-51, 76),
 *       "x3" ({@code tripled}, slot 43) bold yellow right-aligned at 228;</li>
 *   <li>status and result lines ({@code status_<1..5>}, {@code result_<1..5>}, slot 22): centred on window x 88, cap
 *       top 93, the mod's colours and weights, with shadow;</li>
 *   <li>the timer bar: the blackjack GUI's {@code bar} item (slot 39, window (20, 102), 136 px);</li>
 *   <li>the result labels ({@code net_wide}: left at the item's x + 19, cap top + 4; {@code net_compact}: right-aligned
 *       at the item's x + 18, cap top + 9; both plain with shadow like the mod's literal labels), drawn from the slot
 *       under the item (custom model data strings 0..6 = characters, colours 0 / 1 = text / shadow);</li>
 *   <li>buttons MITGEHEN / AUSSTEIGEN ({@code call|fold_on|off}, the mod's {@code ActionButton}, 68x17) over slots
 *       45..48 / 50..53 (art from 46 / 51); AUSTEILEN ({@code deal_on|off}, 72x18 at the mod's place over 48..50).</li>
 * </ul>
 */
final class PokerGui {

    static final MachineGuiArt ART = PokerGuiArt.ART;
    /** The paytable rows of the mod ({@code PAY_CATEGORIES}): royal flush .. flush, then "straight or less". */
    static final int[] PAY_CATEGORIES = {9, 8, 7, 6, 5, 4};
    /** Mod x of the texts in the call and dealer boxes: the mod's 287 does not leave the unscaled notes enough room. */
    static final int BOX_TEXT_X = 278;

    // ---- round view (window positions) ------------------------------------------------------------------------------
    static final int CARD_W = 25;
    static final int CARD_H = 32;
    static final int CARD_Y = PokerGuiArt.BOX_Y + 2;

    /** A card position: item name suffix, the slot its model is made for, window x. */
    record CardPlace(String name, int slot, int x) {
    }

    static final List<CardPlace> CARD_PLACES = List.of(
            new CardPlace("board_a", 0, PokerGuiArt.BOARD_X + 4),
            new CardPlace("board_b", 2, PokerGuiArt.BOARD_X + 4 + 27),
            new CardPlace("dealer_0", 9, PokerGuiArt.DEALER_X + 8),
            new CardPlace("dealer_1", 10, PokerGuiArt.DEALER_X + 36),
            new CardPlace("player_0", 16, PokerGuiArt.PLAYER_X + 8),
            new CardPlace("player_1", 17, PokerGuiArt.PLAYER_X + 36));
    static final int DEALER_NAME_SLOT = 18;
    static final int STAKE_LABEL_SLOT = 19;
    static final int STATUS_SLOT = 22;
    static final int PLAYER_NAME_SLOT = 26;
    static final int TRIPLED_SLOT = 43;
    static final int CALL_ART_SLOT = 46;
    static final int FOLD_ART_SLOT = 51;
    /** Net labels are drawn from the slot under their item (row 3 -> row 4). */
    static final int NET_LABEL_SLOT = 36;
    static final int NET_CHARS = 6;
    /** Compact labels (three kinds or more, each at most 64 antes: at most +6528) have five positions. */
    static final int COMPACT_CHARS = 5;
    /**
     * The labels' fixed bounds cover five characters, so the neighbouring items (54 / 36 px away, the mod's 44 / 28)
     * stay clear; a sixth character (a net of 10000 or more: a royal flush on 98 antes or more of one kind, so one or
     * two kinds, wide labels) reaches past them.
     */
    static final int AREA_CHARS = 5;
    /** Hand names: centre x (box + 34 like the mod's 43 / 115), picture top (two-line cap top 50 - 3), 68x21. */
    static final int NAME_W = 68;
    static final int NAME_H = 21;
    static final int NAME_TOP = CARD_Y + 35 - 3;
    static final int NAME_ROOM = 66;
    static final int STAKE_TOP = PokerGuiArt.STAKE_Y + 5;
    static final int STAKE_LABEL_X = PokerGuiArt.STAKE_X0 + 4;
    static final int TRIPLED_RIGHT = PokerGuiArt.STAKE_X1 - 3;
    static final int STATUS_CENTRE = 88;
    static final int STATUS_TOP = 93;
    static final int STATUS_W = 170;
    static final int BUTTON_W = 68;
    static final int BUTTON_H = 17;
    static final int BUTTON_Y = 108;
    /** Left edges of the 68 px buttons, each centred over its four slots (45..48: x 8..77, 50..53: 98..167). */
    static final int CALL_X = 9;
    static final int FOLD_X = 99;

    static final int GOLD = 0xFFFFAA00;
    static final int YELLOW = 0xFFFFFF55;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREEN = 0xFF55FF55;
    static final int RED = 0xFFFF5555;
    static final int PALE = 0xFFC8D7C8;

    private PokerGui() {
    }

    static List<GuiFont.Anchor> anchors() {
        List<GuiFont.Anchor> out = new ArrayList<>();
        // bet view (PokerScreen.extractBetLabels)
        out.add(GuiFont.Anchor.text("pk_headline", ART.windowX(112), ART.windowY(12), "center"));
        out.add(GuiFont.Anchor.text("pk_bet", ART.windowX(112), ART.windowY(75) + PokerGuiArt.FRAME_DY, "center"));
        out.add(GuiFont.Anchor.text("pk_payouts", (ART.panelFrom() + ART.panelTo()) / 2 + MachineGuiArt.ART_X,
                ART.panelWindowY(14), "center"));
        out.add(GuiFont.Anchor.text("pk_ante_pays", ART.windowX(297), ART.panelWindowY(46), "center"));
        for (int row = 0; row < PAY_CATEGORIES.length; row++) {
            int y = ART.panelWindowY(61 + 16 * row);
            out.add(GuiFont.Anchor.text("pk_pay_name_" + row, ART.windowX(244), y, "left"));
            out.add(GuiFont.Anchor.text("pk_pay_odds_" + row, ART.windowX(351), y, "right"));
        }
        String[] boxes = {"call", "dealer"};
        int[] boxY = {163, 209};
        String[] lines = {"label", "note", "last"};
        int[] lineDy = {6, 17, 27};
        for (int b = 0; b < boxes.length; b++) {
            for (int l = 0; l < lines.length; l++) {
                out.add(GuiFont.Anchor.text("pk_" + boxes[b] + "_" + lines[l], ART.windowX(BOX_TEXT_X),
                        ART.panelWindowY(boxY[b] + lineDy[l]), "left"));
            }
        }
        // round view (PokerScreen.extractPanel): title, the box labels above the hand boxes
        out.add(GuiFont.Anchor.text("pk_round_title", STATUS_CENTRE, -8, "center"));
        out.add(GuiFont.Anchor.text("pk_round_dealer", PokerGuiArt.DEALER_X + 2, PokerGuiArt.BOX_Y - 9, "left"));
        out.add(GuiFont.Anchor.text("pk_round_you", PokerGuiArt.PLAYER_X + 2, PokerGuiArt.BOX_Y - 9, "left"));
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage bet = Images.read(jar.bytes("assets/roulettecasino/textures/gui/poker.png"));
        BufferedImage panel = Images.read(jar.bytes("assets/roulettecasino/textures/gui/poker_panel.png"));
        MachineGuiArt.tiles(font, "pk_bet", PokerGuiArt.bet(bet));
        BlackjackGuiArt.tiles(font, "pk_round", PokerGuiArt.round(panel, bet), PokerGuiArt.ROUND_X,
                PokerGuiArt.ROUND_Y);
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        JsonObject lang = Json.parse(jar.bytes("assets/roulettecasino/lang/de_de.json"));
        SlotMachineGui.button(tree, text, "poker/deal_on", lang(lang, "deal"), true, 18, 4.5, 13.5);
        SlotMachineGui.button(tree, text, "poker/deal_off", lang(lang, "deal"), false, 18, 4.5, 13.5);
        cards(tree);
        hands(tree, text, lang);
        statuses(tree, text, lang);
        words(tree, text, lang);
        net(tree, "net_wide", true);
        net(tree, "net_compact", false);
        button(tree, text, "call_on", lang(lang, "call"), true, CALL_ART_SLOT, CALL_X);
        button(tree, text, "call_off", lang(lang, "call"), false, CALL_ART_SLOT, CALL_X);
        button(tree, text, "fold_on", lang(lang, "fold"), true, FOLD_ART_SLOT, FOLD_X);
        button(tree, text, "fold_off", lang(lang, "fold"), false, FOLD_ART_SLOT, FOLD_X);
    }

    static String lang(JsonObject lang, String key) {
        return langKey(lang, "gui.roulettecasino.poker." + key);
    }

    static String langKey(JsonObject lang, String full) {
        if (!lang.has(full)) {
            throw new IllegalStateException("de_de.json lacks " + full);
        }
        return lang.get(full).getAsString();
    }

    /** The card id of a select case: 0..51, or {@code back} for a face-down card. */
    static String face(int id) {
        return id < 0 ? "back" : Integer.toString(id);
    }

    /**
     * Per card position one item that selects the face by custom model data string 0; the faces are the blackjack
     * GUI's card textures ({@code BlackjackGui.cards}: the mod's {@code entity/blackjack_cards.png} cells).
     */
    static void cards(PackTree tree) {
        for (CardPlace p : CARD_PLACES) {
            List<String> values = new ArrayList<>();
            List<JsonObject> models = new ArrayList<>();
            for (int id = -1; id < 52; id++) {
                String model = "card_" + p.name() + "_" + face(id);
                PokerGuiItems.model(tree, model, PokerGuiItems.model(p.slot(), List.of(BlackjackGuiItems.Quad.of(
                        PokerGuiItems.BLACKJACK + BlackjackGui.cardName(id), p.x(), CARD_Y, CARD_W, CARD_H))));
                values.add(face(id));
                models.add(PokerGuiItems.plain(model, 0));
            }
            PokerGuiItems.item(tree, "card_" + p.name(), PokerGuiItems.select(0, values, models));
        }
    }

    /**
     * {@code Font.split(name, 66)}: words in order onto lines of at most {@code room} px (the breaking space is
     * dropped); a single word wider than the room stays on its own line.
     */
    static List<String> split(TextGlyphs text, String name, int room) {
        List<String> out = new ArrayList<>();
        String line = "";
        for (String word : name.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && text.width(candidate) > room) {
                out.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }
        out.add(line);
        return out;
    }

    /**
     * {@code PokerScreen.handLines}: hand_0..9, each line centred on the box's middle (left = middle - width / 2), with
     * shadow. One white picture per name; the items {@code hand_dealer} (slot 18) and {@code hand_player} (slot 26)
     * select it by custom model data string 0 and tint it with colour 0 (the player's yellow, the dealer's white or
     * red) over its shadow tinted with colour 1 one pixel down-right, like the client draws text.
     */
    static void hands(PackTree tree, TextGlyphs text, JsonObject lang) {
        List<String> values = new ArrayList<>();
        List<JsonObject> dealer = new ArrayList<>();
        List<JsonObject> player = new ArrayList<>();
        for (int category = 0; category <= 9; category++) {
            String name = lang(lang, "hand_" + category);
            List<String> lines = split(text, name, NAME_ROOM);
            if (lines.size() > 2) {
                throw new IllegalStateException(name + " needs more than two lines of " + NAME_ROOM + " px");
            }
            BufferedImage img = Images.blank(NAME_W, NAME_H);
            int middle = NAME_W / 2;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int top = lines.size() == 1 ? 7 : 3 + 9 * i;
                text.draw(img, line, middle - text.width(line) / 2, top, WHITE, false);
            }
            String texture = "hand_" + category;
            PokerGuiItems.texture(tree, texture, img);
            values.add(Integer.toString(category));
            dealer.add(handModel(tree, texture, "hand_dealer_" + category, DEALER_NAME_SLOT, PokerGuiArt.DEALER_X));
            player.add(handModel(tree, texture, "hand_player_" + category, PLAYER_NAME_SLOT, PokerGuiArt.PLAYER_X));
        }
        PokerGuiItems.item(tree, "hand_dealer", PokerGuiItems.select(0, values, dealer));
        PokerGuiItems.item(tree, "hand_player", PokerGuiItems.select(0, values, player));
    }

    private static JsonObject handModel(PackTree tree, String texture, String name, int slot, int boxX) {
        int x = boxX + 34 - NAME_W / 2;
        PokerGuiItems.model(tree, name, PokerGuiItems.model(slot, List.of(
                new BlackjackGuiItems.Quad(texture, x + 1, NAME_TOP + 1, NAME_W, NAME_H, 1, 7.9),
                new BlackjackGuiItems.Quad(texture, x, NAME_TOP, NAME_W, NAME_H, 0, 8))));
        return PokerGuiItems.plain(name, 2);
    }

    /**
     * Status and result lines ({@code extractPanel}): result_n bold in {@code resultColor}, status_2 bold gold, the
     * other statuses plain white, all with shadow ({@code centered}). {@value #STATUS_W} px wide (the mod squeezes
     * "Mitgehen oder aussteigen?", 159 px bold, into its 138; the chest has the room), cap top at row 3.
     */
    static void statuses(PackTree tree, TextGlyphs text, JsonObject lang) {
        for (int phase = 1; phase <= 5; phase++) {
            line(tree, text, "status_" + phase, lang(lang, "status_" + phase), phase == 2 ? GOLD : WHITE,
                    phase == 2);
        }
        for (int result = 1; result <= 5; result++) {
            int colour = switch (result) {
                case 1, 2 -> GREEN;
                case 4, 5 -> RED;
                default -> WHITE;
            };
            line(tree, text, "result_" + result, lang(lang, "result_" + result), colour, true);
        }
    }

    private static void line(PackTree tree, TextGlyphs text, String name, String label, int colour, boolean bold) {
        BufferedImage img = Images.blank(STATUS_W, 12);
        if (BlackjackGuiText.width(text, label, bold) > STATUS_W - 2) {
            throw new IllegalStateException(name + " is wider than " + (STATUS_W - 2) + " px");
        }
        BlackjackGuiText.centred(img, text, label, STATUS_W / 2, 3, colour, bold, true);
        PokerGuiItems.picture(tree, name, img, STATUS_SLOT, STATUS_CENTRE - STATUS_W / 2, STATUS_TOP - 3);
    }

    /**
     * The stake band's word (EINSATZ, at the result BILANZ) pale with shadow at window (-51, 76), and "x3" bold
     * yellow with shadow right-aligned at 228 (the mod: 148 - width, 2 px inside the band).
     */
    static void words(PackTree tree, TextGlyphs text, JsonObject lang) {
        String[][] words = {{"label_stake", langKey(lang, "gui.roulettecasino.blackjack.stake")},
                {"label_net", lang(lang, "net")}};
        int room = 0;
        for (String[] w : words) {
            room = Math.max(room, text.width(w[1]) + 1);
        }
        if (room > 42) {
            throw new IllegalStateException("the stake band's word is wider than the mod's 42 px label room");
        }
        for (String[] w : words) {
            BufferedImage img = Images.blank(room, 12);
            text.draw(img, w[1], 0, 3, PALE, true);
            PokerGuiItems.picture(tree, w[0], img, STAKE_LABEL_SLOT, STAKE_LABEL_X, STAKE_TOP - 3);
        }
        int w = BlackjackGuiText.width(text, "x3", true);
        BufferedImage img = Images.blank(20, 12);
        BlackjackGuiText.draw(img, text, "x3", 20 - w, 3, YELLOW, true, true);
        PokerGuiItems.picture(tree, "tripled", img, TRIPLED_SLOT, TRIPLED_RIGHT - 20, STAKE_TOP - 3);
    }

    /**
     * A result label item: {@value #NET_CHARS} (compact: {@value #COMPACT_CHARS}) character positions, each a
     * {@code select} on custom model data string {@code k}, drawn from the slot under the item. Wide (one or two
     * kinds, {@code PokerScreen}: left at x + 19, cap top y + 4): character k at item x + 19 + 6k. Compact (three or
     * more: right-aligned at x + 18, cap top y + 9): character k counted from the right at item x + 12 - 6k. The
     * characters are the blackjack GUI's plain white digits ({@code net_compact_*}) tinted with colour 0 over their
     * shadow tinted with colour 1, over a clear quad of {@value #AREA_CHARS} characters' label area (the same in
     * every model, so the item always has the same bounds).
     */
    static void net(PackTree tree, String name, boolean wide) {
        int slot = NET_LABEL_SLOT;
        int itemX = BlackjackGuiItems.slotX(slot);
        int itemY = BlackjackGuiItems.slotY(slot) - 18;
        int y = itemY + (wide ? 4 : 9);
        int areaX = wide ? itemX + 19 : itemX + 18 - 6 * AREA_CHARS;
        List<JsonObject> parts = new ArrayList<>();
        for (int k = 0; k < (wide ? NET_CHARS : COMPACT_CHARS); k++) {
            int x = wide ? itemX + 19 + 6 * k : itemX + 12 - 6 * k;
            List<String> values = new ArrayList<>();
            List<JsonObject> models = new ArrayList<>();
            for (char c : BlackjackGui.CHARS.toCharArray()) {
                String model = name + "_" + k + "_" + BlackjackGui.charName(c);
                String texture = PokerGuiItems.BLACKJACK + "net_compact_" + BlackjackGui.charName(c);
                PokerGuiItems.model(tree, model, PokerGuiItems.model(slot, List.of(
                        new BlackjackGuiItems.Quad(PokerGuiItems.BLACKJACK + "clear", areaX, y, 6 * AREA_CHARS + 1, 9,
                                -1, 7.8),
                        new BlackjackGuiItems.Quad(texture, x + 1, y + 1, 6, 8, 1, 7.9),
                        new BlackjackGuiItems.Quad(texture, x, y, 6, 8, 0, 8))));
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

    /**
     * {@code BlackjackScreen.ActionButton} (PokerScreen's MITGEHEN / AUSSTEIGEN), not hovered: 68x17, label centred
     * at (w - width) / 2, (h - 8) / 2; white with shadow when active, -8221566 without shadow when not.
     */
    static void button(PackTree tree, TextGlyphs text, String name, String label, boolean active, int slot, int x) {
        int w = BUTTON_W;
        int h = BUTTON_H;
        BufferedImage img = Images.blank(w, h);
        Images.fill(img, 0, 0, w, h, -15325409);
        Images.fill(img, 1, 1, w - 2, h - 2, active ? -6460111 : -11908546);
        Images.fill(img, 2, 2, w - 4, h - 4, active ? -15185353 : -14535893);
        Images.fill(img, 3, 3, w - 6, 1, active ? -12616104 : -13811401);
        Images.fill(img, 3, h - 4, w - 6, 1, active ? -15123668 : -15063263);
        int width = text.width(label);
        if (width > w - 8) {
            throw new IllegalStateException(label + " does not fit a " + w + " px button");
        }
        text.draw(img, label, (w - width) / 2, (h - 8) / 2, active ? WHITE : -8221566, active);
        PokerGuiItems.picture(tree, name, img, slot, x, BUTTON_Y);
    }
}
