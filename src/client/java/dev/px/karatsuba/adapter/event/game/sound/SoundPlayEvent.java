package dev.px.karatsuba.adapter.event.game.sound;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A sound about to be played, before it reaches the mixer.
 *
 * <p>Cancelling silences it. That is all this event is for, and it covers every
 * sound the client plays &mdash; a mob, a block, a UI click &mdash; because it is
 * posted at the single point they all pass through.
 *
 * <p>The sound is identified by its registry id as a string, for example
 * {@code minecraft:entity.wither.spawn}. A string rather than the game's
 * {@code Identifier} for the usual reason, and it happens to be the more useful
 * shape anyway: matching is a {@code contains} or an {@code equals} and needs no
 * registry lookup.
 *
 * <pre>{@code
 * @Subscribe
 * private void onSound(SoundPlayEvent event) {
 *     if (event.idContains("entity.wither.spawn")) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <p>Posted on the game thread, from the render/tick path that queues the sound.
 *
 * <h2>Why there is no volume or pitch</h2>
 * Both look like obvious fields for this event and neither can be read here. A
 * sound instance carries its own multipliers, but the actual numbers come from
 * the resolved {@code Sound} behind it &mdash; and that is not attached until
 * the sound system looks it up, which happens <em>after</em> the point this
 * event is posted from. Reading them at that point is a
 * {@code NullPointerException}.
 *
 * <p>Moving the event later would make them available and make cancelling
 * meaningless, since by then the sound has been resolved and queued. Deciding
 * whether a sound should play needs to happen before any of that, and what is
 * knowable before any of that is what this event carries.
 */
@Getter
@RequiredArgsConstructor
public final class SoundPlayEvent extends CancellableEvent {

    /** Full registry id, for example {@code minecraft:block.tnt.explode}. */
    private final String id;

    /** Which volume slider governs it: {@code MASTER}, {@code HOSTILE}, and so on. */
    private final String category;

    /** Where in the world it is coming from. */
    private final Vec3 position;

    /**
     * @return whether the id contains this fragment
     *
     * <p>The match almost every caller wants, because an id is namespaced and the
     * namespace is never the interesting half: {@code entity.wither.spawn} rather
     * than {@code minecraft:entity.wither.spawn}.
     */
    public boolean idContains(String fragment) {
        return id.contains(fragment);
    }
}
