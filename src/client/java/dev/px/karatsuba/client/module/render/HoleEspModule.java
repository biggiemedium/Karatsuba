package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.PresenceTracker;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks the one-block gaps a player can stand in and not be knocked out of.
 *
 * <p><b>The module in this batch that arbitrates nothing</b>, and it is here to
 * show where the line falls. It writes no game state at all: it reads the world
 * and it draws. Two of these running at once would both draw, which is not a
 * conflict &mdash; it is two outlines. Rendering composes, so there is nothing
 * for a service to own.
 *
 * <p>That is the general shape. Arbiters exist for <em>contended writes</em>:
 * the head, the hotbar, the velocity, the swing. Reads and draws never contend,
 * which is why {@code adapter.game} and {@code adapter.world} are stateless
 * statics and why adding a visual module needs no new service.
 *
 * <h2>Scanned on the tick, drawn every frame</h2>
 * The scan is a few thousand block lookups. At twenty a second that is nothing;
 * at three hundred frames a second it is the module's whole cost and the answer
 * has not changed between frames anyway. So the tick finds the holes and the
 * frame draws what the tick found, which is the same split the HUD uses.
 */
@ModuleInfo(name = "Hole ESP", description = "Highlights one-block holes you can stand in",
        category = "Render")
public final class HoleEspModule extends Module {

    /** The four directions a hole has to be walled on. */
    private static final Vec3i[] SIDES = {
            Vec3i.of(1, 0, 0), Vec3i.of(-1, 0, 0),
            Vec3i.of(0, 0, 1), Vec3i.of(0, 0, -1)
    };

    /** A drawn hole is inset this much from each wall, so its faces do not z-fight the blocks. */
    private static final double INSET = 0.002d;
    private static final double FULL_SIZE = 1d - INSET * 2d;

    private final NumberSetting<Integer> radius = integer("Radius", 8, 2, 24)
            .describe("Blocks out from the player, horizontally");

    private final NumberSetting<Integer> depth = integer("Depth", 3, 1, 8)
            .describe("Blocks up and down from the player's feet");

    private final BooleanSetting fill = bool("Fill", true);

    private final ColorSetting fillColor = color("Fill Colour", Color.of(80, 220, 140, 45))
            .visibleWhen(fill);

    private final ColorSetting outlineColor = color("Outline", Color.of(80, 220, 140, 200));

    private final NumberSetting<Float> thickness = number("Thickness", 1.2f, 0.5f, 4f).step(0.1f);

    private final BooleanSetting throughWalls = bool("Through Walls", true)
            .describe("Show holes behind blocks too, not only the ones in plain sight");

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop holes in as they are found and fade them out when they stop being holes");

    /**
     * The holes on screen, appearing, shown or fading.
     *
     * <p>Keyed and valued by cell: the box is built from the cell at draw time,
     * because its size changes as it animates.
     */
    private final PresenceTracker<Vec3i, Vec3i> holes = PresenceTracker.popIn();

    /** How many holes the last scan found, for the ArrayList. Ones fading out are not counted. */
    private int found;

    /**
     * Rescans, and tells the tracker what was found.
     *
     * <p>A cell found this scan pops in if it is new; a cell no longer found
     * &mdash; filled in, or now out of range &mdash; fades out where it was.
     */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            holes.clear();
            found = 0;
            return;
        }
        holes.setAnimated(animations.isOn());
        List<Vec3i> cells = scan();
        holes.begin();
        for (Vec3i cell : cells) {
            holes.report(cell, true, cell);
        }
        holes.end();
        found = cells.size();
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (holes.size() == 0) {
            return;
        }
        Render.setDepthTest(!throughWalls.isOn());
        try {
            for (PresenceTracker.Entry<Vec3i> hole : holes.entries()) {
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
     * <p>Grows up out of the floor rather than from its middle, so it reads as the
     * hole filling with colour; the overshoot of the pop-in curve makes it rise a
     * touch past the top and settle.
     */
    private void draw(Vec3i cell, float presence, float alpha) {
        double size = FULL_SIZE * Math.max(0f, presence);
        if (size <= 0d || alpha <= 0f) {
            return;
        }
        Box box = Box.around(Vec3.of(cell.getX() + 0.5d, cell.getY() + INSET, cell.getZ() + 0.5d), size, size);
        if (fill.isOn()) {
            Render.box(box, fillColor.resolve().fade(alpha));
        }
        Render.boxOutline(box, thickness.getFloat(), outlineColor.resolve().fade(alpha));
    }

    /**
     * Walks the box around the player looking for holes.
     *
     * <p>A hole is a cell the player fits in, with a floor under it and solid
     * blocks on all four sides. The cheap tests come first: most cells fail on
     * "is there room here" and never reach the four neighbour lookups.
     */
    private List<Vec3i> scan() {
        Vec3i feet = Players.blockPosition();
        int reach = radius.getInt();
        int down = depth.getInt();

        List<Vec3i> found = new ArrayList<>();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -down; dy <= down; dy++) {
                    Vec3i cell = feet.add(dx, dy, dz);
                    if (isHole(cell)) {
                        found.add(cell);
                    }
                }
            }
        }
        return found;
    }

    /**
     * @return whether a player standing in this cell would be walled in
     *
     * <p>Two cells of headroom, a floor, and four solid sides at foot level. The
     * sides are only checked at the lower cell: a hole that is open at head
     * height is still a hole, and is the common kind.
     */
    private boolean isHole(Vec3i cell) {
        if (!Blocks.isOpen(cell) || !Blocks.isOpen(cell.add(0, 1, 0))) {
            return false;
        }
        if (!Blocks.isStandable(cell.add(0, -1, 0))) {
            return false;
        }
        for (Vec3i side : SIDES) {
            if (!Blocks.isSolid(cell.add(side.getX(), side.getY(), side.getZ()))) {
                return false;
            }
        }
        return true;
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
}
