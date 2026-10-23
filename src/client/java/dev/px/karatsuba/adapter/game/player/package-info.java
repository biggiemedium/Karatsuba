/**
 * The local player: where they are, what they are holding down, where they are
 * looking, and what they do.
 *
 * <p>Four seams, all about the one entity that is different from every other:
 * the client owns it, can write to it, and is the only one that knows what its
 * input keys are doing.
 *
 * <ul>
 *   <li>{@code Players} &mdash; position, rotation, velocity, state flags.</li>
 *   <li>{@code Inputs} &mdash; the movement controls, read and forced, with the
 *       adapter remembering what it forced so a teardown is one call.</li>
 *   <li>{@code Rotations} &mdash; Core's {@code RotationSink}, plus the
 *       swap-and-restore that makes a silent rotation reach the server without
 *       moving the camera.</li>
 *   <li>{@code Interactions} &mdash; attacking and using.</li>
 *   <li>{@code Digging} &mdash; the game's own block breaking, pointed at a
 *       block other than the crosshair's.</li>
 *   <li>{@code Effects} &mdash; the status effects on the player, and their icons.</li>
 * </ul>
 *
 * <p>Everything here reads and writes live game state, so it is <b>game thread
 * only</b>. None of it arbitrates: two modules that both call {@code Inputs.hold}
 * both get it. Deciding which should win is
 * {@code dev.px.karatsuba.client.service}'s job.
 */
package dev.px.karatsuba.adapter.game.player;
