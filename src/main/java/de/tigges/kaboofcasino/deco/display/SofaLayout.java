package de.tigges.kaboofcasino.deco.display;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Verbatim port of the mod's {@code casinodeco/SofaLayout} (how neighbouring sofas join into couches and corners) plus
 * the pieces of {@code CasinoSofaBlock} that only compute: the model geometry, {@code seatYaw}, {@code seatShift}.
 * Directions are the mod's cardinal indices 0 north, 1 east, 2 south, 3 west. Pure.
 */
public final class SofaLayout {

    /** {@code CasinoSofaBlock.SEAT_HEIGHT}, {@code SEAT_BACK_SHIFT}, {@code SEAT_CORNER_SHIFT}. */
    public static final double SEAT_HEIGHT = 0.625;
    public static final double SEAT_BACK_SHIFT = 0.1375;
    public static final double SEAT_CORNER_SHIFT = 0.1;

    /** Cardinal steps (x, z) of the mod's {@code CARDINAL}: north, east, south, west. */
    static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private SofaLayout() {
    }

    public static int bit(int direction) {
        return 1 << (direction & 3);
    }

    public static int turnMask(int mask, int turns) {
        turns &= 3;
        return (mask << turns | mask >> 4 - turns) & 15;
    }

    public static boolean isCorner(int joins) {
        return Integer.bitCount(joins) == 2 && joins != 5 && joins != 10;
    }

    public static int arms(int joins, int facing) {
        if (joins == 0) {
            return bit(facing + 1) | bit(facing + 3);
        }
        return Integer.bitCount(joins) == 1 ? bit(Integer.numberOfTrailingZeros(joins) + 2) : 0;
    }

    private static int axis(int joins) {
        if (joins != 0 && !isCorner(joins) && Integer.bitCount(joins) <= 2) {
            return (joins & 5) != 0 ? 0 : 1;
        }
        return -1;
    }

    /** {@code CasinoSofaBlock.geometry}: the model index, {@code sofa_<hex>}. */
    public static int geometry(int joins, int back, int facing) {
        return joins << 8 | back | arms(joins, facing) << 4;
    }

    /** The {@code back} mask the mod gives a new sofa ({@code getStateForPlacement}): behind the sitter. */
    public static int placedBack(int facing) {
        return bit(facing + 2);
    }

    public static Map<Cell, Layout> calculate(Map<Cell, Integer> facing) {
        Map<Cell, Integer> joins = new HashMap<>();
        for (Cell c : facing.keySet()) {
            int mask = 0;
            for (int d = 0; d < 4; d++) {
                if (facing.containsKey(c.step(d))) {
                    mask |= bit(d);
                }
            }
            joins.put(c, mask);
        }
        return calculate(facing, joins, Map.of());
    }

    public static Map<Cell, Layout> calculate(Map<Cell, Integer> facing, Map<Cell, Integer> joins,
                                              Map<Cell, Integer> continuationBack) {
        Map<Cell, Layout> result = new HashMap<>();
        for (Cell c : facing.keySet()) {
            int mask = joins.get(c);
            int f = facing.get(c);
            if (mask == 0) {
                result.put(c, new Layout(0, bit(f + 2), arms(0, f)));
            } else if (isCorner(mask) || Integer.bitCount(mask) > 2) {
                result.put(c, new Layout(mask, 15 ^ mask, 0));
            }
        }
        for (Cell start : facing.keySet()) {
            if (result.containsKey(start)) {
                continue;
            }
            int lineAxis = axis(joins.get(start));
            int perpendicular = lineAxis == 0 ? 10 : 5;
            List<Cell> segment = new ArrayList<>();
            Set<Cell> visited = new HashSet<>();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);
            TreeMap<Cell, Integer> constraints = new TreeMap<>();
            TreeMap<Cell, Integer> continuation = new TreeMap<>();
            Cell anchor = start;
            while (!queue.isEmpty()) {
                Cell cx = queue.remove();
                segment.add(cx);
                if (cx.compareTo(anchor) < 0) {
                    anchor = cx;
                }
                int savedBack = continuationBack.getOrDefault(cx, 0) & perpendicular;
                if (Integer.bitCount(savedBack) == 1) {
                    continuation.put(cx, savedBack);
                }
                for (int d : new int[] {lineAxis, lineAxis + 2}) {
                    if ((joins.get(cx) & bit(d)) != 0) {
                        Cell next = cx.step(d);
                        int nextMask = joins.get(next);
                        if (axis(nextMask) == lineAxis) {
                            if (visited.add(next)) {
                                queue.add(next);
                            }
                        } else {
                            int outside = (15 ^ nextMask) & perpendicular;
                            if (Integer.bitCount(outside) == 1) {
                                constraints.put(next, outside);
                            }
                        }
                    }
                }
            }
            int back;
            if (!constraints.isEmpty()) {
                back = constraints.firstEntry().getValue();
            } else if (!continuation.isEmpty()) {
                back = continuation.firstEntry().getValue();
            } else {
                int preferred = facing.get(anchor) + 2 & 3;
                back = bit(preferred);
                if ((back & perpendicular) == 0) {
                    back = bit(preferred + 1);
                }
            }
            for (Cell cxx : segment) {
                result.put(cxx, new Layout(joins.get(cxx), back, arms(joins.get(cxx), facing.get(cxx))));
            }
        }
        return result;
    }

    /** {@code CasinoSofaBlock.seatYaw}: degrees (Minecraft yaw: 0 south, 90 west). */
    public static float seatYaw(int back, int facing) {
        int x = 0;
        int z = 0;
        for (int d = 0; d < 4; d++) {
            if ((back & bit(d)) != 0) {
                x -= STEP[d][0];
                z -= STEP[d][1];
            }
        }
        return back == 0 ? toYRot(facing) : (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /** {@code CasinoSofaBlock.seatShift}: (x, z) in blocks, towards the back (or the corner). */
    public static double[] seatShift(int back) {
        double amount = Integer.bitCount(back) == 1 ? SEAT_BACK_SHIFT : SEAT_CORNER_SHIFT;
        double x = 0;
        double z = 0;
        for (int d = 0; d < 4; d++) {
            if ((back & bit(d)) != 0) {
                x += STEP[d][0] * amount;
                z += STEP[d][1] * amount;
            }
        }
        return new double[] {x, z};
    }

    /** {@code Direction.toYRot} of a cardinal index: north 180, east 270 (-90), south 0, west 90. */
    static float toYRot(int cardinal) {
        return switch (cardinal & 3) {
            case 0 -> 180f;
            case 1 -> 270f;
            case 2 -> 0f;
            default -> 90f;
        };
    }

    /** A sofa cell (x, z); ordered by x then z like the mod. */
    public record Cell(int x, int z) implements Comparable<Cell> {
        public Cell step(int direction) {
            return switch (direction & 3) {
                case 0 -> new Cell(x, z - 1);
                case 1 -> new Cell(x + 1, z);
                case 2 -> new Cell(x, z + 1);
                default -> new Cell(x - 1, z);
            };
        }

        @Override
        public int compareTo(Cell c) {
            int xOrder = Integer.compare(x, c.x);
            return xOrder != 0 ? xOrder : Integer.compare(z, c.z);
        }
    }

    /** One cell's result. */
    public record Layout(int joins, int backs, int arms) {
        public int geometry() {
            return joins << 8 | backs | arms << 4;
        }
    }
}
