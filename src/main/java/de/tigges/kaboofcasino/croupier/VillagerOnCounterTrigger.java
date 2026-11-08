package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.deco.display.DecoRecord;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * Jack's option A, on a click only: when a player right-clicks a counter without a croupier (within reach), the counter
 * looks into the block directly above it (never further) for a villager that may become its croupier
 * ({@link CroupierEligibility}: standing on the counter's top, unemployed, level 1, no experience, no trades, adult,
 * not a nitwit, not leashed, not named, not riding, no passengers, not trading or asleep). Such a villager releases its
 * job site, bed and meeting point (as the mod's hiring does before it removes the villager), its full data
 * ({@code Entity#createSnapshot}) and its inventory go into the croupier's record, the croupier appears and the
 * villager is removed. A villager standing on a counter nobody clicks stays a villager. Every conversion is logged with
 * world, position, villager UUID and the clicking player; a refused villager is logged once per reason. Thread: MAIN.
 */
final class VillagerOnCounterTrigger implements CroupierSpawnTrigger {

    /** Remembered refusals (villager -> verdict), so repeated clicks log a villager once per reason. */
    private final Map<UUID, CroupierEligibility.Verdict> refused = new HashMap<>();
    private boolean poiWarned;

    @Override
    public String name() {
        return "villager brought onto a counter, counter clicked (A)";
    }

    @Override
    public CroupierEligibility.Verdict stationUsed(Spawns spawns, DecoRecord station, Player player,
                                                  boolean inReach) {
        Pos c = station.anchor();
        World world = spawns.world(c);
        if (world == null || !world.isChunkLoaded(c.chunkX(), c.chunkZ())) {
            return CroupierEligibility.Verdict.NOBODY;
        }
        BoundingBox above = new BoundingBox(c.x(), c.y() + 1, c.z(), c.x() + 1, c.y() + 2, c.z() + 1);
        CroupierEligibility.Verdict answer = CroupierEligibility.Verdict.NOBODY;
        for (Entity e : world.getNearbyEntities(above, x -> x instanceof Villager)) {
            Villager villager = (Villager) e;
            CroupierEligibility.Verdict verdict = CroupierEligibility.check(facts(villager), c, false, inReach);
            if (verdict == CroupierEligibility.Verdict.NOT_ABOVE_COUNTER
                    || verdict == CroupierEligibility.Verdict.GONE) {
                continue; // beside the counter (its box only reaches into the block above), or dying: not there
            }
            if (verdict == CroupierEligibility.Verdict.OK) {
                if (convert(spawns, station, villager, player)) {
                    return CroupierEligibility.Verdict.OK;
                }
                answer = CroupierEligibility.Verdict.GONE; // could not be saved (logged); the click is still answered
                continue;
            }
            note(villager, c, verdict, player);
            if (answer == CroupierEligibility.Verdict.NOBODY || answer == CroupierEligibility.Verdict.NOT_ON_TOP
                    || answer == CroupierEligibility.Verdict.GONE) {
                answer = verdict;
            }
        }
        return answer;
    }

    /** A refusal is logged once per villager and reason (diagnosis for admins). */
    private void note(Villager villager, Pos counter, CroupierEligibility.Verdict verdict, Player player) {
        if (refused.size() >= 1024) {
            refused.clear();
        }
        if (refused.put(villager.getUniqueId(), verdict) != verdict) {
            Log.get().info("Villager {} on the croupier counter at {} does not become a croupier: {} (clicked by {})",
                    villager.getUniqueId(), counter, verdict, player.getName());
        }
    }

    static CroupierEligibility.Facts facts(Villager v) {
        Location l = v.getLocation();
        return new CroupierEligibility.Facts(v.isValid() && !v.isDead(), l.getX(), l.getY(), l.getZ(), v.isOnGround(),
                Villager.Profession.NONE.equals(v.getProfession()),
                Villager.Profession.NITWIT.equals(v.getProfession()), v.getVillagerLevel(),
                v.getVillagerExperience(), v.getRecipeCount(), v.isAdult(), v.isLeashed(), v.customName() != null,
                v.isInsideVehicle(), !v.getPassengers().isEmpty(), v.isTrading(), v.isSleeping());
    }

    private boolean convert(Spawns spawns, DecoRecord station, Villager villager, Player player) {
        UUID vid = villager.getUniqueId();
        Location was = villager.getLocation();
        List<String> items = new ArrayList<>();
        for (ItemStack stack : villager.getInventory().getContents()) {
            if (stack != null && !stack.isEmpty()) {
                items.add(Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
            }
        }
        releasePois(villager);
        EntitySnapshot snapshot = villager.createSnapshot();
        if (snapshot == null) {
            Log.get().warn("Villager {} on the croupier counter at {}: no snapshot possible, not converted", vid,
                    station.anchor());
            return false;
        }
        CroupierSpawnTrigger.Origin origin = new CroupierSpawnTrigger.Origin(vid, snapshot.getAsString(),
                Bukkit.getMinecraftVersion(), items);
        if (!spawns.spawn(station, origin, player)) {
            return false;
        }
        villager.remove();
        Log.get().info("Croupier: villager {} on the counter at {} ({}, {}, {}) became a croupier; clicked by {} ({}) "
                + "at {} blocks; {} item(s) kept", vid, station.anchor().world(), station.anchor().x(),
                station.anchor().y(), station.anchor().z(), player.getName(), player.getUniqueId(),
                String.format(java.util.Locale.ROOT, "%.1f", player.getLocation().distance(was)), items.size());
        return true;
    }

    /**
     * {@code Villager.releaseAllPois()} (job site, bed, potential job site, meeting point), which the mod's hiring
     * calls before it removes a villager and which no API offers: a removed villager would otherwise keep its bed
     * claimed for good. Reflection on the server's own (Mojang-named) villager; then the four memories are erased, so
     * a villager restored from the snapshot does not think it still owns them. If the release fails the conversion
     * goes on with claims and memories kept (consistent for a restore; logged once).
     */
    private void releasePois(Villager villager) {
        try {
            Method handle = villager.getClass().getMethod("getHandle");
            Object nms = handle.invoke(villager);
            nms.getClass().getMethod("releaseAllPois").invoke(nms);
            for (MemoryKey<Location> key : List.of(MemoryKey.JOB_SITE, MemoryKey.POTENTIAL_JOB_SITE, MemoryKey.HOME,
                    MemoryKey.MEETING_POINT)) {
                villager.setMemory(key, null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!poiWarned) {
                poiWarned = true;
                Log.get().warn("Croupier: cannot release a converted villager's bed/job site/meeting point ({}); they "
                        + "stay claimed", e.toString());
            }
        }
    }
}
