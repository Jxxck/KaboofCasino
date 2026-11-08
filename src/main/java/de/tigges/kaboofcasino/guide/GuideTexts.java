package de.tigges.kaboofcasino.guide;

import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import net.kyori.adventure.text.Component;

/**
 * The texts of the guide dialogs that are not the mod's (messages_de.yml, editable) and the names of vanilla items
 * (the server's registry). {@link GuideService} fills it on every page; tests use fixed texts.
 *
 * @param recipe      label of a shaped recipe ("Rezept"; the mod has no key for it, only the shapeless hint)
 * @param anyKind     appended to a tag ingredient shown by its first member (" (beliebige Sorte)")
 * @param credits     the licence line on the cover (render.md 5.3)
 * @param gotoPage    hover text of a link to spread n
 * @param vanillaName the translatable name of a {@code minecraft:} item
 * @param notes       plugin notes shown right under a mod text (its lang key) where this server works differently
 */
public record GuideTexts(Component recipe, Component anyKind, Component credits, IntFunction<Component> gotoPage,
                         Function<String, Component> vanillaName, Map<String, Component> notes) {

    public GuideTexts {
        notes = notes == null ? Map.of() : Map.copyOf(notes);
    }
}
