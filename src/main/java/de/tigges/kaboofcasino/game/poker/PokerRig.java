package de.tigges.kaboofcasino.game.poker;

import de.tigges.kaboofcasino.game.blackjack.BlackjackCardMotion;
import de.tigges.kaboofcasino.game.blackjack.BlackjackCardRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackDealerRig;
import de.tigges.kaboofcasino.game.blackjack.BlackjackLayout;
import de.tigges.kaboofcasino.game.blackjack.BlackjackRig;
import de.tigges.kaboofcasino.game.blackjack.DealerIdleAnimation;
import de.tigges.kaboofcasino.game.blackjack.DealerPlayAnimation;
import de.tigges.kaboofcasino.machine.Facing;
import de.tigges.kaboofcasino.machine.MachineRecord;
import de.tigges.kaboofcasino.visual.Coins;
import de.tigges.kaboofcasino.visual.Displays;
import de.tigges.kaboofcasino.visual.FloatingPanel;
import de.tigges.kaboofcasino.visual.RigMath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;

/**
 * The poker table's entities ({@code PokerTableRenderer} and the table's block models). All item displays stand at the
 * anchor's bottom centre (light sampled there = the renderer's {@code lightCoords} of the block entity, which the
 * cards use), non-persistent, tagged; culling box {@value #WIDTH} x {@value #HEIGHT} blocks, which holds the whole
 * 4 x 3 table, the dealer behind it and every card position in every facing ({@code PokerGeometryTest}):
 * <ul>
 *   <li>table: 12 displays of the block models {@code roulettecasino:block/poker_0..11} at their cell centres, turned
 *       like the blockstate's {@code "y": 90 * turns} (view range 2.0 like the blocks; felt with the printed seats and
 *       board, rim, pedestal and the shoe are in these models, the renderer draws nothing static);</li>
 *   <li>dealer: the blackjack dealer's 8 displays ({@link BlackjackDealerRig}: the renderer uses the same
 *       {@code BlackjackDealerModel}, five layers and {@code DealerPlacement}, i.e. (1.0, 0, -1.2875) from the anchor's
 *       corner for SOUTH, scale 0.9375; view range 1.0 = the renderer's 64 blocks), posed from
 *       {@code PokerTableState.dealerInput} (shuffle, deal aimed at the slot, flip, pay/collect/push towards the seat,
 *       celebrate, clear, and watching the seat during the decision); brightness = the renderer's {@code dealerLight}
 *       (the maximum of the anchor's light and the two cells of row -2 behind the table, refreshed every
 *       {@value #LIGHT_EVERY} ticks);</li>
 *   <li>cards: one display per card while {@code BlackjackCardMotion.pose} shows it at its poker slot
 *       ({@code PokerTableState.slot}: the player's two hole cards at his seat, the dealer's two face down until
 *       revealed (index 1, then 0), the five board cards), plus up to 7 shoe cards while the SHUFFLE gesture runs
 *       ({@code shuffleDeck}); view range 1.0; the hole cards change from the back to their face when revealed;</li>
 *   <li>the floating panel ({@link FloatingPanel}, a text display above the board, {@link PokerPanel}) and the payout
 *       coins ({@link Coins}) from the seat's bet spot.</li>
 * </ul>
 * Cadence as the blackjack table ({@link BlackjackRig}): moving cards every tick with the pose of {@code now + 1} and
 * interpolation 1; dealer gestures (also the watch fade and its finger tap) every tick with the pose of {@code now + 2}
 * and interpolation 2 while a player is within {@value #GESTURE_RANGE} blocks of the anchor (the renderer's view
 * distance); the idle loop and the steady watch every 8 ticks (4 during the loop's quick moves,
 * {@link BlackjackRig#idleEvery}) with the pose of {@code now + n + 1} and interpolation n + 1, only while a player is
 * within {@value #IDLE_RANGE} blocks and the shared idle budget ({@code lights.max-updates-per-tick}) grants it. All
 * times are the world's game time (the mod's renderer clock), also at spawn. A transformation equal to the last one
 * sent is not sent again (cards at rest, the legs). Thread: MAIN.
 */
public final class PokerRig {

