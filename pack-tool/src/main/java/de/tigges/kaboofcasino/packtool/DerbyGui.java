package de.tigges.kaboofcasino.packtool;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The duck derby chest GUI (render.md 5; mod {@code DuckDerbyScreen}). The title is the background of
 * {@link DerbyGuiArt} (both felt panels, bet and inventory frames), the four duck cards (glyphs at twice the
 * resolution) and "DUCK DERBY" over the right panel; everything that changes is an {@code oversized_in_gui} item below
 * {@code gui/derby/} that draws at a fixed window position from a fixed slot of {@code DerbyBetMenu}:
 * <pre>
 *  row 0  slots 0 / 3 / 5 / 8: lane state (veil_L: the mod's veil with the name redrawn muted; chosen_L: the gold
 *         lines of the staked lane); 1: lobby (the member cells behind the heads); 6: info (people count, page,
 *         the empty lobby); 7: hint; while the help is open 2 / 7: the help panel's halves
 *  rows 1-3  the bets (grid columns 0 / 3 / 5 / 8)
 *  row 4  36-39 SOLO / CLAIM (art in 37), 40 HELP, 41-44 GROUP / CONFIRM / WITHDRAW (art in 42)
 *  row 5  45-52 the member heads (vanilla player heads), 53 the pager
 * </pre>
 * All texts are the mod's, from its lang file of the pack's language ({@link PackLang}).
 */
final class DerbyGui {

    static final String PREFIX = "derby";
    /** {@code gui.roulettecasino.derby.duck_0..3}, checked against the mod's de_de.json and en_us.json. */
    static final String[] DUCKS = {"GELB", "ROT", "WEISS", "SCHWARZ"};
    static final String[] DUCKS_EN = {"YELLOW", "RED", "WHITE", "BLACK"};

    /** A duck's name in the pack's language. */
    static String duck(int lane) {
        return PackLang.text(DUCKS_EN[lane], DUCKS[lane]);
    }

    // ---- slots (DerbyBetMenu uses the same) ------------------------------------------------------------------------
    static final int[] LANE_SLOTS = {0, 3, 5, 8};
    static final int LOBBY_SLOT = 1;
    static final int HELP_LEFT_SLOT = 2;
    static final int INFO_SLOT = 6;
    static final int HINT_SLOT = 7;
    static final int HELP_RIGHT_SLOT = 7;
    static final int SOLO_ART = 37;
    static final int HELP_SLOT = 40;
    static final int GROUP_ART = 42;
    static final int FIRST_HEAD = 45;
    static final int PAGER_SLOT = 53;
    static final int PAGE_SIZE = 8;
    static final int MAX_PLAYERS = 64;
    static final int MAX_PAGES = MAX_PLAYERS / PAGE_SIZE;
    static final int MAX_SECONDS = 10;

    // ---- window geometry ---------------------------------------------------------------------------------------------
    /** The veil reaches from the card's top to the last bet frame (the mod: 3 px further, here the buttons start). */
    static final int VEIL_BOTTOM = 89;
    static final int BUTTON_Y = 89;
    static final int BUTTON_H = 18;
    static final int SOLO_X = -4;
    static final int GROUP_X = 97;
    static final int BUTTON_W = 83;
    static final int HELP_X = 79;
    /** The right panel's text area. */
    static final int PANEL_X = DerbyGuiArt.RIGHT_X + 4;
    static final int PANEL_W = DerbyGuiArt.RIGHT_W - 8;
    static final int HINT_Y = 2;
    static final int HINT_H = 22;
    static final int HINT_ROOM = DerbyGuiArt.RIGHT_W - 12;
    static final int COUNT_Y = 32;
    static final int PAGE_Y = 44;
    static final int DUCKS_Y = 60;
    static final int WAITING_Y = 84;
    /** The help panel (the mod's 310 px wide panel, re-laid out to fit above the inventory). */
    static final int HELP_PANEL_X = -9;
    static final int HELP_PANEL_Y = -8;
    static final int HELP_PANEL_W = 310;
    static final int HELP_PANEL_H = 133;
    static final int HELP_SPLIT = 155;

    private DerbyGui() {
    }

