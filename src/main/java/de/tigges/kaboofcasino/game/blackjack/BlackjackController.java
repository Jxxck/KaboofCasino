package de.tigges.kaboofcasino.game.blackjack;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.game.GameMenu;
import de.tigges.kaboofcasino.game.MachineController;
import de.tigges.kaboofcasino.game.StakeMenu;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.gui.Usability;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.wager.ItemAmount;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import de.tigges.kaboofcasino.wager.RoundRecord;
import de.tigges.kaboofcasino.wager.RoundState;
import de.tigges.kaboofcasino.wager.SettleReason;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Runtime of one blackjack table ({@code BlackjackTableBlockEntity}). One player per table, one hand against the
 * drawn dealer, as in the mod (games.md 3 "Multiplayer": the five spots are drawn, only the owner plays; others get
 * "busy").
 * <ul>
 *   <li><b>DEAL</b>: the stakes of the three bet slots are committed in one tick as PLAYING records whose payout is
 *       the hand's stand result ({@link BlackjackStand} on a copy of the shoe), then the mod's
 *       {@link BlackjackRound} runs on the table's shoe and the round menu opens.</li>
 *   <li><b>HIT / DOUBLE</b>: the new stand result is computed on a copy first, the records are rewritten (double:
 *       together with the second stake, {@link WagerEngine#revise}) and only then the live round deals the card;
 *       the live round deals exactly the predicted cards. STAND changes no record.</li>
 *   <li><b>Quit, kick, stop, crash, death, watchdog</b> settle the records as they stand: the stand result (PLAN
 *       decision 2). Once the owner is gone the live hand stands at once, so the table shows what was paid.</li>
 *   <li>The decision: the payout is checked against the records (rewritten if they differ, which only a bug could
 *       cause), the records settle 22 ticks later with the first payout coin, one coin every 3 ticks, the table is
 *       free 15 ticks after the round is over and the coins are out (or at once on the owner's next click).</li>
 * </ul>
 * The listener mirrors the mod's {@code Events}: the table view ({@link BlackjackTableState}) for the rig, sounds at
 * their scheduled round ticks, confetti on a win. Thread: MAIN.
 */
public final class BlackjackController extends MachineController {

    static final String GAME = "blackjack";
    /** {@code PAYOUT_DELAY}, {@code PAYOUT_STEP}, {@code ++endHold > 14}. */
    static final int PAYOUT_DELAY = 22;
    static final int PAYOUT_STEP = 3;
    static final int END_HOLD = 14;
    /** Sounds play at the anchor block ({@code playSound(null, worldPosition)}: its centre), in table pixels. */
    static final double[] ANCHOR_CENTRE = {24, 8, 24};
    /** {@code isInRange}: the menu stays valid within 8 blocks of this table point. */
    static final double[] RANGE_POINT = {32, 14, 12};
    static final double RANGE = 8.0;
    /** A PLAYING record's watchdog end: this long after its last revision (the controller owns it while it ticks). */
    static final long RECORD_ENDS_MILLIS = 60_000;
    private static final Particle.DustOptions RED_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xDF3B4B), 1.15f);
    private static final Particle.DustOptions GOLD_CONFETTI = new Particle.DustOptions(Color.fromRGB(0xFFD168), 1.25f);
    private static final Particle.DustOptions GREEN_CONFETTI = new Particle.DustOptions(Color.fromRGB(0x36D886),
            1.15f);

    private BlackjackRig rig;
    /** Created at the first DEAL ({@code BlackjackTableBlockEntity} 355-357); not saved, as in the mod. */
    private BlackjackShoe shoe;
    private Hand hand;
    private final BlackjackTableState idleView = new BlackjackTableState();
    private BlackjackPanel.Content shownPanel;

    /** One filled bet slot of the running hand. */
    record Seat(int slot, ItemAmount stake, UUID round) {
    }

    private record ScheduledSound(int at, String id, float volume, float pitch) {
    }

    /** The running hand ({@code round != null} in the mod). */
    private final class Hand implements BlackjackRound.Listener {
        final UUID player;
        final String name;
        final int spot;
        final long seed;
        /** Game tick of the DEAL click; the round's clock starts on the next tick (round tick 1 = start + 1). */
        final long start;
        final int bias;
        final BlackjackTableState view;
        final List<Seat> seats;
        final List<ScheduledSound> sounds = new ArrayList<>();
        final ArrayDeque<String> coins = new ArrayDeque<>();
        BlackjackRound round;
        BlackjackRound.Result decided = BlackjackRound.Result.NONE;
        int payoutWait;
        boolean settled;
        int endHold;
        /** The owner quit, died or was kicked: his records are settled, the hand stands at its next chance. */
        boolean ownerGone;
        boolean dirty = true;

        Hand(UUID player, String name, int spot, long seed, int bias, long start, List<Seat> seats) {
            this.player = player;
            this.name = name;
            this.spot = spot;
            this.seed = seed;
            this.bias = bias;
            this.start = start;
            this.view = new BlackjackTableState(start, spot);
            this.view.phaseChanged(BlackjackRound.Phase.DEALING, 0);
            this.seats = new ArrayList<>(seats);
        }

        private void schedule(int at, String id, float volume, float pitch) {
            sounds.add(new ScheduledSound(at, id, volume, pitch));
        }

        @Override
        public void shuffled(int at) {
            view.shuffled(at);
            schedule(at, "card_shuffle", 0.9f, 1.0f);
            dirty = true;
        }

        @Override
        public void cardDealt(boolean toDealer, int index, int card, boolean faceUp, int at) {
            view.cardDealt(toDealer, index, card, faceUp, at);
            float pitch = 0.94f + index * 0.04f + (toDealer ? 0.05f : 0.0f);
            schedule(at + 7, "card_flip", 0.3f, 0.75f);
            schedule(at + 14, "card_deal", 0.8f, pitch);
            dirty = true;
        }

        @Override
        public void holeCardRevealed(int card, int at) {
            view.holeCardRevealed(card, at);
            schedule(at + 4 + 2, "card_flip", 0.9f, 1.0f);
            dirty = true;
        }

        @Override
        public void dealerPeek(boolean blackjack, int at) {
            view.dealerPeek(blackjack, at);
            schedule(at + 5, "card_flip", 0.4f, 0.7f);
            dirty = true;
        }

        @Override
        public void turnOpened(int deadline, int at) {
            view.turnOpened(deadline, at);
            schedule(at, "tick", 0.5f, 1.5f);
            dirty = true;
        }

        @Override
        public void turnTimedOut(int at) {
            view.turnTimedOut(at);
            Player p = Bukkit.getPlayer(player);
            if (p != null && !ownerGone) {
                ctx.messages().get().actionBar(p, Msg.BLACKJACK_TIMEOUT);
            }
            dirty = true;
        }

        @Override
        public void phaseChanged(BlackjackRound.Phase phase, int at) {
            view.phaseChanged(phase, at);
            dirty = true;
        }

        @Override
        public void decided(BlackjackRound.Result result, int at) {
            view.decided(result, at);
            view.doubled = round != null && round.doubled();
            decided = result;
            payoutWait = PAYOUT_DELAY;
            Log.get().info("blackjack #{}: hand of {} ends {} (player {} = {}, dealer {} = {}{}{})",
                    machine.record().number(), name, result, cards(round.player()), BlackjackCards.total(round.player()),
                    cards(round.dealer()), BlackjackCards.total(round.dealer()), round.doubled() ? ", doubled" : "",
                    ownerGone ? ", owner gone" : "");
            confirm(this, result);
            World world = world();
            if (world != null) {
                if (result.isWin()) {
                    schedule(at + 6, "win", 1.1f, 1.0f);
                    celebrate(world, spot, result == BlackjackRound.Result.BLACKJACK);
                } else if (result.isPush()) {
                    schedule(at + 6, "blackjack_push", 0.95f, 1.0f);
                } else {
                    schedule(at + 6, "lose", 0.95f, 1.0f);
                }
            }
            dirty = true;
        }

        @Override
        public void reaction(BlackjackRound.Reaction reaction, int at) {
            view.reaction(reaction, at);
            dirty = true;
        }

        @Override
        public void cleared(int at) {
            view.cleared(at);
            schedule(at + 7, "card_deal", 0.55f, 0.8f);
            dirty = true;
        }
    }

    public BlackjackController(Machine machine, CasinoContext ctx) {
        super(machine, ctx);
    }

    // ---- MachineGame -----------------------------------------------------------------------------------------------

    @Override
    protected boolean running() {
        return hand != null;
    }

    @Override
    protected Msg busyMessage() {
        return Msg.BLACKJACK_BUSY;
    }

    /** Table pixels ({@code BlackjackLayout.toBlock}: (24, 24) is the anchor block's centre) to anchor offsets. */
    @Override
    protected double[] toBlock(double x, double y, double z) {
        return BlackjackLayout.toBlock(x, y, z, machine.facing().turns());
    }

    @Override
    protected GameMenu newMenu(Player player) {
        Hand h = hand;
        if (h != null && h.player.equals(player.getUniqueId())) {
            return new BlackjackRoundMenu(player.getUniqueId(), this, ctx);
        }
        return new BlackjackBetMenu(player.getUniqueId(), this, ctx);
    }

    /**
     * {@code BlackjackTableBlockEntity.use}: a finished hand of the clicking owner ends at once; someone else's hand,
     * or someone else holding the betting menu, is "busy"; the owner reopens his running hand.
     */
    @Override
    public void interact(Player player) {
        Messages m = ctx.messages().get();
        UUID id = player.getUniqueId();
        Hand h = hand;
        if (h != null && h.player.equals(id) && h.round.isOver() && h.settled && h.coins.isEmpty()) {
            finish();
            h = null;
        }
        if (h != null && !h.player.equals(id)) {
            m.actionBar(player, Msg.BLACKJACK_BUSY, Messages.text("name", h.name));
            return;
        }
        if (h == null && claimant != null && !claimant.equals(id) && claimantActive()) {
            Player other = Bukkit.getPlayer(claimant);
            m.actionBar(player, Msg.BLACKJACK_BUSY, Messages.text("name", other == null ? "?" : other.getName()));
            return;
        }
        if (!mayOpen(player)) {
            return;
        }
        if (h == null) {
            claimant = id;
        }
        if (!ctx.menus().open(player, newMenu(player)) && hand == null) {
            claimant = null;
        }
    }

    /**
     * {@code BlackjackMenu.stillValid} / {@code isInRange}: the table exists, the viewer is within 8 blocks of table
     * point (32, 14, 12) and, while a hand runs, he is its owner.
     */
    @Override
    public boolean menuValid(Player player) {
        if (ctx.machines().byId(machine.id()).isEmpty()) {
            return false;
        }
        World w = world();
        if (w == null || !player.getWorld().equals(w)) {
            return false;
        }
        Hand h = hand;
        if (h != null && !h.player.equals(player.getUniqueId())) {
            return false;
        }
        return player.getLocation().distanceSquared(at(w, RANGE_POINT)) <= RANGE * RANGE;
    }

    @Override
    public void spawn() {
        World world = world();
        if (world == null) {
            return;
        }
        if (rig == null) {
            rig = new BlackjackRig(machine.record(), world);
        }
        rig.spawn();
        shownPanel = null;
    }

    @Override
    public void despawn() {
        if (rig != null) {
            rig.despawn();
        }
        closeMenus();
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        Hand h = hand;
        long now = ctx.clock().getAsLong();
        if (h == null || h.settled || !heartbeat.owns(now, now)) {
            return false;
        }
        return h.seats.stream().anyMatch(s -> s.round().equals(roundId));
    }

    @Override
    public void checkRig() {
        if (rig != null && rig.spawned() && rig.broken()) {
            Log.get().info("blackjack #{}: rig entities vanished, respawning", machine.record().number());
            rig.despawn();
            spawn();
        }
    }

    /**
     * The table is removed while loaded ({@code onTableBroken}): an undecided hand is refunded (the mod ejects the
     * wager), a decided one pays its result.
     */
    @Override
    public void removed() {
        Hand h = hand;
        if (h != null && !h.settled) {
            h.settled = true;
            if (h.decided == BlackjackRound.Result.NONE) {
                Player p = Bukkit.getPlayer(h.player);
                if (p != null && !p.isDead()) {
                    revise(p, h, s -> List.of(s.stake()), "refund (table removed)", List.of());
                }
            }
            for (Seat s : h.seats) {
                settleRound(h.player, h.name, s.round(), SettleReason.MACHINE_GONE);
            }
        }
        if (h != null) {
            h.round.abort();
        }
        hand = null;
        claimant = null;
        closeMenus();
    }

    // ---- DEAL, HIT, STAND, DOUBLE ----------------------------------------------------------------------------------

    /** DEAL (button 0): commits the stakes and starts the hand; opens the round menu. */
    void deal(Player player, StakeMenu menu) {
        Messages m = ctx.messages().get();
        if (!mayCommit(player)) {
            return;
        }
        List<StakeMenu.Stake> stakes = menu.stakes();
        if (stakes.isEmpty()) {
            m.actionBar(player, Msg.BLACKJACK_NO_BET);
            return;
        }
        if (shoe == null) {
            shoe = new BlackjackShoe(new ShoeRandom(ctx.random().nextLong()));
        }
        int bias = ctx.config().get().blackjack().bias();
        long seed = ctx.random().nextLong();
        // the live round shuffles in its constructor when the shoe is low: the copy does the same with the same random
        BlackjackShoe sim = shoe.copy();
        if (sim.needsShuffle()) {
            sim.shuffle();
        }
        BlackjackRound.Result stand = BlackjackStand.stand(List.of(), List.of(), false, sim, bias);
        long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
        List<WagerEngine.Request> requests = new ArrayList<>();
        for (StakeMenu.Stake s : stakes) {
            requests.add(new WagerEngine.Request(machine.id(), GAME, s.amount(),
                    BlackjackPayout.payout(s.amount(), stand, BlackjackPayout.roll(seed, s.slot())), ends,
                    detail(s.slot(), List.of(), List.of(), false, stand, "deal"), RoundState.PLAYING));
        }
        List<UUID> rounds = commit(player, requests, Msg.BLACKJACK_NO_BET);
        if (rounds == null) {
            menu.render();
            return;
        }
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < stakes.size(); i++) {
            seats.add(new Seat(stakes.get(i).slot(), stakes.get(i).amount(), rounds.get(i)));
        }
        double[] p = tablePosition(player.getLocation());
        int spot = BlackjackLayout.nearestSpot(p[0], p[1]);
        Hand h = new Hand(player.getUniqueId(), player.getName(), spot, seed, bias, Bukkit.getCurrentTick(), seats);
        hand = h;
        h.round = new BlackjackRound(shoe, h, bias);
        claimant = player.getUniqueId();
        menu.clearStakes();
        Log.get().info("{} deals blackjack #{} with {} at spot {} (bias {}, shoe {} cards)", player.getName(),
                machine.record().number(), stakes.stream().map(s -> s.amount().toString()).toList(), spot, bias,
                shoe.remaining());
        ctx.menus().open(player, new BlackjackRoundMenu(player.getUniqueId(), this, ctx));
    }

    /** HIT (button 1): the records first (stand result after this card), then the live card. */
    void hit(Player player) {
        Hand h = ownHand(player);
        if (h == null || !h.round.canHit() || !safeDecision(player)) {
            return;
        }
        BlackjackRound.Result next = BlackjackStand.hitThenStand(h.round.player(), h.round.dealer(), h.round.doubled(),
                shoe.copy(), h.bias);
        if (revise(player, h, s -> BlackjackPayout.payout(s.stake(), next, BlackjackPayout.roll(h.seed, s.slot())),
                "hit, then stand=" + next, List.of()) == WagerEngine.Revision.DONE) {
            h.round.hit();
            h.dirty = true;
        }
    }

    /** STAND (button 2): the records already hold the stand result. */
    void stand(Player player) {
        Hand h = ownHand(player);
        if (h == null || !h.round.canStand()) {
            return;
        }
        h.round.stand();
        h.dirty = true;
    }

    /**
     * DOUBLE (button 3, {@code takeExtraStake}): the same stakes again from the storage slots and the records with
     * doubled stakes and the result after one card, in one tick; then the live card. Not enough items: the mod's
     * message, nothing changes.
     */
    void doubleDown(Player player) {
        Hand h = ownHand(player);
        if (h == null || !h.round.canDouble() || !safeDecision(player)) {
            return;
        }
        BlackjackRound.Result next = BlackjackStand.doubleDown(h.round.player(), h.round.dealer(), shoe.copy(),
                h.bias);
        List<ItemAmount> extra = h.seats.stream().map(Seat::stake).toList();
        List<Seat> doubled = h.seats.stream().map(s -> new Seat(s.slot(),
                new ItemAmount(s.stake().item(), s.stake().count() * 2), s.round())).toList();
        WagerEngine.Revision r = revise(player, h, doubled, s -> BlackjackPayout.payout(s.stake(), next,
                BlackjackPayout.roll(h.seed, s.slot())), "double, then stand=" + next, extra);
        if (r == WagerEngine.Revision.NOT_ENOUGH) {
            ctx.messages().get().actionBar(player, Msg.BLACKJACK_CANNOT_DOUBLE);
            return;
        }
        if (r == WagerEngine.Revision.DONE) {
            h.seats.clear();
            h.seats.addAll(doubled);
            h.round.doubleDown();
            h.view.doubled = true;
            h.dirty = true;
        }
    }

    private Hand ownHand(Player player) {
        Hand h = hand;
        if (h == null || h.ownerGone || !h.player.equals(player.getUniqueId()) || !Usability.usable(player)) {
            return null;
        }
        return h;
    }

    /** A decision is a commit: never in danger (the menu closes; the hand runs on and times out to stand). */
    private boolean safeDecision(Player player) {
        if (ctx.menus().safe(player)) {
            return true;
        }
        ctx.messages().get().actionBar(player, Msg.ERROR_UNSAFE);
        player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        return false;
    }

    // ---- records ---------------------------------------------------------------------------------------------------

    private WagerEngine.Revision revise(Player player, Hand h, java.util.function.Function<Seat, List<ItemAmount>> pay,
                                       String why, List<ItemAmount> extra) {
        return revise(player, h, h.seats, pay, why, extra);
    }

    /**
     * Rewrites the hand's records: {@code seats} (stakes) and payouts from {@code pay}, plus {@code extra} taken, in
     * one ledger write ({@link WagerEngine#revise}). GONE (already settled) and errors change nothing here.
     */
    private WagerEngine.Revision revise(Player player, Hand h, List<Seat> seats,
                                        java.util.function.Function<Seat, List<ItemAmount>> pay, String why,
                                        List<ItemAmount> extra) {
        try {
            PlayerAccount account = new PlayerAccount(player);
            long ends = ctx.clock().getAsLong() + RECORD_ENDS_MILLIS;
            List<RoundRecord> revised = new ArrayList<>();
            for (Seat s : seats) {
                RoundRecord old = account.ledger().round(s.round()).orElse(null);
                if (old == null) {
                    return WagerEngine.Revision.GONE;
                }
                List<ItemAmount> payout = pay.apply(s);
                revised.add(new RoundRecord(old.id(), old.machine(), old.game(), RoundState.PLAYING, s.stake(),
                        payout, old.created(), ends, detail(s.slot(), h.round.player(), h.round.dealer(),
                        h.round.doubled() || !extra.isEmpty(), null, why)));
            }
            return ctx.engine().revise(account, revised, extra);
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "blackjack {} for {} refused: {}", why, player.getName(),
                    e.getMessage());
            return WagerEngine.Revision.GONE;
        }
    }

    /**
     * At the decision: the paid result must be the stored stand result. The live hand deals the predicted cards, so
     * they only differ after a bug; then the records follow the table (the owner online) and it is logged.
     */
    private void confirm(Hand h, BlackjackRound.Result result) {
        Player p = Bukkit.getPlayer(h.player);
        if (p == null || p.isDead() || h.ownerGone) {
            return;
        }
        try {
            PlayerAccount account = new PlayerAccount(p);
            boolean differs = false;
            for (Seat s : h.seats) {
                RoundRecord r = account.ledger().round(s.round()).orElse(null);
                if (r != null && !r.payout().equals(BlackjackPayout.payout(s.stake(), result,
                        BlackjackPayout.roll(h.seed, s.slot())))) {
                    differs = true;
                }
            }
            if (differs) {
                Log.get().warn("blackjack #{}: the hand of {} ended {} but its records held another stand result; "
                        + "records follow the table", machine.record().number(), h.name, result);
            }
            revise(p, h, s -> BlackjackPayout.payout(s.stake(), result, BlackjackPayout.roll(h.seed, s.slot())),
                    "result " + result, List.of());
        } catch (LedgerCodec.CorruptLedgerException e) {
            Log.critical("LEDGER_UNREADABLE", "blackjack result of {} not recorded: {}", h.name, e.getMessage());
        }
    }

    /** The round log's detail: slot, cards, doubled, and the stand result the record pays. */
    static String detail(int slot, List<Integer> player, List<Integer> dealer, boolean doubled,
                         BlackjackRound.Result stand, String why) {
        return "slot=" + slot + " p=" + cards(player) + " d=" + cards(dealer) + (doubled ? " x2" : "")
                + (stand == null ? "" : " stand=" + stand) + " " + why;
    }

    private static String cards(List<Integer> cards) {
        StringBuilder out = new StringBuilder();
        for (int c : cards) {
            if (!out.isEmpty()) {
                out.append(',');
            }
            out.append(c < 0 ? "?" : BlackjackCards.rankName(c) + "cdhs".charAt(BlackjackCards.suit(c)));
        }
        return out.toString();
    }

    // ---- ticking ---------------------------------------------------------------------------------------------------

    @Override
    public void tick(long now) {
        heartbeat.beat(ctx.clock().getAsLong());
        World world = world();
        if (world == null || rig == null) {
            return;
        }
        rig.tickCoins(now);
        Hand h = hand;
        if (h != null) {
            runHand(world, h, now);
        }
        Hand current = hand;
        rig.update(current != null ? current.view : idleView, now);
        panel(current);
        if (current != null) {
            Player owner = Bukkit.getPlayer(current.player);
            if (owner != null && MenuService.openMenu(owner) instanceof BlackjackRoundMenu menu
                    && menu.controller() == this) {
                menu.refresh(snapshot(owner));
            }
        }
    }

    private void runHand(World world, Hand h, long now) {
        Player owner = Bukkit.getPlayer(h.player);
        if (!h.ownerGone && (owner == null || !owner.isOnline() || owner.isDead())) {
            // quit/kick/death settled his records (the stand result); the table shows the same hand
            h.ownerGone = true;
            Log.get().info("blackjack #{}: {} left the hand, it stands", machine.record().number(), h.name);
        }
        if (h.ownerGone && h.round.canStand()) {
            h.round.stand();
        }
        if (now <= h.start) {
            return;
        }
        h.round.tick();
        int at = h.round.now();
        for (int i = h.sounds.size() - 1; i >= 0; i--) {
            ScheduledSound s = h.sounds.get(i);
            if (s.at() <= at) {
                h.sounds.remove(i);
                sound(world, s.id(), ANCHOR_CENTRE, s.volume(), s.pitch());
            }
        }
        if (h.decided != BlackjackRound.Result.NONE && (!h.settled || !h.coins.isEmpty()) && --h.payoutWait <= 0) {
            if (!h.settled) {
                h.settled = true;
                for (Seat s : h.seats) {
                    settleRound(h.player, h.name, s.round(), SettleReason.NORMAL);
                    queueCoins(h.coins, BlackjackPayout.payout(s.stake(), h.decided,
                            BlackjackPayout.roll(h.seed, s.slot())));
                }
            }
            if (!h.coins.isEmpty()) {
                rig.coin(PlayerAccount.material(h.coins.poll()), h.spot, coinTarget(world, h.player), now);
                sound(world, "payout", ANCHOR_CENTRE, 0.43f, 0.95f + ctx.random().nextFloat() * 0.2f);
            }
            h.payoutWait = PAYOUT_STEP;
        }
        if (h.round.isOver() && h.settled && h.coins.isEmpty() && ++h.endHold > END_HOLD) {
            finish();
        }
    }

    /** {@code finishRound}: the table is free; the owner's open round menu closes. */
    private void finish() {
        Hand h = hand;
        hand = null;
        claimant = null;
        if (h != null) {
            Player p = Bukkit.getPlayer(h.player);
            if (p != null && MenuService.openMenu(p) instanceof BlackjackRoundMenu menu && menu.controller() == this) {
                p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
    }

    /** The spectator panel: the owner and his stake while a hand runs (the rig adds totals and result); else hidden. */
    private void panel(Hand h) {
        BlackjackPanel.Content content = null;
        if (h != null) {
            // totals and the result come from the view in the rig (the renderer's appear delay and flip hiding)
            content = new BlackjackPanel.Content(h.player, h.name, h.seats.stream().map(Seat::stake).toList());
        }
        if (content == null ? shownPanel != null : !content.equals(shownPanel)) {
            rig.panel(content);
            shownPanel = content;
        }
    }

    /** The round menu's state ({@code refreshSnapshot}). */
    BlackjackSnapshot snapshot(Player viewer) {
        long now = Bukkit.getCurrentTick();
        Hand h = hand;
        if (h == null || !h.player.equals(viewer.getUniqueId())) {
            return BlackjackSnapshot.betting(now);
        }
        BlackjackRound r = h.round;
        int phase = switch (r.phase()) {
            case DEALING -> BlackjackSnapshot.PHASE_DEALING;
            case PLAYER_TURN -> BlackjackSnapshot.PHASE_TURN;
            case PLAYER_DRAW -> BlackjackSnapshot.PHASE_DRAWING;
            case DEALER_TURN -> BlackjackSnapshot.PHASE_DEALER;
            default -> BlackjackSnapshot.PHASE_RESULT;
        };
        List<Long> playerAt = new ArrayList<>();
        List<Long> dealerAt = new ArrayList<>();
        for (BlackjackCardMotion.Card c : h.view.cards) {
            (c.toDealer ? dealerAt : playerAt).add(c.dealtAt);
        }
        int left = r.phase() == BlackjackRound.Phase.PLAYER_TURN ? Math.max(0, r.deadline() - r.now()) : 0;
        boolean afford = r.canDouble() && afford(viewer, h);
        return new BlackjackSnapshot(phase, r.player(), r.dealerVisible(), playerAt, dealerAt, left, r.result().code,
                r.canHit(), r.canStand(), r.canDouble(), r.doubled(), afford,
                h.seats.stream().map(Seat::stake).toList(), now);
    }

    /** {@code BlackjackMenu.canAffordDouble}: the same stakes again as plain items in the storage slots. */
    private static boolean afford(Player player, Hand h) {
        PlayerAccount account = new PlayerAccount(player);
        for (ItemAmount need : ItemAmount.merge(h.seats.stream().map(Seat::stake).toList())) {
            if (account.countPlain(need.item()) < need.count()) {
                return false;
            }
        }
        return true;
    }

    /** {@code tablePosition}: the player's feet in table pixels (x, z). */
    double[] tablePosition(Location l) {
        double dx = l.getX() - (machine.anchor().x() + 0.5);
        double dz = l.getZ() - (machine.anchor().z() + 0.5);
        for (int i = 0; i < machine.facing().turns(); i++) {
            double nx = dz;
            dz = -dx;
            dx = nx;
        }
        return new double[] {24.0 + dx * 16.0, 24.0 + dz * 16.0};
    }

    /** The table centre one block up (coin target fallback, proximity). */
    @Override
    protected Location centre(World world) {
        return at(world, new double[] {32, 16, 14});
    }

    /** {@code celebrate}: confetti at the player's spot, y 18 px; a blackjack adds fireworks. */
    private void celebrate(World world, int spot, boolean big) {
        double[] c = BlackjackLayout.spotCenter(spot);
        double[] px = {c[0], 18.0, c[1]};
        int n = big ? 26 : 14;
        particle(world, Particle.DUST, px, n, 0.42, 0.18, 0.42, 0.18, RED_CONFETTI);
        particle(world, Particle.DUST, px, n, 0.42, 0.18, 0.42, 0.18, GOLD_CONFETTI);
        particle(world, Particle.DUST, px, n, 0.42, 0.18, 0.42, 0.18, GREEN_CONFETTI);
        if (big) {
            particle(world, Particle.FIREWORK, px, 26, 0.36, 0.22, 0.36, 0.2, null);
        }
    }
}
