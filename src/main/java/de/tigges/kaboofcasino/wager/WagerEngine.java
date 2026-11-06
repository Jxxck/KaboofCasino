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

    /** The committed records, one per stake (a slot machine round has one, a wheel or Plinko round up to 3). */
    public record Committed(List<RoundRecord> rounds) implements Commit {
        public Committed {
            rounds = List.copyOf(rounds);
        }

        public RoundRecord round() {
            return rounds.getFirst();
        }
    }

    /** @param item the stake item the refusal is about (null if none) */
    public record Refused(Refusal refusal, String item) implements Commit {
        public Refused(Refusal refusal) {
            this(refusal, null);
        }
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
        /** Highest stake per bet slot, or per round if {@link #capPerRound()}. */
        int maxStake();

        /** true: {@link #maxStake} covers all stakes of a round together; false: each stake (the mod). */
        default boolean capPerRound() {
            return false;
        }

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
        Refused r = checkAll(account, List.of(stake));
        return r == null ? null : r.refusal();
    }

    /**
     * The checks of {@link #commitAll}: owed list empty, room for one open record per stake, every item a bet item,
     * every count >= 1, every stake (or, with {@code bets.cap-per: round}, all together) at most
     * {@code bets.max-stake}, and enough plain items of every item in the storage slots (stakes of the same item
     * add up).
     */
    public Refused checkAll(Account account, List<ItemAmount> stakes) {
        Ledger ledger = account.ledger();
        if (!ledger.owed().isEmpty()) {
            return new Refused(Refusal.OWED_PENDING);
        }
        if (stakes.isEmpty()) {
            return new Refused(Refusal.BAD_COUNT);
        }
        if (ledger.rounds().size() + stakes.size() > limits.maxOpenRounds()) {
            return new Refused(Refusal.ROUNDS_FULL);
        }
        long total = 0;
        for (ItemAmount stake : stakes) {
            if (!limits.betItems().contains(stake.item())) {
                return new Refused(Refusal.NOT_ALLOWED, stake.item());
            }
            if (stake.count() < 1) {
                return new Refused(Refusal.BAD_COUNT, stake.item());
            }
            if (stake.count() > limits.maxStake()) {
                return new Refused(Refusal.MAX_STAKE);
            }
            total += stake.count();
        }
        if (limits.capPerRound() && total > limits.maxStake()) {
            return new Refused(Refusal.MAX_STAKE);
        }
        for (ItemAmount need : ItemAmount.merge(stakes)) {
            if (account.countPlain(need.item()) < need.count()) {
                return new Refused(Refusal.NOT_ENOUGH, need.item());
            }
        }
        return null;
    }

    /** Takes the stake and writes the round in one tick, or refuses without changing anything. */
    public Commit commit(Account account, Request request) {
        return commitAll(account, List.of(request));
    }

    /**
     * Takes every stake and writes one record per stake in one ledger write (one tick), or refuses without changing
     * anything. All or nothing: the checks cover all stakes together.
     */
    public Commit commitAll(Account account, List<Request> requests) {
        Threads.requireMain();
        Refused refused = checkAll(account, requests.stream().map(Request::stake).toList());
        if (refused != null) {
            return refused;
        }
        long now = clock.getAsLong();
        List<RoundRecord> rounds = new java.util.ArrayList<>();
        Ledger next = account.ledger();
        for (Request request : requests) {
            RoundRecord round = new RoundRecord(UUID.randomUUID(), request.machine(), request.game(),
                    RoundState.DECIDED, request.stake(), request.payout(), now, request.endsMillis(),
                    request.detail());
            rounds.add(round);
            next = next.withRound(round);
        }
        // plan every take first (merged per item, nothing changes if planning fails), then apply and write the ledger
        Account.TakePlan takes = account.planTake(requests.stream().map(Request::stake).toList());
        account.applyTake(takes);
        account.ledger(next);
        for (RoundRecord round : rounds) {
            sink.started(account.id(), account.name(), round);
        }
        return new Committed(rounds);
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
