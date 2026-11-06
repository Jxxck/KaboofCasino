package de.tigges.kaboofcasino.pack;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Classifies the first bytes of a connection on the game port (pure, unit-tested).
 *
 * <p>A Minecraft handshake starts with a VarInt packet length followed by packet id 0x00; the legacy server-list ping
 * starts with 0xFE. An HTTP request starts with a method and a space. A handshake whose length byte happens to be
 * 'G' (71) or 'H' (72) is followed by 0x00, never by 'E', so two bytes already tell them apart; an HTTP method is
 * only assumed once the bytes so far are a prefix of a known method.
 */
final class HttpSniff {

    /** What to do with the connection. */
    enum Kind {
        /** Not enough bytes yet. */
        WAIT,
        /** Minecraft (handshake, legacy ping, anything else): replay the bytes to the game pipeline. */
        MINECRAFT,
        /** A complete HTTP request head ({@code \r\n\r\n}). */
        HTTP,
        /** An HTTP request whose head is larger than {@link #MAX_HEAD}. */
        HTTP_TOO_LARGE
    }

    /** A parsed request line; {@code path} without the query string. */
    record Request(String method, String path) {
    }

    static final int MAX_HEAD = 8192;
    static final List<String> METHODS = List.of("GET ", "HEAD ", "POST ", "PUT ", "DELETE ", "OPTIONS ", "PATCH ",
            "CONNECT ", "TRACE ");

    private HttpSniff() {
    }

    static Kind classify(byte[] data, int length) {
        if (length == 0) {
            return Kind.WAIT;
        }
        if ((data[0] & 0xff) == 0xFE) {
            return Kind.MINECRAFT;
        }
        boolean method = false;
        boolean prefix = false;
        for (String m : METHODS) {
            int n = Math.min(length, m.length());
            if (startsWith(data, m, n)) {
                if (length >= m.length()) {
                    method = true;
                } else {
                    prefix = true;
                }
            }
        }
        if (method) {
            if (headEnd(data, length) >= 0) {
                return Kind.HTTP;
            }
            return length >= MAX_HEAD ? Kind.HTTP_TOO_LARGE : Kind.WAIT;
        }
        return prefix ? Kind.WAIT : Kind.MINECRAFT;
    }

    /** True if the bytes so far start like an HTTP method (complete or not). */
    static boolean looksLikeHttp(byte[] data, int length) {
        if (length == 0) {
            return false;
        }
        for (String m : METHODS) {
            if (startsWith(data, m, Math.min(length, m.length()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, String m, int n) {
        for (int i = 0; i < n; i++) {
            if (data[i] != (byte) m.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Index after the blank line ending the head, or -1. */
    static int headEnd(byte[] data, int length) {
        for (int i = 0; i + 3 < length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = 0; i + 1 < length; i++) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i + 2;
            }
        }
        return -1;
    }

    /** The request line, or null if it is not complete or malformed. */
    static Request requestLine(byte[] data, int length) {
        int end = -1;
        for (int i = 0; i < length; i++) {
            if (data[i] == '\n') {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return null;
        }
        String line = new String(data, 0, end, StandardCharsets.ISO_8859_1).strip();
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return null;
        }
        String path = parts[1];
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        return new Request(parts[0], path);
    }

    /** Status line + headers (+ nothing else) of a response that closes the connection. */
    static byte[] head(int status, String reason, String contentType, long length, String extra) {
        StringBuilder b = new StringBuilder();
        b.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        if (contentType != null) {
            b.append("Content-Type: ").append(contentType).append("\r\n");
        }
        b.append("Content-Length: ").append(length).append("\r\n");
        if (extra != null) {
            b.append(extra);
        }
        b.append("Connection: close\r\n\r\n");
        return b.toString().getBytes(StandardCharsets.ISO_8859_1);
    }
}
