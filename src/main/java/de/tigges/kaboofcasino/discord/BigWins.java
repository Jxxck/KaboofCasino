package de.tigges.kaboofcasino.discord;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.storage.RoundLog;
import de.tigges.kaboofcasino.util.ChipText;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.Chips;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.RoundRecord;
import de.tigges.kaboofcasino.wager.SettleReason;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Big wins on Discord ({@code discord.big-wins}): a settled chip bet that pays at least {@code big-win-multiplier}
 * times its stake and at least {@code big-win-min-chips} chips goes to the channel ("Bob gewinnt 1.500 Chips am
 * Spielautomaten!", 100× der Einsatz von 15 Chips). Refunds and Texas Hold'em seats (games between players) never
 * count; players hidden from the leaderboard never appear; per player one post per {@code big-win-cooldown-seconds}.
 * A round settled by the join after a crash ({@code JOIN_RECOVERY}) is never posted: it may have been posted before
 * the crash (the post left at once, the player file was older).
 * Each record counts alone (a three-slot wheel spin is three bets, as on the leaderboard's multiplier). Thread: MAIN.
 */
public final class BigWins {

    static final int GOLD = 0xF1C40F;

    private final CasinoContext ctx;
    private final DiscordBridge discord;
    private final Predicate<UUID> hidden;
    private final Map<UUID, Long> lastPost = new HashMap<>();

    public BigWins(CasinoContext ctx, DiscordBridge discord, Predicate<UUID> hidden) {
        this.ctx = ctx;
        this.discord = discord;
        this.hidden = hidden;
    }

    /** A round was settled (the round log calls this on MAIN after recording it). */
    public void settled(UUID player, RoundRecord r, SettleReason reason) {
        CasinoConfig.Discord cfg = ctx.config().get().discord();
        if (!cfg.bigWins() || reason == SettleReason.JOIN_RECOVERY || RoundLog.refund(r.detail())
                || !Chips.VALUE.equals(r.stake().item())) {
            return;
        }
        long stake = r.stake().count();
        long payout = r.payout().stream().filter(a -> Chips.VALUE.equals(a.item())).mapToLong(ItemAmount::count).sum();
        if (!big(stake, payout, cfg.multiplier(), cfg.minChips()) || hidden.test(player)) {
            return;
        }
        long now = ctx.clock().getAsLong();
        Long last = lastPost.get(player);
        if (last != null && now - last < cfg.cooldownSeconds() * 1000L) {
            return;
        }
        Player online = Bukkit.getPlayer(player);
        String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(player).getName();
        if (name == null) {
            return;
        }
        Messages m = ctx.messages().get();
        String game = m.raw(gameMsg(r.game()));
        String title = fill(m.raw(Msg.DISCORD_BIG_WIN_TITLE), name, payout, stake, game);
        String text = fill(m.raw(Msg.DISCORD_BIG_WIN_TEXT), DiscordBridge.md(name), payout, stake, game);
        if (discord.post(title, text, GOLD, player, name)) {
            lastPost.put(player, now);
            Log.get().info("big win of {} posted to Discord: {} chips for {} at {} ({})", name, payout, stake, r.game(),
                    r.id());
        }
    }

    private static String fill(String template, String name, long payout, long stake, String game) {
        return template.replace("<player>", name).replace("<chips>", ChipText.chips(payout))
                .replace("<stake>", ChipText.chips(stake)).replace("<game>", game)
                .replace("<multiplier>", multiplier(stake, payout));
    }

    /** True if {@code payout} is a big win on {@code stake}. Pure. */
    static boolean big(long stake, long payout, int multiplier, int minChips) {
        return stake > 0 && payout >= minChips && payout >= stake * multiplier;
    }

    /** payout / stake: "100", "52,5" (one decimal, cut). Pure. */
    static String multiplier(long stake, long payout) {
        long tenths = payout * 10 / stake;
        return tenths % 10 == 0 ? ChipText.number(tenths / 10) : ChipText.number(tenths / 10) + "," + tenths % 10;
    }

    static Msg gameMsg(String game) {
        return switch (game == null ? "" : game) {
            case "slot_machine" -> Msg.DISCORD_GAME_SLOT_MACHINE;
            case "fortune_wheel" -> Msg.DISCORD_GAME_FORTUNE_WHEEL;
            case "plinko" -> Msg.DISCORD_GAME_PLINKO;
            case "roulette" -> Msg.DISCORD_GAME_ROULETTE;
            case "blackjack" -> Msg.DISCORD_GAME_BLACKJACK;
            case "poker" -> Msg.DISCORD_GAME_POKER;
            case "derby" -> Msg.DISCORD_GAME_DERBY;
            default -> Msg.DISCORD_GAME_OTHER;
        };
    }
}
