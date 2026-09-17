package dev.px.karatsuba.adapter.event.game;

import dev.px.core.event.StagedEvent;
import dev.px.core.event.Stage;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;

/**
 * The player's own tick, before and after the game runs it.
 *
 * <p>Core ships the events every version has &mdash; tick, render, input, chat,
 * world, screen &mdash; and stops there. Anything that names a game concept lives
 * in the adapter, because the whole reason Core compiles without Minecraft is that
 * no such event is declared in it. This is the first of those, and the shape every
 * other one should copy.
 *
 * <p><b>Why this and not {@code TickEvent}.</b> {@code TickEvent} is the client
 * tick: it fires in a menu, with no player, and it fires around everything the
 * game does that tick. This fires around the player's own update, which is where a
 * module that wants to read or influence movement has to be if it is to see
 * consistent state &mdash; position, rotation and the on-ground flag as the game is
 * about to use them, rather than whatever they happened to be at the top of the
 * frame.
 *
 * <p><b>Staged</b>, because the same call site is interesting from both sides:
 * {@link Stage#PRE} is where a handler acts before the game does, and
 * {@link Stage#POST} is where one reads the result. A handler that wants only one
 * says so on the annotation and never sees the other:
 *
 * <pre>{@code
 * @Subscribe(stage = Stage.PRE)
 * private void onUpdate(PlayerUpdateEvent event) {
 *     if (event.getPosition().getY() < 0d) {
 *         event.cancel();          // the game's own update is skipped
 *     }
 * }
 * }</pre>
 *
 * <p>Cancelling PRE skips the game's update entirely, so POST is not posted either.
 * That is a blunt instrument and deliberately so &mdash; it is the one thing a
 * mixin at HEAD can offer, and pretending otherwise would be a lie about what the
 * injection point can do.
 *
 * <p>The fields are a snapshot in Core's types, taken when the event is posted. A
 * handler that needs live state asks
 * {@link dev.px.karatsuba.adapter.game.Players}; what is carried here is what was
 * true at the moment the stage began, which is what makes a PRE and a POST worth
 * comparing.
 */
public final class PlayerUpdateEvent extends StagedEvent {

    /** Feet position as the stage began. */
    private final Vec3 position;

    /** Yaw and pitch as the stage began. */
    private final Vec2 rotation;

    private final boolean onGround;

    public PlayerUpdateEvent(Stage stage, Vec3 position, Vec2 rotation, boolean onGround) {
        super(stage);
        this.position = position;
        this.rotation = rotation;
        this.onGround = onGround;
    }

    // Written out rather than generated: Lombok is Core's compile-time dependency,
    // not the mod's, and the adapter has no business borrowing it.

    public Vec3 getPosition() {
        return position;
    }

    public Vec2 getRotation() {
        return rotation;
    }

    public boolean isOnGround() {
        return onGround;
    }
}
