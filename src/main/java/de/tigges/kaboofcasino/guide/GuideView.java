package de.tigges.kaboofcasino.guide;

import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * One guide dialog, server-free ({@link GuideDialogs} builds it, {@link GuidePaper} turns it into a Paper
 * {@code Dialog}): a multi-action dialog with a title, a scrollable body of texts and items, a grid of buttons below
 * the body ({@value #COLUMNS} columns) and the exit button in the fixed footer, which is also what ESC runs.
 */
public record GuideView(int page, Component title, List<Body> body, List<Button> buttons, Button exit) {

    /** Button grid columns: the four bookmarks fill one row, the page buttons share the last (centred) row. */
    public static final int COLUMNS = 4;

    public GuideView {
        body = List.copyOf(body);
        buttons = List.copyOf(buttons);
    }

    /** A body element (the client stacks them vertically, centred, 10 px apart). */
    public sealed interface Body permits TextBody, ItemBody {
    }

    /** {@code minecraft:plain_message}: wrapped text, centred. */
    public record TextBody(Component text, int width) implements Body {
    }

    /**
     * {@code minecraft:item}: the item drawn 16 x 16 at the top left of a {@code width} x {@code height} box (hover:
     * its tooltip; count above 1 drawn on it), with an optional description to its right (left aligned, vertically
     * centred, clickable links inside).
     *
     * @param item        item id ({@code minecraft:...} or a casino id); never a tag
     * @param description null for none
     */
    public record ItemBody(String item, int count, Component description, int descriptionWidth, int width, int height)
            implements Body {
    }

    /** A dialog button; {@code tooltip} null for none. */
    public record Button(Component label, Component tooltip, int width, GuideAction action) {
    }
}
