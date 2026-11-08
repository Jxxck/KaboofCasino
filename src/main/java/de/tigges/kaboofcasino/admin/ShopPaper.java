package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.cashier.Prizes;
import de.tigges.kaboofcasino.config.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * The shop dialog as a Paper dialog, built like the croupier's hand's ({@link AdminPaper}): multi action, ESC closes
 * (sending "Fertig" with the inputs), no pause, {@code after_action = none}; a prize line is its item with the
 * description beside it. Needs a running server. Thread: MAIN.
 */
final class ShopPaper {

    private ShopPaper() {
    }

    static Dialog dialog(ShopDialogs.View view, Messages messages) {
        List<DialogBody> body = new ArrayList<>();
        for (ShopDialogs.Line line : view.body()) {
            if (line instanceof ShopDialogs.PrizeLine prize) {
                Optional<ItemStack> one = Prizes.one(prize.entry(), messages);
                if (one.isPresent()) {
                    body.add(DialogBody.item(one.get().asQuantity(Math.min(prize.entry().count(),
                            one.get().getMaxStackSize()))).description(DialogBody.plainMessage(line.text(),
                            ShopDialogs.WIDTH - 40)).showTooltip(true).build());
                    continue;
                }
            }
            body.add(DialogBody.plainMessage(line.text(), ShopDialogs.WIDTH));
        }
        List<DialogInput> inputs = view.inputs().stream().map(ShopPaper::input).toList();
        DialogBase base = DialogBase.builder(view.title())
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .inputs(inputs)
                .build();
        MultiActionType type = DialogType.multiAction(view.buttons().stream().map(ShopPaper::button).toList())
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
            case AdminView.RangeInput r -> throw new IllegalArgumentException("no sliders in the shop dialog: "
                    + r.key());
        };
    }

    private static ActionButton button(ShopDialogs.Button b) {
        ActionButton.Builder builder = ActionButton.builder(b.label())
                .width(b.width())
                .action(DialogAction.customClick(ShopClick.KEY, b.action().payload()));
        if (b.tooltip() != null) {
            builder.tooltip(b.tooltip());
        }
        return builder.build();
    }
}
