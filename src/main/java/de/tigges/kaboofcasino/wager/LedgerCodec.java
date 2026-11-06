package de.tigges.kaboofcasino.wager;

import de.tigges.kaboofcasino.util.Keys;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player PDC <-> {@link Ledger}. One key {@code kaboofcasino:ledger} holds a container
 * {@code {v, rounds: [..], owed: [{item, count}]}}; it is written in one {@code set} and removed when empty.
 * Every release must still read records written by older versions ({@code v}). A record that cannot be decoded is
 * never guessed: {@link CorruptLedgerException} stops all casino actions for that player.
 */
public final class LedgerCodec {

    public static final int VERSION = 1;
    private static final NamespacedKey V = Keys.key("v");
    private static final NamespacedKey ROUNDS = Keys.key("rounds");
    private static final NamespacedKey OWED = Keys.key("owed");
    private static final NamespacedKey ID = Keys.key("id");
    private static final NamespacedKey MACHINE = Keys.key("machine");
    private static final NamespacedKey GAME = Keys.key("game");
    private static final NamespacedKey STATE = Keys.key("state");
    private static final NamespacedKey STAKE = Keys.key("stake");
    private static final NamespacedKey PAYOUT = Keys.key("payout");
    private static final NamespacedKey CREATED = Keys.key("created");
    private static final NamespacedKey ENDS = Keys.key("ends");
    private static final NamespacedKey DETAIL = Keys.key("detail");
    private static final NamespacedKey ITEM = Keys.key("item");
    private static final NamespacedKey COUNT = Keys.key("count");

    /** The stored ledger is unreadable or from a newer plugin version. */
    public static final class CorruptLedgerException extends RuntimeException {
        public CorruptLedgerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private LedgerCodec() {
    }

    public static boolean has(PersistentDataContainerView pdc) {
        return pdc.has(Keys.LEDGER);
    }

    public static Ledger read(PersistentDataContainerView pdc) {
        if (!pdc.has(Keys.LEDGER)) {
            return Ledger.EMPTY;
        }
        try {
            PersistentDataContainer root = pdc.get(Keys.LEDGER, PersistentDataType.TAG_CONTAINER);
            int v = root.getOrDefault(V, PersistentDataType.INTEGER, 0);
            if (v < 1 || v > VERSION) {
                throw new IllegalStateException("ledger version " + v + " (this plugin knows 1.." + VERSION + ")");
            }
            List<RoundRecord> rounds = new ArrayList<>();
            for (PersistentDataContainer r : root.getOrDefault(ROUNDS, PersistentDataType.LIST.dataContainers(),
                    List.of())) {
                rounds.add(new RoundRecord(
                        UUID.fromString(r.get(ID, PersistentDataType.STRING)),
                        UUID.fromString(r.get(MACHINE, PersistentDataType.STRING)),
                        r.get(GAME, PersistentDataType.STRING),
                        RoundState.valueOf(r.get(STATE, PersistentDataType.STRING)),
                        amount(r.get(STAKE, PersistentDataType.TAG_CONTAINER)),
                        amounts(r.getOrDefault(PAYOUT, PersistentDataType.LIST.dataContainers(), List.of())),
                        r.get(CREATED, PersistentDataType.LONG),
                        r.get(ENDS, PersistentDataType.LONG),
                        r.getOrDefault(DETAIL, PersistentDataType.STRING, "")));
            }
            List<ItemAmount> owed = amounts(root.getOrDefault(OWED, PersistentDataType.LIST.dataContainers(),
                    List.of()));
            return new Ledger(rounds, owed);
        } catch (RuntimeException e) {
            throw new CorruptLedgerException("unreadable casino ledger: " + e, e);
        }
    }

    public static void write(PersistentDataContainer pdc, Ledger ledger) {
        if (ledger.isEmpty()) {
            pdc.remove(Keys.LEDGER);
            return;
        }
        PersistentDataAdapterContext ctx = pdc.getAdapterContext();
        PersistentDataContainer root = ctx.newPersistentDataContainer();
        root.set(V, PersistentDataType.INTEGER, VERSION);
        List<PersistentDataContainer> rounds = new ArrayList<>();
        for (RoundRecord r : ledger.rounds()) {
            PersistentDataContainer c = ctx.newPersistentDataContainer();
            c.set(ID, PersistentDataType.STRING, r.id().toString());
            c.set(MACHINE, PersistentDataType.STRING, r.machine().toString());
            c.set(GAME, PersistentDataType.STRING, r.game());
            c.set(STATE, PersistentDataType.STRING, r.state().name());
            c.set(STAKE, PersistentDataType.TAG_CONTAINER, container(ctx, r.stake()));
            c.set(PAYOUT, PersistentDataType.LIST.dataContainers(), containers(ctx, r.payout()));
            c.set(CREATED, PersistentDataType.LONG, r.created());
            c.set(ENDS, PersistentDataType.LONG, r.ends());
            c.set(DETAIL, PersistentDataType.STRING, r.detail());
            rounds.add(c);
        }
        root.set(ROUNDS, PersistentDataType.LIST.dataContainers(), rounds);
        root.set(OWED, PersistentDataType.LIST.dataContainers(), containers(ctx, ledger.owed()));
        pdc.set(Keys.LEDGER, PersistentDataType.TAG_CONTAINER, root);
    }

    private static PersistentDataContainer container(PersistentDataAdapterContext ctx, ItemAmount a) {
        PersistentDataContainer c = ctx.newPersistentDataContainer();
        c.set(ITEM, PersistentDataType.STRING, a.item());
        c.set(COUNT, PersistentDataType.LONG, a.count());
        return c;
    }

    private static List<PersistentDataContainer> containers(PersistentDataAdapterContext ctx, List<ItemAmount> list) {
        return list.stream().map(a -> container(ctx, a)).toList();
    }

    private static ItemAmount amount(PersistentDataContainer c) {
        return new ItemAmount(c.get(ITEM, PersistentDataType.STRING), c.get(COUNT, PersistentDataType.LONG));
    }

    private static List<ItemAmount> amounts(List<PersistentDataContainer> list) {
        return list.stream().map(LedgerCodec::amount).toList();
    }
}
