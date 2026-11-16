package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The blackjack chest GUIs (render.md 5.2; mod {@code BlackjackScreen}): the bet view (the mod's 366x266 window, see
 * {@link BlackjackGuiArt}) and the round view (the mod's 160x196 side panel re-built around the chest). Static texts
 * are title anchors; everything that changes during a round is an {@code oversized_in_gui} item whose model draws at
 * a fixed window position from a fixed slot of the round menu ({@code BlackjackRoundMenu} uses the same slots):
 * <ul>
 *   <li>cards ({@code card_<id>}, {@code card_back}; ids 0..51 = suit * 13 + rank like {@code BlackjackCards}): the
 *       mod's 25x32 faces from {@code entity/blackjack_cards.png}, top 16 px above the item area of a slot in row 0
 *       (dealer, window y 2) or row 2 (player, y 38); {@code _9} variants sit 9 px further right, so hands of up to
 *       five cards keep the mod's 27 px step (columns 0, 1+9, 3, 4+9, 6), longer hands use the 18 px slot pitch;</li>
 *   <li>totals ({@code badge_<n>}, slot 17 for the dealer at window (198, 2), slot 35 for the player at (198, 38)):
 *       the mod's 23x11 badge, bold total in yellow, red above 21;</li>
 *   <li>status and result lines ({@code status_<1..5>}, {@code result_<1..8>}, slot 40): centred on window x 88, cap
 *       top 93, texts from the mod's lang file of the pack's language in the mod's colours and weights;</li>
 *   <li>the timer bar ({@code bar}, slot 39): {@code range_dispatch} on custom model data float 0 = filled pixels
 *       (1..136) at window (20, 102), colour = custom model data colour 0;</li>
 *   <li>"x2" ({@code doubled}, slot 34) right-aligned at window x 220 on the stake row; the result labels
 *       ({@code net_wide}: bold, left of a slot + 1, cap top + 4; {@code net_compact}: plain, right-aligned at the
 *       previous slot's item x + 17, cap top + 9, like the mod's two stake layouts): composites of four character
 *       positions selected by custom model data strings 0..3 (wide: from the left, compact: from the right), text
 *       colour = colour 0, shadow colour = colour 1;</li>
 *   <li>buttons KARTE / HALTEN / DOPPELN ({@code hit|stand|double_on|off}, 52x17 over slots 45..47 / 48..50 / 51..53,
 *       art in 46 / 49 / 52) in the mod's {@code DerbyUiArt.button} colours; AUSTEILEN ({@code deal_on|off}, 72x14
 *       over 48..50) like the other games' play buttons.</li>
 * </ul>
 */
final class BlackjackGui {

    static final MachineGuiArt ART = BlackjackGuiArt.ART;
    /** Payout rows of the mod (blackjack, win, push), lang suffixes. */
    static final String[] PAY_ROWS = {"blackjack", "win", "push"};

    // ---- round view slots (window positions in BlackjackRoundMenu's layout) ------------------------------------------
    static final int DEALER_BADGE_SLOT = 17;
    static final int PLAYER_BADGE_SLOT = 35;
    static final int DOUBLED_SLOT = 34;
    static final int BAR_SLOT = 39;
    static final int STATUS_SLOT = 40;
    static final int[] BUTTON_ART_SLOTS = {46, 49, 52};
    /** A card's picture relative to its slot's item area. */
    static final int CARD_DY = -16;
    static final int CARD_SHIFT = 9;
    static final int CARD_W = 25;
    static final int CARD_H = 32;
    /** Badge: right edge (inclusive) and the dealer badge's top (the player's lies 36 px lower, from slot 35). */
    static final int BADGE_RIGHT = 220;
    static final int BADGE_DEALER_TOP = 2;
    static final int BAR_X = 20;
    static final int BAR_Y = 102;
    static final int BAR_W = 136;
    static final int STATUS_CENTRE = 88;
    static final int STATUS_TOP = 93;
    static final int DOUBLED_RIGHT = 220;
    static final int STAKE_TOP = 72;

    static final int GOLD = 0xFFFFAA00;
    static final int YELLOW = 0xFFFFFF55;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREEN = 0xFF55FF55;
    static final int RED = 0xFFFF5555;

    private BlackjackGui() {
    }

