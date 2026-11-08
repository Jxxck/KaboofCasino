package de.tigges.kaboofcasino.croupier;

import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * The mod's {@code CroupierHiring} (games.md 10.3): which hand holds the card, what happens to the card and the badge,
 * the burst of particles and the two sounds where the croupier stood, and the 14-tick stream of particles to the
 * player's hands with its end burst and sounds. The pure parts ({@link #cardHand}, {@link #outcome},
 * {@link #streamPoint}) are verbatim; the rest sends the same particles and sounds through Bukkit. Thread: MAIN.
 */
public final class CroupierHiring {

    /** {@code STREAM_TICKS}. */
    static final int STREAM_TICKS = 14;
    static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(14629707), 1.15F);
    static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(16765288), 1.25F);
    static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(3594374), 1.15F);

    /** A hand. */
    public enum Hand {
        MAIN, OFF
    }

    /** What happens to the card and where the badge goes. */
    public record Outcome(boolean consumeCard, boolean badgeIntoHand) {
    }

    private CroupierHiring() {
    }

    /**
     * {@code cardHand}: the clicked hand if it holds the card; else, for a main-hand click with an empty main hand and
     * the card in the off hand, the off hand; else none (null: the click trades).
     */
    public static Hand cardHand(Hand clicked, boolean mainIsCard, boolean mainEmpty, boolean offIsCard) {
        if (clicked == Hand.MAIN ? mainIsCard : offIsCard) {
            return clicked;
        }
        return clicked == Hand.MAIN && mainEmpty && offIsCard ? Hand.OFF : null;
    }

    /**
     * {@code hire}: in creative the card stays and the badge goes to the inventory (dropped at the player if full); in
     * survival (and adventure) one card is used, and the badge goes into that hand if the stack is now empty, else
     * into the inventory (or dropped at the player).
     */
    public static Outcome outcome(boolean creative, int cardsInHand) {
        if (creative) {
            return new Outcome(false, false);
        }
        return new Outcome(true, cardsInHand - 1 <= 0);
    }

    /**
     * The stream's k-th particle (k 0..3) of tick {@code age}: the eased line from the croupier's centre to the hands,
     * lifted by an arc of 0.9 and swirled with a radius shrinking from 0.22 ({@code tickStreams}).
     */
    public static double[] streamPoint(double[] from, double[] hands, int age, int k) {
        double t0 = (double) age / STREAM_TICKS;
        double t1 = (double) (age + 1) / STREAM_TICKS;
        double t = t0 + (double) k / 4.0 * (t1 - t0);
        double eased = t * t * (3.0 - 2.0 * t);
        double x = from[0] + (hands[0] - from[0]) * eased;
        double y = from[1] + (hands[1] - from[1]) * eased + Math.sin(t * Math.PI) * 0.9;
        double z = from[2] + (hands[2] - from[2]) * eased;
        double swirl = t * 9.0 + (double) k * 1.57;
        x += Math.cos(swirl) * 0.22 * (1.0 - t);
        z += Math.sin(swirl) * 0.22 * (1.0 - t);
        return new double[] {x, y, z};
    }

    /** The particle of the stream's k-th point: end rod, gold confetti, happy villager, green confetti. */
    static void streamParticle(World world, int k, double x, double y, double z) {
        switch (k) {
            case 0 -> world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            case 1 -> world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, GOLD_CONFETTI);
            case 2 -> world.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            default -> world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, GREEN_CONFETTI);
        }
    }

    /** {@code burst} at the croupier's centre and the two sounds of {@code hire}. */
    static void burst(World world, Location at) {
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, x, y, z, 34, 0.3, 0.45, 0.3, 0.45);
        world.spawnParticle(Particle.POOF, x, y - 0.2, z, 12, 0.25, 0.4, 0.25, 0.04);
        world.spawnParticle(Particle.DUST, x, y, z, 14, 0.4, 0.55, 0.4, 0.2, RED_CONFETTI);
        world.spawnParticle(Particle.DUST, x, y, z, 16, 0.4, 0.55, 0.4, 0.2, GOLD_CONFETTI);
        world.spawnParticle(Particle.DUST, x, y, z, 14, 0.4, 0.55, 0.4, 0.2, GREEN_CONFETTI);
        world.spawnParticle(Particle.END_ROD, x, y, z, 14, 0.3, 0.5, 0.3, 0.12);
        world.playSound(at, Sound.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        world.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.7F, 1.2F);
    }

    /** The player's "hands": eye + look x 0.55 + (0, -0.45, 0). */
    static double[] hands(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        return new double[] {eye.getX() + look.getX() * 0.55, eye.getY() + look.getY() * 0.55 - 0.45,
            eye.getZ() + look.getZ() * 0.55};
    }

    /** One running stream (the mod's {@code Stream}). */
    static final class Stream {
        private final World world;
        private final UUID player;
        private final double[] from;
        private int age;

        Stream(World world, UUID player, Location from) {
            this.world = world;
            this.player = player;
            this.from = new double[] {from.getX(), from.getY(), from.getZ()};
        }

        /** One server tick; false when the stream is over (finished, or the player left or changed worlds). */
        boolean tick() {
            Player p = org.bukkit.Bukkit.getPlayer(player);
            if (p == null || !p.isValid() || !p.getWorld().equals(world)) {
                return false;
            }
            double[] hands = hands(p);
            for (int k = 0; k < 4; k++) {
                double[] at = streamPoint(from, hands, age, k);
                streamParticle(world, k, at[0], at[1], at[2]);
            }
            if (++age > STREAM_TICKS) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, hands[0], hands[1], hands[2], 10, 0.25, 0.2, 0.25, 0.0);
                world.spawnParticle(Particle.DUST, hands[0], hands[1], hands[2], 10, 0.2, 0.2, 0.2, 0.05,
                        GOLD_CONFETTI);
                world.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("roulettecasino", "card_flip"),
                        net.kyori.adventure.sound.Sound.Source.PLAYER, 0.9F, 1.0F), hands[0], hands[1], hands[2]);
                world.playSound(new Location(world, hands[0], hands[1], hands[2]), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                        SoundCategory.PLAYERS, 0.9F, 1.4F);
                return false;
            }
            return true;
        }
    }
}