    /** Culling box: |x|, |z| <= 2.6 blocks around the anchor's bottom centre (table to 2.5), up to 2.25 (dealer). */
    static final float WIDTH = 5.2F;
    static final float HEIGHT = 2.25F;
    static final float BODY_RANGE = 2.0F;
    static final float RANGE = 1.0F;
    /** {@code PokerTableBlock.PART}: 4 columns x 3 rows, part p at (p % 4 - 1, p / 4 - 1). */
    static final int PARTS = 12;
    /** Panel: horizontally at table px ({@code BOARD_X[2]}, {@code BOARD_Z}) like the mod; the text display grows
     * upwards from 2.25 blocks, so the five-line panel is centred near the mod's 2.5. */
    static final double PANEL_X = PokerLayout.BOARD_X[2];
    static final double PANEL_Z = PokerLayout.BOARD_Z;
    static final double PANEL_Y = 2.25;
    /** {@code eject}: the payout leaves the seat's bet spot at 15.5 px. */
    static final double COIN_Y = 15.5;
    static final int CARD_DURATION = 1;
    static final int FAST_DURATION = 2;
    static final int IDLE_RANGE = 32;
    /** {@code PokerTableRenderer.getViewDistance}: 64 blocks from the anchor block's centre. */
    static final int GESTURE_RANGE = 64;
    static final int LIGHT_EVERY = 40;
    /** {@code BlackjackCardMotion.SHUFFLE_CARDS}. */
    static final int DECK = BlackjackCardMotion.SHUFFLE_CARDS;
    /** Safety cap on card displays (a hand has 9, a full table of three 13). */
    static final int MAX_CARDS = 16;

    private final MachineRecord record;
    private final World world;
    private final Facing facing;
    private final long phase;
    private final LongPredicate idleGrant;
    private final List<ItemDisplay> body = new ArrayList<>();
    private final ItemDisplay[] dealer = new ItemDisplay[BlackjackDealerRig.PARTS.length];
    private final Transformation[] dealerSent = new Transformation[BlackjackDealerRig.PARTS.length];
    private final Map<CardKey, CardDisplay> cards = new LinkedHashMap<>();
    private final CardDisplay[] deck = new CardDisplay[DECK];
    private final FloatingPanel panel;
    private final Coins coins;
    private Display.Brightness dealerLight;
    private long lastDealer = Long.MIN_VALUE / 2;
    private long lastLight = Long.MIN_VALUE / 2;
    private PokerPanel.Content content;
    private PokerPanel.Hands shownHands;
    private PokerPanel.Content shownContent;
    private PokerTableState lastView = new PokerTableState();
    private long lastNow;
    private Component tableText;

    /** A dealt card's identity within a view (the seat tells the players' cards of a shared hand apart). */
    private record CardKey(boolean toDealer, int spot, int index, long dealtAt) {
    }

    private static final class CardDisplay {
        final ItemDisplay display;
        int value;
        Transformation sent;

        CardDisplay(ItemDisplay display, int value, Transformation sent) {
            this.display = display;
            this.value = value;
            this.sent = sent;
        }
    }

    /**
     * @param idleGrant the shared per-tick budget for idle updates of all machines
     *                  ({@code lights.max-updates-per-tick}, {@link de.tigges.kaboofcasino.visual.LightBudget}); asked
     *                  only when this table's idle dealer is due
     */
    public PokerRig(MachineRecord record, World world, LongPredicate idleGrant) {
        this.idleGrant = idleGrant;
        this.record = record;
        this.world = world;
        this.facing = record.facing();
        this.phase = BlackjackDealerRig.phase(record.anchor().x(), record.anchor().y(), record.anchor().z());
        this.panel = new FloatingPanel(record.id());
        this.coins = new Coins(record.id());
    }

    /** Every model key the rig uses. */
    static List<String> modelKeys() {
        List<String> out = new ArrayList<>();
        for (int part = 0; part < PARTS; part++) {
            out.add(bodyModel(part));
        }
        for (int i = 0; i < BlackjackDealerRig.PARTS.length; i++) {
            out.add(BlackjackDealerRig.model(i));
        }
        for (int card = -1; card < 52; card++) {
            out.add(BlackjackCardRig.model(card));
        }
        return out;
    }

    static String bodyModel(int part) {
        return "roulettecasino:block/poker_" + part;
    }

    /** The table part p's model centre: its cell centre, (p % 4 - 1, 0.5, p / 4 - 1) blocks in the south frame. */
    static Transformation bodyTransform(Facing facing, int part) {
        return RigMath.part(facing, RigMath.point(16 * (part % 4) - 8, 8, 16 * (part / 4) - 8));
    }

    /** The dealer's pose at {@code now} (renderer 70-71, 83: idle at {@code time + phase}, then the view's input). */
    static DealerIdleAnimation.Pose dealerPose(PokerTableState view, double now, long phase) {
        return BlackjackDealerRig.pose(input(view, now), now, phase);
    }

