package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The light plate under vanilla's player-inventory label: in every felt menu title (slot machine, fortune wheel,
 * Plinko, roulette, blackjack and poker in both views, duck derby, cashier) at the same window place, under the whole
 * label, readable, clear of the inventory frames, and in the titles of the built pack.
 */
class InventoryLabelPlateTest {

    private static ModJar jar;

    @BeforeAll
    static void open() {
        jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
    }

    private static BufferedImage gui(String name) {
        return Images.read(jar.bytes("assets/roulettecasino/textures/gui/" + name + ".png"));
    }

    /** A title's background art and the window position of its pixel (0, 0). */
    record Art(String name, BufferedImage image, int x, int y) {
    }

    private static List<Art> arts() {
        int mx = MachineGuiArt.ART_X;
        int my = MachineGuiArt.ART_Y;
        return List.of(
                new Art("slot", SlotMachineGui.compose(gui("slot_machine")), SlotMachineGui.ART_X,
                        SlotMachineGui.ART_Y),
                new Art("wheel", WheelGui.compose(gui("fortune_wheel")), mx, my),
                new Art("plinko", PlinkoGui.compose(gui("plinko")), mx, my),
                new Art("roulette", RouletteGui.compose(gui("roulette")), mx, my),
                new Art("blackjack bet", BlackjackGuiArt.bet(gui("blackjack")), mx, my),
                new Art("blackjack round", BlackjackGuiArt.round(gui("blackjack_panel"), gui("blackjack")),
                        BlackjackGuiArt.ROUND_X, BlackjackGuiArt.ROUND_Y),
                new Art("poker bet", PokerGuiArt.bet(gui("poker")), mx, my),
                new Art("poker round", PokerGuiArt.round(gui("poker_panel"), gui("poker")), PokerGuiArt.ROUND_X,
                        PokerGuiArt.ROUND_Y),
                new Art("derby", DerbyGuiArt.background(gui("duck_derby_felt")), DerbyGuiArt.ART_X,
                        DerbyGuiArt.ART_Y),
                new Art("cashier", CashierGui.background(gui("duck_derby_felt")), DerbyGuiArt.ART_X,
                        DerbyGuiArt.ART_Y));
    }

    private static void assertPlate(String name, BufferedImage image, int ox, int oy) {
        for (int y = InventoryLabelPlate.Y0; y <= InventoryLabelPlate.Y1; y++) {
            for (int x = InventoryLabelPlate.X0; x <= InventoryLabelPlate.X1; x++) {
                assertEquals(InventoryLabelPlate.colour(x, y), image.getRGB(x - ox, y - oy), name + " at " + x + ","
                        + y);
            }
        }
    }

    @Test
    void everyFeltTitleHasThePlate() {
        List<Art> arts = arts();
        assertEquals(10, arts.size());
        for (Art a : arts) {
            assertPlate(a.name(), a.image(), a.x(), a.y());
            // felt right of it (the plate is under the label only)
            int right = a.image().getRGB(InventoryLabelPlate.X1 + 2 - a.x(), 131 - a.y());
            assertTrue(contrast(right, InventoryLabelPlate.LABEL_COLOUR) < 2, a.name() + ": felt beside the plate");
        }
    }

