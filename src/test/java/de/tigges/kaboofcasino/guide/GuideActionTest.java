package de.tigges.kaboofcasino.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

/** The click payload: what the dialogs send parses back; anything a client could forge instead is refused. */
class GuideActionTest {

    private static final Pattern ENTRY = Pattern.compile("(\\w+):\"([^\"]*)\"");

    /** Reads our SNBT like Paper's DialogResponseView does for string entries. */
    private static Optional<GuideAction> parse(String snbt, int pages) {
        Map<String, String> values = new HashMap<>();
        Matcher m = ENTRY.matcher(snbt);
        while (m.find()) {
            values.put(m.group(1), m.group(2));
        }
        return GuideAction.parse(values::get, pages);
    }

    private static Optional<GuideAction> parse(Map<String, String> values) {
        return GuideAction.parse(values::get, 34);
    }

    @Test
    void everyActionRoundTrips() {
        for (GuideAction a : List.of(GuideAction.turn(1), GuideAction.turn(34), GuideAction.link(12),
                GuideAction.tab(33), GuideAction.back(), GuideAction.close())) {
            assertEquals(Optional.of(a), parse(a.snbt(), 34), a.snbt());
        }
        assertEquals("{do:\"link\",to:\"12\"}", GuideAction.link(12).snbt());
        assertEquals("{do:\"close\"}", GuideAction.close().snbt());
        assertEquals("kaboofcasino:guide", GuideAction.ID);
        ClickEvent<?> click = GuideAction.tab(23).click();
        assertTrue(click.payload() instanceof ClickEvent.Payload.Custom custom
                && custom.key().asString().equals(GuideAction.ID)
                && custom.nbt().string().equals("{do:\"tab\",to:\"23\"}"));
    }

    @Test
    void forgedPayloadsAreRefused() {
        assertEquals(Optional.empty(), parse(Map.of()));
        assertEquals(Optional.empty(), parse(Map.of("do", "run", "to", "3")));
        assertEquals(Optional.empty(), parse(Map.of("do", "TURN", "to", "3")));
        assertEquals(Optional.empty(), parse(Map.of("do", "turn")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "0")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "35")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "-1")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "+3")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "3.0")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "")));
        assertEquals(Optional.empty(), parse(Map.of("do", "link", "to", "99999999999999999999")));
        assertEquals(Optional.empty(), parse(Map.of("do", "tab", "to", "/op me")));
        assertEquals(Optional.empty(), parse(Map.of("do", "back", "to", "3")));
        assertEquals(Optional.empty(), parse(Map.of("do", "close", "to", "1")));
        assertEquals(Optional.empty(), parse(Map.of("do", "x".repeat(40_000))));
        assertEquals(Optional.of(GuideAction.link(7)), parse(Map.of("do", "link", "to", "007")));
        assertEquals(Optional.of(GuideAction.back()), parse(Map.of("do", "back", "id", "ignored")));
    }

    @Test
    void actionsCheckTheirOwnPage() {
        assertThrows(IllegalArgumentException.class, () -> GuideAction.turn(0));
        assertThrows(IllegalArgumentException.class, () -> new GuideAction(GuideAction.Kind.BACK, 3));
        assertThrows(IllegalArgumentException.class, () -> new GuideAction(GuideAction.Kind.CLOSE, -1));
    }
}
