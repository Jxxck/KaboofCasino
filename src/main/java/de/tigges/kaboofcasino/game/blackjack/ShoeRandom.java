package de.tigges.kaboofcasino.game.blackjack;

import java.util.Random;

/**
 * {@link java.util.Random}'s exact 48-bit LCG (same seed, same numbers; {@code ShoeRandomTest}) with a copyable state,
 * so {@link BlackjackShoe#copy()} can predict the cards the shoe deals next, also with a bias (whose draws use the
 * shoe's random). The mod builds its shoe with {@code new Random(server.getRandom().nextLong())}
 * ({@code BlackjackTableBlockEntity} 355-357); the plugin seeds this one from the casino generator.
 */
public final class ShoeRandom extends Random {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    /** Set by {@link #setSeed}, which {@code Random}'s constructor calls before this class's fields initialise. */
    private long state;

    public ShoeRandom(long seed) {
        super(seed);
    }

    private ShoeRandom(ShoeRandom from) {
        super(0L);
        this.state = from.state;
    }

    @Override
    public synchronized void setSeed(long seed) {
        state = (seed ^ MULTIPLIER) & MASK;
    }

    @Override
    protected int next(int bits) {
        state = (state * MULTIPLIER + ADDEND) & MASK;
        return (int) (state >>> (48 - bits));
    }

    /** An independent generator in the same state. */
    public ShoeRandom copy() {
        return new ShoeRandom(this);
    }
}