    /** The play input at {@code now} (what decides the update cadence). */
    static DealerPlayAnimation.Input input(PokerTableState view, double now) {
        DealerPlayAnimation.Input in = new DealerPlayAnimation.Input();
        view.dealerInput(now, in);
        return in;
    }

    /**
     * True while the dealer moves fast at {@code now} (a gesture, the watch fading in or out, the watch's finger tap):
     * he is then sent every tick, else at the idle cadence.
     */
    static boolean fast(PokerTableState view, long now) {
        return BlackjackDealerRig.fast(input(view, (double) now));
    }

    /** A card's display transformation at {@code now}, or null while {@code BlackjackCardMotion.pose} hides it. */
    static Transformation cardTransform(Facing facing, PokerTableState view, BlackjackCardMotion.Card card,
                                        double now) {
        BlackjackCardMotion.Pose pose = new BlackjackCardMotion.Pose();
        return BlackjackCardMotion.pose(card, view.slot(card), now, pose) ? BlackjackCardRig.transform(facing, pose)
                : null;
    }

    /** The anchor's bottom centre, where every item display stands. */
    private Location base() {
        return new Location(world, record.anchor().x() + 0.5, record.anchor().y(), record.anchor().z() + 0.5);
    }

    /** The panel's anchor point relative to the anchor block's corner (blocks). */
    static double[] panelPoint(Facing facing) {
        double[] b = BlackjackLayout.toBlock(PANEL_X, 0.0, PANEL_Z, facing.turns());
        return new double[] {b[0], PANEL_Y, b[2]};
    }

    /** Where a payout coin of {@code seat} starts, relative to the anchor block's corner (blocks). */
    static double[] coinPoint(Facing facing, int seat) {
        double[] c = PokerLayout.seatCenter(seat);
        return BlackjackLayout.toBlock(c[0], COIN_Y, c[1], facing.turns());
    }

    public boolean spawned() {
        return dealer[0] != null;
    }

    /** Table, dealer at the idle pose of the current game time; cards and panel follow with {@link #update}. */
    public void spawn() {
        despawn();
        Location base = base();
        for (int part = 0; part < PARTS; part++) {
            body.add(Displays.item(world, base, record.id(), Displays.model(bodyModel(part)),
                    bodyTransform(facing, part), BODY_RANGE, this::box));
        }
        long now = world.getGameTime();
        dealerLight = light();
        lastLight = now;
        Transformation[] t = BlackjackDealerRig.transforms(facing, dealerPose(new PokerTableState(), now, phase));
        for (int i = 0; i < dealer.length; i++) {
            dealer[i] = Displays.item(world, base, record.id(), Displays.model(BlackjackDealerRig.model(i)), t[i],
                    RANGE, d -> {
                        box(d);
                        d.setBrightness(dealerLight);
                    });
            dealerSent[i] = t[i];
        }
        lastDealer = now;
    }

    private void box(ItemDisplay d) {
        d.setDisplayWidth(WIDTH);
        d.setDisplayHeight(HEIGHT);
    }

    public void despawn() {
        body.forEach(Displays::remove);
        body.clear();
        for (int i = 0; i < dealer.length; i++) {
            Displays.remove(dealer[i]);
            dealer[i] = null;
            dealerSent[i] = null;
        }
        cards.values().forEach(c -> Displays.remove(c.display));
        cards.clear();
        for (int i = 0; i < DECK; i++) {
            if (deck[i] != null) {
                Displays.remove(deck[i].display);
                deck[i] = null;
            }
        }
        panel.hide();
        shownHands = null;
        shownContent = null;
        tableText = null;
        coins.clear();
    }

    /** True if any rig entity vanished (/kill, another plugin); the controller then respawns the rig. */
    public boolean broken() {
        for (ItemDisplay d : body) {
            if (!d.isValid()) {
                return true;
            }
        }
        for (ItemDisplay d : dealer) {
            if (d != null && !d.isValid()) {
                return true;
            }
        }
        for (CardDisplay c : cards.values()) {
            if (!c.display.isValid()) {
                return true;
            }
        }
        for (CardDisplay c : deck) {
            if (c != null && !c.display.isValid()) {
                return true;
            }
        }
        return panel.broken();
    }

