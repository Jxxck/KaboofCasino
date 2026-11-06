package de.tigges.kaboofcasino.game;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * The casino's own generator (server.md 1.2 "RNG"): L64X128MixRandom seeded from SecureRandom, never the world
 * random (players could influence the shared level RNG). Seeds never leave the server. Thread: MAIN.
 */
public final class CasinoRandom {

    private final RandomGenerator random;

    public CasinoRandom() {
        this(new SecureRandom().nextLong());
    }

    public CasinoRandom(long seed) {
        this.random = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public long nextLong() {
        return random.nextLong();
    }

    public float nextFloat() {
        return random.nextFloat();
    }

    public double nextDouble() {
        return random.nextDouble();
    }
}
