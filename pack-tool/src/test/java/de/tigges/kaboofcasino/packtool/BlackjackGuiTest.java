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

/** The blackjack GUI art and item geometry against the mod's textures and the round menu's slots. */
class BlackjackGuiTest {

    private static ModJar jar;
    private static BufferedImage mod;
    private static TextGlyphs text;

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        mod = Images.read(jar.bytes("assets/roulettecasino/textures/gui/blackjack.png"));
        text = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
    }

    /**
     * True if mod column {@code x} holds nothing of its own outside the given rows: every pixel is felt or the same
     * horizontal line/border pixel as column 320 (lines run on across a cut).
     */
    private static boolean plain(int x, int skipFrom, int skipTo) {
        for (int y = 3; y < mod.getHeight() - 3; y++) {
            if (y >= skipFrom && y < skipTo) {
                continue;
            }
            int p = mod.getRGB(x, y);
            if (!(Lettering.felt(p) && p != 0xFF14291F) && p != mod.getRGB(320, y)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void thePayoutPanelLosesOnlyPlainColumns() {
        // the lettering (rows 222..252) and the corner ornaments (254..262) are lifted out before the cut
        int[][] cuts = {{236, 4}, {242, 2}, {276, 3}, {351, 2}, {357, 4}};
        int total = 0;
        for (int[] c : cuts) {
            for (int x = c[0]; x < c[0] + c[1]; x++) {
                assertTrue(plain(x, 222, 263), "column " + x);
                total++;
            }
        }
        assertEquals(15, total);
        // the frame's inner dark line (235, 362) stays
        assertEquals(0xFF102E23, mod.getRGB(235, 120));
        assertEquals(0xFF102E23, mod.getRGB(362, 120));
    }

    @Test
    void theBetArtEndsAtMost125PxRightOfTheChest() {
        BufferedImage art = BlackjackGuiArt.bet(mod);
        assertEquals(266, art.getHeight());
        int right = MachineGuiArt.ART_X + art.getWidth();
        assertTrue(right - 176 <= 125, "overhang " + (right - 176));
        // the bet frames land 3 px around the grid slots (2 / 4 / 6, 3): item areas at window (44 / 80 / 116, 72)
        MachineGuiArt a = BlackjackGuiArt.ART;
        for (int i = 0; i < 3; i++) {
            int frameX = a.windowX(69 + 31 * i) + BlackjackGuiArt.FRAME_DX[i];
            int frameY = a.windowY(93) + BlackjackGuiArt.FRAME_DY;
            assertEquals(8 + 18 * (2 + 2 * i) - 3, frameX, "frame " + i);
            assertEquals(18 + 18 * 3 - 3, frameY, "frame " + i);
        }
    }

    @Test
    void payoutTextsKeepTheirRoomInsideTheBoxes() {
        MachineGuiArt a = BlackjackGuiArt.ART;
        int boxLeft = a.windowX(241);
        int boxRight = a.windowX(356);
        int iconRight = a.windowX(275);
        int textLeft = a.windowX(283);
        assertTrue(textLeft - iconRight >= 3, "gap icon to text " + (textLeft - iconRight));
        assertTrue(boxLeft < iconRight);
        String[] widest = {"Mehr oder 21", "BLACKJACK"};
        boolean[] bold = {false, true};
        for (int i = 0; i < widest.length; i++) {
            // the text's last pixel plus its shadow stay left of the border with at least 2 px felt
            int end = textLeft + BlackjackGuiText.width(text, widest[i], bold[i]);
            assertTrue(boxRight - end >= 2, widest[i] + " ends at " + end + ", border " + boxRight);
        }
        assertTrue(boxRight - a.windowX(351) >= 2, "odds");
        // header line and boxes centred in the narrower panel
        int panelLeft = a.panelFrom() + MachineGuiArt.ART_X;
        int panelRight = a.panelTo() + MachineGuiArt.ART_X;
        assertEquals(boxLeft - panelLeft, panelRight - 1 - boxRight);
        assertEquals(a.windowX(249) - panelLeft, panelRight - 1 - a.windowX(348));
    }

    @Test
    void theRoundPanelFitsAroundTheChest() {
        BufferedImage art = BlackjackGuiArt.round(
                Images.read(jar.bytes("assets/roulettecasino/textures/gui/blackjack_panel.png")), mod);
        assertEquals(BlackjackGuiArt.ROUND_W, art.getWidth());
        assertEquals(BlackjackGuiArt.ROUND_H, art.getHeight());
        // centred on the chest, within 125 px on both sides; its bottom frame visible at 427x240 (window y < 231)
        assertEquals(88, BlackjackGuiArt.ROUND_X + BlackjackGuiArt.ROUND_W / 2);
        assertTrue(BlackjackGuiArt.ROUND_X >= -125 && BlackjackGuiArt.ROUND_X + BlackjackGuiArt.ROUND_W <= 301);
        assertEquals(231, BlackjackGuiArt.ROUND_Y + BlackjackGuiArt.ROUND_H);
        // the inventory slot art lies exactly under the vanilla slots (window 7..168 x 139..214)
        assertEquals(mod.getRGB(30, 175), art.getRGB(7 - BlackjackGuiArt.ROUND_X, 139 - BlackjackGuiArt.ROUND_Y));
        assertEquals(mod.getRGB(191, 250), art.getRGB(168 - BlackjackGuiArt.ROUND_X, 214 - BlackjackGuiArt.ROUND_Y));
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
    void itemModelsDrawWhereTheRoundMenuExpectsThem() {
        // cards in row 0 (dealer) and 2 (player): 25x32, top 16 px above the item area; _9 shifted right
        JsonObject card = BlackjackGuiItems.model(0, List.of(BlackjackGuiItems.Quad.of("c", 8, 2, 25, 32)));
        double[] r = rect(card, 0, 18 + 3);
        assertEquals(List.of(62.0, 38.0, 87.0, 70.0), List.of(r[0], r[1], r[2], r[3]));
        JsonObject shifted = BlackjackGuiItems.model(0, List.of(BlackjackGuiItems.Quad.of("c", 17, 2, 25, 32)));
        r = rect(shifted, 0, 1);
        assertEquals(35.0, r[0]);
        // the badge of slot 17 (dealer) and slot 35 (player) at window x 198..220, y 2 / 38
        JsonObject badge = BlackjackGuiItems.model(BlackjackGui.DEALER_BADGE_SLOT,
                List.of(BlackjackGuiItems.Quad.of("b", 198, 2, 23, 11)));
        r = rect(badge, 0, BlackjackGui.PLAYER_BADGE_SLOT);
        assertEquals(List.of(198.0, 38.0, 221.0, 49.0), List.of(r[0], r[1], r[2], r[3]));
        // the buttons over slot triples
        JsonObject button = BlackjackGuiItems.model(46, List.of(BlackjackGuiItems.Quad.of("h", 8, 108, 52, 17)));
        for (int b = 0; b < 3; b++) {
            r = rect(button, 0, BlackjackGui.BUTTON_ART_SLOTS[b]);
            assertEquals(8 + 54 * b, r[0]);
            assertEquals(60 + 54 * b, r[2]);
        }
    }

    @Test
    void statusTextsFitTheModsRoom() {
        JsonObject lang = Json.parse(jar.bytes("assets/roulettecasino/lang/de_de.json"));
        for (int i = 1; i <= 8; i++) {
            assertTrue(BlackjackGuiText.width(text, BlackjackGui.lang(lang, "result_" + i), true) <= 138);
        }
        for (int i = 1; i <= 5; i++) {
            assertTrue(BlackjackGuiText.width(text, BlackjackGui.lang(lang, "status_" + i), i == 2) <= 138);
        }
        for (String b : new String[] {"hit", "stand", "double"}) {
            assertTrue(text.width(BlackjackGui.lang(lang, b)) <= 52 - 8, b);
        }
        assertTrue(text.width(BlackjackGui.lang(lang, "deal")) <= 72 - 8);
    }
}
