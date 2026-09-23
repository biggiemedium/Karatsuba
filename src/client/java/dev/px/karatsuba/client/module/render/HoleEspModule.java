package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
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

    private final NumberSetting<Integer> radius = integer("Radius", 8, 2, 24)
            .describe("Blocks out from the player, horizontally");

    private final NumberSetting<Integer> depth = integer("Depth", 3, 1, 8)
            .describe("Blocks up and down from the player's feet");

    private final BooleanSetting fill = bool("Fill", true);

    private final ColorSetting fillColor = color("Fill Colour", Color.of(80, 220, 140, 45))
            .visibleWhen(fill);

    private final ColorSetting outlineColor = color("Outline", Color.of(80, 220, 140, 200));

    private final NumberSetting<Float> thickness = number("Thickness", 1.2f, 0.5f, 4f).step(0.1f);

    /**
     * What the last scan found.
     *
     * <p>Replaced wholesale rather than mutated, so the render pass never sees a
     * half-built list. It is only ever read on the same thread that writes it, so
     * this is about readability rather than safety.
     */
    private List<Box> holes = List.of();

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            holes = List.of();
            return;
        }
        holes = scan();
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        for (Box hole : holes) {
            if (fill.isOn()) {
                Render.box(hole, fillColor.resolve());
            }
            Render.boxOutline(hole, thickness.getFloat(), outlineColor.resolve());
        }
    }

    /**
     * Walks the box around the player looking for holes.
     *
     * <p>A hole is a cell the player fits in, with a floor under it and solid
     * blocks on all four sides. The cheap tests come first: most cells fail on
     * "is there room here" and never reach the four neighbour lookups.
     */
    private List<Box> scan() {
        Vec3i feet = Players.blockPosition();
        int reach = radius.getInt();
        int down = depth.getInt();

        List<Box> found = new ArrayList<>();

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -down; dy <= down; dy++) {
                    Vec3i cell = feet.add(dx, dy, dz);
                    if (isHole(cell)) {
                        found.add(boxOf(cell));
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

    /** The cell as a world-space box, slightly inset so it does not z-fight the walls. */
    private static Box boxOf(Vec3i cell) {
        return Box.of(cell.getX(), cell.getY(), cell.getZ(),
                cell.getX() + 1d, cell.getY() + 1d, cell.getZ() + 1d).expand(-0.002d);
    }

    @Override
    protected void onDisable() {
        holes = List.of();
    }

    @Override
    public String getDisplayInfo() {
        return holes.isEmpty() ? "" : String.valueOf(holes.size());
    }
}
