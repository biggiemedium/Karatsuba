package dev.px.karatsuba.adapter.event.game.combat;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An end crystal has just appeared in the client world: placed by anyone, or
 * come into view.
 *
 * <p>Posted on the game thread the moment its spawn is handled, before the next
 * tick, and after the crystal is already in the combat kit's crystal tracker. A
 * crystal aura breaks on spawn by handing the crystal straight to its search:
 *
 * <pre>{@code
 * @Subscribe
 * private void onCrystal(CrystalSpawnEvent event) {
 *     BreakOption<?> now = search.spawned(event.getCrystal());
 *     if (now != null) { ... }
 * }
 * }</pre>
 *
 * <p>{@link #getCrystal()} is the game's entity, as an opaque handle: pass it to
 * the combat library, which is written to take it, and read everything else from
 * the fields here or the {@code Tracked} the search hands back.
 */
@Getter
@RequiredArgsConstructor
public final class CrystalSpawnEvent extends Event {

    /** The crystal entity, untyped so a module never names the game's class. */
    private final Object crystal;

    /** Its entity id, for attacking it through {@code Services.actions()}. */
    private final int id;

    /** Where it stands, which is also where it explodes from. */
    private final Vec3 position;
}