    /** WCAG relative luminance of an opaque colour. */
    private static double luminance(int argb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = (argb >> (16 - 8 * i) & 255) / 255.0;
            c[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /**
     * Under every pixel of the label (cap tops to descenders, "Inventar" and the longer "Inventory", "Inventaire",
     * "Inventario") the plate's cream, at least 7:1 against #404040; the plate ends above the inventory's slot frames
     * (139) and is the same in every menu.
     */
    @Test
    void theLabelIsReadableOnThePlate() {
        TextGlyphs text = TextGlyphs.load(Path.of(System.getProperty("kaboofcasino.packSrc")).resolve("vanilla"));
        assertEquals(128, InventoryLabelPlate.LABEL_Y);
        for (String label : List.of("Inventar", "Inventory", "Inventaire", "Inventario")) {
            int last = InventoryLabelPlate.LABEL_X + text.width(label) - 2; // the advance includes one free column
            assertTrue(last < InventoryLabelPlate.X1, label + " ends at " + last);
            for (int y = InventoryLabelPlate.LABEL_Y; y < InventoryLabelPlate.LABEL_Y + 8; y++) {
                for (int x = InventoryLabelPlate.LABEL_X; x <= last; x++) {
                    int under = InventoryLabelPlate.colour(x, y);
                    assertTrue(under == InventoryLabelPlate.FILL || under == InventoryLabelPlate.SHADE,
                            label + " at " + x + "," + y);
                    assertTrue(contrast(under, InventoryLabelPlate.LABEL_COLOUR) >= 7, label + " at " + x + "," + y);
                }
            }
        }
        // one cream row above the cap tops, the plate clear of the first inventory slot frame (y 139)
        assertEquals(InventoryLabelPlate.FILL, InventoryLabelPlate.colour(InventoryLabelPlate.LABEL_X,
                InventoryLabelPlate.LABEL_Y - 1));
        assertTrue(InventoryLabelPlate.Y1 < 139 - 1);
        assertTrue(InventoryLabelPlate.X0 < InventoryLabelPlate.LABEL_X - 1);
        // the mod's colours: its button border and outline, its text cream, its roulette chips' insert
        assertEquals(0xFFB5914B, InventoryLabelPlate.BORDER);
        assertEquals(0xFFF3E7BF, InventoryLabelPlate.FILL);
        assertEquals(ChipPack.INSERT, InventoryLabelPlate.SHADE);
    }

    /** The built pack: each felt title's background glyphs, put together at their layout positions, show the plate. */
    @Test
    void thePlateIsInEveryBuiltTitle() throws IOException {
        PackTool.Result result = PackTool.build(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"), Path.of(System.getProperty("kaboofcasino.packSrc")));
        Map<String, byte[]> zip = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(result.zip()))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                zip.put(e.getName(), in.readAllBytes());
            }
        }
        JsonObject glyphs = Json.parse(result.layout()).getAsJsonObject("glyphs");
        for (String prefix : List.of("slot", "wheel", "plinko", "roulette", "bj_bet", "bj_round", "pk_bet", "pk_round",
                "derby", "cashier")) {
            // the label area in window coordinates, filled from the title's background tiles
            int w = InventoryLabelPlate.X1 - InventoryLabelPlate.X0 + 1;
            int h = InventoryLabelPlate.Y1 - InventoryLabelPlate.Y0 + 1;
            BufferedImage area = Images.blank(w, h);
            int tiles = 0;
            for (String name : glyphs.keySet()) {
                if (!name.startsWith(prefix + "_bg_")) {
                    continue;
                }
                tiles++;
                JsonObject g = glyphs.getAsJsonObject(name);
                BufferedImage tile = Images.read(zip.get("assets/kaboofcasino/textures/font/" + prefix + "/bg_"
                        + name.substring(prefix.length() + 4) + ".png"));
                for (int y = 0; y < tile.getHeight(); y++) {
                    for (int x = 0; x < tile.getWidth(); x++) {
                        int wx = g.get("x").getAsInt() + x - InventoryLabelPlate.X0;
                        int wy = g.get("y").getAsInt() + y - InventoryLabelPlate.Y0;
                        if (wx >= 0 && wy >= 0 && wx < w && wy < h) {
                            area.setRGB(wx, wy, tile.getRGB(x, y));
                        }
                    }
                }
            }
            assertTrue(tiles >= 2, prefix + ": " + tiles + " tiles");
            assertPlate(prefix, area, InventoryLabelPlate.X0, InventoryLabelPlate.Y0);
        }
    }
}
