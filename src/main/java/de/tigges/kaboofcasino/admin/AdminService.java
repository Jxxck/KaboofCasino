package de.tigges.kaboofcasino.admin;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.ConfigEdits;
import de.tigges.kaboofcasino.config.ConfigLoader;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.CasinoItem;
import de.tigges.kaboofcasino.machine.CasinoItems;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineSettings;
import de.tigges.kaboofcasino.machine.MachineType;
import de.tigges.kaboofcasino.util.FakePlayers;
import de.tigges.kaboofcasino.util.Keys;
import de.tigges.kaboofcasino.util.Log;
import de.tigges.kaboofcasino.util.Threads;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.dialog.DialogResponseView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * The croupier's hand (games.md 10.5, 20; PLAN decision 6), Jack's partial port: admins ({@value #PERMISSION}, ops) in
 * creative mode set a machine's name and its own win chance or bias (stored in the machine's record), copy them into
 * the hand and paste them onto another machine of the same game, and set each game's type value and "breakable in
 * survival" for the whole casino (written into {@code config.yml} and applied like {@code /casino reload}). The
 * games read the result through {@link MachineSettings}.
 *
 * <ul>
 *   <li>Use on a machine: its dialog; sneaking: paste the copy onto it ("Einstellungen auf %s eingefügt", or "Keine
 *       Kopie in der Hand ...").</li>
 *   <li>Use in the air or on a plain block: the whole casino's dialog; sneaking in the air: forget the copy
 *       ("Kopierte Einstellungen vergessen."); sneaking on a plain block: nothing.</li>
 *   <li>Outside creative every use only says "Die Hand des Croupiers funktioniert nur im Kreativmodus."; in creative
 *       without the permission: "Dazu fehlt dir die Berechtigung."-message of the plugin.</li>
 *   <li>At most 20 uses and dialog clicks per player and game second ({@link EditRate}); every change is logged.</li>
 * </ul>
 * The floating name labels over machines ({@link AdminLabels}) and the check that closes a dialog whose player or
 * machine is gone run every {@value #TICKS} ticks. Thread: MAIN.
 */
public final class AdminService {

    public static final String PERMISSION = "kaboofcasino.admin";
    /** Hand PDC: the copy ({@link HandCopy}). */
    static final NamespacedKey COPY = Keys.key("croupier_copy");
    /** Overworld PDC: the copied values by token ({@link Clipboard}). */
    static final NamespacedKey CLIPBOARD = Keys.key("croupier_clipboard");
    static final int TICKS = 5;

    private final CasinoContext ctx;
    private final IntSupplier reload;
    private final AdminSessions sessions = new AdminSessions();
    private final EditRate rate = new EditRate();
    private final Map<UUID, Long> usedTick = new HashMap<>();
    private Clipboard clipboard = new Clipboard();
    private AdminLabels labels;
    private BukkitTask task;

    /** @param reload {@code /casino reload} (config and texts); returns the number of warnings */
    public AdminService(CasinoContext ctx, IntSupplier reload) {
        this.ctx = ctx;
        this.reload = reload;
    }

    public void enable() {
        World overworld = Bukkit.getWorlds().getFirst();
        clipboard = Clipboard.decode(overworld.getPersistentDataContainer().get(CLIPBOARD, PersistentDataType.STRING));
        labels = new AdminLabels(ctx, this::allowed);
        Bukkit.getPluginManager().registerEvents(new HandListener(this, ctx.machines()), ctx.plugin());
        task = Bukkit.getScheduler().runTaskTimer(ctx.plugin(), this::tick, TICKS, TICKS);
        Log.get().info("KaboofCasino croupier's hand: {} copied setting(s) kept", clipboard.size());
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : sessions.open()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.closeDialog();
            }
        }
        sessions.clear();
        rate.clear();
        usedTick.clear();
        if (labels != null) {
            labels.clear();
        }
    }

    // ---- who may ---------------------------------------------------------------------------------------------------

    /** {@code CasinoSettings.allowed} plus the admin permission: creative, the hand in either hand, an admin. */
    boolean allowed(Player player) {
        return player.isConnected() && !FakePlayers.isFake(player) && player.getGameMode() == GameMode.CREATIVE
                && player.hasPermission(PERMISSION) && handSlot(player) != null;
    }

    /** The hand that holds the croupier's hand: the main hand first ({@code CasinoSettings.hand}). */
    private static EquipmentSlot handSlot(Player player) {
        PlayerInventory inv = player.getInventory();
        if (isHand(inv.getItemInMainHand())) {
            return EquipmentSlot.HAND;
        }
        return isHand(inv.getItemInOffHand()) ? EquipmentSlot.OFF_HAND : null;
    }

    static boolean isHand(ItemStack stack) {
        return CasinoItems.of(stack).orElse(null) == CasinoItem.CROUPIER_HAND;
    }

    /**
     * Gate of every use: one per click (both hands send one), creative, permission, rate limit. Tells the player why
     * not; false = do nothing.
     */
    private boolean mayUse(Player player) {
        long tick = Bukkit.getCurrentTick();
        Long before = usedTick.put(player.getUniqueId(), tick);
        if (before != null && before == tick) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.sendActionBar(AdminTexts.message("creative_only"));
            return false;
        }
        if (!player.hasPermission(PERMISSION)) {
            ctx.messages().get().actionBar(player, Msg.ERROR_NO_PERMISSION);
            return false;
        }
        return !limited(player);
    }

    private boolean limited(Player player) {
        boolean limited = rate.limited(player.getUniqueId(), player.getWorld().getGameTime());
        if (limited) {
            Log.get().debug("KaboofCasino croupier's hand: edit of {} dropped (rate limit)", player.getName());
        }
        return limited;
    }

    // ---- uses ------------------------------------------------------------------------------------------------------

    /** Right click in the air: the whole casino; sneaking: forget the copy ({@code CroupierHandItem.use}). */
    void useInAir(Player player, boolean sneaking) {
        Threads.requireMain();
        if (!mayUse(player)) {
            return;
        }
        if (sneaking) {
            forgetCopy(player);
        } else {
            open(player, AdminSessions.Target.world());
        }
    }

    /** Right click on a block that is no machine: like the air, but sneaking does nothing ({@code interact}: FAIL). */
    void useOnBlock(Player player, boolean sneaking) {
        Threads.requireMain();
        if (player.getGameMode() != GameMode.CREATIVE) {
            mayUse(player);
            return;
        }
        if (!sneaking) {
            useInAir(player, false);
        }
    }

    /** Right click on a machine: its dialog; sneaking: paste ({@code CasinoSettings.interact}). */
    void useOnMachine(Player player, Machine machine, boolean sneaking) {
        Threads.requireMain();
        if (!mayUse(player)) {
            return;
        }
        if (sneaking) {
            paste(player, machine, true);
        } else {
            open(player, new AdminSessions.Target(AdminClick.Form.of(machine.type()), machine.id()));
        }
    }

    // ---- dialog clicks ---------------------------------------------------------------------------------------------

    void click(Player player, DialogResponseView view) {
        Threads.requireMain();
        Optional<AdminSessions.Target> open = sessions.target(player.getUniqueId());
        if (open.isEmpty()) {
            Log.get().debug("KaboofCasino croupier's hand: click of {} without an open dialog", player.getName());
            return;
        }
        Optional<AdminClick> click = view == null ? Optional.empty()
                : AdminClick.parse(open.get().form(), view::getText, view::getFloat, view::getBoolean);
        if (click.isEmpty()) {
            Log.get().warn("KaboofCasino croupier's hand: invalid click payload from {} ignored", player.getName());
            return;
        }
        long tick = Bukkit.getCurrentTick();
        switch (sessions.apply(player.getUniqueId(), click.get(), tick)) {
            case AdminSessions.Closed closed -> {
                player.closeDialog();
                if (allowed(player) && closed.click().inputs().isPresent() && !limited(player)) {
                    applyInputs(player, closed.target(), closed.click().inputs().get());
                }
            }
            case AdminSessions.Ignored ignored -> Log.get().debug("KaboofCasino croupier's hand: click {} of {} "
                    + "ignored ({})", click.get().kind(), player.getName(), ignored.why());
            case AdminSessions.Confirm confirm -> {
                if (still(player, confirm.target()) && !limited(player)) {
                    show(player, confirm.target(), false, false);
                }
            }
            case AdminSessions.Run run -> {
                if (still(player, run.target()) && !limited(player)) {
                    run(player, run.target(), run.click());
                }
            }
        }
    }

    /** True if the player may still edit and the dialog's machine is still there; else closes the dialog. */
    private boolean still(Player player, AdminSessions.Target target) {
        if (allowed(player) && (target.machine() == null || ctx.machines().byId(target.machine()).isPresent())) {
            return true;
        }
        close(player, "no longer allowed or machine gone");
        return false;
    }

    private void run(Player player, AdminSessions.Target target, AdminClick click) {
        Optional<Machine> machine = target.machine() == null ? Optional.empty()
                : ctx.machines().byId(target.machine());
        boolean copied = false;
        boolean pasted = false;
        switch (click.kind()) {
            case SAVE, FORGET -> {
                click.inputs().ifPresent(in -> applyInputs(player, target, in));
                if (click.kind() == AdminClick.Kind.FORGET) {
                    forgetCopy(player);
                }
            }
            case COPY -> {
                click.inputs().ifPresent(in -> applyInputs(player, target, in));
                copy(player, machine.orElseThrow());
                copied = true;
            }
            case PASTE -> {
                paste(player, machine.orElseThrow(), false);
                pasted = true;
            }
            case RESET -> {
                Machine m = machine.orElseThrow();
                saveOwn(player, m, m.record().own().reset(), "reset");
            }
            case ALL_NORMAL -> allNormal(player);
            case DONE -> throw new IllegalStateException("closed above");
        }
        if (sessions.isOpen(player.getUniqueId())) {
            show(player, target, copied, pasted);
        }
    }

    // ---- showing ---------------------------------------------------------------------------------------------------

    private void open(Player player, AdminSessions.Target target) {
        sessions.open(player.getUniqueId(), target, Bukkit.getCurrentTick());
        show(player, target, false, false);
    }

    private void show(Player player, AdminSessions.Target target, boolean copied, boolean pasted) {
        try {
            AdminClick.Kind confirming = sessions.confirming(player.getUniqueId(), Bukkit.getCurrentTick())
                    .orElse(null);
            CasinoConfig config = ctx.config().get();
            AdminView view;
            if (target.machine() == null) {
                view = AdminDialogs.world(new AdminDialogs.WorldData(typeValues(config),
                        config.machines().breakable(), copyIn(player).isPresent(), confirming));
            } else {
                Machine m = ctx.machines().byId(target.machine()).orElseThrow();
                view = AdminDialogs.machine(new AdminDialogs.MachineData(m.type(), m.record().number(),
                        m.record().own(), MachineSettings.typeValue(config, m.type()), m.anchor().x(), m.anchor().y(),
                        m.anchor().z(), m.anchor().world(), copyIn(player).isPresent(), confirming, copied, pasted));
            }
            player.showDialog(AdminPaper.dialog(view));
        } catch (RuntimeException e) {
            sessions.forget(player.getUniqueId());
            player.closeDialog();
            String code = Log.incident("croupier's hand dialog for " + player.getName(), e);
            ctx.messages().get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
        }
    }

    private static Map<MachineType, Integer> typeValues(CasinoConfig config) {
        Map<MachineType, Integer> values = new EnumMap<>(MachineType.class);
        for (MachineType type : MachineSettings.MOD_ORDER) {
            values.put(type, MachineSettings.typeValue(config, type));
        }
        return values;
    }

    private void close(Player player, String why) {
        if (sessions.forget(player.getUniqueId())) {
            Log.get().debug("KaboofCasino closes the croupier's hand dialog of {} ({})", player.getName(), why);
            player.closeDialog();
        }
    }

    // ---- changes ---------------------------------------------------------------------------------------------------

    private void applyInputs(Player player, AdminSessions.Target target, AdminClick.Inputs inputs) {
        switch (inputs) {
            case AdminClick.MachineInputs in -> ctx.machines().byId(target.machine()).ifPresent(m -> saveOwn(player,
                    m, AdminDialogs.resolve(m.type(), m.record().own(), MachineSettings.typeValue(ctx.config().get(),
                            m.type()), in), "edit"));
            case AdminClick.WorldInputs in -> {
                CasinoConfig config = ctx.config().get();
                Map<MachineType, Integer> old = typeValues(config);
                Map<MachineType, Integer> next = AdminDialogs.resolve(old, in);
                Map<String, Object> changes = new LinkedHashMap<>();
                for (MachineType type : MachineSettings.MOD_ORDER) {
                    if (!next.get(type).equals(old.get(type))) {
                        changes.put(MachineSettings.configPath(type), next.get(type));
                    }
                }
                if (in.breakable() != config.machines().breakable()) {
                    changes.put("machines.breakable", in.breakable());
                }
                writeConfig(player, changes, "edit");
            }
        }
    }

    /** {@code cleartypes} ("Alles normal"): every win chance back to -1, every bias to 0. */
    private void allNormal(Player player) {
        CasinoConfig config = ctx.config().get();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (MachineType type : MachineSettings.MOD_ORDER) {
            int normal = MachineSettings.usesBias(type) ? 0 : MachineSettings.INHERIT_CHANCE;
            if (MachineSettings.typeValue(config, type) != normal) {
                changes.put(MachineSettings.configPath(type), normal);
            }
        }
        writeConfig(player, changes, "all normal");
    }

    private void writeConfig(Player player, Map<String, Object> changes, String action) {
        if (changes.isEmpty()) {
            return;
        }
        CasinoConfig before = ctx.config().get();
        Path file = ctx.plugin().getDataFolder().toPath().resolve(ConfigLoader.FILE);
        try {
            ConfigEdits.write(file, changes);
        } catch (IOException | RuntimeException e) {
            String code = Log.incident("croupier's hand: writing " + changes + " into " + file, e);
            ctx.messages().get().send(player, Msg.ERROR_INTERNAL, Messages.text("code", code));
            return;
        }
        int warnings = reload.getAsInt();
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, Object> e : changes.entrySet()) {
            details.add(e.getKey() + " " + old(before, e.getKey()) + " -> " + e.getValue());
        }
        Log.get().info("{} changed the casino's settings ({}): {}; config.yml rewritten and reloaded ({} warning(s))",
                player.getName(), action, String.join(", ", details), warnings);
    }

    private static Object old(CasinoConfig config, String path) {
        if (path.equals("machines.breakable")) {
            return config.machines().breakable();
        }
        for (MachineType type : MachineSettings.MOD_ORDER) {
            if (MachineSettings.configPath(type).equals(path)) {
                return MachineSettings.typeValue(config, type);
            }
        }
        return "?";
    }

    private void saveOwn(Player player, Machine machine, MachineSettings.Own next, String action) {
        MachineSettings.Own old = machine.record().own();
        if (next.equals(old)) {
            return;
        }
        ctx.machines().saveOwn(machine, next);
        List<String> details = new ArrayList<>();
        if (!old.name().equals(next.name())) {
            details.add("name '" + old.name() + "' -> '" + next.name() + "'");
        }
        if (old.chance() != next.chance()) {
            details.add("chance " + chance(old.chance()) + " -> " + chance(next.chance()));
        }
        if (old.bias() != next.bias()) {
            details.add("bias " + bias(old.bias()) + " -> " + bias(next.bias()));
        }
        Log.get().info("{} changed machine settings of {} #{} at {} ({}): {}", player.getName(), machine.type().id(),
                machine.record().number(), machine.anchor(), action, String.join(", ", details));
    }

    private static String chance(int chance) {
        return chance == MachineSettings.INHERIT_CHANCE ? "inherit" : Integer.toString(chance);
    }

    private static String bias(int bias) {
        return bias == MachineSettings.INHERIT_BIAS ? "inherit" : Integer.toString(bias);
    }

    // ---- copy and paste --------------------------------------------------------------------------------------------

    /** The copy in the hand the player uses (main hand first), if any. */
    private Optional<HandCopy> copyIn(Player player) {
        EquipmentSlot slot = handSlot(player);
        if (slot == null) {
            return Optional.empty();
        }
        ItemStack hand = player.getInventory().getItem(slot);
        return HandCopy.decode(hand.getPersistentDataContainer().get(COPY, PersistentDataType.STRING));
    }

    /** {@code copy}: the values under a new token on the server, the token and the source in the hand. */
    private void copy(Player player, Machine machine) {
        EquipmentSlot slot = handSlot(player);
        if (slot == null) {
            return;
        }
        MachineSettings.Own own = machine.record().own();
        UUID token = UUID.randomUUID();
        clipboard.put(token, new Clipboard.Copied(own.chance(), own.bias()));
        saveClipboard();
        HandCopy copy = new HandCopy(machine.type(), token, machine.anchor().x(), machine.anchor().y(),
                machine.anchor().z(), machine.record().number(), own.name());
        ItemStack hand = player.getInventory().getItem(slot).clone();
        hand.editPersistentDataContainer(pdc -> pdc.set(COPY, PersistentDataType.STRING, copy.encode()));
        hand.setData(DataComponentTypes.LORE, ItemLore.lore(lore(copy)));
        player.getInventory().setItem(slot, hand);
        Log.get().info("{} copied the settings of {} #{} at {} (chance {}, bias {})", player.getName(),
                machine.type().id(), machine.record().number(), machine.anchor(), chance(own.chance()),
                bias(own.bias()));
    }

    /**
     * {@code paste}: the copied win chance and bias onto the machine if it is the same game (else nothing of this
     * port's settings changes, as in the mod); by sneak-click also "Einstellungen auf %s eingefügt".
     */
    private void paste(Player player, Machine machine, boolean bySneaking) {
        Optional<HandCopy> copy = copyIn(player);
        if (copy.isEmpty()) {
            player.sendActionBar(AdminTexts.message("nothing_to_paste"));
            return;
        }
        Clipboard.Copied values = clipboard.get(copy.get().token()).orElse(Clipboard.Copied.NONE);
        MachineSettings.Own own = machine.record().own();
        if (copy.get().type() == machine.type()) {
            saveOwn(player, machine, own.withChance(values.chance()).withBias(values.bias()), "paste");
        } else {
            Log.get().info("{} pasted a {} copy onto {} #{} at {}: another game, nothing to take over",
                    player.getName(), copy.get().type().id(), machine.type().id(), machine.record().number(),
                    machine.anchor());
        }
        if (bySneaking) {
            player.sendActionBar(AdminTexts.message("pasted", AdminTexts.displayName(machine.type(),
                    machine.record().own().name(), machine.record().number())));
        }
    }

    /** {@code forgetcopy}: removes the copy from the hand ("Kopierte Einstellungen vergessen."); nothing without one. */
    private void forgetCopy(Player player) {
        EquipmentSlot slot = handSlot(player);
        if (slot == null || !player.getInventory().getItem(slot).getPersistentDataContainer().has(COPY)) {
            return;
        }
        ItemStack hand = player.getInventory().getItem(slot).clone();
        hand.editPersistentDataContainer(pdc -> pdc.remove(COPY));
        hand.setData(DataComponentTypes.LORE, ItemLore.lore(lore(null)));
        player.getInventory().setItem(slot, hand);
        player.sendActionBar(AdminTexts.message("copy_forgotten"));
        Log.get().info("{} forgot the copied settings in the croupier's hand", player.getName());
    }

    /** The hand's lore: the item's grey line, with a copy also "Kopierte Einstellungen: ..." and the help line. */
    private List<Component> lore(HandCopy copy) {
        List<Component> lines = new ArrayList<>();
        lines.add(ctx.messages().get().render(Msg.ITEM_CROUPIER_HAND_LORE));
        if (copy != null) {
            Component source = copy.number() > 0 ? AdminTexts.displayName(copy.type(), copy.name(), copy.number())
                    : AdminTexts.typeName(copy.type());
            lines.add(AdminTexts.item("copied", source, Component.text(copy.x()), Component.text(copy.y()),
                    Component.text(copy.z())).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lines.add(AdminTexts.item("copied_help").color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }

    private void saveClipboard() {
        Bukkit.getWorlds().getFirst().getPersistentDataContainer().set(CLIPBOARD, PersistentDataType.STRING,
                clipboard.encode());
    }

    // ---- housekeeping ----------------------------------------------------------------------------------------------

    /** Every {@value #TICKS} ticks: the labels, and dialogs whose player may no longer edit or whose machine is gone. */
    private void tick() {
        for (UUID id : sessions.open()) {
            Player player = Bukkit.getPlayer(id);
            AdminSessions.Target target = sessions.target(id).orElse(null);
            if (player == null) {
                sessions.forget(id);
            } else if (target != null) {
                still(player, target);
            }
        }
        try {
            labels.tick();
        } catch (RuntimeException e) {
            Log.incident("croupier's hand labels", e);
        }
    }

    /** The client closed the dialog itself (death screen, world change) or the player left. */
    void forget(Player player) {
        sessions.forget(player.getUniqueId());
    }

    void quit(Player player) {
        sessions.forget(player.getUniqueId());
        rate.forget(player.getUniqueId());
        usedTick.remove(player.getUniqueId());
    }
}
