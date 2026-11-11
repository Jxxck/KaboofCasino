package de.tigges.kaboofcasino.discord;

import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * The casino's only way to Discord (Jack 2026-11-11): KaboofDiscord's public
 * {@code post(source, title, text, color, player, name)} (1.1.0), called by reflection so neither plugin needs the
 * other to build or run. Without KaboofDiscord, with an older one, or with its {@code events.casino} off, nothing is
 * sent. KaboofDiscord sends from its own thread: a post never waits for Discord. Thread: MAIN.
 */
public final class DiscordBridge {

    static final String PLUGIN = "KaboofDiscord";
    static final String SOURCE = "casino";

    private Plugin cachedFor;
    private Method cached;
    private boolean warned;

    /**
     * One embed: {@code title} is plain text (beside the player's head if {@code player} is given), {@code text} may
     * use Discord formatting ({@link #md} for names in it).
     *
     * @return true if KaboofDiscord took it
     */
    public boolean post(String title, String text, int color, UUID player, String name) {
        Threads.requireMain();
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN);
        if (p == null || !p.isEnabled()) {
            return false;
        }
        try {
            if (cachedFor != p) {
                cached = p.getClass().getMethod("post", String.class, String.class, String.class, int.class,
                        UUID.class, String.class);
                cachedFor = p;
            }
            return Boolean.TRUE.equals(cached.invoke(p, SOURCE, title, text, color, player, name));
        } catch (NoSuchMethodException e) {
            if (!warned) {
                warned = true;
                Log.get().warn("KaboofDiscord {} cannot take casino posts (needs 1.1.0 or newer)",
                        p.getPluginMeta().getVersion());
            }
            return false;
        } catch (InvocationTargetException e) {
            Log.get().warn("KaboofDiscord refused a casino post: {}", String.valueOf(e.getCause()));
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.get().warn("casino post to KaboofDiscord failed: {}", e.toString());
            return false;
        }
    }

    /** True if a post can be handed over now: KaboofDiscord is there, enabled and has the API. Thread: MAIN. */
    public boolean ready() {
        Threads.requireMain();
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN);
        if (p == null || !p.isEnabled()) {
            return false;
        }
        try {
            p.getClass().getMethod("post", String.class, String.class, String.class, int.class, UUID.class,
                    String.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** {@code text} as plain Discord text: formatting characters escaped, so a name like {@code __Bob__} stays one. */
    public static String md(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ("\\*_~`|[]<>#".indexOf(c) >= 0 || c == '-' && i == 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
