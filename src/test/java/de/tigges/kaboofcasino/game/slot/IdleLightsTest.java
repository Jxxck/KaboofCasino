package de.tigges.kaboofcasino.game.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdleLightsTest {

    @Test
    void bulbsStayInTheModsRange() {
        for (double t = 0; t < 960; t += 0.5) {
            for (int i = 0; i < 15; i++) {
                double b = IdleLights.bulb(i, 15, t);
                assertTrue(b >= 0.25 - 1e-9 && b <= 1.0 + 1e-9, "bulb " + b);
            }
            double beacon = IdleLights.beacon(t);
            assertTrue(beacon >= 0.62 - 1e-9 && beacon <= 1.0 + 1e-9);
        }
        assertEquals(0.4, IdleLights.afterGame(0.4, 1.0, 0), 1e-12);
        assertEquals(1.0, IdleLights.afterGame(0.4, 1.0, 14), 1e-12);
        assertEquals(480 - 1 + 0.0, IdleLights.clock(-1, 0, 0, 0) + 0.0, 1e-9 + 480);
    }

    @Test
    void storedStopsParse() {
        assertEquals(3, SlotController.parseStops("stops=3,4,5")[0]);
        assertEquals(5, SlotController.parseStops("stops=3,4,13")[2]);
        assertEquals(0, SlotController.parseStops("")[1]);
        assertEquals(0, SlotController.parseStops("stops=x,1")[0]);
    }
}
