package de.tigges.kaboofcasino.board;

import de.tigges.kaboofcasino.CasinoContext;
import de.tigges.kaboofcasino.config.Msg;
import de.tigges.kaboofcasino.machine.Machine;
import de.tigges.kaboofcasino.machine.MachineGame;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * One leaderboard (wall board or column): shows a page of the shared {@link BoardSnapshot}; turns to the next page
 * every {@code leaderboard.cycle-seconds} unless the croupier's hand pinned it; a right click goes to the next category
 * (with the category pinned: the other period). No rounds, never busy. Thread: MAIN.
 */
public final class BoardController implements MachineGame {

    /** One page turn per board per half second at most (right-click spam). */
    static final int CLICK_GAP_TICKS = 10;

    private final Machine machine;
    private final CasinoContext ctx;
    private final BoardService service;
    private final BoardRig rig;
    private BoardPin.Page page;
    private long lastTurn;
    private long lastClick = Long.MIN_VALUE / 2;
    private long now;

    BoardController(Machine machine, CasinoContext ctx, BoardService service) {
        this.machine = machine;
        this.ctx = ctx;
        this.service = service;
        this.rig = new BoardRig(machine);
        this.page = pin().pages().getFirst();
    }

    public Machine machine() {
        return machine;
    }

    public BoardPin pin() {
        return BoardPin.parse(machine.record().data());
    }

    BoardPin.Page page() {
        return page;
    }

    private World world() {
        return Bukkit.getWorld(NamespacedKey.fromString(machine.anchor().world()));
    }

    @Override
    public void spawn() {
        World world = world();
        if (world != null) {
            rig.spawn(world);
            render();
        }
    }

    @Override
    public void despawn() {
        rig.despawn();
    }

    @Override
    public void tick(long tick) {
        now = tick;
        if (tick % 20 != 0 || !rig.spawned()) {
            return;
        }
        int cycle = ctx.config().get().leaderboard().cycleSeconds();
        List<BoardPin.Page> pages = pin().pages();
        if (cycle > 0 && pages.size() > 1 && tick - lastTurn >= cycle * 20L) {
            int i = pages.indexOf(page);
            page = pages.get((i + 1) % pages.size());
            lastTurn = tick;
        }
        render();
    }

    /** Draws the current page from the latest snapshot (the rig skips unchanged pages). */
    void render() {
        BoardSnapshot s = service.snapshot();
        BoardRig.Layout l = rig.layout();
        rig.show(BoardView.page(page, s == null ? List.of() : service.visible(s.top(page.category(), page.period())),
                s == null ? ctx.config().get().leaderboard().minStake() : s.minStake(), l.namePx(), l.leftPx(),
                l.rightPx(), l.compact(), s == null, ctx.messages().get()));
    }

    @Override
    public boolean busy() {
        return false;
    }

    @Override
    public void interact(Player player) {
        if (now - lastClick < CLICK_GAP_TICKS) {
            return;
        }
        lastClick = now;
        var next = pin().clicked(page);
        if (next.isEmpty()) {
            ctx.messages().get().actionBar(player, Msg.BOARD_PINNED);
            return;
        }
        page = next.get();
        lastTurn = now;
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        render();
    }

    /** The croupier's hand changed the pin: saved in the record, the page follows it at once. */
    public void pinned(BoardPin pin) {
        ctx.machines().saveData(machine, pin.format());
        page = pin.fit(page);
        lastTurn = now;
        render();
    }

    @Override
    public void removed() {
        rig.despawn();
    }

    @Override
    public boolean ownsRound(UUID roundId) {
        return false;
    }

    @Override
    public void checkRig() {
        if (!rig.intact()) {
            spawn();
        }
    }
}
