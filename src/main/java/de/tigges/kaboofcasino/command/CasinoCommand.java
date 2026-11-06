package de.tigges.kaboofcasino.command;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.CasinoItems;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.storage.RoundLog;
import de.tigges.kaboofcasino.util.ItemNames;
import de.tigges.kaboofcasino.util.Pos;
import de.tigges.kaboofcasino.wager.Ledger;
import de.tigges.kaboofcasino.wager.LedgerCodec;
import de.tigges.kaboofcasino.wager.PlayerAccount;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /casino}: info with the licence link, {@code stats}, {@code abholen}, {@code credits}; admins:
 * {@code give <item> [player]}, {@code remove} (the machine looked at: settles, no drop), {@code reload}.
 * Permissions {@code kaboofcasino.use} (everyone) and {@code kaboofcasino.admin} (ops). Thread: MAIN.
 */
public final class CasinoCommand implements BasicCommand {

    private final CasinoContext ctx;
    private final RoundLog log;
    private final Reloader reloader;

    /** Reloads config and messages; returns the number of warnings. */
    @FunctionalInterface
    public interface Reloader {
        int reload();
    }

    public CasinoCommand(CasinoContext ctx, RoundLog log, Reloader reloader) {
        this.ctx = ctx;
        this.log = log;
        this.reloader = reloader;
    }

    @Override
    public String permission() {
        return "kaboofcasino.use";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Messages m = ctx.messages().get();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> info(sender);
            case "credits" -> m.lines(Msg.COMMAND_CREDITS).forEach(sender::sendMessage);
            case "stats", "statistik" -> stats(sender);
            case "abholen", "collect" -> {
                if (sender instanceof Player p) {
                    ctx.ledger().collect(p);
                }
            }
            case "give" -> admin(sender, () -> give(sender, args));
            case "remove", "entfernen" -> admin(sender, () -> remove(sender));
            case "reload" -> admin(sender, () -> m.send(sender, Msg.COMMAND_RELOADED,
                    Messages.text("warnings", Integer.toString(reloader.reload()))));
            default -> m.send(sender, Msg.COMMAND_USAGE);
        }
    }

    private void admin(CommandSender sender, Runnable action) {
        if (!sender.hasPermission("kaboofcasino.admin")) {
            ctx.messages().get().send(sender, Msg.ERROR_NO_PERMISSION);
            return;
        }
        action.run();
    }

    private void info(CommandSender sender) {
        Messages m = ctx.messages().get();
        m.lines(Msg.COMMAND_INFO).forEach(sender::sendMessage);
        if (sender instanceof Player p) {
            Ledger l;
            try {
                l = new PlayerAccount(p).ledger();
            } catch (LedgerCodec.CorruptLedgerException e) {
                m.send(p, Msg.ERROR_INTERNAL, Messages.text("code", "LEDGER"));
                return;
            }
            Component owed = l.owed().isEmpty() ? Component.text("–") : ItemNames.list(l.owed());
            m.send(p, Msg.COMMAND_OPEN_ROUNDS, Messages.text("rounds", Integer.toString(l.rounds().size())),
                    Messages.component("owed", owed));
        }
    }

    private void stats(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return;
        }
        log.stats(p.getUniqueId(), lines -> Bukkit.getScheduler().runTask(ctx.plugin(), () -> {
            Messages m = ctx.messages().get();
            if (lines.isEmpty()) {
                m.send(p, Msg.COMMAND_STATS_EMPTY);
                return;
            }
            long rounds = lines.stream().mapToLong(RoundLog.StatLine::rounds).sum();
            m.send(p, Msg.COMMAND_STATS_HEADER, Messages.text("rounds", Long.toString(rounds)));
            for (RoundLog.StatLine line : lines) {
                p.sendMessage(m.render(Msg.COMMAND_STATS_LINE, Messages.component("item", ItemNames.name(line.item())),
                        Messages.text("staked", Long.toString(line.staked())),
                        Messages.text("won", Long.toString(line.won()))));
            }
        }));
    }

    private void give(CommandSender sender, String[] args) {
        Messages m = ctx.messages().get();
        if (args.length < 2) {
            m.send(sender, Msg.COMMAND_USAGE);
            return;
        }
        Optional<CasinoItem> item = CasinoItem.byId(args[1].toLowerCase(Locale.ROOT));
        if (item.isEmpty()) {
            m.send(sender, Msg.COMMAND_GIVE_UNKNOWN, Messages.text("name", args[1]), Messages.text("list",
                    String.join(", ", ids())));
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
        if (target == null) {
            m.send(sender, Msg.COMMAND_PLAYER_UNKNOWN, Messages.text("name", args.length >= 3 ? args[2] : "?"));
            return;
        }
        ItemStack stack = CasinoItems.create(item.get(), m, 1);
        target.getInventory().addItem(stack).values().forEach(rest -> target.getWorld().dropItemNaturally(
                target.getLocation(), rest));
        m.send(sender, Msg.COMMAND_GIVE_DONE, Messages.text("player", target.getName()),
                Messages.component("item", m.render(item.get().nameMsg())));
    }

    private void remove(CommandSender sender) {
        Messages m = ctx.messages().get();
        if (!(sender instanceof Player p)) {
            return;
        }
        Block target = p.getTargetBlockExact(8);
        Optional<Machine> machine = target == null ? Optional.empty() : ctx.machines().at(Pos.of(target));
        if (machine.isEmpty()) {
            m.send(p, Msg.MACHINE_NONE);
            return;
        }
        ctx.machines().remove(machine.get(), "admin remove by " + p.getName(), null);
        m.send(p, Msg.MACHINE_REMOVED);
    }

    private static List<String> ids() {
        return Arrays.stream(CasinoItem.values()).map(CasinoItem::id).toList();
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        boolean admin = source.getSender().hasPermission("kaboofcasino.admin");
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            Stream<String> subs = Stream.of("stats", "abholen", "credits");
            if (admin) {
                subs = Stream.concat(subs, Stream.of("give", "remove", "reload"));
            }
            return subs.filter(s -> s.startsWith(prefix)).toList();
        }
        if (admin && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return ids().stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 3) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            }
        }
        return List.of();
    }
}