    static List<GuiFont.Anchor> anchors() {
        List<GuiFont.Anchor> out = new ArrayList<>();
        // bet view (BlackjackScreen.extractBetLabels)
        out.add(GuiFont.Anchor.text("bj_headline", ART.windowX(112), ART.windowY(12), "center"));
        out.add(GuiFont.Anchor.text("bj_payouts", (ART.panelFrom() + ART.panelTo()) / 2 + MachineGuiArt.ART_X,
                ART.panelWindowY(14), "center"));
        out.add(GuiFont.Anchor.text("bj_bet", ART.windowX(112), ART.windowY(82) + BlackjackGuiArt.FRAME_DY,
                "center"));
        for (int row = 0; row < PAY_ROWS.length; row++) {
            int y = 44 + 53 * row;
            out.add(GuiFont.Anchor.text("bj_pay_label_" + row, ART.windowX(283), ART.panelWindowY(y + 6), "left"));
            out.add(GuiFont.Anchor.text("bj_pay_note_" + row, ART.windowX(283), ART.panelWindowY(y + 18), "left"));
            out.add(GuiFont.Anchor.text("bj_pay_odds_" + row, ART.windowX(351), ART.panelWindowY(y + 32), "right"));
        }
        // round view (BlackjackScreen.extractPanel): title, and the labels in the bands' left part
        out.add(GuiFont.Anchor.text("bj_round_title", STATUS_CENTRE, -8, "center"));
        int labelX = BlackjackGuiArt.BAND_X0 + 3;
        out.add(GuiFont.Anchor.text("bj_round_dealer", labelX, BlackjackGuiArt.BANDS[0][0] + 4, "left"));
        out.add(GuiFont.Anchor.text("bj_round_you", labelX, BlackjackGuiArt.BANDS[1][0] + 4, "left"));
        out.add(GuiFont.Anchor.text("bj_round_stake", labelX, STAKE_TOP + 4, "left"));
        return out;
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        BufferedImage bet = Images.read(jar.bytes("assets/roulettecasino/textures/gui/blackjack.png"));
        BufferedImage panel = Images.read(jar.bytes("assets/roulettecasino/textures/gui/blackjack_panel.png"));
        MachineGuiArt.tiles(font, "bj_bet", BlackjackGuiArt.bet(bet));
        BlackjackGuiArt.tiles(font, "bj_round", BlackjackGuiArt.round(panel, bet), BlackjackGuiArt.ROUND_X,
                BlackjackGuiArt.ROUND_Y);
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        JsonObject lang = Json.parse(jar.bytes(PackLang.current().modLangPath("roulettecasino")));
        SlotMachineGui.button(tree, text, "blackjack/deal_on", lang(lang, "deal"), true, 14, 4.5, 11.5);
        SlotMachineGui.button(tree, text, "blackjack/deal_off", lang(lang, "deal"), false, 14, 4.5, 11.5);
        BlackjackGuiItems.texture(tree, "clear", Images.blank(16, 16));
        cards(tree, Images.read(jar.bytes("assets/roulettecasino/textures/entity/blackjack_cards.png")));
        badges(tree, text);
        statuses(tree, text, lang);
        bar(tree);
        doubled(tree, text);
        net(tree, text, "net_wide", true);
        net(tree, text, "net_compact", false);
        String[] buttons = {"hit", "stand", "double"};
        for (String b : buttons) {
            button(tree, text, b + "_on", lang(lang, b), true);
            button(tree, text, b + "_off", lang(lang, b), false);
        }
    }

    static String lang(JsonObject lang, String key) {
        String full = "gui.roulettecasino.blackjack." + key;
        if (!lang.has(full)) {
            throw new IllegalStateException("the mod's " + PackLang.current().modCode() + ".json lacks " + full);
        }
        return lang.get(full).getAsString();
    }

    /** The 53 faces (BlackjackScreen.hand: column = rank or 13 for the back, row = suit) in two positions each. */
    static void cards(PackTree tree, BufferedImage sheet) {
        if (sheet.getWidth() != 350 || sheet.getHeight() != 128) {
            throw new IllegalStateException("unexpected card sheet " + sheet.getWidth() + "x" + sheet.getHeight());
        }
        for (int id = -1; id < 52; id++) {
            String name = cardName(id);
            int column = id < 0 ? 13 : id % 13;
            int row = id < 0 ? 0 : id / 13;
            BlackjackGuiItems.texture(tree, name, Images.crop(sheet, column * CARD_W, row * CARD_H, CARD_W, CARD_H));
            for (int shift : new int[] {0, CARD_SHIFT}) {
                String model = shift == 0 ? name : name + "_" + shift;
                int x = BlackjackGuiItems.slotX(0) + shift;
                int y = BlackjackGuiItems.slotY(0) + CARD_DY;
                BlackjackGuiItems.model(tree, model, BlackjackGuiItems.model(0, List.of(
                        BlackjackGuiItems.Quad.of(name, x, y, CARD_W, CARD_H))));
                BlackjackGuiItems.item(tree, model, BlackjackGuiItems.plain(model, 0));
            }
        }
    }

