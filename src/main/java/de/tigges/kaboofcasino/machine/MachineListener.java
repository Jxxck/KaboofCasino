package de.tigges.kaboofcasino.machine;

import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Log;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.block.data.type.Switch;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right clicks: on a carrier -> the machine's game ({@link MachineGame#interact}); with a machine item on a block
 * -> placement. Fake players never interact. Creative pick-block on a carrier gives the machine item instead of a
 * barrier. Thread: MAIN.
 */
public final class MachineListener implements Listener {

    private final MachineService machines;
    private final MenuService menus;
    private final Supplier<Messages> messages;

    public MachineListener(MachineService machines, MenuService menus, Supplier<Messages> messages) {
        this.machines = machines;
        this.menus = menus;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Optional<Machine> machine = machines.at(block);
        if (machine.isPresent()) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            if (event.getHand() != EquipmentSlot.HAND || FakePlayers.isFake(player)) {
                return;
            }
            try {
                menus.beginInteraction(player);
                if (!machines.verify(machine.get())) {
                    return;
                }
                machine.get().game().interact(player);
            } catch (RuntimeException e) {
                String code = Log.incident("interaction with " + machine.get().id(), e);
                messages.get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
            }
            return;
        }
        ItemStack item = event.getItem();
        Optional<CasinoItem> casino = CasinoItems.of(item);
        if (casino.isEmpty()) {
            return;
        }
        // a casino item is never used as paper on a block; machines place, the rest does nothing
        event.setUseItemInHand(Event.Result.DENY);
        Optional<MachineType> type = casino.get().machine();
        if (type.isEmpty() || FakePlayers.isFake(player) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!player.isSneaking() && interactable(block)) {
            return; // let doors, chests ... work as usual (vanilla: the block's use comes before the item's)
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        if (!player.hasPermission("kaboofcasino.use")) {
            messages.get().actionBar(player, Msg.ERROR_NO_PERMISSION);
            return;
        }
        MachineService.PlaceResult result = machines.place(player, type.get(), block, event.getBlockFace(),
                event.getHand(), item);
        if (result == MachineService.PlaceResult.PLACED) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack rest = item.clone();
                rest.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(event.getHand(), rest.getAmount() <= 0 ? null : rest);
            }
        } else {
            machines.tell(player, result);
        }
    }

    /** Blocks whose right click does something in vanilla (containers, doors, switches, work stations, beds). */
    static boolean interactable(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Openable || data instanceof Switch || data instanceof Bed || data instanceof Repeater
                || data instanceof Comparator || data instanceof NoteBlock) {
            return true;
        }
        if (block.getState(false) instanceof InventoryHolder) {
            return true;
        }
        return switch (block.getType()) {
            case CRAFTING_TABLE, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, GRINDSTONE, STONECUTTER, LOOM,
                 CARTOGRAPHY_TABLE, SMITHING_TABLE, LECTERN, BELL, ENDER_CHEST, BEACON, RESPAWN_ANCHOR, CAKE,
                 DAYLIGHT_DETECTOR, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent event) {
        Optional<Machine> machine = machines.at(event.getBlock());
        if (machine.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            int slot = event.getTargetSlot();
            player.getInventory().setItem(slot, CasinoItems.create(CasinoItem.forMachine(machine.get().type()),
                    messages.get(), 1));
            player.getInventory().setHeldItemSlot(slot);
        }
    }
}
