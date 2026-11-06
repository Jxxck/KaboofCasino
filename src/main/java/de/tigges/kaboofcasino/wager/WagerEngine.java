package de.tigges.kaboofcasino.wager;

import de.tigges.kaboofcasino.util.Threads;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The bet engine (PLAN decision 2, server.md 1). The player carries his own rounds:
 * <ul>
 *   <li><b>Commit</b> (one tick): checks, then the stake leaves the inventory and the round record is written to the
 *       ledger. Everything that can refuse runs before the first mutation.</li>
 *   <li><b>Settle</b> (one tick): the record is removed and its payout moves to {@code owed} in one ledger write,
 *       then {@link #deliverOwed} puts what fits into the inventory and keeps the rest owed. Never anything on the
 *       ground. Settling a round that is no longer in the ledger is a no-op (idempotent).</li>
 *   <li>New rounds are refused while anything is owed, so owed never grows past one round's overflow.</li>
 *   <li><b>Death</b>: {@link #drainForDeath} moves the ledger into the death drops as far as the grave holds it; the
 *       rest stays owed.</li>
 * </ul>
 * Because the ledger lives in the player PDC, the server saves it in the same file as the inventory: a crash rolls
 * both back together (no dupe, no loss). Thread: MAIN.
 */
public final class WagerEngine {

    /** Why a commit was refused. */
    public enum Refusal {
        OWED_PENDING, ROUNDS_FULL, NOT_ALLOWED, BAD_COUNT, MAX_STAKE, NOT_ENOUGH
    }

    /** Result of {@link #commit}. */
    public sealed interface Commit permits Committed, Refused {
    }

    public record Committed(RoundRecord round) implements Commit {
    }

    public record Refused(Refusal refusal) implements Commit {
    }

    /** What a game asks to commit. The outcome is already drawn; the payout is fixed. */
    public record Request(UUID machine, String game, ItemAmount stake, List<ItemAmount> payout, long endsMillis,
                          String detail) {
        public Request {
            payout = List.copyOf(payout);
        }
    }

    /** Settings the engine reads per call (config reloads apply to the next round). */
    public interface Limits {
        int maxStake();

        int maxOpenRounds();

        Set<String> betItems();
    }

    private final Limits limits;
    private final RoundSink sink;
    private final LongSupplier clock;

    public WagerEngine(Limits limits, RoundSink sink, LongSupplier clock) {
        this.limits = limits;
        this.sink = sink;
        this.clock = clock;
    }

    /** The checks of {@link #commit} without mutating anything (GUI hints). */
    public Refusal check(Account account, ItemAmount stake) {
        Ledger ledger = account.ledger();
        if (!ledger.owed().isEmpty()) {
            return Refusal.OWED_PENDING;
        }
        if (ledger.rounds().size() >= limits.maxOpenRounds()) {
            return Refusal.ROUNDS_FULL;
        }
        if (!limits.betItems().contains(stake.item())) {
            return Refusal.NOT_ALLOWED;
        }
        if (stake.count() < 1) {
            return Refusal.BAD_COUNT;
        }
        if (stake.count() > limits.maxStake()) {
            return Refusal.MAX_STAKE;
        }
        if (account.countPlain(stake.item()) < stake.count()) {
            return Refusal.NOT_ENOUGH;
        }
        return null;
    }

    /** Takes the stake and writes the round in one tick, or refuses without changing anything. */
    public Commit commit(Account account, Request request) {
        Threads.requireMain();
        Refusal refusal = check(account, request.stake());
        if (refusal != null) {
            return new Refused(refusal);
        }
        long now = clock.getAsLong();
        RoundRecord round = new RoundRecord(UUID.randomUUID(), request.machine(), request.game(), RoundState.DECIDED,
                request.stake(), request.payout(), now, request.endsMillis(), request.detail());
        Ledger next = account.ledger().withRound(round);
        account.take(request.stake().item(), request.stake().count());
        account.ledger(next);
        sink.started(account.id(), account.name(), round);
        return new Committed(round);
    }

    /**
     * Settles one round: record out, payout into owed (one write), then delivery of what fits.
     *
     * @return the settled record, or null if it was not (or no longer) open
     */
    public RoundRecord settle(Account account, UUID roundId, SettleReason reason) {
        Threads.requireMain();
        Ledger ledger = account.ledger();
        RoundRecord round = ledger.round(roundId).orElse(null);
        if (round == null) {
            return null;
        }
        account.ledger(ledger.withoutRound(roundId).plusOwed(round.payout()));
        sink.settled(account.id(), round, reason, clock.getAsLong());
        deliverOwed(account);
        return round;
    }

    /** Settles every open round (quit, stop, join recovery). Returns the settled records. */
    public List<RoundRecord> settleAll(Account account, SettleReason reason) {
        List<RoundRecord> settled = new ArrayList<>();
        for (RoundRecord round : account.ledger().rounds()) {
            RoundRecord r = settle(account, round.id(), reason);
            if (r != null) {
                settled.add(r);
            }
        }
        deliverOwed(account);
        return settled;
    }

    /**
     * Settles rounds whose scheduled end lies more than {@code graceMillis} in the past and that no live machine
     * still runs ({@code live}).
     */
    public List<RoundRecord> settleStale(Account account, long graceMillis, Predicate<UUID> live) {
        long now = clock.getAsLong();
        List<RoundRecord> settled = new ArrayList<>();
        for (RoundRecord round : account.ledger().rounds()) {
            if (round.ends() + graceMillis <= now && !live.test(round.id())) {
                RoundRecord r = settle(account, round.id(), SettleReason.TIMEOUT);
                if (r != null) {
                    settled.add(r);
                }
            }
        }
        return settled;
    }

    /**
     * Gives as much of the owed list as fits; the rest stays owed. One item at a time: give it, then write the ledger
     * at once, so an exception in a later item can neither dupe nor lose an earlier one.
     *
     * @return what was delivered (merged)
     */
    public List<ItemAmount> deliverOwed(Account account) {
        Threads.requireMain();
        if (account.ledger().owed().isEmpty()) {
            return List.of();
        }
        List<ItemAmount> delivered = new ArrayList<>();
        for (ItemAmount owed : account.ledger().owed()) {
            long rest = ItemAmount.total(account.give(List.of(owed)), owed.item());
            long given = owed.count() - rest;
            if (given <= 0) {
                continue;
            }
            Ledger current = account.ledger();
            List<ItemAmount> next = new ArrayList<>();
            for (ItemAmount o : current.owed()) {
                if (!o.item().equals(owed.item())) {
                    next.add(o);
                } else if (o.count() - given > 0) {
                    next.add(new ItemAmount(o.item(), o.count() - given));
                }
            }
            account.ledger(current.withOwed(next));
            delivered.add(new ItemAmount(owed.item(), given));
        }
        return List.copyOf(delivered);
    }

    /** Where death winnings go: puts what fits into the death drops and returns the rest. */
    public interface DropTarget {
        /**
         * Must validate everything before it changes the drops (an exception then changes nothing).
         *
         * @return what did not fit (stays owed)
         */
        List<ItemAmount> put(List<ItemAmount> items);
    }

    /**
     * Death: every open round's payout and the owed list go into the death drops as far as DeathBan's grave holds
     * them ({@link DeathDrops}); the rest stays owed. The drops change first, the ledger is written last (no rounds,
     * owed = what did not fit).
     *
     * @return what went into the drops
     */
    public List<ItemAmount> drainForDeath(Account account, DropTarget drops) {
        Threads.requireMain();
        Ledger ledger = account.ledger();
        if (ledger.isEmpty()) {
            return List.of();
        }
        List<ItemAmount> all = new ArrayList<>(ledger.owed());
        for (RoundRecord round : ledger.rounds()) {
            all.addAll(round.payout());
        }
        all = ItemAmount.merge(all);
        List<ItemAmount> kept = ItemAmount.merge(drops.put(all));
        account.ledger(new Ledger(List.of(), kept));
        long now = clock.getAsLong();
        for (RoundRecord round : ledger.rounds()) {
            sink.settled(account.id(), round, SettleReason.DEATH, now);
        }
        List<ItemAmount> dropped = new ArrayList<>();
        for (ItemAmount a : all) {
            long rest = a.count() - ItemAmount.total(kept, a.item());
            if (rest > 0) {
                dropped.add(new ItemAmount(a.item(), rest));
            }
        }
        return List.copyOf(dropped);
    }

    /**
     * The death event was cancelled after {@link #drainForDeath}: what was found back in the drops goes back
     * (inventory, then owed).
     */
    public void restoreAfterCancelledDeath(Account account, List<ItemAmount> items) {
        Threads.requireMain();
        account.ledger(account.ledger().plusOwed(items));
        deliverOwed(account);
    }
}
