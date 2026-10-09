package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.Event;

/**
 * The game has just drawn the held items, and the hand projection is still set.
 *
 * <p>The one moment {@code Silhouettes.hands()} works: it draws the items again,
 * the same way, into a mask a shader can outline. Anything composited here lands
 * over the hands and under the HUD. Not posted when the hands are not drawn (F1,
 * third person, spectating).
 */
public final class HandRenderEvent extends Event {
}
