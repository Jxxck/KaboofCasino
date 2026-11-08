package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.board.BoardCategory;
import de.tigges.kaboofcasino.board.BoardPeriod;
import de.tigges.kaboofcasino.board.BoardPin;
import de.tigges.kaboofcasino.config.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The croupier's hand's pin dialog for a leaderboard: buttons, the current marks, and what each click sets. */
class BoardPinsTest {

    private static Messages messages;

    @BeforeAll
    static void load(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("messages_de.yml");
        try (InputStream in = BoardPinsTest.class.getClassLoader().getResourceAsStream("messages_de.yml")) {
            Files.write(file, in.readAllBytes());
        }
        messages = Messages.load(file, () -> BoardPinsTest.class.getClassLoader()
                .getResourceAsStream("messages_de.yml")).messages();
    }

    @Test
    void theDialogListsEveryChoiceAndMarksTheCurrentOnes() {
        BoardPins.View v = BoardPins.view(3, new BoardPin(BoardCategory.POKER, null), messages);
        assertEquals("Bestenliste #3", PlainTextComponentSerializer.plainText().serialize(v.title()));
        assertEquals(1 + BoardCategory.values().length + 1 + BoardPeriod.values().length, v.buttons().size());
        assertEquals(1, v.buttons().stream().filter(b -> b.current() && b.action().startsWith("cat:")).count());
        assertTrue(v.buttons().stream().anyMatch(b -> b.current() && b.action().equals("cat:poker")));
        assertTrue(v.buttons().stream().anyMatch(b -> b.current() && b.action().equals("period:auto")));
        assertEquals(BoardPins.DONE, v.exit().action());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(v.body().getFirst())
                .contains("Kategorie: Poker: größter Gewinn · Zeitraum: automatisch"));
    }

    @Test
    void clicksSetOrClearThePin() {
        BoardPin none = BoardPin.NONE;
        assertEquals(Optional.of(new BoardPin(BoardCategory.ROUNDS, null)), BoardPins.apply(none, "cat:runden"));
        assertEquals(Optional.of(new BoardPin(null, BoardPeriod.TOTAL)), BoardPins.apply(none, "period:gesamt"));
        BoardPin both = new BoardPin(BoardCategory.NET, BoardPeriod.WEEK);
        assertEquals(Optional.of(new BoardPin(null, BoardPeriod.WEEK)), BoardPins.apply(both, "cat:auto"));
        assertEquals(Optional.of(new BoardPin(BoardCategory.NET, null)), BoardPins.apply(both, "period:auto"));
        assertTrue(BoardPins.apply(both, "cat:bogus").isEmpty());
        assertTrue(BoardPins.apply(both, BoardPins.DONE).isEmpty());
        assertTrue(BoardPins.apply(both, null).isEmpty());
    }
}
