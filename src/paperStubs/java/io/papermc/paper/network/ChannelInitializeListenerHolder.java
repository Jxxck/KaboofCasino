package io.papermc.paper.network;

import net.kyori.adventure.key.Key;

/**
 * Compile-only stand-in for the class of the same name in the Paper server jar (not in paper-api): signatures only,
 * so the plugin builds without a server jar. Never packaged; at runtime the server's own class is used.
 * {@code PaperStubsTest} compares these signatures with a real server jar when one is given.
 */
public final class ChannelInitializeListenerHolder {

    private ChannelInitializeListenerHolder() {
    }

    public static boolean hasListener(Key key) {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public static void addListener(Key key, ChannelInitializeListener listener) {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public static ChannelInitializeListener removeListener(Key key) {
        throw new UnsupportedOperationException("compile-only stub");
    }
}
