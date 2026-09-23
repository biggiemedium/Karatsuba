package dev.px.karatsuba.adapter.event.game;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The player is about to hit an entity.
 *
 * <p>Posted before the swing reaches the server, so cancelling stops the attack
 * happening at all. The target is described rather than handed over: an id, a
 * name and a position are what a module needs to decide, and none of them pins it
 * to a game version.
 *
 * <pre>{@code
 * @Subscribe
 * private void onAttack(AttackEvent event) {
 *     if (event.isPlayer() && Core.social().isFriend(event.getTargetName())) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <p>Posted on the game thread. For the richer picture &mdash; health, hitbox,
 * friend status &mdash; look the entity up through {@code adapter.game.Entities}
 * by its id.
 */
@Getter
@RequiredArgsConstructor
public final class AttackEvent extends CancellableEvent {

    /** The target's entity id, for looking it up again this tick. */
    private final int targetId;

    /** The target's display name. */
    private final String targetName;

    /** Where the target is, interpolated for this frame. */
    private final Vec3 targetPosition;

    private final boolean player;
}
