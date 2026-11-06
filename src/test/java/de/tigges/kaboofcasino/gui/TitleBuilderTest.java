package de.tigges.kaboofcasino.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs a built title through a small model of the client's title drawing (pen at window x 8, advance per glyph,
 * spaces from the gui font) and checks every art glyph and text lands where it should. Uses the real
 * gui-layout.json of this build (the pack is embedded under pack/ in the processed resources).
 */
class TitleBuilderTest {

    private static GuiLayout layout;

    @BeforeAll
    static void load() {
        layout = GuiLayout.load(TitleBuilderTest.class.getClassLoader().getResourceAsStream("pack/gui-layout.json"));
    }

    /** One drawn run: font, start x, text. */
    record Run(Key font, int x, String text) {
    }

    private static List<Run> draw(Component title) {
        List<Run> runs = new ArrayList<>();
        int pen = 8;
        for (Component c : title.children()) {
            TextComponent t = (TextComponent) c;
            Key font = t.font();
            String s = t.content();
            if (font.equals(layout.guiFont())) {
                int start = pen;
                StringBuilder art = new StringBuilder();
                for (int i = 0; i < s.length(); ) {
                    int cp = s.codePointAt(i);
                    i += Character.charCount(cp);
                    if (cp >= 0xF800 && cp < 0xF900) {
                        int bit = cp >= 0xF821 ? cp - 0xF821 : cp - 0xF801;
                        pen += cp >= 0xF821 ? 1 << bit : -(1 << bit);
                        start = pen;
                    } else {
                        art.appendCodePoint(cp);
                        int code = cp;
                        GuiLayout.Glyph g = glyphByCode(code);
                        pen += g.advance();
                    }
                }
                if (!art.isEmpty()) {
                    runs.add(new Run(font, start, art.toString()));
                }
            } else {
                runs.add(new Run(font, pen, s));
                pen += layout.width(s);
            }
        }
        return runs;
    }

    private static GuiLayout.Glyph glyphByCode(int code) {
        for (String name : List.of("slot_bg_0_0", "slot_bg_1_0", "slot_bg_0_1", "slot_bg_1_1")) {
            if (layout.glyph(name).codePoint() == code) {
                return layout.glyph(name);
            }
        }
        for (int k = 0; k < 3; k++) {
            for (int f = 0; f < 8; f++) {
                GuiLayout.Glyph g = layout.glyph("slot_reel_" + k + "_" + f);
                if (g.codePoint() == code) {
                    return g;
                }
            }
        }
        throw new AssertionError("unknown glyph " + Integer.toHexString(code));
    }

    @Test
    void spacesAddUp() {
        for (int d : new int[] {-1, -31, -358, 1, 7, 179, 1000, -2047}) {
            String s = layout.spaces(d);
            int sum = 0;
            for (int cp : s.codePoints().toArray()) {
                sum += cp >= 0xF821 ? 1 << (cp - 0xF821) : -(1 << (cp - 0xF801));
            }
            assertEquals(d, sum);
        }
    }

    @Test
    void artAndTextLandOnTheirPositions() {
        TitleBuilder t = new TitleBuilder(layout);
        t.art("slot_bg_0_0").art("slot_bg_1_0").art("slot_bg_0_1").art("slot_bg_1_1");
        t.art("slot_reel_0_3", 25).art("slot_reel_1_0", 69).art("slot_reel_2_7", 113);
        t.text("Spielautomat", 89, -12, TextColor.color(0xF3E7BF), TitleBuilder.Align.CENTER);
        t.text("×100", 320, 22, NamedTextColor.GOLD, TitleBuilder.Align.RIGHT);
        List<Run> runs = draw(t.build());
        assertEquals(new Run(layout.guiFont(), -23, Character.toString(layout.glyph("slot_bg_0_0").codePoint())),
                runs.get(0));
        assertEquals(-23 + 179, runs.get(1).x());
        assertEquals(-23, runs.get(2).x());
        assertEquals(25, runs.get(4).x());
        assertEquals(113, runs.get(6).x());
        Run headline = runs.get(7);
        assertEquals(Key.key("kaboofcasino:t_m12"), headline.font());
        assertEquals(89 - layout.width("Spielautomat") / 2, headline.x());
        Run mult = runs.get(8);
        assertEquals(320 - layout.width("×100"), mult.x());
        assertTrue(layout.width("×100") > 0);
    }

    @Test
    void glyphFontsAndPositionsFromThePack() {
        GuiLayout.Glyph bg = layout.glyph("slot_bg_1_1");
        assertEquals(-23 + 179, bg.x());
        assertEquals(-24 + 133, bg.y());
        assertEquals(180, bg.advance());
        assertEquals(41, layout.glyph("slot_reel_2_5").advance());
        assertEquals(Key.key("kaboofcasino:t_58"), layout.textFont(58));
        assertEquals(6, layout.width("A"));
    }
}