    /**
     * Every server tick while the chunk is loaded: {@code view} is the table's {@code PokerTableState} as the mod's
     * block entity keeps it (a fresh one when idle), {@code gameTime} the world's game time (the view's clock).
     */
    public void update(PokerTableState view, long gameTime) {
        PokerTableState v = view == null ? new PokerTableState() : view;
        lastView = v;
        lastNow = gameTime;
        if (!spawned()) {
            return;
        }
        updateCards(v, gameTime);
        updateDeck(v, gameTime);
        updateDealer(v, gameTime);
        updatePanel(v, gameTime);
    }

    // ---- cards ------------------------------------------------------------------------------------------------------

    /** {@code PokerTableRenderer.cards} 177-185: every card of the view at its poker slot. */
    private void updateCards(PokerTableState view, long now) {
        Set<CardKey> seen = new HashSet<>();
        for (BlackjackCardMotion.Card card : view.cards) {
            Transformation shownNow = cardTransform(facing, view, card, (double) now);
            if (shownNow == null || seen.size() >= MAX_CARDS) {
                continue;
            }
            CardKey key = new CardKey(card.toDealer, card.toDealer ? -1 : card.spot, card.index, card.dealtAt);
            if (!seen.add(key)) {
                continue;
            }
            Transformation ahead = cardTransform(facing, view, card, (double) (now + CARD_DURATION));
            Transformation t = ahead != null ? ahead : shownNow;
            CardDisplay shown = cards.get(key);
            if (shown == null) {
                ItemDisplay d = Displays.item(world, base(), record.id(),
                        Displays.model(BlackjackCardRig.model(card.value)), t, RANGE, this::box);
                cards.put(key, new CardDisplay(d, card.value, t));
                continue;
            }
            if (shown.value != card.value) {
                // a hole card revealed: still face down at this moment (its flip starts 4 ticks later)
                shown.value = card.value;
                shown.display.setItemStack(Displays.model(BlackjackCardRig.model(card.value)));
            }
            send(shown, t);
        }
        for (Iterator<Map.Entry<CardKey, CardDisplay>> it = cards.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<CardKey, CardDisplay> e = it.next();
            if (!seen.contains(e.getKey())) {
                Displays.remove(e.getValue().display);
                it.remove();
            }
        }
    }

    /** The shoe cards of the SHUFFLE gesture ({@code PokerTableRenderer} 187-196). */
    private void updateDeck(PokerTableState view, long now) {
        List<BlackjackCardMotion.Pose> poses = new ArrayList<>();
        List<BlackjackCardMotion.Pose> ahead = new ArrayList<>();
        if (view.gesture == DealerPlayAnimation.Kind.SHUFFLE) {
            BlackjackCardMotion.shuffleDeck((double) (now - view.gestureAt), poses);
            BlackjackCardMotion.shuffleDeck((double) (now + CARD_DURATION - view.gestureAt), ahead);
        }
        for (int i = 0; i < DECK; i++) {
            boolean visible = i < poses.size() && poses.get(i).visible;
            if (!visible) {
                if (deck[i] != null) {
                    Displays.remove(deck[i].display);
                    deck[i] = null;
                }
                continue;
            }
            BlackjackCardMotion.Pose target = i < ahead.size() && ahead.get(i).visible ? ahead.get(i) : poses.get(i);
            Transformation t = BlackjackCardRig.transform(facing, target);
            if (deck[i] == null) {
                ItemDisplay d = Displays.item(world, base(), record.id(), Displays.model(BlackjackCardRig.model(-1)),
                        t, RANGE, this::box);
                deck[i] = new CardDisplay(d, -1, t);
            } else {
                send(deck[i], t);
            }
        }
    }

    private static void send(CardDisplay c, Transformation t) {
        if (t.equals(c.sent)) {
            return;
        }
        c.sent = t;
        c.display.setInterpolationDelay(0);
        c.display.setInterpolationDuration(CARD_DURATION);
        c.display.setTransformation(t);
    }

    // ---- dealer -----------------------------------------------------------------------------------------------------

    private void updateDealer(PokerTableState view, long now) {
        if (now - lastLight >= LIGHT_EVERY) {
            lastLight = now;
            Display.Brightness light = light();
            if (!light.equals(dealerLight)) {
                dealerLight = light;
                for (ItemDisplay d : dealer) {
                    d.setBrightness(light);
                }
            }
        }
        int duration;
        if (fast(view, now)) {
            if (!playerWithin(GESTURE_RANGE)) {
                return;
            }
            duration = FAST_DURATION;
        } else {
            int every = BlackjackRig.idleEvery(now, phase);
            // the shared idle budget (lights.max-updates-per-tick) is asked only when this table is due
            if (now - lastDealer < every || !playerWithin(IDLE_RANGE) || !idleGrant.test(now)) {
                return;
            }
            duration = every + 1;
        }
        lastDealer = now;
        Transformation[] t = BlackjackDealerRig.transforms(facing, dealerPose(view, (double) (now + duration), phase));
        for (int i = 0; i < dealer.length; i++) {
            if (t[i].equals(dealerSent[i])) {
                continue;
            }
            dealerSent[i] = t[i];
            dealer[i].setInterpolationDelay(0);
            dealer[i].setInterpolationDuration(duration);
            dealer[i].setTransformation(t[i]);
        }
    }

