package de.tigges.kaboofcasino.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tigges.kaboofcasino.admin.AdminClick.Form;
import de.tigges.kaboofcasino.admin.AdminClick.Kind;
import de.tigges.kaboofcasino.admin.AdminClick.MachineInputs;
import de.tigges.kaboofcasino.admin.AdminClick.WorldInputs;
import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The click payload of the croupier's-hand dialogs: what the dialogs send parses back; anything a client could forge
 * instead (unknown or foreign actions, missing, mistyped or out-of-range inputs, fractional sliders, names with codes)
 * is refused, and a "Speichern" without valid inputs does nothing.
 */
class AdminClickTest {

    /** A response as Paper's DialogResponseView hands it out (strings, floats, booleans by key). */
    private static final class Response {
        final Map<String, Object> values = new HashMap<>();

        Response put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        Response remove(String key) {
            values.remove(key);
            return this;
        }

        Optional<AdminClick> parse(Form form) {
            return AdminClick.parse(form,
                    k -> values.get(k) instanceof String s ? s : null,
                    k -> values.get(k) instanceof Float f ? f : null,
                    k -> values.get(k) instanceof Boolean b ? b : null);
        }
    }

    private static Response machine(String action) {
        return new Response().put("do", action).put("name", "Lucky").put("like_casino", false).put("value", 30f);
    }

    private static Response world(String action) {
        Response r = new Response().put("do", action).put("breakable", true);
        for (MachineType t : MachineSettings.MOD_ORDER) {
            if (MachineSettings.usesBias(t)) {
                r.put(AdminClick.biasKey(t), 0f);
            } else {
                r.put(AdminClick.naturalKey(t), true).put(AdminClick.chanceKey(t), 50f);
            }
        }
        return r;
    }

    @Test
    void theDialogsClicksParse() {
        assertEquals(Optional.of(new AdminClick(Kind.SAVE, Optional.of(new MachineInputs("Lucky", false, 30)))),
                machine("save").parse(Form.CHANCE_MACHINE));
        assertEquals(new MachineInputs("", true, -100), machine("done").put("name", "  ").put("like_casino", true)
                .put("value", -100f).parse(Form.BIAS_MACHINE).orElseThrow().inputs().orElseThrow());
        for (String action : new String[] {"save", "copy", "paste", "reset", "done"}) {
            assertTrue(machine(action).parse(Form.CHANCE_MACHINE).isPresent(), action);
        }
        for (String action : new String[] {"save", "all_normal", "forget", "done"}) {
            assertTrue(world(action).parse(Form.WORLD).isPresent(), action);
        }
        WorldInputs in = (WorldInputs) world("save").put("slots_natural", false).put("slots_chance", 0f)
                .put("blackjack_bias", -100f).put("poker_bias", 100f).put("breakable", false)
                .parse(Form.WORLD).orElseThrow().inputs().orElseThrow();
        Map<MachineType, Integer> sliders = new EnumMap<>(MachineType.class);
        for (MachineType t : MachineSettings.MOD_ORDER) {
            sliders.put(t, MachineSettings.usesBias(t) ? 0 : 50);
        }
        sliders.put(MachineType.SLOT_MACHINE, 0);
        sliders.put(MachineType.BLACKJACK_TABLE, -100);
        sliders.put(MachineType.POKER_TABLE, 100);
        assertEquals(new WorldInputs(sliders, EnumSet.of(MachineType.ROULETTE_TABLE, MachineType.DUCK_DERBY,
                MachineType.FORTUNE_WHEEL, MachineType.PLINKO), false), in);
        assertEquals("{do:\"all_normal\"}", Kind.ALL_NORMAL.snbt());
        assertEquals("kaboofcasino:croupier_hand", AdminClick.ID);
    }

    @Test
    void forgedActionsAreRefused() {
        assertEquals(Optional.empty(), new Response().parse(Form.WORLD));
        assertEquals(Optional.empty(), machine("SAVE").parse(Form.CHANCE_MACHINE));
        assertEquals(Optional.empty(), machine("run").parse(Form.CHANCE_MACHINE));
        assertEquals(Optional.empty(), machine("open").parse(Form.CHANCE_MACHINE));
        assertEquals(Optional.empty(), machine("rename").parse(Form.CHANCE_MACHINE));
        // actions of the other dialog
        assertEquals(Optional.empty(), machine("all_normal").parse(Form.CHANCE_MACHINE));
        assertEquals(Optional.empty(), machine("forget").parse(Form.BIAS_MACHINE));
        assertEquals(Optional.empty(), world("copy").parse(Form.WORLD));
        assertEquals(Optional.empty(), world("paste").parse(Form.WORLD));
        assertEquals(Optional.empty(), world("reset").parse(Form.WORLD));
        assertEquals(Optional.empty(), new Response().put("do", 1f).parse(Form.WORLD));
    }

    @Test
    void invalidInputsMakeASaveDoNothing() {
        Response[] bad = {
                machine("save").remove("name"),
                machine("save").put("name", "§4Rot"),
                machine("save").put("name", "a\nb"),
                machine("save").put("name", "x".repeat(65)),
                machine("save").put("name", 5f),
                machine("save").remove("like_casino"),
                machine("save").put("like_casino", "true"),
                machine("save").remove("value"),
                machine("save").put("value", 30.5f),
                machine("save").put("value", -1f),
                machine("save").put("value", 101f),
                machine("save").put("value", Float.NaN),
                machine("save").put("value", Float.POSITIVE_INFINITY),
                machine("save").put("value", "30"),
        };
        for (Response r : bad) {
            assertEquals(Optional.empty(), r.parse(Form.CHANCE_MACHINE), r.values.toString());
        }
        // the bias range is -100..100, the chance range 0..100
        assertTrue(machine("save").put("value", -1f).parse(Form.BIAS_MACHINE).isPresent());
        assertEquals(Optional.empty(), machine("save").put("value", -101f).parse(Form.BIAS_MACHINE));
        Response[] badWorld = {
                world("save").remove("breakable"),
                world("save").put("breakable", 1f),
                world("save").remove("roulette_natural"),
                world("save").put("derby_chance", 100.25f),
                world("save").put("wheel_chance", -1f),
                world("save").put("plinko_chance", 101f),
                world("save").put("blackjack_bias", 101f),
                world("save").remove("poker_bias"),
                world("save").put("poker_bias", true),
        };
        for (Response r : badWorld) {
            assertEquals(Optional.empty(), r.parse(Form.WORLD), r.values.toString());
        }
        // other actions keep working without inputs; their inputs are just not applied
        Optional<AdminClick> done = machine("done").put("value", 500f).parse(Form.CHANCE_MACHINE);
        assertEquals(Optional.of(new AdminClick(Kind.DONE, Optional.empty())), done);
        assertEquals(Optional.of(new AdminClick(Kind.PASTE, Optional.empty())), new Response().put("do", "paste")
                .parse(Form.CHANCE_MACHINE));
        assertEquals(Optional.of(new AdminClick(Kind.ALL_NORMAL, Optional.empty())), new Response()
                .put("do", "all_normal").parse(Form.WORLD));
    }

    @Test
    void slidersOnlyTakeWholeNumbers() {
        assertEquals(Integer.valueOf(0), AdminClick.whole(0f, 0, 100));
        assertEquals(Integer.valueOf(-100), AdminClick.whole(-100f, -100, 100));
        assertEquals(null, AdminClick.whole(-0.0001f, 0, 100));
        assertEquals(null, AdminClick.whole(null, 0, 100));
        assertEquals(null, AdminClick.whole(Float.NaN, 0, 100));
    }
}