    static List<GuiFont.Anchor> anchors() {
        List<GuiFont.Anchor> out = new ArrayList<>();
        out.add(GuiFont.Anchor.text("derby_brand", DerbyGuiArt.RIGHT_CENTRE, -8, "center"));
        return out;
    }

    static String lang(JsonObject lang, String key) {
        String full = "gui.roulettecasino.derby." + key;
        if (!lang.has(full)) {
            throw new IllegalStateException("the mod's " + PackLang.current().modCode() + ".json lacks " + full);
        }
        return lang.get(full).getAsString();
    }

    static void add(ModJar jar, PackTree tree, GuiFont font, TextGlyphs text) {
        JsonObject lang = Json.parse(jar.bytes(PackLang.current().modLangPath("roulettecasino")));
        for (int lane = 0; lane < 4; lane++) {
            if (!lang(lang, "duck_" + lane).equals(duck(lane))) {
                throw new IllegalStateException("duck_" + lane + " is not " + duck(lane));
            }
        }
        BufferedImage bg = DerbyGuiArt.background(Images.read(
                jar.bytes("assets/roulettecasino/textures/gui/duck_derby_felt.png")));
        int tile = (bg.getWidth() + 1) / 2;
        for (int i = 0; i < 2; i++) {
            int w = Math.min(tile, bg.getWidth() - i * tile);
            font.art(PREFIX + "_bg_" + i, Images.crop(bg, i * tile, 0, w, bg.getHeight()), "font/derby/bg_" + i
                    + ".png", DerbyGuiArt.ART_X + i * tile, DerbyGuiArt.ART_Y);
        }
        for (int lane = 0; lane < 4; lane++) {
            font.grid(new String[] {PREFIX + "_card_" + lane}, DerbyGuiArt.card(text, lane, 2),
                    "font/derby/card_" + lane + ".png", 1, 1, DerbyGuiArt.CARD_H, 13 - DerbyGuiArt.CARD_Y,
                    DerbyGuiArt.CARD_X[lane], DerbyGuiArt.CARD_Y);
        }
        for (GuiFont.Anchor a : anchors()) {
            font.anchor(a);
        }
        BufferedImage guide = Images.read(jar.bytes("assets/roulettecasino/textures/item/casino_guide.png"));
        DerbyGuiItems.texture(tree, "guide", guide);
        lanes(tree, text);
        hints(tree, text, lang);
        info(tree, text, lang);
        lobby(tree);
        buttons(tree, text, lang);
        help(tree, text, lang);
    }

    /** {@code veil_L} (muted lane) and {@code chosen_L} (the staked lane's gold lines), drawn from row 0. */
    static void lanes(PackTree tree, TextGlyphs text) {
        int h = VEIL_BOTTOM - DerbyGuiArt.CARD_Y;
        for (int lane = 0; lane < 4; lane++) {
            int x = DerbyGuiArt.CARD_X[lane];
            BufferedImage veil = Images.blank(DerbyGuiArt.CARD_W, h);
            Images.fill(veil, 0, 0, DerbyGuiArt.CARD_W, h, DerbyGuiArt.VEIL);
            // the banner under the veil, opaque, with the name redrawn muted (covers the bright name of the title)
            BufferedImage banner = Images.blank(DerbyGuiArt.CARD_W - 2, 11);
            Images.fill(banner, 0, 0, banner.getWidth(), banner.getHeight(), 0xFF000000 | DerbyGuiArt.BANNER[lane]);
            for (int y = 0; y < banner.getHeight(); y++) {
                for (int bx = 0; bx < banner.getWidth(); bx++) {
                    Images.blend(banner, bx, y, DerbyGuiArt.VEIL);
                }
            }
            DerbyGuiArt.name(new DerbyGuiArt.Canvas(banner, 1, 25, 1), text, lane, 0, 0, true);
            Images.copy(banner, 0, 0, banner.getWidth(), banner.getHeight(), veil, 1, 25);
            DerbyGuiItems.picture(tree, "veil_" + lane, veil, LANE_SLOTS[lane], x, DerbyGuiArt.CARD_Y);
            BufferedImage chosen = Images.blank(DerbyGuiArt.CARD_W, DerbyGuiArt.CARD_H);
            Images.fill(chosen, 0, 0, DerbyGuiArt.CARD_W, 1, DerbyGuiArt.GOLD_LINE);
            Images.fill(chosen, 0, DerbyGuiArt.CARD_H - 1, DerbyGuiArt.CARD_W, 1, DerbyGuiArt.GOLD_LINE);
            DerbyGuiItems.picture(tree, "chosen_" + lane, chosen, LANE_SLOTS[lane], x, DerbyGuiArt.CARD_Y);
        }
    }

