package de.tigges.kaboofcasino.game.derby;

import de.tigges.kaboofcasino.game.PanelText;
import de.tigges.kaboofcasino.wager.ItemAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The floating panel above the derby ({@code DuckDerbyRenderer.extractPanel} 111-160, {@code panel} 444-618,
 * {@code sharedPanel} 271-417, {@code countdown} 419-442 and {@code CasinoFloatingPanels.player/badge}), as the lines
 * of one text display:
 * <ul>
 *   <li><b>group</b> ({@code shared && members > 0}): while enrolling the countdown "Start in N s" and its bar (the
 *       remaining time over 200 ticks, in 20 steps); the title "N Spieler", while racing "N Spieler · Rennen läuft",
 *       from the result tick a second line "SIEGER: X · ZWEITER: Y"; up to 8 bettors in join order (head with the
 *       lane strip under it, name, up to three stakes as item sprite and "×N"; an entrant near the panel gets his own
 *       copy with his entry first, {@link DerbyPanelViewers}); "+ N Spieler" when more entered;</li>
 *   <li><b>solo</b> (otherwise, while any shown bet is set): the player's head and name (the badge, while no group
 *       session runs and an entrant exists), per staked lane its header (the duck's name on the lane colour inside
 *       box over its bronze line) and its stakes one per row, and from the result tick "SIEGER: &lt;duck&gt;" and
 *       "ZWEITER: &lt;duck&gt;" in bold with the duck's colour.</li>
 * </ul>
 * Nothing of the result exists in the content before {@code resultTick}: winner and second come from
 * {@link DerbyView#winnerShown}/{@link DerbyView#secondShown} at the panel's own game tick. Words are the mod's
 * translation keys (the pack has them in every language) with the German {@code de_de.json} text as fallback.
 * Bars use the pack's {@code kaboofcasino:derby_panel} font ({@code DerbyPack}). Pure.
 */
public final class DerbyPanel {

    /** {@code HEADER}: the lane colours (yellow, red, white, black). */
    static final int[] HEADER = {0xDEB74F, 0x9B2631, 0xEAE4CC, 0x21252B};
    /** {@code HEADER_TEXT}: the duck name on its header. */
    static final int[] HEADER_TEXT = {0x173C2E, 0xF9ECC1, 0x173C2E, 0xF9ECC1};
    /** {@code WINNER_NAME}: the duck's name in the solo result lines. */
    static final int[] WINNER_NAME = {0xFFFF55, 0xFF5555, 0xFFFFFF, 0xAAAAAA};
    /** {@code TITLE} (-398143): titles, names, the countdown. */
    static final TextColor TITLE = TextColor.color(0xF9ECC1);
    /** The countdown bar's fill (-866724). */
    static final TextColor BAR_FILL = TextColor.color(0xF2C65C);
    static final String[] NAME_KEYS = {"gui.roulettecasino.derby.duck_0", "gui.roulettecasino.derby.duck_1",
        "gui.roulettecasino.derby.duck_2", "gui.roulettecasino.derby.duck_3"};
    /** German fallbacks ({@code de_de.json}). */
    static final String[] NAMES = {"GELB", "ROT", "WEISS", "SCHWARZ"};
    static final String PAYOUT_KEY = "gui.roulettecasino.derby.payout";
    static final String PAYOUT = "SIEGER";
    static final String SECOND_KEY = "gui.roulettecasino.derby.second";
    static final String SECOND = "ZWEITER";
    static final String RESULT_LINE_KEY = "gui.roulettecasino.derby.result_line";
    static final String RESULT_LINE = "%s: %s";
    static final String COUNTDOWN_KEY = "gui.roulettecasino.derby.countdown";
    static final String COUNTDOWN = "Start in %s s";
    static final String RACING_KEY = "gui.roulettecasino.derby.racing";
    static final String RACING = "Rennen läuft";
    static final String MORE_KEY = "gui.roulettecasino.derby.more";
    static final String MORE = "+ %s Spieler";
    static final String PEOPLE = "%s Spieler";
    /** {@code sharedPanel}: at most 8 bettors; 3 stakes per bettor and per lane column. */
    static final int MAX_BETTORS = 8;
    static final int STAKES = 3;
    /** {@code ENROLL_TICKS}: the countdown bar spans 200 ticks; drawn in 20 steps of 5 px. */
    static final int ENROLL_TICKS = 200;
    static final int BAR_STEPS = 20;

    /** The pack's panel font ({@code DerbyPack}): code points and advances. */
    static final Key FONT = Key.key("kaboofcasino", "derby_panel");
    static final int NEG_SPACE = 0xE000;
    static final int POS_SPACE = 0xE008;
    static final int EDGE = 0xE010;
    static final int FILL = 0xE011;
    static final int STRIP = 0xE012;
    static final int TRACK = 0xE013;
    static final int BAR = 0xE014;
    static final int EDGE_ADVANCE = 57;
    static final int FILL_ADVANCE = 55;
    static final int STRIP_ADVANCE = 9;
    static final int TRACK_ADVANCE = 103;
    static final int BAR_STEP_PX = 5;

    private DerbyPanel() {
    }

    /** What the panel shows; equal contents need no resend. */
    public sealed interface Content permits Shared, Solo {
        /** Text lines of the display (for its height). */
        int lines();
    }

    /** One entrant's cell. */
    record Bettor(UUID id, String name, int lane, List<ItemAmount> stakes) {
        Bettor {
            stakes = List.copyOf(stakes);
        }
    }

    /** A staked lane's column: its stakes in row order. */
    record Lane(int lane, List<ItemAmount> stakes) {
        Lane {
            stakes = List.copyOf(stakes);
        }
    }

    /**
     * The group panel: entrants ({@code members}), countdown seconds and bar steps while enrolling, the shown bettors,
     * winner and second (-1 before the result tick).
     */
    record Shared(int members, boolean enrolling, int seconds, int bar, List<Bettor> bettors, int winner, int second)
            implements Content {
        Shared {
            bettors = List.copyOf(bettors);
        }

        @Override
        public int lines() {
            return (enrolling ? 2 : 0) + (winner >= 0 ? 2 : 1) + bettors.size() + (members > bettors.size() ? 1 : 0);
        }
    }

    /** The solo panel: badge (null without), the staked lanes, winner and second (-1 before the result tick). */
    record Solo(UUID badgeId, String badgeName, List<Lane> lanes, int winner, int second) implements Content {
        Solo {
            lanes = List.copyOf(lanes);
        }

        @Override
        public int lines() {
            int n = badgeId != null ? 1 : 0;
            for (Lane l : lanes) {
                n += 1 + l.stakes().size();
            }
            return n + (winner >= 0 ? 1 : 0) + (winner >= 0 && second >= 0 ? 1 : 0);
        }
    }

    /** {@code extractRenderState}/{@code extractPanel}/{@code panel} at game tick {@code gameTime} for everyone (the
     * entrants in join order); null = no panel. */
    public static Content content(DerbyView view, long gameTime) {
        return content(view, gameTime, null);
    }

    /**
     * The panel as {@code viewer} sees it: {@code DerbyLobby.visible(members, viewer, 8)} puts his own entry first
     * (then the others in join order); null = everyone's order. Only the group panel's bettors depend on the viewer.
     */
    public static Content content(DerbyView view, long gameTime, UUID viewer) {
        if (view == null) {
            return null;
        }
        int winner = view.winnerShown(gameTime);
        int second = winner >= 0 ? view.secondShown(gameTime) : -1;
        if (view.shared() && view.members() > 0) {
            List<Bettor> bettors = new ArrayList<>();
            for (DerbyMember m : DerbyMember.visible(view.bettors(), viewer, MAX_BETTORS)) {
                bettors.add(new Bettor(m.id(), m.name(), Math.floorMod(m.lane(), 4),
                        m.stakes().subList(0, Math.min(STAKES, m.stakes().size()))));
            }
            int seconds = view.enrolling() ? (int) Math.max(0L, (view.enrollmentUntil() - gameTime + 19L) / 20L) : 0;
            int bar = view.enrolling() ? barSteps(view.enrollmentUntil(), gameTime) : 0;
            return new Shared(view.members(), view.enrolling(), seconds, bar, bettors, winner, second);
        }
        List<Lane> lanes = new ArrayList<>();
        for (int lane = 0; lane < 4; lane++) {
            List<ItemAmount> stakes = new ArrayList<>();
            for (int row = 0; row < STAKES; row++) {
                ItemAmount a = view.displayed().get(row * 4 + lane);
                if (a != null) {
                    stakes.add(a);
                }
            }
            if (!stakes.isEmpty()) {
                lanes.add(new Lane(lane, stakes));
            }
        }
        if (lanes.isEmpty()) {
            return null;
        }
        DerbyMember badge = !view.shared() && !view.bettors().isEmpty() ? view.bettors().get(0) : null;
        return new Solo(badge == null ? null : badge.id(), badge == null ? null : badge.name(), lanes, winner,
                second);
    }

    /** {@code enrollmentProgress} (the remaining fraction of the 200 ticks) in {@value #BAR_STEPS} steps. */
    static int barSteps(long until, long gameTime) {
        float p = DerbyPanelPresentation.progress(until, gameTime, 0.0F, ENROLL_TICKS);
        return Math.clamp(Math.round(p * BAR_STEPS), 0, BAR_STEPS);
    }

    // ---- text ---------------------------------------------------------------------------------------------------------

    private static Component word(String key, String fallback, Component... args) {
        return Component.translatable().key(key).fallback(fallback).arguments(args).build();
    }

    static Component duckName(int lane) {
        return word(NAME_KEYS[lane], NAMES[lane]);
    }

    /** {@code peopleKey(count, language)}: one text for every viewer, so the plural rule of the languages without a
     * "few" form (person for 1, else people); German says "%s Spieler" for all. */
    static Component people(int count) {
        String key = DerbyPanelPresentation.peopleKey(count, "en_us");
        return word(key, PEOPLE, Component.text(count));
    }

    /** {@code result_line}: "%s: %s". */
    static Component resultLine(String key, String fallback, Component duck) {
        return word(RESULT_LINE_KEY, RESULT_LINE, word(key, fallback), duck);
    }

    /** Glyphs of the panel font, without a shadow (bars are flat in the mod). */
    private static Component glyphs(String text, TextColor color) {
        return Component.text(text, color).font(FONT).shadowColor(ShadowColor.none());
    }

    /** Space characters of the panel font moving the pen by {@code delta} px. */
    static String spaces(int delta) {
        StringBuilder out = new StringBuilder();
        int first = delta < 0 ? NEG_SPACE : POS_SPACE;
        int rest = Math.abs(delta);
        for (int bit = 7; bit >= 0; bit--) {
            while (rest >= 1 << bit) {
                out.appendCodePoint(first + bit);
                rest -= 1 << bit;
            }
        }
        return out.toString();
    }

    /** The default font's advance of the German duck names' letters (caps 6 px, "I" 4, space 4). */
    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += c == 'I' || c == ' ' ? 4 : 6;
        }
        return w;
    }

    /**
     * A lane header (renderer 520-537): the bronze line ({@code HEADER_EDGE}), the box in {@code HEADER[lane]} and the
     * duck's name centred on it in {@code HEADER_TEXT[lane]}; the line advances exactly the edge's width, so it stays
     * centred.
     */
    static Component header(int lane) {
        int w = width(NAMES[lane]);
        int start = 1 + (54 - w) / 2;
        return Component.text()
                .append(glyphs(Character.toString(EDGE), PanelText.WHITE))
                .append(glyphs(spaces(1 - EDGE_ADVANCE), PanelText.WHITE))
                .append(glyphs(Character.toString(FILL), TextColor.color(HEADER[lane])))
                .append(glyphs(spaces(start - 1 - FILL_ADVANCE), PanelText.WHITE))
                .append(duckName(lane).color(TextColor.color(HEADER_TEXT[lane])))
                .append(glyphs(spaces(EDGE_ADVANCE - start - w), PanelText.WHITE))
                .build();
    }

    /** {@code countdown} 432-439: the frame and the fill of {@code steps} x 5 px (the remaining time). */
    static Component bar(int steps) {
        TextComponent.Builder line = Component.text()
                .append(glyphs(Character.toString(TRACK) + spaces(1 - TRACK_ADVANCE), PanelText.WHITE));
        StringBuilder fill = new StringBuilder();
        for (int k = 4; k >= 0; k--) {
            if ((steps & (1 << k)) != 0) {
                fill.appendCodePoint(BAR + k).append(spaces(-1));
            }
        }
        line.append(glyphs(fill.toString(), BAR_FILL));
        line.append(glyphs(spaces(TRACK_ADVANCE - 1 - steps * BAR_STEP_PX), PanelText.WHITE));
        return line.build();
    }

    /** One stake: item sprite and "×N" ({@code PanelText.stakes} for one). */
    private static Component stake(ItemAmount a) {
        return PanelText.stakes(List.of(a));
    }

    /** A bettor's cell (renderer 359-397): the lane strip under the head, the name (title colour), the stakes. */
    static Component bettor(Bettor b) {
        TextComponent.Builder line = Component.text()
                .append(glyphs(Character.toString(STRIP) + spaces(-STRIP_ADVANCE), TextColor.color(HEADER[b.lane()])))
                .append(Component.object(ObjectContents.playerHead(b.id())))
                .append(Component.text(" " + b.name(), TITLE));
        for (ItemAmount a : b.stakes()) {
            line.append(Component.text("  ")).append(stake(a));
        }
        return line.build();
    }

    public static Component render(Content content) {
        return switch (content) {
            case Shared s -> shared(s);
            case Solo s -> solo(s);
        };
    }

    private static Component lines(List<Component> lines) {
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }

    static Component shared(Shared s) {
        List<Component> lines = new ArrayList<>();
        if (s.enrolling()) {
            lines.add(word(COUNTDOWN_KEY, COUNTDOWN, Component.text(s.seconds())).color(TITLE));
            lines.add(bar(s.bar()));
        }
        Component title = people(s.members());
        if (s.winner() >= 0) {
            lines.add(title.color(TITLE));
            lines.add(Component.text()
                    .append(resultLine(PAYOUT_KEY, PAYOUT, duckName(s.winner())))
                    .append(Component.text(" · "))
                    .append(resultLine(SECOND_KEY, SECOND, duckName(s.second())))
                    .color(TITLE).build());
        } else if (s.enrolling()) {
            lines.add(title.color(TITLE));
        } else {
            lines.add(Component.text().append(title).append(Component.text(" · "))
                    .append(word(RACING_KEY, RACING)).color(TITLE).build());
        }
        for (Bettor b : s.bettors()) {
            lines.add(bettor(b));
        }
        if (s.members() > s.bettors().size()) {
            lines.add(word(MORE_KEY, MORE, Component.text(s.members() - s.bettors().size())).color(TITLE));
        }
        return lines(lines);
    }

    static Component solo(Solo s) {
        List<Component> lines = new ArrayList<>();
        if (s.badgeId() != null) {
            lines.add(PanelText.header(s.badgeId(), s.badgeName()));
        }
        for (Lane l : s.lanes()) {
            lines.add(header(l.lane()));
            for (ItemAmount a : l.stakes()) {
                lines.add(stake(a));
            }
        }
        if (s.winner() >= 0) {
            lines.add(resultLine(PAYOUT_KEY, PAYOUT,
                    duckName(s.winner()).color(TextColor.color(WINNER_NAME[s.winner()])))
                    .color(PanelText.WHITE).decorate(TextDecoration.BOLD));
            if (s.second() >= 0) {
                lines.add(resultLine(SECOND_KEY, SECOND,
                        duckName(s.second()).color(TextColor.color(WINNER_NAME[s.second()])))
                        .color(PanelText.WHITE).decorate(TextDecoration.BOLD));
            }
        }
        return lines(lines);
    }

}
