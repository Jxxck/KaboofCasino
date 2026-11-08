package de.tigges.kaboofcasino.packtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/**
 * The croupier's pack part and the mod data the plugin's croupier copies: its one trade (the plugin's
 * {@code CroupierTrade} constants: 24 emeralds, 1 hiring card, 64 uses, 2 xp, price multiplier 0), the hiring card's
 * item definition and the texts the croupier shows.
 */
class CroupierPackTest {

    private static String modText(String path) {
        try (ZipFile zip = new ZipFile(Path.of(System.getProperty("kaboofcasino.modJar")).toFile())) {
            ZipEntry e = zip.getEntry(path);
            assertTrue(e != null, path);
            try (InputStream in = zip.getInputStream(e)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void theModsCroupierTradeIsTheOneThePluginSells() {
        JsonObject trade = Json.parse(modText("data/roulettecasino/villager_trade/croupier/1/hiring_card.json")
                .getBytes(StandardCharsets.UTF_8));
        JsonObject wants = trade.getAsJsonObject("wants");
        assertEquals("minecraft:emerald", wants.get("id").getAsString());
        assertEquals(24, wants.get("count").getAsInt());
        JsonObject gives = trade.getAsJsonObject("gives");
        assertEquals("roulettecasino:croupier_hiring_card", gives.get("id").getAsString());
        assertFalse(gives.has("count")); // 1
        assertFalse(trade.has("additional_wants"));
        assertEquals(64, trade.get("max_uses").getAsInt());
        assertEquals(2, trade.get("xp").getAsInt());
        assertEquals(0.0, trade.get("reputation_discount").getAsDouble());
        // the level-1 set holds exactly this trade; no other level has one
        JsonObject set = Json.parse(modText("data/roulettecasino/trade_set/croupier/level_1.json")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(1, set.get("amount").getAsInt());
        JsonArray tag = Json.parse(modText("data/roulettecasino/tags/villager_trade/croupier/level_1.json")
                .getBytes(StandardCharsets.UTF_8)).getAsJsonArray("values");
        assertEquals(1, tag.size());
        assertEquals("roulettecasino:croupier/1/hiring_card", tag.get(0).getAsString());
    }

    @Test
    void theHiringCardAndTheCroupiersTexts() {
        ModJar jar = ModJar.open(Path.of(System.getProperty("kaboofcasino.modJar")),
                System.getProperty("kaboofcasino.modJarSha1"));
        PackTree tree = new PackTree();
        CroupierPack.add(jar, tree, Path.of(System.getProperty("kaboofcasino.packSrc")));
        assertTrue(tree.has("assets/roulettecasino/items/croupier_hiring_card.json"));
        assertTrue(CroupierPack.LANG_PREFIXES.contains("entity.roulettecasino.villager.croupier="));
        assertTrue(CroupierPack.LANG_PREFIXES.contains("message.roulettecasino.croupier_busy="));
        JsonObject de = Json.parse(jar.bytes("assets/roulettecasino/lang/de_de.json"));
        assertEquals("Croupier", de.get("entity.roulettecasino.villager.croupier").getAsString());
        assertEquals("Dieser Croupier ist beschäftigt.", de.get("message.roulettecasino.croupier_busy").getAsString());
        // the croupier's other needs are shipped by the blackjack part: the dealer model (it wears the mod's croupier
        // profession texture), the badge and the card_flip sound of the hiring stream
        assertTrue(BlackjackPack.SOUND_EVENTS.contains("card_flip"));
        assertTrue(BlackjackPack.LANG_PREFIXES.stream()
                .anyMatch(p -> p.startsWith("item.roulettecasino.croupier_badge")),
                BlackjackPack.LANG_PREFIXES.toString());
    }
}
