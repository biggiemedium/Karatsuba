package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.CancellableEvent;

/**
 * The game is about to draw the overworld sky.
 *
 * <p>Cancel it to replace the sky: the dome, the sunrise glow, the sun, moon and
 * stars and the dark lower half are then all skipped this frame, and whatever the
 * handler draws (with {@code ShaderPasses.sky}) is the sky. Clouds, rain and the
 * world itself are drawn on top as usual. The camera's rotation and the world
 * projection are set, which is what {@code ShaderPasses.sky} reads.
 *
 * <p>Only posted for skies of the overworld kind; the End's and the Nether's
 * never are.
 */
public final class SkyRenderEvent extends CancellableEvent {
}
