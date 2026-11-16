package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.deco.display.DealerStationHook;
import de.tigges.kaboofcasino.deco.display.DecoBlock;
import de.tigges.kaboofcasino.deco.display.DecoRecord;
import de.tigges.kaboofcasino.deco.display.DecoType;
import de.tigges.kaboofcasino.deco.display.DisplayDecoService;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.CasinoItems;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.util.Threads;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.view.MerchantView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

/**
 * The croupier villager of MineVegas Casino (games.md 10) for vanilla clients: a display NPC at a croupier counter
 * (Jack's option A). Keeps the index of loaded croupiers (records in the chunk PDC, {@link CroupierRecords}), rebuilds
 * their entities at chunk load and when they vanish, runs their idle loop, trade screen, hiring and sounds, applies
 * vanilla's job-site rule when a counter is lost, and asks the {@link CroupierSpawnTrigger} for a new croupier when a
 * player right-clicks a free counter (never on its own).
 *
 * <ul>
 *   <li>Trade: a right click with the main hand (not sneaking, as a villager) opens a vanilla merchant screen titled
 *       with the mod's {@code entity.roulettecasino.villager.croupier} ("Croupier"): 24 emeralds for a hiring card, 64
 *       uses, 2 villager experience, price multiplier 0 ({@link CroupierTrade}); uses and experience live in the
 *       record; restocked by vanilla's rule while it has its counter (it works at the counter in working hours, at
 *       most every 300 ticks: up to 2 restocks a day, the second 2400 ticks after the first). One player at a time:
 *       the mod's busy rule
 *       for hiring ({@code message.roulettecasino.croupier_busy}) also answers a second player who wants to trade (a
 *       vanilla villager would just ignore him). Sounds as the villager makes them: yes/no when the payment changes,
 *       its ambient (trade) voice now and then, the cartographer's work sound when it works, and the trade's
 *       experience orb at the croupier.</li>
 *   <li>Hiring: a hiring card on the croupier ({@link CroupierHiring}): busy check, the kept villager inventory drops
 *       at its spot, the croupier is gone for good, the badge, the burst, the sounds and the stream.</li>
 *   <li>Counter lost ({@link DealerStationHook#onRemoved}): vanilla's job-site rule. Before its first trade (no
 *       experience) the croupier loses its profession and is the villager again (its snapshot, at its spot); with
 *       experience it keeps its profession: it stays without a counter (tradeable and hireable, no restock) and
 *       re-attaches to a free counter placed right next to it.</li>
 * </ul>
 * Thread: MAIN.
 */
public final class CroupierService implements DealerStationHook, CroupierSpawnTrigger.Spawns {

    /** Ticks between the rig checks and the near-player refresh. */
    public static final int SCAN_TICKS = 20;
    /** Rig spawns per tick after chunk loads. */
    static final int SPAWNS_PER_TICK = 4;
    /** A trade screen closes when its player is further from the croupier (vanilla: interaction range + 4). */
    static final double TRADE_RANGE = 8.0;
    /** Its sounds are heard this far (volume 1). */
    static final int SOUND_RANGE = 16;
    static final Component TITLE = Component.translatable("entity.roulettecasino.villager.croupier", "Croupier");
    /** {@code message.roulettecasino.croupier_busy} with the mod's text in the casino's language as fallback. */
    static Component busy() {
        return Component.translatable("message.roulettecasino.croupier_busy",
                de.tigges.kaboofcasino.config.Language.text("This croupier is busy.",
                        "Dieser Croupier ist beschäftigt."));
    }

    private final CasinoContext ctx;
    private final DisplayDecoService deco;
    private final CroupierSpawnTrigger trigger;
    private final Map<UUID, Croupier> byId = new LinkedHashMap<>();
    /** Counter (deco record id) -> its croupier. */
    private final Map<UUID, UUID> byCounter = new HashMap<>();
    /** Loaded croupiers by chunk ({@code world chunkKey}). */
    private final Map<String, Set<UUID>> byChunk = new HashMap<>();
    /** The two blocks each croupier's body takes (no second croupier is placed there). */
    private final Map<Pos, UUID> byCell = new HashMap<>();
    /** Player with an open trade screen -> croupier. */
    private final Map<UUID, UUID> byTrader = new HashMap<>();
    private final Deque<UUID> pendingSpawns = new ArrayDeque<>();
    private final Set<UUID> pendingChecks = new LinkedHashSet<>();
    private final List<CroupierHiring.Stream> streams = new ArrayList<>();
    private final SplittableRandom random = new SplittableRandom();
    private boolean enabled;

