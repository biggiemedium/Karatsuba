package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.PresenceTracker;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.BlastResistance;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks the holes a player can stand in and be safe from explosions.
 *
 * <p>A hole is only safe if an explosion cannot open it, so its floor and every
 * wall must be blast-proof &mdash; bedrock, or obsidian and the blocks that hold
 * like it (see {@link Blocks#blastResistance}). A gap walled with dirt is a gap,
 * not a hole, and is not shown.
 *
 * <h2>Single and double</h2>
 * A <b>single</b> hole is one open cell with a safe floor and four safe walls. A
 * <b>double</b> is two open cells side by side, open to each other, with both
 * floors and all six outer walls safe. Each is coloured by what it is made of:
 * all bedrock, all obsidian, or a mix &mdash; bedrock walls cannot be mined out
 * from under you, obsidian ones can.
 *
 * <h2>Draws only</h2>
 * It writes no game state, so no service arbitrates it: two modules drawing are
 * two outlines, not a conflict. Arbiters exist for contended writes; reads and
 * draws never contend.
 *
 * <h2>Scanned on the tick, drawn every frame</h2>
 * The scan is a few thousand block lookups: nothing at twenty a second, the
 * module's whole cost at three hundred, and the answer does not change between
 * frames. So the tick finds the holes and the frame draws what the tick found.
 */
@ModuleInfo(name = "Hole ESP", description = "Highlights bedrock and obsidian holes, single and double",
        category = "Render")
public final class HoleEspModule extends ClientModule {

    /** The four directions a hole is walled on. */
    private static final Vec3i[] SIDES = {
            Vec3i.of(1, 0, 0), Vec3i.of(-1, 0, 0),
            Vec3i.of(0, 0, 1), Vec3i.of(0, 0, -1)
    };

    /**
     * Where a double hole's second cell may be. Only the positive two, so each
     * double is found once, from its western or northern cell, never from both.
     */
    private static final Vec3i[] DOUBLE_DIRECTIONS = {Vec3i.of(1, 0, 0), Vec3i.of(0, 0, 1)};

    /** A drawn hole is inset this much from each wall, so its faces do not z-fight the blocks. */
    private static final double INSET = 0.002d;

    // ------------------------------------------------------------------ range

    private final NumberSetting<Integer> radius = integer("Radius", 8, 2, 24)
            .describe("Blocks out from the player, horizontally");

    private final NumberSetting<Integer> depth = integer("Depth", 6, 3, 20)
            .describe("Blocks up and down from the player's feet");

    // ------------------------------------------------------------------ holes

    private final BooleanSetting bedrock = bool("Bedrock", true)
            .describe("Holes walled entirely with bedrock");
    private final BooleanSetting obsidian = bool("Obsidian", true)
            .describe("Holes walled entirely with obsidian, crying obsidian or ender chests");
    private final BooleanSetting mixed = bool("Mixed", true)
            .describe("Holes walled with some of each");
    private final BooleanSetting doubles = bool("Double Holes", true)
            .describe("Two open cells side by side, walled all round");

    private final GroupSetting holeTypes = group("Holes", true)
            .containing(bedrock, obsidian, mixed, doubles);

    // ------------------------------------------------------------- appearance

    private final ColorSetting bedrockColor = color("Bedrock Colour", Color.of(61, 229, 42, 200))
            .visibleWhen(bedrock);
    private final ColorSetting obsidianColor = color("Obsidian Colour", Color.of(165, 72, 215, 200))
            .visibleWhen(obsidian);
    private final ColorSetting mixedColor = color("Mixed Colour", Color.of(34, 133, 241, 200))
            .visibleWhen(mixed);

    private final BooleanSetting fill = bool("Fill", true);
    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.25f, 0.05f, 1f).step(0.05f)
            .describe("The fill's opacity, as a share of the colour's")
            .visibleWhen(fill);
    private final NumberSetting<Float> height = number("Height", 1f, 0.1f, 2f).step(0.1f)
            .describe("How tall the drawn box is, in blocks");
    private final NumberSetting<Float> thickness = number("Thickness", 1.2f, 0.5f, 4f).step(0.1f);

    private final BooleanSetting throughWalls = bool("Through Walls", true)
            .describe("Show holes behind blocks too, not only the ones in plain sight");
    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop holes in as they are found and fade them out when they stop being holes");

    private final GroupSetting appearance = group("Appearance", false)
            .containing(bedrockColor, obsidianColor, mixedColor, fill, fillOpacity, height, thickness,
                    throughWalls, animations);

    /**
     * The holes on screen, appearing, shown or fading. Keyed by the hole itself,
     * so a single that becomes a double, or obsidian that becomes mixed, fades out
     * as what it was and pops in as what it is.
     */
    private final PresenceTracker<Hole, Hole> holes = PresenceTracker.popIn();

    /** How many holes the last scan found, for the ArrayList. Ones fading out are not counted. */
    private int found;

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            holes.clear();
            found = 0;
            return;
        }
        holes.setAnimated(animations.isOn());
        List<Hole> scanned = scan(Players.blockPosition(), radius.getInt(), depth.getInt(), doubles.isOn(), Cells.LIVE)
                .stream().filter(hole -> shows(hole.material())).toList();
        holes.begin();
        for (Hole hole : scanned) {
            holes.report(hole, true, hole);
        }
        holes.end();
        found = scanned.size();
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (holes.size() == 0) {
            return;
        }
        Render.setDepthTest(!throughWalls.isOn());
        try {
            for (PresenceTracker.Entry<Hole> hole : holes.entries()) {
                draw(hole.value(), hole.presence(), hole.alpha());
            }
        } finally {
            // Back on for whatever draws after this module in the same frame.
            Render.setDepthTest(true);
        }
    }

    /**
     * One hole, at some point in its animation.
     *
     * <p>Grows up out of the floor and out from its middle, so it reads as the
     * hole filling with colour; the pop-in curve's overshoot makes it rise a touch
     * past full and settle.
     */
    private void draw(Hole hole, float presence, float alpha) {
        double scale = Math.max(0f, presence);
        if (scale <= 0d || alpha <= 0f) {
            return;
        }
        Vec3i low = hole.first();
        Vec3i high = hole.isDouble() ? hole.second() : hole.first();
        double minX = Math.min(low.getX(), high.getX());
        double minZ = Math.min(low.getZ(), high.getZ());
        double maxX = Math.max(low.getX(), high.getX()) + 1d;
        double maxZ = Math.max(low.getZ(), high.getZ()) + 1d;

        double centerX = (minX + maxX) / 2d;
        double centerZ = (minZ + maxZ) / 2d;
        double halfX = ((maxX - minX) / 2d - INSET) * scale;
        double halfZ = ((maxZ - minZ) / 2d - INSET) * scale;
        double floor = low.getY() + INSET;
        double top = floor + (height.getFloat() - INSET * 2d) * scale;

        Box box = Box.of(centerX - halfX, floor, centerZ - halfZ, centerX + halfX, top, centerZ + halfZ);
        Color color = colorOf(hole.material());
        if (fill.isOn()) {
            Render.box(box, color.fade(alpha * fillOpacity.getFloat()));
        }
        Render.boxOutline(box, thickness.getFloat(), color.fade(alpha));
    }

    private Color colorOf(Material material) {
        return switch (material) {
            case BEDROCK -> bedrockColor.resolve();
            case OBSIDIAN -> obsidianColor.resolve();
            case MIXED -> mixedColor.resolve();
        };
    }

    private boolean shows(Material material) {
        return switch (material) {
            case BEDROCK -> bedrock.isOn();
            case OBSIDIAN -> obsidian.isOn();
            case MIXED -> mixed.isOn();
        };
    }

    // ------------------------------------------------------------------- scan

    /**
     * Walks the box around {@code feet} looking for holes, of every material.
     *
     * <p>The cheap test comes first: most cells fail on "is there room here" and
     * never reach a wall lookup. A cell already claimed by a double is skipped, so
     * a double's second cell is never also reported as something else.
     *
     * <p>Pure over {@code cells}, which is the live world in game and a fake grid
     * when the rules are being checked.
     */
    public static List<Hole> scan(Vec3i feet, int reach, int down, boolean doubles, Cells cells) {
        List<Hole> result = new ArrayList<>();
        Set<Vec3i> claimed = new HashSet<>();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -down; dy <= down; dy++) {
                    Vec3i cell = feet.add(dx, dy, dz);
                    if (claimed.contains(cell) || !hasRoom(cell, cells)) {
                        continue;
                    }
                    Hole hole = single(cell, cells);
                    if (hole == null && doubles) {
                        hole = doubleFrom(cell, cells);
                    }
                    if (hole == null) {
                        continue;
                    }
                    result.add(hole);
                    claimed.add(hole.first());
                    if (hole.isDouble()) {
                        claimed.add(hole.second());
                    }
                }
            }
        }
        return result;
    }

    /** @return whether a player fits in this cell: it and the one above are open */
    private static boolean hasRoom(Vec3i cell, Cells cells) {
        return cells.isOpen(cell) && cells.isOpen(above(cell));
    }

    /** @return a single hole at this cell, or null if its floor or any wall is not blast-proof */
    private static Hole single(Vec3i cell, Cells cells) {
        Walls walls = new Walls(cells);
        walls.add(below(cell));
        for (Vec3i side : SIDES) {
            walls.add(offset(cell, side));
        }
        return walls.isSafe() ? new Hole(cell, null, walls.material()) : null;
    }

    /**
     * @return a double hole with this cell as its western or northern half, or null
     *
     * <p>The two cells share a side, which must be open; every other side of
     * both, and both floors, must be blast-proof.
     */
    private static Hole doubleFrom(Vec3i cell, Cells cells) {
        for (Vec3i direction : DOUBLE_DIRECTIONS) {
            Vec3i other = offset(cell, direction);
            if (!hasRoom(other, cells)) {
                continue;
            }
            Walls walls = new Walls(cells);
            walls.add(below(cell));
            walls.add(below(other));
            for (Vec3i side : SIDES) {
                if (!side.equals(direction)) {
                    walls.add(offset(cell, side));
                }
                if (!side.equals(negate(direction))) {
                    walls.add(offset(other, side));
                }
            }
            if (walls.isSafe()) {
                return new Hole(cell, other, walls.material());
            }
        }
        return null;
    }

    private static Vec3i offset(Vec3i cell, Vec3i by) {
        return cell.add(by.getX(), by.getY(), by.getZ());
    }

    private static Vec3i negate(Vec3i direction) {
        return Vec3i.of(-direction.getX(), -direction.getY(), -direction.getZ());
    }

    private static Vec3i above(Vec3i cell) {
        return cell.add(0, 1, 0);
    }

    private static Vec3i below(Vec3i cell) {
        return cell.add(0, -1, 0);
    }

    @Override
    protected void onDisable() {
        holes.clear();
        found = 0;
    }

    @Override
    public String getDisplayInfo() {
        return found == 0 ? "" : String.valueOf(found);
    }

    // ------------------------------------------------------------------ types

    /** What a hole is walled with. */
    public enum Material {
        BEDROCK,
        OBSIDIAN,
        MIXED
    }

    /**
     * A hole: one cell, or two for a double, and what it is walled with.
     *
     * @param second the other half of a double, or null for a single
     */
    public record Hole(Vec3i first, Vec3i second, Material material) {

        public boolean isDouble() {
            return second != null;
        }
    }

    /** The two questions a scan asks about a cell. */
    public interface Cells {

        /** The world the player is in, through the adapter. */
        Cells LIVE = new Cells() {
            @Override
            public boolean isOpen(Vec3i cell) {
                return Blocks.isOpen(cell);
            }

            @Override
            public BlastResistance blastResistance(Vec3i cell) {
                return Blocks.blastResistance(cell);
            }
        };

        /** @return whether nothing solid occupies the cell */
        boolean isOpen(Vec3i cell);

        /** @return how the cell's block stands up to explosions */
        BlastResistance blastResistance(Vec3i cell);
    }

    /** The floor and walls of a candidate hole, checked as they are added. */
    private static final class Walls {

        private final Cells cells;
        private boolean safe = true;
        private boolean anyBedrock;
        private boolean anyObsidian;

        Walls(Cells cells) {
            this.cells = cells;
        }

        void add(Vec3i block) {
            if (!safe) {
                return;
            }
            switch (cells.blastResistance(block)) {
                case UNBREAKABLE -> anyBedrock = true;
                case RESISTANT -> anyObsidian = true;
                case NONE -> safe = false;
            }
        }

        boolean isSafe() {
            return safe;
        }

        Material material() {
            if (anyBedrock && anyObsidian) {
                return Material.MIXED;
            }
            return anyBedrock ? Material.BEDROCK : Material.OBSIDIAN;
        }
    }
}