    /** Greedy word wrap at {@code room} px (the breaking space dropped; a single long word stays alone). */
    static List<String> wrap(TextGlyphs text, String s, int room) {
        return PokerGui.split(text, s, room);
    }

    /** One hint picture: up to two lines centred in the right panel; {@code coloured} = the duck name's colour. */
    static BufferedImage hint(TextGlyphs text, String prefix, String duck, int colour, int duckColour) {
        BufferedImage img = Images.blank(PANEL_W, HINT_H);
        String full = prefix + (duck == null ? "" : duck);
        List<String> lines = wrap(text, full, HINT_ROOM);
        if (lines.size() > 2) {
            throw new IllegalStateException("hint '" + full + "' needs more than two lines");
        }
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, PANEL_X, HINT_Y, 1);
        int centre = DerbyGuiArt.RIGHT_CENTRE;
        int consumed = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int top = lines.size() == 1 ? HINT_Y + 7 : HINT_Y + 2 + 10 * i;
            int left = centre - text.width(line) / 2;
            // the duck's name keeps its own colour wherever the line breaks
            int prefixChars = Math.max(0, Math.min(line.length(), prefix.length() - consumed));
            String head = line.substring(0, prefixChars);
            String tail = line.substring(prefixChars);
            DerbyGuiArt.text(g, text, head, left, top, colour, false, false);
            if (!tail.isEmpty()) {
                DerbyGuiArt.text(g, text, tail, left + text.width(head), top, duckColour, false, false);
            }
            consumed += line.length() + 1;
        }
        return img;
    }

    /**
     * {@code extractLabels}' hint line: pick, several, picked_0..3 ("Deine Ente: " + the duck in its hint colour),
     * confirmed and countdown_0..10; one item {@code hint} selecting on custom model data string 0.
     */
    static void hints(PackTree tree, TextGlyphs text, JsonObject lang) {
        List<String> values = new ArrayList<>();
        List<JsonObject> models = new ArrayList<>();
        java.util.function.BiConsumer<String, BufferedImage> add = (value, img) -> {
            values.add(value);
            models.add(DerbyGuiItems.pictureModel(tree, "hint_" + value, img, HINT_SLOT, PANEL_X, HINT_Y));
        };
        add.accept("pick", hint(text, lang(lang, "pick"), null, DerbyGuiArt.CREAM, 0));
        add.accept("several", hint(text, lang(lang, "several"), null, DerbyGuiArt.SEVERAL, 0));
        String picked = lang(lang, "picked");
        if (!picked.endsWith("%s")) {
            throw new IllegalStateException("picked does not end with %s: " + picked);
        }
        for (int lane = 0; lane < 4; lane++) {
            add.accept("picked_" + lane, hint(text, picked.substring(0, picked.length() - 2), duck(lane),
                    DerbyGuiArt.CREAM, 0xFF000000 | DerbyGuiArt.HINT_COLOR[lane]));
        }
        add.accept("confirmed", hint(text, lang(lang, "confirmed"), null, DerbyGuiArt.CREAM, 0));
        String countdown = lang(lang, "countdown");
        for (int s = 0; s <= MAX_SECONDS; s++) {
            add.accept("countdown_" + s, hint(text, countdown.replace("%s", Integer.toString(s)), null,
                    DerbyGuiArt.CREAM, 0));
        }
        DerbyGuiItems.item(tree, "hint", DerbyGuiItems.select(0, values, models));
    }

    /** A centred one-line picture over the right panel's text area. */
    static BufferedImage line(TextGlyphs text, String s, int top, int colour, boolean shadow) {
        BufferedImage img = Images.blank(PANEL_W, 11);
        if (text.width(s) > PANEL_W - 2) {
            throw new IllegalStateException("'" + s + "' is wider than the right panel");
        }
        DerbyGuiArt.text(new DerbyGuiArt.Canvas(img, PANEL_X, top - 1, 1), text, s,
                DerbyGuiArt.RIGHT_CENTRE - text.width(s) / 2, top, colour, false, shadow);
        return img;
    }

    /**
     * The right panel's lobby texts ({@code info}: a composite on strings 0 / 1 / 2): "%s Spieler" (0..64, cream with
     * shadow), the page "n / m" (2..8 pages) and the empty lobby (the mod's four ducks and "Noch keine Spieler").
     */
    static void info(PackTree tree, TextGlyphs text, JsonObject lang) {
        String people = lang(lang, "people");
        List<String> counts = new ArrayList<>();
        List<JsonObject> countModels = new ArrayList<>();
        for (int n = 0; n <= MAX_PLAYERS; n++) {
            counts.add(Integer.toString(n));
            countModels.add(DerbyGuiItems.pictureModel(tree, "count_" + n, line(text, people.replace("%s",
                    Integer.toString(n)), COUNT_Y, DerbyGuiArt.CREAM, true), INFO_SLOT, PANEL_X, COUNT_Y - 1));
        }
        List<String> pages = new ArrayList<>();
        List<JsonObject> pageModels = new ArrayList<>();
        for (int m = 2; m <= MAX_PAGES; m++) {
            for (int n = 1; n <= m; n++) {
                pages.add(n + "/" + m);
                pageModels.add(DerbyGuiItems.pictureModel(tree, "page_" + n + "_" + m, line(text, n + " / " + m,
                        PAGE_Y, DerbyGuiArt.CREAM, false), INFO_SLOT, PANEL_X, PAGE_Y - 1));
            }
        }
        BufferedImage empty = Images.blank(PANEL_W, WAITING_Y + 10 - DUCKS_Y);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(empty, PANEL_X, DUCKS_Y, 1);
        for (int lane = 0; lane < 4; lane++) {
            DerbyGuiArt.duck(g, DerbyGuiArt.RIGHT_CENTRE - 44 + lane * 24, DUCKS_Y, lane, 1.0);
        }
        DerbyGuiArt.fitted(g, text, lang(lang, "waiting"), DerbyGuiArt.RIGHT_CENTRE, WAITING_Y, PANEL_W - 2,
                DerbyGuiArt.WAITING, false, 0);
        JsonObject emptyModel = DerbyGuiItems.pictureModel(tree, "empty", empty, INFO_SLOT, PANEL_X, DUCKS_Y);
        DerbyGuiItems.item(tree, "info", DerbyGuiItems.composite(List.of(
                DerbyGuiItems.select(0, counts, countModels),
                DerbyGuiItems.select(1, pages, pageModels),
                DerbyGuiItems.select(2, List.of("empty"), List.of(emptyModel)))));
    }

    /**
     * The member cells behind the heads (the mod's member card in slot size: dark rim, inner fill, the lane bar in
     * the duck's hint colour along the bottom): {@code lobby}, a composite of 8 selects on strings 0..7 = lane.
     */
    static void lobby(PackTree tree) {
        for (int lane = 0; lane < 4; lane++) {
            BufferedImage cell = Images.blank(18, 18);
            Images.fill(cell, 0, 0, 18, 18, DerbyGuiArt.MEMBER_OUTER);
            Images.fill(cell, 1, 1, 16, 16, DerbyGuiArt.MEMBER_INNER);
            Images.fill(cell, 1, 15, 16, 2, 0xFF000000 | DerbyGuiArt.HINT_COLOR[lane]);
            DerbyGuiItems.texture(tree, "member_" + lane, cell);
        }
        List<JsonObject> parts = new ArrayList<>();
        for (int k = 0; k < PAGE_SIZE; k++) {
            int slot = FIRST_HEAD + k;
            List<String> values = new ArrayList<>();
            List<JsonObject> models = new ArrayList<>();
            for (int lane = 0; lane < 4; lane++) {
                String name = "member_" + k + "_" + lane;
                DerbyGuiItems.model(tree, name, DerbyGuiItems.model(LOBBY_SLOT, List.of(BlackjackGuiItems.Quad.of(
                        "member_" + lane, DerbyGuiArt.slotX(slot) - 1, DerbyGuiArt.slotY(slot) - 1, 18, 18))));
                values.add(Integer.toString(lane));
                models.add(DerbyGuiItems.plain(name));
            }
            parts.add(DerbyGuiItems.select(k, values, models));
        }
        DerbyGuiItems.item(tree, "lobby", DerbyGuiItems.composite(parts));
    }

    /** {@code DuckDerbyScreen.CasinoButton}: {@code DerbyUiArt.button} + the label centred with shadow (room w - 8). */
    static BufferedImage button(TextGlyphs text, String label, int w, int h, boolean active) {
        BufferedImage img = Images.blank(w, h);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, 0, 0, 1);
        DerbyGuiArt.button(g, 0, 0, w, h, active, false);
        if (text.width(label) > w - 8) {
            throw new IllegalStateException("'" + label + "' does not fit a " + w + " px button");
        }
        DerbyGuiArt.fitted(g, text, label, w / 2, (h - 8) / 2, w - 8, active ? DerbyGuiArt.CREAM : DerbyGuiArt.GREY,
                true, 0);
        return img;
    }

    static void buttons(PackTree tree, TextGlyphs text, JsonObject lang) {
        record B(String name, String key, boolean active, int slot, int x) {
        }
        for (B b : List.of(new B("solo_on", "solo", true, SOLO_ART, SOLO_X),
                new B("solo_off", "solo", false, SOLO_ART, SOLO_X),
                new B("claim_on", "claim", true, SOLO_ART, SOLO_X),
                new B("group_on", "group", true, GROUP_ART, GROUP_X),
                new B("group_off", "group", false, GROUP_ART, GROUP_X),
                new B("confirm_on", "confirm", true, GROUP_ART, GROUP_X),
                new B("confirm_off", "confirm", false, GROUP_ART, GROUP_X),
                new B("withdraw_on", "withdraw", true, GROUP_ART, GROUP_X))) {
            DerbyGuiItems.picture(tree, b.name(), button(text, lang(lang, b.key()), BUTTON_W, BUTTON_H, b.active()),
                    b.slot(), b.x(), BUTTON_Y);
        }
        // the help button: the mod's 30x30 button with the guide at 1.5x, here one slot: 18x18, the guide 16x16
        BufferedImage help = Images.blank(18, 18);
        DerbyGuiArt.button(new DerbyGuiArt.Canvas(help, 0, 0, 1), 0, 0, 18, 18, true, false);
        DerbyGuiItems.texture(tree, "help_button", help);
        DerbyGuiItems.model(tree, "help", DerbyGuiItems.model(HELP_SLOT, List.of(
                new BlackjackGuiItems.Quad("help_button", HELP_X, BUTTON_Y, 18, 18, -1, 8),
                new BlackjackGuiItems.Quad("guide", HELP_X + 1, BUTTON_Y + 1, 16, 16, -1, 8.1))));
        DerbyGuiItems.item(tree, "help", DerbyGuiItems.plain("help"));
        // the pager (the mod's 18x16 arrow buttons, here one slot for both directions)
        DerbyGuiItems.picture(tree, "pager", button(text, ">", 18, 18, true), PAGER_SLOT,
                DerbyGuiArt.slotX(PAGER_SLOT) - 1, DerbyGuiArt.slotY(PAGER_SLOT) - 1);
    }

    /**
     * The help panel ({@code DuckDerbyScreen.extractRenderState} while {@code helpOpen}): the frame, the title band with
     * the guide and "Spielanleitung", {@code help_bet}, the three outcome rows, {@code help_start} and the back
     * button. The mod's panel is up to 246 px high and scrolls; above the chest's inventory there are 133 px, so the
     * three rows stand side by side (each with its medal, rank and reward on two lines) and the title is not scaled.
     */
    static BufferedImage helpPanel(TextGlyphs text, JsonObject lang) {
        int w = HELP_PANEL_W;
        int h = HELP_PANEL_H;
        BufferedImage img = Images.blank(w, h);
        DerbyGuiArt.Canvas g = new DerbyGuiArt.Canvas(img, 0, 0, 1);
        DerbyGuiArt.frame(g, 0, 0, w, h, -1777722);
        g.fill(7, 6, w - 7, 26, DerbyGuiArt.FELT);
        g.fill(7, 26, w - 7, 27, -5601721);
        DerbyGuiArt.fitted(g, text, lang(lang, "help_title"), w / 2 + 8, 12, w - 66, DerbyGuiArt.CREAM, false, 0);
        int left = 17;
        int room = w - 36;
        List<String> bet = wrap(text, lang(lang, "help_bet"), room);
        int y = 32;
        for (String line : bet) {
            DerbyGuiArt.text(g, text, line, left, y, DerbyGuiArt.INK, false, false);
            y += 10;
        }
        String[] ranks = {"help_first", "help_second", "help_others"};
        String[] rewards = {"×3", lang(lang, "help_returned"), lang(lang, "help_lost")};
        int[] rewardColours = {-9416675, DerbyGuiArt.INK, -8176078};
        int boxW = 94;
        int gap = 3;
        int boxY = y + 3;
        int boxH = 34;
        for (int row = 0; row < 3; row++) {
            int rx = 11 + row * (boxW + gap);
            g.fill(rx, boxY, rx + boxW, boxY + boxH, -5853557);
            g.fill(rx + 1, boxY + 1, rx + boxW - 1, boxY + boxH - 1, row == 2 ? -2043713 : -2958915);
            DerbyGuiArt.outcome(g, text, rx + 4, boxY + 4, row);
            String rank = lang(lang, ranks[row]);
            if (text.width(rank) > boxW - 25) {
                throw new IllegalStateException(rank + " does not fit its help box");
            }
            DerbyGuiArt.text(g, text, rank, rx + 22, boxY + 8, DerbyGuiArt.INK, false, false);
            if (text.width(rewards[row]) > boxW - 8) {
                throw new IllegalStateException(rewards[row] + " does not fit its help box");
            }
            DerbyGuiArt.text(g, text, rewards[row], rx + boxW - 6 - text.width(rewards[row]), boxY + 22,
                    rewardColours[row], false, false);
        }
        y = boxY + boxH + 6;
        for (String line : wrap(text, lang(lang, "help_start"), room)) {
            DerbyGuiArt.text(g, text, line, left, y, DerbyGuiArt.INK, false, false);
            y += 10;
        }
        int bx = w / 2 - 40;
        int by = h - 26;
        if (y > by) {
            throw new IllegalStateException("the help text runs into the back button (" + y + " > " + by + ")");
        }
        DerbyGuiArt.button(g, bx, by, 80, 18, true, false);
        DerbyGuiArt.fitted(g, text, PackLang.text("Back", "Zurück"), w / 2, by + 5, 70, DerbyGuiArt.CREAM, false, 0);
        return img;
    }

    /** The help panel as two pictures (left from slot 2, right from slot 7) plus the guide in its title band. */
    static void help(PackTree tree, TextGlyphs text, JsonObject lang) {
        BufferedImage panel = helpPanel(text, lang);
        BufferedImage left = Images.crop(panel, 0, 0, HELP_SPLIT, HELP_PANEL_H);
        BufferedImage right = Images.crop(panel, HELP_SPLIT, 0, HELP_PANEL_W - HELP_SPLIT, HELP_PANEL_H);
        DerbyGuiItems.texture(tree, "help_left", left);
        DerbyGuiItems.texture(tree, "help_right", right);
        DerbyGuiItems.model(tree, "help_left", DerbyGuiItems.model(HELP_LEFT_SLOT, List.of(
                BlackjackGuiItems.Quad.of("help_left", HELP_PANEL_X, HELP_PANEL_Y, HELP_SPLIT, HELP_PANEL_H),
                new BlackjackGuiItems.Quad("guide", HELP_PANEL_X + 12, HELP_PANEL_Y + 8, 16, 16, -1, 8.1))));
        DerbyGuiItems.item(tree, "help_left", DerbyGuiItems.plain("help_left"));
        DerbyGuiItems.picture(tree, "help_right", right, HELP_RIGHT_SLOT, HELP_PANEL_X + HELP_SPLIT, HELP_PANEL_Y);
    }
}
