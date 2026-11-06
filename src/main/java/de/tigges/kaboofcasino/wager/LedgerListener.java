package de.tigges.kaboofcasino.wager;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The lifecycle hooks of the bet engine (server.md 1.3):
 * <ul>
 *   <li><b>Quit</b> (also kick, timeout): every open round settles now, before the quit save (F7). A dead player is
 *       skipped: his death already drained the ledger.</li>
 *   <li><b>Death</b>, LOW (before DeathBan's HIGH grave): rounds and owed go into the drops; with keepInventory into
 *       the inventory/owed. MONITOR: if the event ended cancelled, the items go back and leave the drops.</li>
 *   <li><b>Join</b> / respawn after a dead join: records still in the file are from a crash -> settled
 *       (JOIN_RECOVERY), then owed items are delivered.</li>
 *   <li><b>Timer</b>: owed delivery and the watchdog for records no machine settles in time.</li>
 *   <li><b>Disable</b>: everyone online settles (STOP); Paper saves the players afterwards (F8).</li>
 * </ul>
 * Thread: MAIN.
 */
public final class LedgerListener implements Listener {

    /** A record whose machine never settled it is settled this long after its scheduled end. */
    static final long STALE_GRACE_MILLIS = 10_000;

    private final WagerEngine engine;
    private final Supplier<Messages> messages;
    private final Predicate<UUID> liveRound;
    /** Drops added at LOW per player, removed again if the death is cancelled. */
    private final Map<UUID, Drained> drained = new HashMap<>();
    /** Players whose ledger could not be read (CRITICAL logged once; casino blocked for them). */
    private final Set<UUID> corrupt = new HashSet<>();

    private record Drained(List<ItemAmount> items, List<ItemStack> stacks) {
    }

    public LedgerListener(WagerEngine engine, Supplier<Messages> messages, Predicate<UUID> liveRound) {
        this.engine = engine;
        this.messages = messages;
        this.liveRound = liveRound;
    }

    /** True if the ledger of this player is unreadable (all casino actions refused). */
    public boolean blocked(UUID player) {
        return corrupt.contains(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isDead()) {
            recover(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerPostRespawnEvent event) {
        recover(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.isDead()) {
            return;
        }
        guarded(player, "quit", a -> {
            List<RoundRecord> settled = engine.settleAll(a, SettleReason.QUIT);
            if (!settled.isEmpty()) {
                Log.get().info("{} quit with {} open round(s): settled before the save", player.getName(),
                        settled.size());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        guarded(player, "death", a -> {
            if (event.getKeepInventory()) {
                engine.settleAll(a, SettleReason.DEATH);
                return;
            }
            List<ItemAmount> items = engine.drainForDeath(a);
            if (items.isEmpty()) {
                return;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemAmount item : items) {
                var m = PlayerAccount.material(item.item());
                long rest = item.count();
                while (rest > 0) {
                    int n = (int) Math.min(rest, m.getMaxStackSize());
                    stacks.add(ItemStack.of(m, n));
                    rest -= n;
                }
            }
            event.getDrops().addAll(stacks);
            drained.put(player.getUniqueId(), new Drained(items, stacks));
            Log.get().warn("{} died with casino winnings: {} into the death drops", player.getName(), items);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathMonitor(PlayerDeathEvent event) {
        Drained d = drained.remove(event.getPlayer().getUniqueId());
        if (d == null || !event.isCancelled()) {
            return;
        }
        for (ItemStack stack : d.stacks()) {
            event.getDrops().removeIf(s -> s == stack);
        }
        guarded(event.getPlayer(), "cancelled death", a -> engine.restoreAfterCancelledDeath(a, d.items()));
        Log.get().warn("the death of {} was cancelled: casino winnings {} went back", event.getPlayer().getName(),
                d.items());
    }

    /** Join recovery + owed delivery. */
    void recover(Player player) {
        if (FakePlayers.isFake(player)) {
            return;
        }
        guarded(player, "join", a -> {
            List<RoundRecord> settled = engine.settleAll(a, SettleReason.JOIN_RECOVERY);
            if (!settled.isEmpty()) {
                Log.get().warn("{} joined with {} round(s) from before a crash: settled {}", player.getName(),
                        settled.size(), settled.stream().map(RoundRecord::id).toList());
            }
            remindOwed(player, a);
        });
    }

    /** Timer: owed delivery and stale rounds for everyone online. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead() || !LedgerCodec.has(player.getPersistentDataContainer())) {
                continue;
            }
            guarded(player, "timer", a -> {
                engine.settleStale(a, STALE_GRACE_MILLIS, liveRound);
                List<ItemAmount> delivered = engine.deliverOwed(a);
                if (!delivered.isEmpty()) {
                    messages.get().send(player, Msg.OWED_DELIVERED,
                            Messages.component("items", ItemNames.list(delivered)));
                }
            });
        }
    }

    /** {@code /casino abholen}. */
    public void collect(Player player) {
        guarded(player, "collect", a -> {
            List<ItemAmount> delivered = engine.deliverOwed(a);
            if (!delivered.isEmpty()) {
                messages.get().send(player, Msg.OWED_DELIVERED, Messages.component("items",
                        ItemNames.list(delivered)));
            }
            if (!remindOwed(player, a) && delivered.isEmpty()) {
                messages.get().send(player, Msg.OWED_NONE);
            }
        });
    }

    private boolean remindOwed(Player player, Account a) {
        List<ItemAmount> owed = a.ledger().owed();
        if (owed.isEmpty()) {
            return false;
        }
        messages.get().send(player, Msg.OWED_REMAINING, Messages.component("items", ItemNames.list(owed)));
        return true;
    }

    /** onDisable: settle everyone online (STOP). */
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isDead()) {
                guarded(player, "stop", a -> engine.settleAll(a, SettleReason.STOP));
            }
        }
    }

    private void guarded(Player player, String what, java.util.function.Consumer<Account> work) {
        if (FakePlayers.isFake(player)) {
            return;
        }
        try {
            work.accept(new PlayerAccount(player));
            corrupt.remove(player.getUniqueId());
        } catch (LedgerCodec.CorruptLedgerException e) {
            if (corrupt.add(player.getUniqueId())) {
                Log.critical("LEDGER_UNREADABLE", "the casino ledger of {} ({}) cannot be read during {}: {}. "
                        + "Nothing was changed; the casino is blocked for this player until an admin checks the file.",
                        player.getName(), player.getUniqueId(), what, e.getMessage());
            }
        } catch (RuntimeException e) {
            Log.incident("ledger " + what + " of " + player.getName(), e);
        }
    }
}
