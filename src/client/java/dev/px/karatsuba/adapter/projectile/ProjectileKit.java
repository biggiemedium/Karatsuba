package dev.px.karatsuba.adapter.projectile;

import dev.px.core.entity.EntityTracker;
import dev.px.core.entity.Tracked;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.prediction.Lookahead;
import dev.px.core.world.BlockView;
import dev.px.projectile.Shooter;
import dev.px.projectile.aim.AimSolver;
import dev.px.projectile.flight.Flight;
import dev.px.projectile.flight.Trajectory;

/**
 * The projectile library, wired to this game: every fact its README asks a client
 * to supply, read from 1.21.4's own code.
 *
 * <p>Unlike the combat kit this needs no type variable. The library is typed by
 * nothing of the game's: flights take {@code Tracked<?>}, the aim solver a
 * {@code Lookahead<Object>}. So a module holds the kit as it is:
 *
 * <pre>{@code
 * ProjectileKit kit = Projectiles.kit();
 *
 * Shot shot = kit.held();                                  // what using your hand would launch; null if nothing
 * if (shot != null) {
 *     Trajectory path = shot.trajectory(kit.you(), Players.rotation());
 *     // draw path.getPoints(), path.getHit(), path.landingSpread()
 * }
 *
 * for (Tracked<?> pearl : kit.projectiles().all()) {
 *     Trajectory theirs = kit.follow(pearl);               // where something already flying lands
 * }
 *
 * AimSolver bowAim = kit.aim(shot).arcs(Arc.LOW, Arc.HIGH).build();
 * Aim aim = bowAim.at(kit.you(), target);                  // a rotation; turning to it is the module's
 * }</pre>
 *
 * <h2>What is wired, by README section</h2>
 * <table>
 *   <caption>The projectile README, and where each part lives</caption>
 *   <tr><th>Section</th><th>Here</th></tr>
 *   <tr><td>1. Your projectiles</td><td>{@link ProjectileType}: gravity, drag, step order and entity
 *       margin per kind</td></tr>
 *   <tr><td>2. Launching</td><td>{@link Shot}: power, pitch offset, spread, origin and shooter velocity
 *       per item; {@link #held()} picks the one in your hand; {@link #you()}</td></tr>
 *   <tr><td>3. Where it goes</td><td>{@link #blocks()}, {@link #flight}, and what can be hit</td></tr>
 *   <tr><td>4. Someone else's</td><td>{@link #projectiles()}, {@link #follow}, {@link #velocityOf}</td></tr>
 *   <tr><td>5. Aiming</td><td>{@link #aim}, {@link #lookahead()}</td></tr>
 * </table>
 *
 * <p><b>Game thread only</b>, like everything a flight reads.
 */
public interface ProjectileKit {

    // ---------------------------------------------------------------- world

    /** Collision shapes, whole, as a projectile's raycast meets them. See {@link ProjectileBlocks}. */
    BlockView blocks();

    /** Projectiles in the air that a flight can follow: arrows, tridents, thrown items, llama spit. */
    EntityTracker<?> projectiles();

    // ------------------------------------------------------------------ you

    /** You, as a launch sees you: your eyes, your velocity as the game hands it over, and the ground. */
    Shooter you();

    /**
     * You at this frame: your eyes between ticks, as the camera has them. For a
     * preview drawn every frame, so it sits still on screen while you move; the
     * real launch leaves from the tick's position, a fraction of a block away.
     */
    Shooter you(float partialTicks);

    /** Where you are looking at this frame, between ticks. */
    Vec2 view(float partialTicks);

    // ---------------------------------------------------------------- shots

    /**
     * What using the item in your hand would launch now, and how: the item being
     * used first (a drawn bow or trident), then the main hand, then the off hand,
     * as the game tries them. Null when neither hand launches anything.
     */
    Shot held();

    /**
     * Where the item {@link #held()} launches sits in first person at this frame:
     * in front of you, on the side of the hand holding it (your main arm, or the
     * other for the off hand). For drawing the held preview out of the hand; the
     * projectile itself leaves from your eyes. Null when nothing is held.
     */
    Vec3 hand(float partialTicks);

    /** The shot this kind of item launches, whether or not it is held; see {@link Shot.Item}. */
    Shot shot(Shot.Item item);

    // -------------------------------------------------------------- flights

    /**
     * The flight for {@code type}: its rules, {@link #blocks()}, and everything a
     * projectile can hit, you included. One per type, shared.
     */
    Flight flight(ProjectileType type);

    /** @return the kind of projectile a tracked entity is, or null for one this game cannot fly */
    ProjectileType typeOf(Tracked<?> projectile);

    /** @return the game's own velocity for a tracked projectile, in blocks a tick; null for anything else */
    Vec3 velocityOf(Tracked<?> projectile);

    /**
     * @return whether you launched it. The client learns a projectile's owner from
     *         its spawn packet, so one thrown by someone out of sight has none, and
     *         counts as not yours
     */
    boolean isYours(Tracked<?> projectile);

    /**
     * Where a projectile already in the air goes, from its own velocity. Null for
     * something {@link #typeOf} does not know.
     */
    Trajectory follow(Tracked<?> projectile);

    // --------------------------------------------------------------- aiming

    /** Where a target will be: Core's prediction while it is reliable, where they are otherwise. */
    Lookahead<Object> lookahead();

    /**
     * An aim solver for {@code shot}, with its flight, launch rules and
     * {@link #lookahead()} given. Still yours: aim point, arcs, delay.
     */
    AimSolver.Builder aim(Shot shot);
}
