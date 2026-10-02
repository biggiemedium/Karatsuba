package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The black wireframe the game draws around the block under the crosshair.
 *
 * <p>Posted just before it is drawn, and cancelling suppresses it. A module that
 * wants to draw its own replaces the vanilla outline by cancelling this and
 * drawing from {@code Render3DEvent}, which is where a world-space draw belongs
 * &mdash; this event fires inside the game's own outline pass, with its matrices
 * and its vertex consumer bound, and is not a safe place to issue arbitrary
 * drawing.
 *
 * <pre>{@code
 * @Subscribe
 * private void onOutline(BlockOutlineEvent event) {
 *     event.cancel();
 *     target = event.getShape();     // remembered for the render pass
 * }
 *
 * @Subscribe
 * private void onRender3D(Render3DEvent event) {
 *     if (target != null) {
 *         Render.boxOutline(target, 1.5f, colour);
 *     }
 * }
 * }</pre>
 *
 * <p>Only fires when the game would have drawn an outline at all: looking at a
 * block, not in spectator, and with the setting on. A module wanting the target
 * regardless asks {@code adapter.game.Targets} instead.
 */
@Getter
@RequiredArgsConstructor
public final class BlockOutlineEvent extends CancellableEvent {

    /** The block's position on the grid. */
    private final Vec3i position;

    /**
     * The outline in world space, already offset onto {@link #getPosition()}.
     *
     * <p>The block's real shape rather than a unit cube: a slab is half height, a
     * fence is a post, and an outline drawn to the cube would sit around thin air.
     * Taken from the game's own outline shape, so it is exactly what the vanilla
     * wireframe would have traced.
     */
    private final Box shape;
}
