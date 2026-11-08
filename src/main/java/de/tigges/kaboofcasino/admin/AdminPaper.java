package de.tigges.kaboofcasino.admin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import java.util.List;

/**
 * An {@link AdminView} as a Paper dialog, built like the guide's ({@code GuidePaper}): {@code minecraft:multi_action},
 * closable with ESC, no pause, {@code after_action = none} (a click keeps the dialog until the server's answer
 * replaces or closes it). Every button is a custom click with {@link AdminClick#KEY} and {@code {do:"..."}}; the
 * client adds the inputs' values. The exit button "Fertig" is also what ESC sends. Needs a running server
 * ({@code Dialog.create} uses the registry). Thread: MAIN.
 */
final class AdminPaper {

    private AdminPaper() {
    }

    static Dialog dialog(AdminView view) {
        List<DialogBody> body = view.body().stream()
                .map(t -> (DialogBody) DialogBody.plainMessage(t.text(), t.width()))
                .toList();
        List<DialogInput> inputs = view.inputs().stream().map(AdminPaper::input).toList();
        DialogBase base = DialogBase.builder(view.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .inputs(inputs)
                .build();
        MultiActionType type = DialogType.multiAction(view.buttons().stream().map(AdminPaper::button).toList())
                .columns(AdminView.COLUMNS)
                .exitAction(button(view.exit()))
                .build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static DialogInput input(AdminView.Input input) {
        return switch (input) {
            case AdminView.TextInput t -> DialogInput.text(t.key(), t.label())
                    .width(t.width())
                    .initial(t.initial())
                    .maxLength(t.maxLength())
                    .build();
            case AdminView.BoolInput b -> DialogInput.bool(b.key(), b.label())
                    .initial(b.initial())
                    .build();
            case AdminView.RangeInput r -> DialogInput.numberRange(r.key(), r.label(), r.start(), r.end())
                    .step(1f)
                    .initial((float) r.initial())
                    .labelFormat(r.labelFormat())
                    .width(r.width())
                    .build();
        };
    }

    private static ActionButton button(AdminView.Button b) {
        ActionButton.Builder builder = ActionButton.builder(b.label())
                .width(b.width())
                .action(DialogAction.customClick(AdminClick.KEY, b.action().payload()));
        if (b.tooltip() != null) {
            builder.tooltip(b.tooltip());
        }
        return builder.build();
    }
}
