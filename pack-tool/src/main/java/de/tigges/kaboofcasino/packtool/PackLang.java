package de.tigges.kaboofcasino.packtool;

import java.util.Locale;

/**
 * The language a pack is built for: the lettering drawn into its pictures and models, which of the mod's lang files
 * the drawn texts come from, and the pack's description. The plugin jar holds one casino pack per language and serves
 * the one of its {@code language:} setting. (The lang files inside a pack are not affected: every pack carries all of
 * the mod's languages, and the client picks its own.)
 *
 * <p>One build runs for one language at a time ({@link PackTool#build}); the classes that draw text ask
 * {@link #current()}. Outside a build it is the system property {@code kaboofcasino.language}, else English.
 */
enum PackLang {
    EN("en", "en_us"),
    DE("de", "de_de");

    private static volatile PackLang current = byCode(System.getProperty("kaboofcasino.language", "en"));

    private final String code;
    private final String modCode;

    PackLang(String code, String modCode) {
        this.code = code;
        this.modCode = modCode;
    }

    static PackLang current() {
        return current;
    }

    static void use(PackLang lang) {
        current = java.util.Objects.requireNonNull(lang);
    }

    static PackLang byCode(String code) {
        String c = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        for (PackLang l : values()) {
            if (l.code.equals(c)) {
                return l;
            }
        }
        throw new IllegalArgumentException("unknown language " + code + " (en or de)");
    }

    /** {@code en} / {@code de}: the folder of this language's files. */
    String code() {
        return code;
    }

    /** The mod's lang file of this language: {@code en_us} / {@code de_de}. */
    String modCode() {
        return modCode;
    }

    /** {@code assets/<namespace>/lang/<en_us|de_de>.json} in the mod jar. */
    String modLangPath(String namespace) {
        return "assets/" + namespace + "/lang/" + modCode + ".json";
    }

    /** The English text in an English pack, the German one in a German pack. */
    static String text(String english, String german) {
        return current == DE ? german : english;
    }
}
