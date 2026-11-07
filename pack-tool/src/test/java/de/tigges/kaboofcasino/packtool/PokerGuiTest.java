package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The poker GUI art and item geometry against the mod's textures and the round menu's slots. */
class PokerGuiTest {

    private static ModJar jar;
    private static BufferedImage mod;
    private static BufferedImage panel;
    private static TextGlyphs text;
    private static JsonObject lang;

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        mod = Images.read(jar.bytes("assets/roulettecasino/textures/gui/poker.png"));
        panel = Images.read(jar.bytes("assets/roulettecasino/textures/gui/poker_panel.png"));
        text = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
        lang = Json.parse(jar.bytes("assets/roulettecasino/lang/de_de.json"));
    }

    /**
     * True if mod column {@code x} holds nothing of its own outside rows skipFrom..skipTo - 1: every pixel is felt or
     * the same horizontal line/border pixel as the reference column (lines run on across a cut).
     */
    private static boolean plain(int x, int reference, int skipFrom, int skipTo) {
        for (int y = 3; y < mod.getHeight() - 3; y++) {
            if (y >= skipFrom && y < skipTo) {
                continue;
            }
            int p = mod.getRGB(x, y);
            if (!(Lettering.felt(p) && p != 0xFF14291F) && p != mod.getRGB(reference, y)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void theScreenLosesOnlyPlainColumns() {
        // the corner ornaments (rows 254..262) are lifted out before the cut
        int total = 0;
        for (int x = 215; x < 220; x++) {
            assertTrue(plain(x, 196, 254, 263), "left panel column " + x);
            total++;
        }
        for (int[] c : new int[][] {{236, 3}, {356, 6}}) {
            for (int x = c[0]; x < c[0] + c[1]; x++) {
                assertTrue(plain(x, 320, 254, 263), "payout column " + x);
                total++;
            }
        }
        // the left panel's dark outline: the payout panel's one stays as the seam
        for (int y = 0; y < mod.getHeight(); y++) {
            assertEquals(mod.getRGB(232, y), mod.getRGB(223, y), "seam row " + y);
        }
        total++;
        assertEquals(15, total);
        // the frames' inner dark lines (220, 235, 362) and the box borders (239, 355) stay
        assertEquals(0xFF102E23, mod.getRGB(220, 120));
        assertEquals(0xFF102E23, mod.getRGB(235, 120));
        assertEquals(0xFF102E23, mod.getRGB(362, 120));
        assertEquals(0xFF806D45, mod.getRGB(239, 100));
        assertEquals(0xFF806D45, mod.getRGB(355, 100));
    }

    @Test
    void theBetArtEndsAtMost125PxRightOfTheChest() {
        BufferedImage art = PokerGuiArt.bet(mod);
        assertEquals(266, art.getHeight());
        int right = MachineGuiArt.ART_X + art.getWidth();
        assertEquals(301, right);
        assertTrue(right - 176 <= 125, "overhang " + (right - 176));
        // the bet frames land 3 px around the grid slots (2 / 4 / 6, 3): item areas at window (44 / 80 / 116, 72)
        MachineGuiArt a = PokerGuiArt.ART;
        for (int i = 0; i < 3; i++) {
            int frameX = a.windowX(PokerGuiArt.FRAME_X + PokerGuiArt.FRAME_STEP * i) + PokerGuiArt.FRAME_DX[i];
            int frameY = a.windowY(PokerGuiArt.FRAME_Y) + PokerGuiArt.FRAME_DY;
            assertEquals(8 + 18 * (2 + 2 * i) - 3, frameX, "frame " + i);
            assertEquals(18 + 18 * 3 - 3, frameY, "frame " + i);
            int cx = a.x(PokerGuiArt.FRAME_X + PokerGuiArt.FRAME_STEP * i) + PokerGuiArt.FRAME_DX[i];
            int cy = a.y(PokerGuiArt.FRAME_Y) + PokerGuiArt.FRAME_DY;
            assertEquals(mod.getRGB(PokerGuiArt.FRAME_X + 31 * i, PokerGuiArt.FRAME_Y), art.getRGB(cx, cy));
        }
        // the payout boxes keep their full width: border to border 117 px as in the mod
        assertEquals(355 - 239, a.x(355) - a.x(239));
        // the DEAL button's place (mod 76..147 x 125..142) is plain felt
        for (int y = 125; y < 143; y++) {
            for (int x = 76; x < 148; x++) {
                assertTrue(Lettering.felt(art.getRGB(a.x(x), a.y(y))), x + "," + y);
            }
        }
    }

    @Test
    void payoutTextsKeepTheirRoomInsideTheBoxes() {
        MachineGuiArt a = PokerGuiArt.ART;
        int boxLeft = a.windowX(239);
        int boxRight = a.windowX(355);
        // "DER EINSATZ ZAHLT" bold with shadow (113 px) between the borders, at least 1 px felt on each side
        int header = BlackjackGuiText.width(text, PokerGui.lang(lang, "ante_pays"), true);
        int headerLeft = a.windowX(297) - header / 2;
        assertTrue(headerLeft - boxLeft >= 2 && boxRight - (headerLeft + header) >= 1,
                "header " + headerLeft + ".." + (headerLeft + header) + " in " + boxLeft + ".." + boxRight);
        // paytable: name and odds of a row never touch, the odds keep the mod's margin to the border
        for (int row = 0; row < PokerGui.PAY_CATEGORIES.length; row++) {
            int category = PokerGui.PAY_CATEGORIES[row];
            String name = row == 5 ? PokerGui.lang(lang, "pay_straight_less") : PokerGui.lang(lang, "hand_" + category);
            int antePays = switch (category) {
                case 9 -> 100;
                case 8 -> 20;
                case 7 -> 10;
                case 6 -> 3;
                case 5 -> 2;
                default -> 1;
            };
            int nameEnd = a.windowX(244) + text.width(name);
            int oddsStart = a.windowX(351) - text.width(antePays + ":1");
            assertTrue(oddsStart - nameEnd >= 6, name + " meets its odds");
        }
        assertTrue(boxRight - a.windowX(351) >= 3, "odds");
        // call / dealer boxes: the texts right of the icons, the widest note and its shadow inside the border
        int textLeft = a.windowX(PokerGui.BOX_TEXT_X);
        assertTrue(textLeft - a.windowX(275) >= 2, "gap chips icon to text");
        for (String key : new String[] {"call_note", "qualify_note", "qualify_else"}) {
            int end = textLeft + text.width(PokerGui.lang(lang, key));
            assertTrue(boxRight - end >= 1, key + " ends at " + end + ", border " + boxRight);
        }
        for (String bold : new String[] {PokerGui.lang(lang, "call"),
                PokerGui.langKey(lang, "gui.roulettecasino.blackjack.dealer")}) {
            assertTrue(boxRight - (textLeft + BlackjackGuiText.width(text, bold, true)) >= 1, bold);
        }
        // the boxes stay centred in the narrower panel (frame, box, frame)
        int panelLeft = a.panelFrom() + MachineGuiArt.ART_X;
        int panelRight = a.panelTo() + MachineGuiArt.ART_X;
        assertEquals(boxLeft - panelLeft, panelRight - 1 - boxRight);
    }

    @Test
    void theRoundPanelFitsAroundTheChest() {
        BufferedImage art = PokerGuiArt.round(panel, mod);
        assertEquals(PokerGuiArt.ROUND_W, art.getWidth());
        assertEquals(PokerGuiArt.ROUND_H, art.getHeight());
        // centred on the chest, within 125 px on both sides; its bottom frame visible at 427x240 (window y < 231)
        assertEquals(88, PokerGuiArt.ROUND_X + PokerGuiArt.ROUND_W / 2);
        assertTrue(PokerGuiArt.ROUND_X >= -125 && PokerGuiArt.ROUND_X + PokerGuiArt.ROUND_W <= 301);
        assertEquals(231, PokerGuiArt.ROUND_Y + PokerGuiArt.ROUND_H);
        // the band pieces are the mod's pixels: the boxes' corners, the card lines, the board band
        int ox = -PokerGuiArt.ROUND_X;
        int oy = -PokerGuiArt.ROUND_Y;
        assertEquals(panel.getRGB(13, 72), art.getRGB(PokerGuiArt.DEALER_X + 4 + ox, PokerGuiArt.BOX_Y + 35 + oy));
        assertEquals(panel.getRGB(85, 72), art.getRGB(PokerGuiArt.PLAYER_X + 4 + ox, PokerGuiArt.BOX_Y + 35 + oy));
        assertEquals(panel.getRGB(10, 129), art.getRGB(PokerGuiArt.BOARD_X + 1 + ox, PokerGuiArt.BOX_Y + 35 + oy));
        assertEquals(panel.getRGB(10, 149), art.getRGB(PokerGuiArt.STAKE_X0 + 1 + ox, PokerGuiArt.STAKE_Y + 18 + oy));
        assertEquals(panel.getRGB(149, 149), art.getRGB(PokerGuiArt.STAKE_X1 - 2 + ox, PokerGuiArt.STAKE_Y + 18 + oy));
        // the inventory slot art lies exactly under the vanilla slots (window 7..168 x 139..214)
        assertEquals(mod.getRGB(30, 175), art.getRGB(7 + ox, 139 + oy));
        assertEquals(mod.getRGB(191, 250), art.getRGB(168 + ox, 214 + oy));
        // fully opaque: the vanilla chest never shows through
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                assertEquals(255, art.getRGB(x, y) >>> 24, x + "," + y);
            }
        }
    }

    /** Window rectangle {x0, y0, x1, y1} of an element of a model drawn from {@code slot}. */
    private static double[] rect(JsonObject model, int element, int slot) {
        double s = model.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("scale").get(0)
                .getAsDouble();
        JsonObject e = model.getAsJsonArray("elements").get(element).getAsJsonObject();
        JsonArray from = e.getAsJsonArray("from");
        JsonArray to = e.getAsJsonArray("to");
        int sx = BlackjackGuiItems.slotX(slot);
        int sy = BlackjackGuiItems.slotY(slot);
        return new double[] {sx + 8 + (from.get(0).getAsDouble() - 8) * s, sy + 8 - (to.get(1).getAsDouble() - 8) * s,
                sx + 8 + (to.get(0).getAsDouble() - 8) * s, sy + 8 - (from.get(1).getAsDouble() - 8) * s};
    }

    @Test
    void cardModelsDrawTheModsSteps() {
        // the board's two variants serve slots 0 / 3 / 6 and 2 / 5: 27 px apart like the mod's BOARD_CARDS_X
        PokerGui.CardPlace a = PokerGui.CARD_PLACES.get(0);
        PokerGui.CardPlace b = PokerGui.CARD_PLACES.get(1);
        int[] slots = {0, 2, 3, 5, 6};
        for (int i = 0; i < 5; i++) {
            PokerGui.CardPlace p = i % 2 == 0 ? a : b;
            JsonObject m = PokerGuiItems.model(p.slot(), List.of(BlackjackGuiItems.Quad.of("c", p.x(),
                    PokerGui.CARD_Y, 25, 32)));
            double[] r = rect(m, 0, slots[i]);
            assertEquals(List.of(21.0 + 27 * i, 15.0, 46.0 + 27 * i, 47.0), List.of(r[0], r[1], r[2], r[3]));
        }
        // the hands: the mod's 28 px step, 8 px into a 70 px box; the board 4 px into its 142 px band
        assertEquals(28, PokerGui.CARD_PLACES.get(3).x() - PokerGui.CARD_PLACES.get(2).x());
        assertEquals(28, PokerGui.CARD_PLACES.get(5).x() - PokerGui.CARD_PLACES.get(4).x());
        assertEquals(PokerGuiArt.DEALER_X + 8, PokerGui.CARD_PLACES.get(2).x());
        assertEquals(PokerGuiArt.PLAYER_X + 8, PokerGui.CARD_PLACES.get(4).x());
        // the three pieces side by side with the mod's 2 px felt between, centred on the chest
        assertEquals(PokerGuiArt.DEALER_X + PokerGuiArt.HAND_W + 2, PokerGuiArt.BOARD_X);
        assertEquals(PokerGuiArt.BOARD_X + PokerGuiArt.BOARD_W + 2, PokerGuiArt.PLAYER_X);
        assertEquals(176, PokerGuiArt.DEALER_X + PokerGuiArt.PLAYER_X + PokerGuiArt.HAND_W);
    }

    @Test
    void otherItemsDrawWhereTheRoundMenuExpectsThem() {
        // buttons: 68x17 each centred over its four slots
        JsonObject call = PokerGuiItems.model(PokerGui.CALL_ART_SLOT, List.of(BlackjackGuiItems.Quad.of("b",
                PokerGui.CALL_X, PokerGui.BUTTON_Y, 68, 17)));
        double[] r = rect(call, 0, PokerGui.CALL_ART_SLOT);
        assertEquals((8 + 78) / 2.0, (r[0] + r[2]) / 2);
        JsonObject fold = PokerGuiItems.model(PokerGui.FOLD_ART_SLOT, List.of(BlackjackGuiItems.Quad.of("b",
                PokerGui.FOLD_X, PokerGui.BUTTON_Y, 68, 17)));
        r = rect(fold, 0, PokerGui.FOLD_ART_SLOT);
        assertEquals((98 + 168) / 2.0, (r[0] + r[2]) / 2);
        assertEquals(List.of(108.0, 125.0), List.of(r[1], r[3]));
        // the status line under the stake band, above the timer bar (blackjack's, window y 102)
        assertTrue(PokerGui.STATUS_TOP - 3 + 12 <= BlackjackGui.BAR_Y);
        assertEquals(PokerGuiArt.BAR_SLOT_X, BlackjackGuiArt.BAR_SLOT_X);
        assertEquals(PokerGuiArt.BAR_SLOT_Y, BlackjackGuiArt.BAR_SLOT_Y);
        assertTrue(PokerGuiArt.STAKE_Y + PokerGuiArt.STAKE_H <= PokerGui.STATUS_TOP - 3);
        // hand names end on the box's bottom line (the mod's 92 = box top + 55)
        assertEquals(PokerGuiArt.BOX_Y + 55, PokerGui.NAME_TOP + PokerGui.NAME_H);
    }

    @Test
    void textsFitTheirRoom() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(BlackjackGuiText.width(text, PokerGui.lang(lang, "result_" + i), true) <= PokerGui.STATUS_W - 2);
            assertTrue(BlackjackGuiText.width(text, PokerGui.lang(lang, "status_" + i), i == 2)
                    <= PokerGui.STATUS_W - 2);
        }
        for (String b : new String[] {"call", "fold"}) {
            assertTrue(text.width(PokerGui.lang(lang, b)) <= PokerGui.BUTTON_W - 8, b);
        }
        assertTrue(text.width(PokerGui.lang(lang, "deal")) <= 72 - 8);
        for (int c = 0; c <= 9; c++) {
            List<String> lines = PokerGui.split(text, PokerGui.lang(lang, "hand_" + c), PokerGui.NAME_ROOM);
            assertTrue(lines.size() <= 2, "hand_" + c);
            for (String line : lines) {
                assertTrue(text.width(line) <= PokerGui.NAME_ROOM, line);
            }
        }
        assertEquals(List.of("Höchste", "Karte"), PokerGui.split(text, "Höchste Karte", 66));
        assertEquals(List.of("Straight", "Flush"), PokerGui.split(text, "Straight Flush", 66));
        assertEquals(List.of("Royal Flush"), PokerGui.split(text, "Royal Flush", 66));
    }
}
