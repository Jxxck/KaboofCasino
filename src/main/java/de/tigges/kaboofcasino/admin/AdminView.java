package de.tigges.kaboofcasino.admin;

import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * One croupier's-hand dialog, server-free ({@link AdminDialogs} builds it, {@link AdminPaper} turns it into a Paper
 * {@code Dialog}): a multi-action dialog with a title, texts, inputs below them, a grid of buttons
 * ({@value #COLUMNS} columns) and the exit button ("Fertig", also ESC) in the footer.
 */
record AdminView(Component title, List<Text> body, List<Input> inputs, List<Button> buttons, Button exit) {

    static final int COLUMNS = 2;

    AdminView {
        body = List.copyOf(body);
        inputs = List.copyOf(inputs);
        buttons = List.copyOf(buttons);
    }

    /** {@code minecraft:plain_message}. */
    record Text(Component text, int width) {
    }

    sealed interface Input permits TextInput, BoolInput, RangeInput {
        String key();
    }

    /** {@code minecraft:text}: one line, at most {@code maxLength} characters. */
    record TextInput(String key, Component label, String initial, int maxLength, int width) implements Input {
    }

    /** {@code minecraft:boolean}: a checkbox. */
    record BoolInput(String key, Component label, boolean initial) implements Input {
    }

    /**
     * {@code minecraft:number_range}: a slider from {@code start} to {@code end} in whole steps; the client shows
     * {@code labelFormat} (a vanilla translation key) with the label and the value.
     */
    record RangeInput(String key, Component label, String labelFormat, int start, int end, int initial, int width)
            implements Input {
    }

    /** A button; {@code tooltip} null for none. */
    record Button(Component label, Component tooltip, int width, AdminClick.Kind action) {
    }
}
