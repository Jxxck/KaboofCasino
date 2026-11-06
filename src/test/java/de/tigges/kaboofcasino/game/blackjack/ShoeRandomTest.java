package de.tigges.kaboofcasino.game.blackjack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** {@link ShoeRandom} is {@link Random} with a copyable state; {@link BlackjackShoe#copy} deals the same cards. */
class ShoeRandomTest {

    @Test
    void sameNumbersAsJavaUtilRandom() {
        for (long seed : new long[] {0, 1, 7, -1, 123456789L, Long.MIN_VALUE, 0x5DEECE66DL}) {
            Random a = new Random(seed);
            ShoeRandom b = new ShoeRandom(seed);
            for (int i = 0; i < 2000; i++) {
                int bound = 1 + (i * 37) % 400;
                assertEquals(a.nextInt(bound), b.nextInt(bound));
                assertEquals(a.nextLong(), b.nextLong());
                assertEquals(a.nextDouble(), b.nextDouble());
                assertEquals(a.nextInt(), b.nextInt());
            }
        }
    }

    @Test
    void theModsShuffleIsReproduced() {
        BlackjackShoe mod = new BlackjackShoe(new Random(42));
        BlackjackShoe ours = new BlackjackShoe(new ShoeRandom(42));
        for (int i = 0; i < 312 * 3; i++) {
            assertEquals(mod.draw(), ours.draw());
        }
    }

    @Test
    void aCopyDealsTheSameCardsAndLeavesTheOriginal() {
        BlackjackShoe shoe = new BlackjackShoe(new ShoeRandom(99));
        for (int i = 0; i < 100; i++) {
            shoe.draw();
        }
        BlackjackShoe copy = shoe.copy();
        int[] predicted = new int[400];
        for (int i = 0; i < predicted.length; i++) {
            predicted[i] = i % 3 == 0 ? copy.drawFavouring(40, c -> c % 13) : copy.draw();
        }
        for (int i = 0; i < predicted.length; i++) {
            assertEquals(predicted[i], i % 3 == 0 ? shoe.drawFavouring(40, c -> c % 13) : shoe.draw(), "card " + i);
        }
    }
}
