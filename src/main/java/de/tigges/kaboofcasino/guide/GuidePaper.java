package de.tigges.kaboofcasino.guide;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link GuideView} as a Paper dialog: {@code minecraft:multi_action} with {@code can_close_with_escape},
 * {@code pause = false} (no pause in multiplayer anyway) and {@code after_action = none}: a click keeps the page on
 * screen until the server's next dialog replaces it (no flicker to the world between pages, no "waiting for
 * response" screen); "Fertig" therefore asks the server to close ({@code closeDialog}), while ESC closes on the client
 * at once and sends the same close click (the exit action is the dialog's cancel action). Every button is a
 * {@link DialogAction#customClick(net.kyori.adventure.key.Key, net.kyori.adventure.nbt.api.BinaryTagHolder) custom
 * click} with {@link GuideAction#KEY}; no command, no callback. Needs a running server ({@code Dialog.create} uses the
 * registry). Thread: MAIN.
 */
final class GuidePaper {

    private GuidePaper() {
    }

    static Dialog dialog(GuideView view, BiFunction<String, Integer, ItemStack> items) {
        List<DialogBody> body = new ArrayList<>();
        for (GuideView.Body b : view.body()) {
            body.add(switch (b) {
                case GuideView.TextBody t -> DialogBody.plainMessage(t.text(), t.width());
                case GuideView.ItemBody i -> {
                    ItemDialogBody.Builder builder = DialogBody.item(items.apply(i.item(), i.count()))
                            .showDecorations(true)
                            .showTooltip(true)
                            .width(i.width())
                            .height(i.height());
                    if (i.description() != null) {
                        builder.description(DialogBody.plainMessage(i.description(), i.descriptionWidth()));
                    }
                    yield builder.build();
                }
            });
        }
        List<ActionButton> buttons = view.buttons().stream().map(GuidePaper::button).toList();
        DialogBase base = DialogBase.builder(view.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build();
        MultiActionType type = DialogType.multiAction(buttons)
                .columns(GuideView.COLUMNS)
                .exitAction(button(view.exit()))
                .build();
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static ActionButton button(GuideView.Button b) {
        ActionButton.Builder builder = ActionButton.builder(b.label())
                .width(b.width())
                .action(DialogAction.customClick(GuideAction.KEY, b.action().payload()));
        if (b.tooltip() != null) {
            builder.tooltip(b.tooltip());
        }
        return builder.build();
    }
}