    static String cardName(int id) {
        return id < 0 ? "card_back" : "card_" + id;
    }

    /** BlackjackScreen.badge: 23x11, border -7441604, fill -15255509, bold total (yellow, red above 21), shadow. */
    static void badges(PackTree tree, TextGlyphs text) {
        for (int total = 0; total <= 31; total++) {
            String label = Integer.toString(total);
            int textW = BlackjackGuiText.width(text, label, true);
            int width = Math.max(textW + 8, 22);
            BufferedImage img = Images.blank(width + 1, 11);
            Images.fill(img, 0, 0, width + 1, 11, 0xFF8E733C);
            Images.fill(img, 1, 1, width - 1, 9, 0xFF17382B);
            BlackjackGuiText.draw(img, text, label, (width + 1 - textW) / 2, 2, total > 21 ? RED : YELLOW, true, true);
            BlackjackGuiItems.picture(tree, "badge_" + total, img, DEALER_BADGE_SLOT, BADGE_RIGHT + 1 - img.getWidth(),
                    BADGE_DEALER_TOP);
        }
    }

    /**
     * Status and result lines (extractPanel): result_n bold in the result colour, status_2 bold gold, the other
     * statuses plain white; none has a shadow ({@code centered} draws without). 140x12, cap top at row 3 (room for
     * the umlaut dots), centred on window x 88.
     */
    static void statuses(PackTree tree, TextGlyphs text, JsonObject lang) {
        for (int phase = 1; phase <= 5; phase++) {
            line(tree, text, "status_" + phase, lang(lang, "status_" + phase), phase == 2 ? GOLD : WHITE, phase == 2);
        }
        for (int result = 1; result <= 8; result++) {
            int colour = switch (result) {
                case 1, 2 -> GREEN;
                case 3 -> YELLOW;
                case 4, 5, 6 -> RED;
                default -> WHITE;
            };
            line(tree, text, "result_" + result, lang(lang, "result_" + result), colour, true);
        }
    }

    private static void line(PackTree tree, TextGlyphs text, String name, String label, int colour, boolean bold) {
        BufferedImage img = Images.blank(140, 12);
        if (BlackjackGuiText.width(text, label, bold) > 138) {
            throw new IllegalStateException(name + " is wider than the mod's 138 px status room");
        }
        BlackjackGuiText.centred(img, text, label, 70, 3, colour, bold, false);
        BlackjackGuiItems.picture(tree, name, img, STATUS_SLOT, STATUS_CENTRE - 70, STATUS_TOP - 3);
    }

    /** The 600-tick bar: 1..136 filled pixels (white, tinted), over a transparent 136 px quad (always oversized). */
    static void bar(PackTree tree) {
        BufferedImage img = Images.blank(BAR_W, 3);
        Images.fill(img, 0, 0, BAR_W, 3, WHITE);
        BlackjackGuiItems.texture(tree, "bar", img);
        JsonArray entries = new JsonArray();
        for (int w = 1; w <= BAR_W; w++) {
            String name = "bar_" + w;
            JsonObject model = BlackjackGuiItems.model(BAR_SLOT, List.of(
                    new BlackjackGuiItems.Quad("clear", BAR_X, BAR_Y, BAR_W, 3, -1, 7.9),
                    new BlackjackGuiItems.Quad("bar", BAR_X, BAR_Y, w, 3, 0, 8)));
            // only the left w of 136 texels
            JsonObject face = model.getAsJsonArray("elements").get(1).getAsJsonObject().getAsJsonObject("faces")
                    .getAsJsonObject("south");
            face.add("uv", Json.array(0, 0, 16.0 * w / BAR_W, 16));
            BlackjackGuiItems.model(tree, name, model);
            JsonObject entry = new JsonObject();
            entry.addProperty("threshold", w);
            entry.add("model", BlackjackGuiItems.plain(name, 1));
            entries.add(entry);
        }
        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        dispatch.add("entries", entries);
        dispatch.add("fallback", BlackjackGuiItems.empty());
        BlackjackGuiItems.item(tree, "bar", dispatch);
    }

