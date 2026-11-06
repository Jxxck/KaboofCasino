package de.tigges.kaboofcasino.util;

import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging facade (PostMarkt pattern). Log text is English; player text comes from messages_de.yml.
 * {@link #critical} marks invariant violations admins must see; {@link #incident} logs an unexpected exception with a
 * short code that is shown to the player.
 */
public final class Log {

    private static volatile Logger logger = LoggerFactory.getLogger("KaboofCasino");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private Log() {
    }

    public static void install(Logger pluginLogger) {
        logger = pluginLogger;
    }

    public static Logger get() {
        return logger;
    }

    public static void critical(String code, String message, Object... args) {
        logger.error("CRITICAL [" + code + "] " + message, args);
    }

    public static String incident(String context, Throwable t) {
        char[] out = new char[6];
        for (int i = 0; i < out.length; i++) {
            out[i] = CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)];
        }
        String code = new String(out);
        logger.error("Incident " + code + " (" + context + ")", t);
        return code;
    }
}
