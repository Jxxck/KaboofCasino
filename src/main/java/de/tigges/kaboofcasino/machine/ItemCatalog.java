package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.util.Keys;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Every casino item by its mod id ({@code roulettecasino:slot_machine}, {@code casinodeco:ivory_marble},
 * {@code casinosound:casino_speaker}, ...), for {@code /casino give} and the mod's recipes. Machine items
 * ({@link CasinoItem}) are built in; the deco, seat and speaker modules register theirs when they enable. Identity is
 * the PDC tag {@code kaboofcasino:item}: machine items store their short id ({@code slot_machine}), registered items
 * their full mod id (with the namespace). Thread: MAIN.
 */
public final class ItemCatalog {

    private static final Map<String, IntFunction<ItemStack>> REGISTERED = new LinkedHashMap<>();

    private ItemCatalog() {
    }

    /** Registers a module's item; {@code factory} builds {@code amount} of it (tagged with {@code id}). */
    public static void register(String id, IntFunction<ItemStack> factory) {
        if (!id.contains(":")) {
            throw new IllegalArgumentException("mod id without namespace: " + id);
        }
        REGISTERED.put(id, factory);
    }

    /** Forgets the registered items (plugin disable). */
    public static void clear() {
        REGISTERED.clear();
    }

    /** Every known mod id: machine items first, then the registered ones. */
    public static List<String> ids() {
        List<String> out = new java.util.ArrayList<>();
        for (CasinoItem item : CasinoItem.values()) {
            out.add("roulettecasino:" + item.id());
        }
        out.addAll(REGISTERED.keySet());
        return out;
    }

    /** True if {@code id} is a registered (non-machine) item. */
    public static boolean registered(String id) {
        return REGISTERED.containsKey(id);
    }

    /** Builds a registered item; machine items go through {@link CasinoItems#create}. */
    public static Optional<ItemStack> createRegistered(String id, int amount) {
        IntFunction<ItemStack> f = REGISTERED.get(id);
        return f == null ? Optional.empty() : Optional.of(f.apply(amount));
    }

    /** The full mod id of a casino stack (machine or registered), by its PDC tag only. */
    public static Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(id.contains(":") ? id : "roulettecasino:" + id);
    }

    /** The registered ids (for tab completion and tests). */
    public static Collection<String> registeredIds() {
        return List.copyOf(REGISTERED.keySet());
    }
}