    /** "x2" bold yellow with shadow, right-aligned at window x 220 (the mod: 148 - width), cap top 76. */
    static void doubled(PackTree tree, TextGlyphs text) {
        int w = BlackjackGuiText.width(text, "x2", true);
        BufferedImage img = Images.blank(20, 9);
        BlackjackGuiText.draw(img, text, "x2", 20 - w, 0, YELLOW, true, true);
        BlackjackGuiItems.picture(tree, "doubled", img, DOUBLED_SLOT, DOUBLED_RIGHT - 20, STAKE_TOP + 4);
    }

    static final String CHARS = "+-0123456789";

    static String charName(char c) {
        return c == '+' ? "plus" : c == '-' ? "minus" : Character.toString(c);
    }

    /**
     * A result label item: four character positions, each a {@code select} on custom model data string {@code k}.
     * Wide (≤ 2 stakes, bold): character k at slot x + 1 + 7k, cap top slot y + 4. Compact (bold off): character k
     * counted from the right at slot x - 7 - 6k, cap top slot y + 9. Every character is a white glyph tinted with
     * colour 0 over its shadow tinted with colour 1, over a transparent quad of the whole label area (the same in every
     * character model, so the item is always drawn oversized with the same bounds).
     */
    static void net(PackTree tree, TextGlyphs text, String name, boolean wide) {
        int slot = 28;
        int sx = BlackjackGuiItems.slotX(slot);
        int sy = BlackjackGuiItems.slotY(slot);
        int advance = wide ? 7 : 6;
        for (char c : CHARS.toCharArray()) {
            if (text.width(Character.toString(c)) != 6) {
                throw new IllegalStateException("'" + c + "' does not advance 6 px");
            }
            BufferedImage glyph = Images.blank(advance, 8);
            BlackjackGuiText.draw(glyph, text, Character.toString(c), 0, 0, WHITE, wide, false);
            BlackjackGuiItems.texture(tree, name + "_" + charName(c), glyph);
        }
        JsonArray parts = new JsonArray();
        for (int k = 0; k < 4; k++) {
            int x = wide ? sx + 1 + advance * k : sx - 7 - advance * k;
            int y = sy + (wide ? 4 : 9);
            JsonArray cases = new JsonArray();
            for (char c : CHARS.toCharArray()) {
                String model = name + "_" + k + "_" + charName(c);
                String texture = name + "_" + charName(c);
                BlackjackGuiItems.model(tree, model, BlackjackGuiItems.model(slot, List.of(
                        new BlackjackGuiItems.Quad("clear", wide ? sx + 1 : sx - 7 - 6 * 3, y, 4 * advance + 1, 9,
                                -1, 7.8),
                        new BlackjackGuiItems.Quad(texture, x + 1, y + 1, advance, 8, 1, 7.9),
                        new BlackjackGuiItems.Quad(texture, x, y, advance, 8, 0, 8))));
                JsonObject when = new JsonObject();
                when.addProperty("when", Character.toString(c));
                when.add("model", BlackjackGuiItems.plain(model, 2));
                cases.add(when);
            }
            JsonObject select = new JsonObject();
            select.addProperty("type", "minecraft:select");
            select.addProperty("property", "minecraft:custom_model_data");
            select.addProperty("index", k);
            select.add("cases", cases);
            select.add("fallback", BlackjackGuiItems.empty());
            parts.add(select);
        }
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", parts);
        BlackjackGuiItems.item(tree, name, composite);
    }

    /** BlackjackScreen.BlackjackButton: DerbyUiArt.button (not hovered) + centred label with shadow, 52x17. */
    static void button(PackTree tree, TextGlyphs text, String name, String label, boolean enabled) {
        int w = 52;
        int h = 17;
        BufferedImage img = Images.blank(w, h);
        Images.fill(img, 0, 0, w, h, -15719140);
        Images.fill(img, 1, 1, w - 2, h - 2, enabled ? -4878005 : -11773114);
        Images.fill(img, 2, 2, w - 4, h - 4, enabled ? -13938371 : -14139856);
        Images.fill(img, 2, 2, w - 4, 1, enabled ? -11373737 : -12956100);
        Images.fill(img, 2, h - 3, w - 4, 1, -15257821);
        if (BlackjackGuiText.width(text, label, false) > w - 8) {
            throw new IllegalStateException(label + " does not fit a " + w + " px button");
        }
        BlackjackGuiText.centred(img, text, label, w / 2, (h - 8) / 2, enabled ? -792641 : -8153987, false, true);
        int slot = BUTTON_ART_SLOTS[0];
        BlackjackGuiItems.picture(tree, name, img, slot, BlackjackGuiItems.slotX(slot) - 18,
                BlackjackGuiItems.slotY(slot));
    }
}
