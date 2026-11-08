package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * "Kopieren" and "Einfügen": the hand's payload (source and token, never the values) and the server's clipboard
 * (token -> win chance and bias, at most 256, saved with the world).
 */
class CopyPasteTest {

    private static final UUID TOKEN = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void theHandPayloadRoundTrips() {
        HandCopy copy = new HandCopy(MachineType.SLOT_MACHINE, TOKEN, -12, 1000, 7, 3, "Lucky | 7 € §?".replace("§", ""));
        assertEquals("1|slot_machine|12345678-1234-1234-1234-123456789abc|-12|1000|7|3|Lucky | 7 € ?", copy.encode());
        assertEquals(Optional.of(copy), HandCopy.decode(copy.encode()));
        HandCopy unnamed = new HandCopy(MachineType.POKER_TABLE, TOKEN, 0, -64, 0, 0, "");
        assertEquals(Optional.of(unnamed), HandCopy.decode(unnamed.encode()));
    }

    @Test
    void foreignOrForgedHandDataIsNoCopy() {
        String good = new HandCopy(MachineType.PLINKO, TOKEN, 1, 2, 3, 4, "P").encode();
        assertTrue(HandCopy.decode(good).isPresent());
        for (String bad : new String[] {null, "", "x", good.replaceFirst("^1", "2"),
                good.replace("plinko", "roulette"), good.replace(TOKEN.toString(), "not-a-uuid"),
                good.replace(TOKEN.toString(), "12345678-1234-1234-1234-123456789ABC"),
                good.replace("|4|P", "|-1|P"), good.replace("|1|2|3|", "|1|2.5|3|"),
                good.replace("|P", "|§cP"), good.replace("|P", "| P"), good.replace("|P", "|" + "x".repeat(65)),
                "1|plinko|" + TOKEN + "|1|2|3|4", "x".repeat(600)}) {
            assertEquals(Optional.empty(), HandCopy.decode(bad), String.valueOf(bad));
        }
        assertThrows(IllegalArgumentException.class, () -> new HandCopy(MachineType.PLINKO, TOKEN, 0, 0, 0, -1, ""));
    }

    @Test
    void theClipboardKeepsTheNewest256AndRoundTrips() {
        Clipboard board = new Clipboard();
        UUID first = new UUID(0, 0);
        for (int i = 0; i < Clipboard.MAX + 10; i++) {
            board.put(new UUID(0, i), new Clipboard.Copied(i % 101, i % 2 == 0 ? MachineSettings.INHERIT_BIAS : -i % 100));
        }
        assertEquals(Clipboard.MAX, board.size());
        assertEquals(Optional.empty(), board.get(first), "the oldest token goes first");
        assertEquals(Optional.of(new Clipboard.Copied(265 % 101, -265 % 100)), board.get(new UUID(0, 265)));
        Clipboard back = Clipboard.decode(board.encode());
        assertEquals(board.encode(), back.encode());
        assertEquals(Clipboard.MAX, back.size());
    }

    @Test
    void theClipboardSkipsBrokenEntries() {
        Clipboard board = Clipboard.decode(TOKEN + "=30,i;garbage;" + new UUID(1, 1) + "=101,0;"
                + new UUID(1, 2) + "=-1,-101;" + new UUID(1, 3) + "=-1,-100;=5,5;" + new UUID(1, 4) + "=x,1");
        assertEquals(2, board.size());
        assertEquals(Optional.of(new Clipboard.Copied(30, MachineSettings.INHERIT_BIAS)), board.get(TOKEN));
        assertEquals(Optional.of(new Clipboard.Copied(-1, -100)), board.get(new UUID(1, 3)));
        assertEquals(0, Clipboard.decode(null).size());
        assertEquals(0, Clipboard.decode("").size());
        // a lost token pastes "follow the type" (CasinoSettings: getOrDefault(token, Copied(-1, MIN_VALUE)))
        assertEquals(new Clipboard.Copied(-1, MachineSettings.INHERIT_BIAS), Clipboard.Copied.NONE);
    }
}
