package de.tigges.kaboofcasino.chipshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.board.BoardView;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.pack.PackFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pack has every model the Chip-Shop shows; its menu texts fit the side panel like the cashier's. */
class ChipShopPackKeysTest {

    @Test
    void thePackHasWhatTheShopUses() {
        for (String key : ChipShopRig.modelKeys()) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
        assertEquals(5, ChipShopRig.modelKeys().size());
        for (String key : List.of(CasinoItem.CHIP_SHOP.model(), "kaboofcasino:gui/chipshop/qty_one",
                "kaboofcasino:gui/chipshop/qty_stack", "kaboofcasino:gui/cashier/buy_on",
                "kaboofcasino:gui/cashier/buy_off", "kaboofcasino:gui/cashier/prev", "kaboofcasino:gui/cashier/next",
                "kaboofcasino:gui/cashier/num_a", "kaboofcasino:gui/cashier/num_b", ChipShopMenu.BLANK)) {
            assertTrue(PackFiles.hasItemModel(key), key);
        }
    }

    @Test
    void theHelpLinesFitThePanel(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("messages_de.yml");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("messages_de.yml")) {
            Files.write(file, in.readAllBytes());
        }
        Messages m = Messages.load(file, () -> getClass().getClassLoader().getResourceAsStream("messages_de.yml"))
                .messages();
        String[] help = m.raw(Msg.CHIPSHOP_HELP).split("\n", -1);
        assertTrue(help.length <= 5);
        for (String line : help) {
            assertTrue(BoardView.width(line.strip()) <= 88, line);
        }
        for (ChipShopCategory c : ChipShopCategory.values()) {
            assertTrue(BoardView.width(m.raw(ChipShopMenu.categoryMsg(c))) <= 88, c.name());
        }
    }
}
