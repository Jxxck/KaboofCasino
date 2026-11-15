package de.tigges.kaboofcasino.pack;

import de.tigges.kaboofcasino.config.CasinoConfig;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * The download address of a pack as one client gets it ({@code pack.url-host}). Pure.
 * <ul>
 *   <li>A fixed host: {@code http://<url-host>:<port>/pack/<sha1>.zip} for everybody, the port being
 *       {@code pack.url-port} on the game port and {@code pack.port} with the own HTTP server.</li>
 *   <li>{@value #AUTO} (default): the address this client connected to, as its handshake names it (the virtual
 *       host). On the game port the handshake's port is used too, so the download takes exactly the way the game
 *       connection took (port forwards, SRV records, LAN and localhost all work without a setting). With the own
 *       HTTP server the port stays {@code pack.port}.</li>
 * </ul>
 * The handshake's host is client input: it only ever goes back to the same client, and only if it is a plain host
 * name or an IP address. Behind a proxy that rewrites or forwards the address (Velocity, BungeeCord, TCPShield) the
 * handshake does not lead to this server's pack; a fixed host is needed there.
 */
public final class PackUrl {

    public static final String AUTO = "auto";

    /** Host names and IPv4 addresses (underscores as some DNS names have them); at most 253 characters. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9._-]{0,251}[A-Za-z0-9])?");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private PackUrl() {
    }

    public static boolean auto(CasinoConfig.Pack p) {
        return AUTO.equalsIgnoreCase(p.urlHost());
    }

    /**
     * @param virtualHost the address the client connected to (may be null: unknown)
     * @return the pack's URL for that client, or null if {@value #AUTO} has no usable address for it
     */
    public static URI of(CasinoConfig.Pack p, InetSocketAddress virtualHost, PackFile file) {
        String authority = authority(p, virtualHost);
        return authority == null ? null : URI.create("http://" + authority + file.path());
    }

    /** {@code host:port} of the URL, or null. */
    static String authority(CasinoConfig.Pack p, InetSocketAddress virtualHost) {
        if (!auto(p)) {
            return p.urlHost() + ":" + p.publicPort();
        }
        if (virtualHost == null) {
            return null;
        }
        String host = host(virtualHost.getHostString());
        int port = p.onGamePort() ? virtualHost.getPort() : p.port();
        if (host == null || port < 1 || port > 65535) {
            return null;
        }
        return host + ":" + port;
    }

    /**
     * A handshake host as it may stand in a URL: cut at the first NUL (mod loaders append markers there), without
     * trailing dots, an IPv6 address in brackets. Null if it is anything but a host name or an IP address.
     */
    static String host(String raw) {
        if (raw == null) {
            return null;
        }
        int nul = raw.indexOf('\0');
        String host = nul < 0 ? raw : raw.substring(0, nul);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.indexOf(':') >= 0) {
            String inner = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
            return IPV6.matcher(inner).matches() ? "[" + inner + "]" : null;
        }
        return NAME.matcher(host).matches() ? host : null;
    }

    /** For the log: the URL, with a placeholder where {@value #AUTO} fills in each client's own address. */
    public static String describe(CasinoConfig.Pack p, PackFile file) {
        if (!auto(p)) {
            return "http://" + p.urlHost() + ":" + p.publicPort() + file.path();
        }
        return "http://<address the player connected to>" + (p.onGamePort() ? "" : ":" + p.port()) + file.path();
    }

    /** A client-supplied host for a log line: printable ASCII only, at most 64 characters. */
    static String printable(InetSocketAddress virtualHost) {
        if (virtualHost == null) {
            return "unknown";
        }
        String raw = virtualHost.getHostString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < 64; i++) {
            char c = raw.charAt(i);
            out.append(c > 0x20 && c < 0x7f ? c : '?');
        }
        return out + ":" + virtualHost.getPort();
    }
}
