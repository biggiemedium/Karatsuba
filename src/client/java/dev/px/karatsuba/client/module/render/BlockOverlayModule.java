package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.math.Box;
import dev.px.core.math.MathUtil;
import dev.px.core.math.Vec3;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.BlockOutlineEvent;
import dev.px.karatsuba.adapter.game.entity.Targets;

/**
 * Replaces the black wireframe on the block under the crosshair.
 *
 * <p>The worked example for <b>cancelling a game event and drawing over it</b>,
 * and for why the two halves of that are separate subscriptions:
 *
 * <ul>
 *   <li>{@link BlockOutlineEvent} is cancelled to suppress the vanilla outline.
 *       That is all it is used for &mdash; it fires inside the game's own outline
 *       pass, with its matrices and vertex consumer bound, which is not a safe
 *       place to issue arbitrary drawing.</li>
 *   <li>{@link Render3DEvent} is where this draws, because that is the pass the
 *       adapter set up for exactly that.</li>
 * </ul>
 *
 * <p>The target is read from {@code Targets} rather than remembered from the
 * event, and that is deliberate: the outline event only fires when the game was
 * going to draw an outline anyway, so an overlay built on it would vanish the
 * moment someone turned the vanilla one off. Reading the crosshair each frame
 * means this works regardless.
 */
@ModuleInfo(name = "Block Overlay", description = "Highlights the block under the crosshair",
        category = "Render")
public final class BlockOverlayModule extends Module {

    /** How close to the target the smoothing has to get before it stops mattering. */
    private static final double SNAP_DISTANCE = 0.01d;

    /** Lifted off the block face, so the overlay does not fight it for depth. */
    private static final double SURFACE_OFFSET = 0.002d;

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.BOTH);

    private final ColorSetting outlineColor = color("Outline", Color.of(255, 255, 255, 200));

    private final ColorSetting fillColor = color("Fill", Color.of(255, 255, 255, 40))
            .visibleWhen(() -> mode.get() != Mode.OUTLINE);

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(() -> mode.get() != Mode.FILL);

    private final BooleanSetting hideVanilla = bool("Hide Vanilla", true)
            .describe("Suppress the game's own black wireframe");

    private final NumberSetting<Float> smoothing = number("Smoothing", 0.4f, 0f, 0.9f)
            .step(0.05f)
            .describe("How much the box slides between blocks; 0 snaps");

    /**
     * The box being drawn, which trails the real target while smoothing is on.
     *
     * <p>Null when nothing is targeted, which is also what resets the smoothing:
     * sliding in from wherever the last block was would look like the overlay
     * flying across the world every time the crosshair leaves a wall.
     */
    private Box drawn;

    /**
     * Suppresses the vanilla wireframe.
     *
     * <p>Subscribed whether or not anything is drawn this frame, because the
     * decision is "should the game draw its outline", and the answer while this
     * module is on is no.
     */
    @Subscribe
    private void onBlockOutline(BlockOutlineEvent event) {
        if (hideVanilla.isOn()) {
            event.cancel();
        }
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        Targets.Block target = Targets.block();
        if (target == null) {
            drawn = null;
            return;
        }

        drawn = approach(drawn, target.getShape(), smoothing.getFloat());
        Box box = drawn.expand(SURFACE_OFFSET);

        switch (mode.get()) {
            case OUTLINE -> Render.boxOutline(box, thickness.getFloat(), outlineColor.resolve());
            case FILL -> Render.box(box, fillColor.resolve());
            case BOTH -> Render.boxFilledOutline(box, fillColor.resolve(), outlineColor.resolve(),
                    thickness.getFloat());
        }
    }

    /**
     * Eases one box towards another, corner by corner.
     *
     * <p>Per corner rather than per centre, so an overlay moving from a slab to a
     * full block grows into it instead of jumping size at the end of the slide.
     *
     * @param from   where the overlay is now, or null to start at the target
     * @param to     where it should be
     * @param factor 0 snaps, approaching 1 trails further behind
     */
    private static Box approach(Box from, Box to, float factor) {
        if (from == null || factor <= 0f) {
            return to;
        }
        // Close enough that another frame of easing would move it by less than a
        // pixel. Snapping here is what stops the lerp creeping forever.
        if (from.getMin().distanceTo(to.getMin()) < SNAP_DISTANCE
                && from.getMax().distanceTo(to.getMax()) < SNAP_DISTANCE) {
            return to;
        }
        double step = 1d - factor;
        return Box.of(approach(from.getMin(), to.getMin(), step),
                approach(from.getMax(), to.getMax(), step));
    }

    private static Vec3 approach(Vec3 from, Vec3 to, double step) {
        return Vec3.of(
                MathUtil.lerp(from.getX(), to.getX(), step),
                MathUtil.lerp(from.getY(), to.getY(), step),
                MathUtil.lerp(from.getZ(), to.getZ(), step));
    }

    @Override
    protected void onDisable() {
        drawn = null;
    }

    @Override
    public String getDisplayInfo() {
        return mode.displayValue();
    }

    public enum Mode {
        OUTLINE,
        FILL,
        BOTH
    }
}
