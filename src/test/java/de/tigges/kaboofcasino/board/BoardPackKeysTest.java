package de.tigges.kaboofcasino.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.pack.PackFiles;
import org.junit.jupiter.api.Test;

/** The pack has every model the boards show and both items' models; the text areas sit where the art left felt. */
class BoardPackKeysTest {

    @Test
    void thePackHasWhatTheBoardsUse() {
        for (String key : BoardRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
        assertEquals(3 + 7, BoardRig.modelKeys().size());
        for (CasinoItem item : CasinoItem.values()) {
            if (item.machine().filter(MachineType::isBoard).isPresent()) {
                assertTrue(PackFiles.hasItemModel(item.model()), item.model());
            }
        }
        assertTrue(PackFiles.hasItemModel(CasinoItem.LEADERBOARD_WALL.model()));
        assertTrue(PackFiles.hasItemModel(CasinoItem.LEADERBOARD_COLUMN.model()));
    }

    /** The client steps 10 font pixels per line, 1/40 block each; title, subtitle and rows must not touch. */
    @Test
    void theLinesNeverOverlapOnAnyBoard() {
        assertEquals(0.25, BoardRig.LINE, 1e-9);
        for (MachineType type : MachineType.values()) {
            if (!type.isBoard()) {
                continue;
            }
            BoardRig.Layout l = BoardRig.layout(type);
            assertTrue(l != null, type.name());
            double rowsBottom = l.rowsTop() - (BoardRig.ROWS + 1) * BoardRig.LINE * l.rowScale();
            double rowTop = rowsBottom + (BoardRig.ROWS + 1) * BoardRig.LINE * l.rowScale();
            assertTrue(rowTop <= l.subtitleY() + 1e-9, type + ": the first row stays under the subtitle");
            assertTrue(l.subtitleY() + BoardRig.LINE * l.subtitleScale() <= l.titleY() + 1e-5, type.name());
            assertTrue((l.leftPx() + l.rightPx()) * BoardRig.PX * l.rowScale() <= 2 * l.halfWidth() + 0.02, type.name());
            assertTrue(l.namePx() + BoardView.width("10. ") <= l.leftPx(), type + ": a name fits its column");
            if (type.onWall()) {
                double top = type.wallRows() - 7 / 16.0;
                assertTrue(l.titleY() + BoardRig.LINE * l.titleScale() <= top + 1e-5, type + ": under the header");
                assertTrue(rowsBottom + BoardRig.LINE * l.rowScale() >= 2 / 16.0 - 1e-5, type + ": row 10 on the felt");
                assertTrue(l.halfWidth() <= type.wallColumns() / 2.0 - 2 / 16.0 + 1e-9, type.name());
                double left = l.centerX() - type.wallColumns() / 2.0;
                assertEquals(-(type.wallColumns() - 1) / 2 - 0.5, left, 1e-9, type + ": over its cells");
                // the culling box reaches the far edge from the anchor
                assertTrue(l.width() / 2 >= l.centerX() + type.wallColumns() / 2.0, type.name());
            }
        }
    }

    @Test
    void theTextStaysOnTheFelt() {
        // wall: felt x -14..30 px around the middle cell (-1.375..1.375 blocks), y 2..25 px, face at z 2 px (-0.375)
        BoardRig.Layout w = BoardRig.WALL;
        assertTrue(w.halfWidth() <= 1.375);
        assertTrue(w.z() > -0.375 && w.z() < -0.375 + 0.5 / 16, "just in front of the felt");
        assertTrue(w.titleY() + BoardRig.LINE * w.titleScale() <= 25 / 16.0 + 1e-9, "the title stays under the header");
        double rowsBottom = w.rowsTop() - (BoardRig.ROWS + 1) * BoardRig.LINE * w.rowScale();
        assertTrue(rowsBottom + BoardRig.LINE * w.rowScale() >= 2 / 16.0, "row 10 stays on the felt");
        assertTrue((w.leftPx() + w.rightPx()) * BoardRig.PX * w.rowScale() <= 2 * w.halfWidth() + 1e-9);
        // column: board origin 2.0 above the anchor, felt x -2..18 px (-0.625..0.625), y -6..17 px around it
        BoardRig.Layout c = BoardRig.COLUMN;
        assertTrue(c.halfWidth() <= 0.625);
        assertTrue(c.z() > 0.125 && c.z() < 0.125 + 0.5 / 16);
        assertTrue(c.titleY() + BoardRig.LINE * c.titleScale() <= 2.0 + 9 / 16.0 + 1e-9);
        double colBottom = c.rowsTop() - (BoardRig.ROWS + 1) * BoardRig.LINE * c.rowScale();
        assertTrue(colBottom + BoardRig.LINE * c.rowScale() >= 2.0 - 14 / 16.0);
        assertTrue((c.leftPx() + c.rightPx()) * BoardRig.PX * c.rowScale() <= 2 * c.halfWidth() + 0.02);
        // the subtitle line stays between the title and the rows
        assertTrue(w.subtitleY() + BoardRig.LINE * w.subtitleScale() <= w.titleY() + 1e-5);
        assertTrue(w.rowsTop() <= w.subtitleY() + 1e-9);
        assertTrue(c.subtitleY() + BoardRig.LINE * c.subtitleScale() <= c.titleY() + 1e-5);
        assertTrue(c.rowsTop() <= c.subtitleY() + 1e-9);
    }
}