    /** The cells whose light the dealer takes ({@code PokerTableRenderer} 72-78): the anchor, (0, -2) and (1, -2). */
    static int[][] lightCells(Facing facing) {
        int[][] cells = {{0, 0}, {0, -2}, {1, -2}};
        int[][] out = new int[cells.length][];
        for (int k = 0; k < cells.length; k++) {
            int x = cells[k][0];
            int z = cells[k][1];
            for (int i = 0; i < facing.turns(); i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            out[k] = new int[] {x, z};
        }
        return out;
    }

    /** {@code LightCoordsUtil.max} over {@link #lightCells}: block and sky light each at their maximum. */
    private Display.Brightness light() {
        int block = 0;
        int sky = 0;
        for (int[] c : lightCells(facing)) {
            int bx = record.anchor().x() + c[0];
            int bz = record.anchor().z() + c[1];
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                continue;
            }
            Block b = world.getBlockAt(bx, record.anchor().y(), bz);
            block = Math.max(block, b.getLightFromBlocks());
            sky = Math.max(sky, b.getLightFromSky());
        }
        return new Display.Brightness(block, sky);
    }

    /** A player's camera within {@code range} blocks of the anchor block's centre (the renderer's distance rule). */
    private boolean playerWithin(int range) {
        Location centre = new Location(world, record.anchor().x() + 0.5, record.anchor().y() + 0.5,
                record.anchor().z() + 0.5);
        double max = (double) range * range;
        for (Player p : world.getPlayers()) {
            if (p.getEyeLocation().distanceSquared(centre) <= max) {
                return true;
            }
        }
        return false;
    }

    // ---- panel ------------------------------------------------------------------------------------------------------

    /**
     * Who plays and his stake; {@code null} hides the panel. The rig shows it while the view is active and fills in
     * the hands and the result from the view every tick ({@code extractPanel}); calling this only on change is
     * enough.
     */
    public void panel(PokerPanel.Content content) {
        this.content = content;
        if (spawned()) {
            updatePanel(lastView, lastNow);
        }
    }

    private void updatePanel(PokerTableState view, long now) {
        if (!PokerPanel.visible(content, view)) {
            if (shownContent != null) {
                panel.hide();
                shownContent = null;
                shownHands = null;
            }
            return;
        }
        PokerPanel.Hands hands = PokerPanel.hands(view, (double) now);
        if (content.equals(shownContent) && hands.equals(shownHands) && !panel.broken()) {
            return;
        }
        Component text = PokerPanel.render(content, hands);
        double[] p = panelPoint(facing);
        panel.show(world, new Location(world, record.anchor().x() + p[0], record.anchor().y() + p[1],
                record.anchor().z() + p[2]), text);
        shownContent = content;
        shownHands = hands;
    }

    /** The panel of a table with several players, already rendered ({@code null} hides it). */
    public void tablePanel(Component text) {
        if (text == null) {
            if (tableText != null) {
                panel.hide();
                tableText = null;
            }
            return;
        }
        if (spawned() && (!text.equals(tableText) || panel.broken())) {
            double[] p = panelPoint(facing);
            panel.show(world, new Location(world, record.anchor().x() + p[0], record.anchor().y() + p[1],
                    record.anchor().z() + p[2]), text);
            tableText = text;
        }
    }

    // ---- coins ------------------------------------------------------------------------------------------------------

    /** One payout coin from {@code seat}'s bet spot ({@code eject}: seat centre, 15.5 px) towards {@code target}. */
    public void coin(String item, int seat, Location target, long now) {
        double[] c = coinPoint(facing, PokerLayout.clampSeat(seat));
        coins.spawn(world, new Location(world, record.anchor().x() + c[0], record.anchor().y() + c[1],
                record.anchor().z() + c[2]), item, target, now);
    }

    public void tickCoins(long now) {
        coins.tick(now);
    }
}
