package de.tigges.kaboofcasino;

import de.tigges.kaboofcasino.config.CasinoConfig;
import de.tigges.kaboofcasino.config.Messages;
import de.tigges.kaboofcasino.game.CasinoRandom;
import de.tigges.kaboofcasino.gui.GuiLayout;
import de.tigges.kaboofcasino.gui.MenuService;
import de.tigges.kaboofcasino.machine.MachineService;
import de.tigges.kaboofcasino.pack.PackSessions;
import de.tigges.kaboofcasino.wager.LedgerListener;
import de.tigges.kaboofcasino.visual.LightBudget;
import de.tigges.kaboofcasino.wager.WagerEngine;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/**
 * The shared services a game needs (wired once in onEnable). Games read config and messages through the suppliers,
 * so {@code /casino reload} applies without recreating them.
 *
 * @param clock epoch millis (round times)
 */
public record CasinoContext(Plugin plugin, Supplier<CasinoConfig> config, Supplier<Messages> messages,
                            WagerEngine engine, CasinoRandom random, GuiLayout layout, MenuService menus,
                            PackSessions packs, LedgerListener ledger, MachineService machines, LongSupplier clock,
                            LightBudget lights) {
}
