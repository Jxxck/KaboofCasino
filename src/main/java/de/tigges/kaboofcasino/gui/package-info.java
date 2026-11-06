/**
 * Menu framework, adapted from PostMarkt's {@code gui} package (selection model). Binding rules for every menu:
 * <ul>
 *   <li>{@code Menu implements InventoryHolder}; recognised with {@code getTopInventory().getHolder(false) instanceof
 *       Menu}; one instance per viewer. The title is the art: font glyphs built per open ({@link TitleBuilder});
 *       changing parts are items in slots.</li>
 *   <li>EVERY click and drag in a casino view is cancelled, top and bottom. No real item ever lies in a menu slot or
 *       on the cursor; bottom clicks only <em>select</em> (the stake leaves the inventory at the commit).</li>
 *   <li>Menus open only in the tick of the click/interaction ({@link MenuService#beginInteraction}), never for
 *       creative, spectator, dead, sleeping or fake players, and never while unsafe ({@link MenuSafety},
 *       {@link DangerSweep}).</li>
 *   <li>Display copies carry {@code kaboofcasino:display}; a close safety net purges any copy that reached an
 *       inventory or the cursor.</li>
 * </ul>
 */
package de.tigges.kaboofcasino.gui;
