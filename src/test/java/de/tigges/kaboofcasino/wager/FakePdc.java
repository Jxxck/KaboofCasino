package de.tigges.kaboofcasino.wager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * A map-backed persistent data container for codec tests (the real one lives in the server): values are kept as
 * their primitive with the primitive's class, so a read with another type finds nothing, as on the server.
 */
final class FakePdc implements PersistentDataContainer {

    static final PersistentDataAdapterContext CONTEXT = FakePdc::new;

    private final Map<NamespacedKey, Object> values = new LinkedHashMap<>();

    Map<NamespacedKey, Object> raw() {
        return values;
    }

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        values.put(key, type.toPrimitive(value, CONTEXT));
    }

    @Override
    public void remove(NamespacedKey key) {
        values.remove(key);
    }

    @Override
    public void readFromBytes(byte[] bytes, boolean clear) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        Object v = values.get(key);
        return v != null && type.getPrimitiveType().isInstance(v);
    }

    @Override
    public boolean has(NamespacedKey key) {
        return values.containsKey(key);
    }

    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        Object v = values.get(key);
        if (v == null || !type.getPrimitiveType().isInstance(v)) {
            return null;
        }
        return type.fromPrimitive(type.getPrimitiveType().cast(v), CONTEXT);
    }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C def) {
        C v = get(key, type);
        return v == null ? def : v;
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        return Set.copyOf(values.keySet());
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        return CONTEXT;
    }

    @Override
    public byte[] serializeToBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSize() {
        return values.size();
    }
}
