package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A visual effect the game is about to apply, with a chance to skip it.
 *
 * <p>One event with a kind rather than nine events with nothing in them. Each of
 * these is the same fact &mdash; "the game is about to draw this, do you mind?"
 * &mdash; and carries no data beyond which one it is, so a class each would be
 * nine files of boilerplate and nine subscriptions in every no-render module.
 * Cancelling suppresses the effect; that is the whole contract.
 *
 * <pre>{@code
 * @Subscribe
 * private void onEffect(EffectRenderEvent event) {
 *     if (event.is(EffectRenderEvent.Effect.FIRE_OVERLAY) && fire.isOn()) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <p>Posted on the render thread, at most a handful of times a frame per effect.
 * Some effects are asked about from more than one place &mdash; see each
 * constant &mdash; and a handler should answer the same way every time, which it
 * will if it answers from a setting.
 */
@Getter
@RequiredArgsConstructor
public final class EffectRenderEvent extends CancellableEvent {

    private final Effect effect;

    /** @return whether this is the given effect */
    public boolean is(Effect candidate) {
        return effect == candidate;
    }

    public enum Effect {

        /** The flames across the screen while the player is burning. */
        FIRE_OVERLAY,

        /** The camera roll when the player takes damage. */
        HURT_TILT,

        /** The purple swirl while standing in a nether portal. */
        PORTAL_OVERLAY,

        /**
         * The Nausea effect: the green swirl over the HUD, and the warp applied to
         * the world. Asked once for each, per frame. The warp is shared with the
         * portal, so suppressing it also stills the wobble inside one.
         */
        NAUSEA,

        /** The status effect icons in the top right of the HUD. */
        STATUS_EFFECT_ICONS,

        /**
         * The Darkness effect: the pulsing in the lightmap and the fog that closes
         * in with it. Asked once for each, per frame.
         */
        DARKNESS,

        /**
         * Distance fog, and the fog under water, in lava and in powder snow.
         * Asked for every fog the game sets up, which is the sky and the terrain
         * separately each frame.
         */
        FOG
    }
}