    public CroupierService(CasinoContext ctx, DisplayDecoService deco) {
        this(ctx, deco, new VillagerOnCounterTrigger());
    }

    CroupierService(CasinoContext ctx, DisplayDecoService deco, CroupierSpawnTrigger trigger) {
        this.ctx = ctx;
        this.deco = deco;
        this.trigger = trigger;
    }

    /** Listener, the dealer station hook, the croupiers of loaded chunks. */
    public void enable() {
        Bukkit.getPluginManager().registerEvents(new CroupierListener(this), ctx.plugin());
        deco.dealerStationHook(this);
        enabled = true;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunkLoaded(chunk);
            }
        }
        Log.get().info("Croupiers enabled ({}): {} in loaded chunks", trigger.name(), byId.size());
    }

    /** Closes the trade screens and removes every entity (records stay in the chunks). */
    public void disable() {
        enabled = false;
        deco.dealerStationHook(null);
        for (Croupier c : List.copyOf(byId.values())) {
            closeScreen(c);
            if (c.rig != null) {
                c.rig.despawn();
            }
        }
        byId.clear();
        byCounter.clear();
        byChunk.clear();
        byCell.clear();
        byTrader.clear();
        pendingSpawns.clear();
        pendingChecks.clear();
        streams.clear();
    }

    // ---- index ------------------------------------------------------------------------------------------------------

    /** The loaded croupiers' records (for commands and tests of the wiring). */
    public List<CroupierRecord> all() {
        List<CroupierRecord> out = new ArrayList<>(byId.size());
        for (Croupier c : byId.values()) {
            out.add(c.record());
        }
        return out;
    }

    private static String chunkKey(Pos p) {
        return p.world() + " " + p.chunkKey();
    }

    /** The two blocks a croupier's body takes ({@link CroupierRig#lightBlocks}). */
    static List<Pos> cells(CroupierRecord r) {
        int[] ys = CroupierRig.lightBlocks(r.standing());
        Pos s = r.stand();
        return List.of(new Pos(s.world(), s.x(), ys[0], s.z()), new Pos(s.world(), s.x(), ys[1], s.z()));
    }

    private void register(Croupier c) {
        byId.put(c.id(), c);
        index(c);
    }

    private void unregister(Croupier c) {
        unindex(c);
        byId.remove(c.id());
        if (c.trader != null) {
            byTrader.remove(c.trader, c.id());
        }
    }

    /** Chunk, cells and counter of the croupier's current record. */
    private void index(Croupier c) {
        CroupierRecord r = c.record();
        byChunk.computeIfAbsent(chunkKey(r.stand()), k -> new HashSet<>()).add(c.id());
        for (Pos p : cells(r)) {
            byCell.put(p, c.id());
        }
        if (r.hasCounter()) {
            byCounter.put(r.counter(), c.id());
        }
    }

    private void unindex(Croupier c) {
        CroupierRecord r = c.record();
        Set<UUID> inChunk = byChunk.get(chunkKey(r.stand()));
        if (inChunk != null) {
            inChunk.remove(c.id());
            if (inChunk.isEmpty()) {
                byChunk.remove(chunkKey(r.stand()));
            }
        }
        for (Pos p : cells(r)) {
            byCell.remove(p, c.id());
        }
        if (r.hasCounter()) {
            byCounter.remove(r.counter(), c.id());
        }
    }

    /** Stores a changed record; the index follows a changed spot or counter. */
    private void update(Croupier c, CroupierRecord updated) {
        unindex(c);
        c.record(updated);
        index(c);
        World w = world(updated.stand());
        if (w != null && w.isChunkLoaded(updated.stand().chunkX(), updated.stand().chunkZ())) {
            CroupierRecords.put(w.getChunkAt(updated.stand().chunkX(), updated.stand().chunkZ()), updated);
        }
    }

    @Override
    public World world(Pos p) {
        return Bukkit.getWorld(NamespacedKey.fromString(p.world()));
    }

    // ---- chunks -----------------------------------------------------------------------------------------------------

    void chunkLoaded(Chunk chunk) {
        if (!enabled || !CroupierRecords.has(chunk)) {
            return;
        }
        for (CroupierRecord r : CroupierRecords.read(chunk)) {
            if (byId.containsKey(r.id())) {
                continue;
            }
            Croupier c = new Croupier(r);
            if (r.hasCounter() && byCounter.containsKey(r.counter())) {
                Log.critical("CROUPIER_COUNTER_TWICE", "croupier {} at {} names the counter of croupier {}; it stays "
                        + "without a counter", r.id(), r.stand(), byCounter.get(r.counter()));
                c.record(r.withoutCounter(r.standing()));
                CroupierRecords.put(chunk, c.record());
            }
            register(c);
            pendingSpawns.add(c.id());
            pendingChecks.add(c.id());
        }
    }

    void chunkUnloaded(Chunk chunk) {
        Set<UUID> ids = byChunk.get(chunk.getWorld().getKey().asString() + " "
                + Pos.chunkKey(chunk.getX(), chunk.getZ()));
        if (ids == null) {
            return;
        }
        for (UUID id : List.copyOf(ids)) {
            Croupier c = byId.get(id);
            if (c != null) {
                closeScreen(c);
                if (c.rig != null) {
                    c.rig.despawn();
                }
                unregister(c);
            }
        }
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    /** Every server tick. */
    public void tick(long tick) {
        if (!enabled) {
            return;
        }
        for (int i = 0; i < SPAWNS_PER_TICK && !pendingSpawns.isEmpty(); i++) {
            Croupier c = byId.get(pendingSpawns.poll());
            if (c != null && c.rig == null) {
                guarded(c, "spawn", () -> spawnRig(c));
            }
        }
        for (UUID id : List.copyOf(pendingChecks)) {
            Croupier c = byId.get(id);
            if (c != null && c.rig != null) {
                pendingChecks.remove(id);
                guarded(c, "counter check", () -> checkCounter(c));
            } else if (c == null) {
                pendingChecks.remove(id);
            }
        }
        boolean scan = tick % SCAN_TICKS == 0;
        for (Croupier c : List.copyOf(byId.values())) {
            if (byId.containsKey(c.id())) {
                guarded(c, "tick", () -> tickOne(c, tick, scan));
            }
        }
        streams.removeIf(s -> {
            try {
                return !s.tick();
            } catch (RuntimeException e) {
                Log.incident("croupier hiring stream", e);
                return true;
            }
        });
    }

    private void spawnRig(Croupier c) {
        World w = world(c.record().stand());
        Pos s = c.record().stand();
        if (w == null || !w.isChunkLoaded(s.chunkX(), s.chunkZ())) {
            return;
        }
        c.rig = new CroupierRig(c.id(), w, c.record().standing());
        c.rig.spawn(w.getGameTime());
    }

    /** After a chunk load: the counter the record names must still be there (same chunk, so it is loaded). */
    private void checkCounter(Croupier c) {
        CroupierRecord r = c.record();
        if (!r.hasCounter()) {
            return;
        }
        Optional<DecoBlock> station = deco.byId(r.counter());
        if (station.isPresent() && station.get().type() == DecoType.DEALER_STATION
                && station.get().anchor().equals(r.counterPos())) {
            return;
        }
        counterLost(c, "its counter at " + r.counterPos() + " is missing at chunk load");
    }

    private void tickOne(Croupier c, long tick, boolean scan) {
        if (c.rig == null) {
            return;
        }
        World w = world(c.record().stand());
        if (w == null) {
            return;
        }
        if (scan) {
            Location centre = c.rig.centre();
            c.heard = playerWithin(w, centre, SOUND_RANGE);
            c.watched = c.heard || playerWithin(w, centre, CroupierRig.IDLE_RANGE);
            if (c.rig.broken()) {
                Log.get().info("Croupier {} at {}: entities vanished, respawning", c.id(), c.record().stand());
                c.rig.spawn(w.getGameTime());
            }
        }
        work(c, w);
        UUID id = c.id();
        c.rig.tick(w.getGameTime(), c.watched, () -> ctx.lights().grant(id, tick));
        screen(c, w);
        if (c.heard && random.nextInt(1000) < c.ambient++) {
            c.ambient = -CroupierTrade.AMBIENT_INTERVAL;
            voice(c, w, c.trading() ? Sound.ENTITY_VILLAGER_TRADE : Sound.ENTITY_VILLAGER_AMBIENT);
        }
    }

    private static boolean playerWithin(World w, Location at, int range) {
        double max = (double) range * range;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= max) {
                return true;
            }
        }
        return false;
    }

    private void voice(Croupier c, World w, Sound sound) {
        w.playSound(c.rig.feet(), sound, SoundCategory.NEUTRAL, 1.0F, CroupierTrade.voicePitch(random));
    }

    /**
     * Vanilla's {@code WorkAtPoi} at the counter: in working hours ({@link CroupierTrade#workHours}), at most every
     * {@value CroupierTrade#WORK_CHECK_TICKS} game ticks and then on a coin flip, the croupier works: its work sound
     * (the cartographer's) and {@code shouldRestock}/{@code restock} ({@link CroupierTrade#shouldRestock}). Not without
     * a counter (no job site) and not while a player trades (deviation: vanilla would resend the offers).
     */
    private void work(Croupier c, World w) {
        CroupierRecord r = c.record();
        long now = w.getGameTime();
        if (!r.hasCounter() || c.trading() || !CroupierTrade.workHours(w.getTime())
                || now - c.lastWork < CroupierTrade.WORK_CHECK_TICKS || random.nextInt(2) != 0) {
            return;
        }
        c.lastWork = now;
        if (c.heard) {
            voice(c, w, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER);
        }
        CroupierTrade.RestockCheck check = CroupierTrade.shouldRestock(r.restock(), now,
                CroupierTrade.day(w.getFullTime()), r.uses());
        if (check.restock()) {
            CroupierTrade.RestockState after = CroupierTrade.restocked(check.state(), now);
            update(c, r.withRestock(0, after));
            Log.get().info("Croupier {} at {} restocked ({} use(s) back, restock {} today)", c.id(), r.stand(),
                    r.uses(), after.today());
        } else if (!check.state().equals(r.restock())) {
            update(c, r.withRestock(r.uses(), check.state()));
        }
    }

    // ---- trade screen -----------------------------------------------------------------------------------------------

    /** Opens the trade screen, or says the croupier is busy. */
    private void trade(Player player, Croupier c) {
        if (c.trading()) {
            if (!player.getUniqueId().equals(c.trader)) {
                player.sendActionBar(busy());
            }
            return;
        }
        if (world(c.record().stand()) == null) {
            return;
        }
        if (c.merchant == null) {
            c.merchant = Bukkit.createMerchant();
        }
        int discount = CroupierTrade.heroDiscount(CroupierTrade.PRICE, heroLevel(player));
        c.merchant.setRecipes(List.of(CroupierTrade.recipe(ctx.messages().get(), c.record().uses(), -discount)));
        MerchantView view = MenuType.MERCHANT.builder().merchant(c.merchant).title(TITLE).checkReachable(false)
                .build(player);
        player.openInventory(view);
        if (player.getOpenInventory() instanceof MerchantView open && open.getMerchant() == c.merchant) {
            c.trader = player.getUniqueId();
            c.inputs = inputs(open.getTopInventory());
            byTrader.put(player.getUniqueId(), c.id());
        }
    }

    /** The amplifier of the player's Hero of the Village effect, -1 without. */
    private static int heroLevel(Player player) {
        PotionEffect hero = player.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        return hero == null ? -1 : hero.getAmplifier();
    }

    /** The payment slots and the chosen offer, as a signature. */
    private static String inputs(MerchantInventory inv) {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < 2; slot++) {
            ItemStack s = inv.getItem(slot);
            sb.append(s == null || s.isEmpty() ? "-" : s.getType().getKey() + "x" + s.getAmount()).append('|');
        }
        return sb.append(inv.getSelectedRecipeIndex()).toString();
    }

    /** The open screen: still open, its player in range; vanilla's yes/no voice when the payment changed. */
    private void screen(Croupier c, World w) {
        if (c.trader == null) {
            return;
        }
        Player p = Bukkit.getPlayer(c.trader);
        if (p == null || !(p.getOpenInventory() instanceof MerchantView view) || view.getMerchant() != c.merchant) {
            endScreen(c);
            return;
        }
        if (!p.getWorld().equals(w) || p.getLocation().distanceSquared(c.rig.feet()) > TRADE_RANGE * TRADE_RANGE) {
            p.closeInventory(InventoryCloseEvent.Reason.CANT_USE);
            endScreen(c);
            return;
        }
        MerchantInventory inv = view.getTopInventory();
        String now = inputs(inv);
        if (!now.equals(c.inputs)) {
            c.inputs = now;
            // AbstractVillager.notifyTradeUpdated: at most once per 20 ticks of the ambient clock
            if (c.ambient > -CroupierTrade.AMBIENT_INTERVAL + 20) {
                c.ambient = -CroupierTrade.AMBIENT_INTERVAL;
                ItemStack result = inv.getItem(2);
                voice(c, w, result == null || result.isEmpty() ? Sound.ENTITY_VILLAGER_NO
                        : Sound.ENTITY_VILLAGER_YES);
            }
        }
    }

    private void endScreen(Croupier c) {
        if (c.trader != null) {
            byTrader.remove(c.trader, c.id());
        }
        c.trader = null;
        c.inputs = "";
    }

    /** Closes the trade screen of whoever has it open. */
    private void closeScreen(Croupier c) {
        if (c.trader == null) {
            return;
        }
        Player p = Bukkit.getPlayer(c.trader);
        endScreen(c);
        if (p != null && p.getOpenInventory() instanceof MerchantView view && view.getMerchant() == c.merchant) {
            p.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    /** A trade screen closed. */
    void closed(Player player, InventoryView view) {
        if (view instanceof MerchantView m) {
            Croupier c = byMerchant(m.getMerchant());
            if (c != null && player.getUniqueId().equals(c.trader)) {
                endScreen(c);
            }
        }
    }

    /** The loaded croupier whose merchant this is, or null. */
    private Croupier byMerchant(Merchant merchant) {
        if (merchant == null) {
            return null;
        }
        for (Croupier c : byId.values()) {
            if (c.merchant == merchant) {
                return c;
            }
        }
        return null;
    }

    /** A trade went through (MONITOR, not cancelled): uses and experience into the record, the orb at the croupier. */
    void purchased(Player player, Merchant merchant, boolean countsUse) {
        UUID id = byTrader.get(player.getUniqueId());
        Croupier c = id == null ? null : byId.get(id);
        if (c == null || merchant != c.merchant) {
            c = byMerchant(merchant);
            if (c == null) {
                return;
            }
        }
        CroupierRecord r = c.record();
        int xp = r.xp() + CroupierTrade.VILLAGER_XP;
        update(c, r.withTrade(countsUse ? r.uses() + 1 : r.uses(), xp));
        c.ambient = -CroupierTrade.AMBIENT_INTERVAL;
        World w = world(r.stand());
        int orb = CroupierTrade.tradeOrb(r.xp(), xp, random);
        if (w != null && c.rig != null) {
            w.spawn(c.rig.feet().add(0, 0.5, 0), ExperienceOrb.class, o -> o.setExperience(orb));
        }
        if (CroupierTrade.levelOf(xp) > CroupierTrade.levelOf(r.xp())) {
            Log.get().info("Croupier {} at {} reached level {} ({} xp)", c.id(), r.stand(), CroupierTrade.levelOf(xp),
                    xp);
        }
    }

    void quit(Player player) {
        UUID id = byTrader.get(player.getUniqueId());
        Croupier c = id == null ? null : byId.get(id);
        if (c != null) {
            endScreen(c);
        }
    }

    // ---- clicks -----------------------------------------------------------------------------------------------------

    /** The croupier an entity (its click box) belongs to, or null. */
    private Croupier owner(Entity e) {
        String tag = e.getPersistentDataContainer().get(Keys.PART, PersistentDataType.STRING);
        if (tag == null) {
            return null;
        }
        try {
            Croupier c = byId.get(UUID.fromString(tag));
            return c != null && c.rig != null && c.rig.owns(e) ? c : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * A right click on an entity: true if it is a croupier's click box (the event is then cancelled). The mod's
     * {@code CroupierHiring.onUse} first (a card in the clicked hand, or in the off hand with an empty main hand), else
     * the villager's trade screen (main hand, not sneaking).
     */
    boolean click(Player player, Entity clicked, EquipmentSlot slot) {
        Croupier c = owner(clicked);
        if (c == null) {
            return false;
        }
        if ((slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND) || FakePlayers.isFake(player)
                || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }
        if (!player.hasPermission("kaboofcasino.use")) {
            ctx.messages().get().actionBar(player, Msg.ERROR_NO_PERMISSION);
            return true;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        CroupierHiring.Hand hand = CroupierHiring.cardHand(
                slot == EquipmentSlot.HAND ? CroupierHiring.Hand.MAIN : CroupierHiring.Hand.OFF, card(main),
                main.isEmpty(), card(inv.getItemInOffHand()));
        if (hand != null) {
            hire(player, c, hand);
        } else if (slot == EquipmentSlot.HAND && !player.isSneaking()) {
            trade(player, c);
        }
        return true;
    }

    private static boolean card(ItemStack stack) {
        return CasinoItems.of(stack).filter(CasinoItem.CROUPIER_HIRING_CARD::equals).isPresent();
    }

    // ---- hiring -----------------------------------------------------------------------------------------------------

    /** {@code CroupierHiring.hire}. */
    private void hire(Player player, Croupier c, CroupierHiring.Hand hand) {
        if (c.trading()) {
            player.sendActionBar(busy());
            return;
        }
        CroupierRecord r = c.record();
        World w = world(r.stand());
        Location centre = c.rig.centre();
        Pos block = cells(r).get(0);
        int dropped = dropItems(w, block, r.items());
        remove(c);
        EquipmentSlot slot = hand == CroupierHiring.Hand.MAIN ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItem(slot);
        ItemStack badge = CasinoItems.create(CasinoItem.CROUPIER_BADGE, ctx.messages().get(), 1);
        CroupierHiring.Outcome outcome = CroupierHiring.outcome(player.getGameMode() == GameMode.CREATIVE,
                held.getAmount());
        if (outcome.consumeCard()) {
            if (outcome.badgeIntoHand()) {
                inv.setItem(slot, badge);
            } else {
                ItemStack rest = held.clone();
                rest.setAmount(held.getAmount() - 1);
                inv.setItem(slot, rest);
                give(player, badge);
            }
        } else {
            give(player, badge);
        }
        CroupierHiring.burst(w, centre);
        streams.add(new CroupierHiring.Stream(w, player.getUniqueId(), centre));
        Log.get().info("Croupier {} at {} hired by {} ({}): badge given{}, {} villager item(s) dropped", c.id(),
                r.stand(), player.getName(), player.getUniqueId(),
                outcome.consumeCard() ? ", card used" : " (creative)", dropped);
    }

    private static void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** The villager's kept inventory at {@code block}, as {@code Block.popResource} drops it. */
    private int dropItems(World w, Pos block, List<String> items) {
        int n = 0;
        for (String item : items) {
            ItemStack stack;
            try {
                stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(item));
            } catch (RuntimeException e) {
                Log.critical("CROUPIER_ITEM_UNREADABLE", "croupier at {}: a kept villager item cannot be read: {}",
                        block, e.toString());
                continue;
            }
            if (stack.isEmpty()) {
                continue;
            }
            double h = 0.125; // EntityType.ITEM height / 2
            Location at = new Location(w, block.x() + 0.5 + random.nextDouble(-0.25, 0.25),
                    block.y() + 0.5 + random.nextDouble(-0.25, 0.25) - h, block.z() + 0.5 + random.nextDouble(-0.25,
                    0.25));
            w.dropItem(at, stack);
            n++;
        }
        return n;
    }

    /** Removes a croupier for good: screen, entities, index, record. */
    private void remove(Croupier c) {
        closeScreen(c);
        if (c.rig != null) {
            c.rig.despawn();
        }
        unregister(c);
        Pos s = c.record().stand();
        World w = world(s);
        if (w != null && w.isChunkLoaded(s.chunkX(), s.chunkZ())) {
            CroupierRecords.remove(w.getChunkAt(s.chunkX(), s.chunkZ()), c.id());
        }
    }

    // ---- the counter (DealerStationHook) ----------------------------------------------------------------------------

    @Override
    public void onPlaced(DecoRecord station, Player placer) {
        Threads.requireMain();
        if (!enabled) {
            return;
        }
        if (!byCounter.containsKey(station.id())) {
            Set<UUID> inChunk = byChunk.get(chunkKey(station.anchor()));
            for (UUID id : inChunk == null ? List.<UUID>of() : List.copyOf(inChunk)) {
                Croupier c = byId.get(id);
                if (c == null || c.record().hasCounter()) {
                    continue;
                }
                Optional<CroupierPlacement.Stand> at = CroupierPlacement.attach(c.record().standing(),
                        station.anchor());
                if (at.isPresent()) {
                    attach(c, station, at.get());
                    break;
                }
            }
        }
        trigger.stationPlaced(this, station, placer);
    }

    private void attach(Croupier c, DecoRecord station, CroupierPlacement.Stand at) {
        CroupierPlacement.Stand before = c.record().standing();
        update(c, c.record().withCounter(station.id(), station.anchor(), at));
        if (!at.equals(before)) {
            respawn(c);
        }
        Log.get().info("Croupier {} at {} took the new counter at {} (facing {})", c.id(), at.feet(),
                station.anchor(), at.facing());
    }

    private void respawn(Croupier c) {
        if (c.rig != null) {
            c.rig.despawn();
            c.rig = null;
        }
        spawnRig(c);
    }

    /**
     * A right click on a counter (option A's only trigger): on a free counter a villager standing on it may become its
     * croupier ({@link CroupierSpawnTrigger#stationUsed}); otherwise the player is told why not (action bar), also with
     * an item in the hand (a deco carrier never takes a held item anyway). A counter with a croupier does nothing (the
     * mod's station has no use).
     */
    @Override
    public boolean onUse(Player player, DecoRecord station) {
        Threads.requireMain();
        if (!enabled || byCounter.containsKey(station.id()) || FakePlayers.isFake(player)
                || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        Messages m = ctx.messages().get();
        if (!player.hasPermission("kaboofcasino.use")) {
            m.actionBar(player, Msg.ERROR_NO_PERMISSION);
            return true;
        }
        CroupierEligibility.Verdict verdict = trigger.stationUsed(this, station, player,
                inReach(player, station.anchor()));
        if (verdict == CroupierEligibility.Verdict.OK) {
            return true;
        }
        m.actionBar(player, hint(verdict));
        return true;
    }

    /** The action-bar answer to a click on a free counter that made no croupier. */
    static Msg hint(CroupierEligibility.Verdict verdict) {
        return switch (verdict) {
            case NOBODY, GONE, NOT_ABOVE_COUNTER -> Msg.CROUPIER_HINT;
            case NOT_ON_TOP -> Msg.CROUPIER_NOT_ON_TOP;
            case NITWIT -> Msg.CROUPIER_NITWIT;
            case EMPLOYED, LEVEL, EXPERIENCE, TRADES -> Msg.CROUPIER_EMPLOYED;
            case BABY -> Msg.CROUPIER_BABY;
            case LEASHED -> Msg.CROUPIER_LEASHED;
            case NAMED -> Msg.CROUPIER_NAMED;
            case RIDING, PASSENGERS -> Msg.CROUPIER_RIDING;
            case TRADING, SLEEPING, COUNTER_TAKEN, OK -> Msg.CROUPIER_BUSY_VILLAGER;
            case OUT_OF_REACH -> Msg.CROUPIER_OUT_OF_REACH;
        };
    }

    /**
     * Vanilla's server-side reach check for a block ({@code isWithinBlockInteractionRange(pos, 1.0)}): the eyes within
     * the block interaction range plus 1 of the block's box.
     */
    static boolean inReach(Player player, Pos block) {
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = (range == null ? 4.5 : range.getValue()) + 1.0;
        Location eye = player.getEyeLocation();
        return distanceSquared(eye.getX(), eye.getY(), eye.getZ(), block) <= reach * reach;
    }

    /** From a point to the box of {@code block}. */
    static double distanceSquared(double x, double y, double z, Pos block) {
        double dx = Math.max(0, Math.max(block.x() - x, x - (block.x() + 1)));
        double dy = Math.max(0, Math.max(block.y() - y, y - (block.y() + 1)));
        double dz = Math.max(0, Math.max(block.z() - z, z - (block.z() + 1)));
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public void onRemoved(DecoRecord station) {
        Threads.requireMain();
        UUID id = byCounter.get(station.id());
        Croupier c = id == null ? null : byId.get(id);
        if (c != null) {
            counterLost(c, "its counter at " + station.anchor() + " was removed");
        }
    }

    /**
     * Vanilla's job-site rule: without experience the croupier is the villager again (its snapshot at its spot); with
     * experience it stays, without a counter (one on the counter's top steps down).
     */
    private void counterLost(Croupier c, String why) {
        CroupierRecord r = c.record();
        World w = world(r.stand());
        if (w == null) {
            return;
        }
        if (r.xp() == 0) {
            if (r.snapshot() == null) {
                remove(c);
                Log.get().info("Croupier {} at {} is gone: {} before its first trade (no villager to give back)",
                        c.id(), r.stand(), why);
                return;
            }
            Location at = c.rig != null ? c.rig.feet() : new Location(w, r.stand().x() + 0.5,
                    r.stand().y() + r.lift(), r.stand().z() + 0.5);
            Entity villager = restore(r, at);
            if (villager != null) {
                remove(c);
                Log.get().info("Croupier {} at {} is a villager again ({}): {} before its first trade", c.id(),
                        r.stand(), villager.getUniqueId(), why);
                return;
            }
            // restore failed (logged): keep it as a croupier without counter, nothing is lost
        }
        CroupierPlacement.Stand s = CroupierPlacement.afterLoss(r, cells(w));
        update(c, r.withoutCounter(s));
        if (!s.equals(r.standing())) {
            respawn(c);
        }
        Log.get().info("Croupier {} at {} keeps its profession without a counter ({} xp): {}", c.id(), s.feet(),
                r.xp(), why);
    }

    /** The villager from the record's snapshot at {@code at}; null (logged) if it cannot be made. */
    private static Entity restore(CroupierRecord r, Location at) {
        String now = Bukkit.getMinecraftVersion();
        if (r.snapshotVersion() != null && !r.snapshotVersion().equals(now)) {
            Log.get().warn("Croupier {}: its villager snapshot is from Minecraft {} (now {}); restoring anyway", r.id(),
                    r.snapshotVersion(), now);
        }
        try {
            EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(r.snapshot());
            return snapshot.createEntity(at);
        } catch (RuntimeException e) {
            Log.critical("CROUPIER_RESTORE_FAILED", "croupier {} at {}: villager {} cannot be restored ({}); the "
                    + "croupier stays without a counter", r.id(), r.stand(), r.villager(), e.toString());
            return null;
        }
    }

    /** The block facts placement needs; never loads a chunk. */
    private Function<Pos, CroupierPlacement.Cell> cells(World w) {
        return p -> {
            if (p.y() < w.getMinHeight() || p.y() >= w.getMaxHeight() || !w.isChunkLoaded(p.chunkX(), p.chunkZ())) {
                return CroupierPlacement.Cell.BLOCKED;
            }
            if (deco.isCell(p) || ctx.machines().at(p).isPresent() || byCell.containsKey(p)) {
                return CroupierPlacement.Cell.BLOCKED;
            }
            Block b = p.block(w);
            double top = 0.0;
            for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
                top = Math.max(top, box.getMaxY());
            }
            return new CroupierPlacement.Cell(false, b.isLiquid(), b.isPassable(), top);
        };
    }

    // ---- spawns (CroupierSpawnTrigger.Spawns) -----------------------------------------------------------------------

    @Override
    public boolean spawn(DecoRecord station, CroupierSpawnTrigger.Origin origin, Player player) {
        Threads.requireMain();
        if (!enabled || byCounter.containsKey(station.id()) || deco.byId(station.id()).isEmpty()) {
            return false;
        }
        Pos counter = station.anchor();
        World w = world(counter);
        if (w == null || !w.isChunkLoaded(counter.chunkX(), counter.chunkZ())) {
            return false;
        }
        double towardsX = 0.0;
        double towardsZ = 0.0;
        if (player != null && player.getWorld().equals(w)) {
            towardsX = player.getLocation().getX() - (counter.x() + 0.5);
            towardsZ = player.getLocation().getZ() - (counter.z() + 0.5);
        }
        CroupierPlacement.Stand s = CroupierPlacement.choose(counter, towardsX, towardsZ, cells(w));
        CroupierRecord r = new CroupierRecord(UUID.randomUUID(), s.feet(), s.lift(), s.facing(), station.id(), counter,
                origin.villager(), origin.snapshot(), origin.version(), origin.items(), 0, 0,
                CroupierTrade.RestockState.NONE, ctx.clock().getAsLong(), player == null ? null : player.getUniqueId());
        CroupierRecords.put(w.getChunkAt(counter.chunkX(), counter.chunkZ()), r);
        Croupier c = new Croupier(r);
        register(c);
        spawnRig(c);
        Log.get().info("Croupier {} appeared at {} ({}, lift {}) facing {} for the counter at {}", c.id(), s.feet(),
                s.feet().equals(counter) ? "on the counter" : "beside it",
                String.format(java.util.Locale.ROOT, "%.3f", s.lift()), s.facing(), counter);
        return true;
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static void guarded(Croupier c, String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Log.incident("croupier " + what + " of " + c.id() + " at " + c.record().stand(), e);
        }
    }
}
