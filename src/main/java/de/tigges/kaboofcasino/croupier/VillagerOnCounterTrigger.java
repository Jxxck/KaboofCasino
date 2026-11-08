package de.tigges.kaboofcasino.croupier;

import de.tigges.kaboofcasino.deco.display.DecoRecord;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Pos;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * Jack's option A: every {@link CroupierService#SCAN_TICKS} ticks each loaded counter without a croupier looks into
 * the block directly above it (never further) for a villager that may become its croupier
 * ({@link CroupierEligibility}: standing on the counter's top, unemployed, level 1, no experience, no trades, adult,
 * not a nitwit, not leashed, not named, not riding, no passengers, not trading or asleep, and a player within
 * {@value CroupierEligibility#PLAYER_RANGE} blocks). Such a villager releases its job site, bed and meeting point (as
 * the mod's hiring does before it removes the villager), its full data ({@code Entity#createSnapshot}) and its
 * inventory go into the croupier's record, the croupier appears and the villager is removed. Every conversion is
 * logged with world, position, villager UUID and the nearest player; a villager on a counter that is not taken is
 * logged once per reason. Thread: MAIN.
 */
final class VillagerOnCounterTrigger implements CroupierSpawnTrigger {

    /** Remembered refusals (villager -> verdict), so a waiting villager is logged once per reason. */
    private final Map<UUID, CroupierEligibility.Verdict> refused = new HashMap<>();
    private boolean poiWarned;

    @Override
    public String name() {
        return "villager brought onto a counter (A)";
    }

    @Override
    public void scan(Spawns spawns) {
        Set<UUID> seen = new HashSet<>();
        for (DecoRecord station : spawns.freeStations()) {
            Pos c = station.anchor();
            World world = spawns.world(c);
            if (world == null || !world.isChunkLoaded(c.chunkX(), c.chunkZ())) {
                continue;
            }
            BoundingBox above = new BoundingBox(c.x(), c.y() + 1, c.z(), c.x() + 1, c.y() + 2, c.z() + 1);
            for (Entity e : world.getNearbyEntities(above, x -> x instanceof Villager)) {
                Villager villager = (Villager) e;
                Optional<Player> near = spawns.nearestPlayer(c, CroupierEligibility.PLAYER_RANGE);
                CroupierEligibility.Verdict verdict = CroupierEligibility.check(facts(villager), c, false,
                        near.isPresent());
                if (verdict != CroupierEligibility.Verdict.OK) {
                    seen.add(villager.getUniqueId());
                    note(villager, c, verdict);
                    continue;
                }
                if (convert(spawns, station, villager, near.get())) {
                    break;
                }
            }
        }
        refused.keySet().retainAll(seen);
    }

    /**
     * A refusal of a villager standing on a counter is logged once per reason (diagnosis for admins); a dying villager
     * (its death animation) and one only passing through are not.
     */
    private void note(Villager villager, Pos counter, CroupierEligibility.Verdict verdict) {
        if (verdict == CroupierEligibility.Verdict.GONE || verdict == CroupierEligibility.Verdict.NOT_ABOVE_COUNTER
                || verdict == CroupierEligibility.Verdict.NOT_ON_TOP) {
            return;
        }
        if (refused.put(villager.getUniqueId(), verdict) != verdict) {
            Log.get().info("Villager {} on the croupier counter at {} does not become a croupier: {}",
                    villager.getUniqueId(), counter, verdict);
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

    private boolean convert(Spawns spawns, DecoRecord station, Villager villager, Player near) {
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
        if (!spawns.spawn(station, origin, near)) {
            return false;
        }
        villager.remove();
        Log.get().info("Croupier: villager {} on the counter at {} ({}, {}, {}) became a croupier; nearest player {} "
                + "({}) at {} blocks; {} item(s) kept", vid, station.anchor().world(), station.anchor().x(),
                station.anchor().y(), station.anchor().z(), near.getName(), near.getUniqueId(),
                String.format(java.util.Locale.ROOT, "%.1f", near.getLocation().distance(was)), items.size());
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
