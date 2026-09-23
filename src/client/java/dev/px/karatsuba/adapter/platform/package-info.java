/**
 * Core's two simplest seams, filled in.
 *
 * <p>{@link dev.px.karatsuba.adapter.platform.MinecraftPlatform} is
 * {@code Platform}: thirteen questions about where to write config, how big the
 * screen is, where the cursor is and how to say something to the player.
 * {@link dev.px.karatsuba.adapter.platform.MinecraftLogger} is
 * {@code CoreLogger}, routed into the game's log rather than {@code System.out}.
 *
 * <p>They sit together because they are the two things Core needs before it can
 * do anything at all &mdash; both are handed to {@code Core.builder(...)} rather
 * than installed afterwards &mdash; and because keeping them out of the adapter
 * root leaves {@code MinecraftAdapter} there on its own, which is what it is:
 * the composition root and nothing else.
 */
package dev.px.karatsuba.adapter.platform;
