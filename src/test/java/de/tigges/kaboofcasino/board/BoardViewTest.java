package de.tigges.kaboofcasino.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.config.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The board texts, the pin and its pages, server-free. */
class BoardViewTest {

    private static Messages messages;

    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("messages_de.yml");
        try (InputStream in = BoardViewTest.class.getClassLoader().getResourceAsStream("messages_de.yml")) {
            Files.write(file, in.readAllBytes());
        }
        messages = Messages.load(file, () -> BoardViewTest.class.getClassLoader()
                .getResourceAsStream("messages_de.yml")).messages();
    }

    static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void valuesAndNames() {
        assertEquals("1.250", BoardView.value(BoardCategory.BIGGEST_WIN, 1250));
        assertEquals("12", BoardView.value(BoardCategory.ROUNDS, 12));
        assertEquals("×12,5", BoardView.value(BoardCategory.MULTIPLIER, 1250));
        assertEquals("×3", BoardView.value(BoardCategory.MULTIPLIER, 300));
        assertEquals("×2,05", BoardView.value(BoardCategory.MULTIPLIER, 205));
        assertEquals("×1.000", BoardView.value(BoardCategory.MULTIPLIER, 100_000));
        assertEquals("Alice", BoardView.fit("Alice", 56));
        String cut = BoardView.fit("Abcdefghijklmnop", 56);
        assertTrue(cut.endsWith("…") && BoardView.width(cut) <= 56, cut);
        for (BoardCategory c : BoardCategory.values()) {
            assertTrue(!plain(messages.render(BoardView.categoryMsg(c))).isBlank(), c.id());
        }
    }

    @Test
    void aPageHasTenRowsAndAlignedColumns() {
        List<BoardEntry> entries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            entries.add(new BoardEntry(new UUID(0, i), "Spieler" + i, 1000L * (3 - i)));
        }
        BoardView.Page p = BoardView.page(new BoardPin.Page(BoardCategory.BIGGEST_WIN, BoardPeriod.WEEK), entries, 25,
                106, 130, 80, false, messages);
        assertEquals("Größter Gewinn", plain(p.title()));
        assertEquals("Diese Woche · in Chips", plain(p.subtitle()));
        String[] names = plain(p.names()).split("\n", -1);
        String[] values = plain(p.values()).split("\n", -1);
        assertEquals(BoardRig.ROWS + 1, names.length);
        assertEquals(BoardRig.ROWS + 1, values.length);
        assertEquals("1. Spieler0", names[0]);
        assertEquals("3.000", values[0]);
        assertEquals("3. Spieler2", names[2]);
        assertEquals("", names[3]);
        // the spacer lines make each column exactly as wide as the layout says
        assertEquals(128, BoardView.width(names[BoardRig.ROWS]));
        assertEquals(80, BoardView.width(values[BoardRig.ROWS]));
        BoardView.Page empty = BoardView.page(new BoardPin.Page(BoardCategory.MULTIPLIER, BoardPeriod.TOTAL),
                List.of(), 25, 106, 130, 80, false, messages);
        assertEquals("Noch keine Einträge", plain(empty.names()).split("\n")[0]);
        assertEquals("Gesamt · ab 25 Chips Einsatz", plain(empty.subtitle()));
        BoardView.Page loading = BoardView.page(new BoardPin.Page(BoardCategory.ROUNDS, BoardPeriod.TOTAL),
                List.of(), 25, 106, 130, 80, true, messages);
        assertEquals("Wird berechnet …", plain(loading.names()).split("\n")[0]);
    }

    @Test
    void compactValuesForTheColumnAndHiddenPlayersDropOutAtOnce() {
        assertEquals("9.999", BoardView.compact(9_999));
        assertEquals("12,3 Tsd.", BoardView.compact(12_399));
        assertEquals("123 Tsd.", BoardView.compact(123_999));
        assertEquals("1,2 Mio.", BoardView.compact(1_299_999));
        assertEquals("12,3 Mio.", BoardView.compact(12_345_678));
        assertEquals("123 Mio.", BoardView.compact(123_456_789));
        assertEquals("×12,5", BoardView.value(BoardCategory.MULTIPLIER, 1250, true));
        assertEquals("12,3 Tsd.", BoardView.value(BoardCategory.BIGGEST_WIN, 12_345, true));
        // a 16-character name in the column's name column (80 px) is cut to about 13 characters
        String cut = BoardView.fit("Abcdefghijklmnop", BoardRig.COLUMN.namePx());
        assertTrue(cut.length() >= 12 && cut.length() <= 15, cut);
        List<BoardEntry> list = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            list.add(new BoardEntry(new UUID(0, i), "P" + i, 100 - i));
        }
        List<BoardEntry> shown = BoardService.visible(list, java.util.Set.of(new UUID(0, 0), new UUID(0, 3)), 10);
        assertEquals(10, shown.size());
        assertEquals("P1", shown.getFirst().name());
        assertEquals("P11", shown.getLast().name());
        assertEquals(BoardService.TOP, 10);
        assertTrue(BoardService.STORED > BoardService.TOP);
    }

    @Test
    void thePinAndItsPages() {
        assertEquals(BoardPin.NONE, BoardPin.parse(""));
        assertEquals(BoardPin.NONE, BoardPin.parse(null));
        BoardPin pin = BoardPin.parse("cat=netto;period=woche");
        assertEquals(new BoardPin(BoardCategory.NET, BoardPeriod.WEEK), pin);
        assertEquals("cat=netto;period=woche", pin.format());
        assertEquals(BoardPin.NONE, BoardPin.parse("cat=bogus;period=?;x"));
        assertEquals(BoardCategory.values().length * 2, BoardPin.NONE.pages().size());
        assertEquals(new BoardPin.Page(BoardCategory.BIGGEST_WIN, BoardPeriod.WEEK), BoardPin.NONE.pages().get(0));
        assertEquals(new BoardPin.Page(BoardCategory.BIGGEST_WIN, BoardPeriod.TOTAL), BoardPin.NONE.pages().get(1));
        assertEquals(1, pin.pages().size());
        assertEquals(2, new BoardPin(BoardCategory.NET, null).pages().size());
        assertEquals(BoardCategory.values().length, new BoardPin(null, BoardPeriod.TOTAL).pages().size());
        // right clicks: next category; category pinned: other period; both pinned: nothing
        BoardPin.Page here = new BoardPin.Page(BoardCategory.NET, BoardPeriod.WEEK);
        assertEquals(new BoardPin.Page(BoardCategory.STAKED, BoardPeriod.WEEK), BoardPin.NONE.clicked(here).get());
        assertEquals(new BoardPin.Page(BoardCategory.NET, BoardPeriod.TOTAL),
                new BoardPin(BoardCategory.NET, null).clicked(here).get());
        assertTrue(pin.clicked(here).isEmpty());
        assertEquals(new BoardPin.Page(BoardCategory.POKER, BoardPeriod.WEEK),
                new BoardPin(BoardCategory.POKER, null).fit(here));
    }
}
