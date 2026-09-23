package dev.px.karatsuba.client.service;

import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec3;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.event.game.PlayerMoveEvent;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.claim.Claims;
import lombok.Getter;

/**
 * Who gets to change the player's motion this tick.
 *
 * <p>Two modules that both assign velocity is the movement equivalent of two
 * modules that both assign the rotation, with the same failure: the winner is
 * whichever handler happened to run last, and neither module can see the other.
 *
 * <h2>Arbitrated per axis group, not per vector</h2>
 * Rotation has one winner because there is one head. Motion does not: a speed
 * module owns the horizontal plane and a glide owns the vertical, and running
 * both at once is the normal case rather than a conflict. Giving the whole
 * vector to one winner would make them mutually exclusive for no reason.
 *
 * <p>So there are two independent contests, {@link Axis#HORIZONTAL} and
 * {@link Axis#VERTICAL}. Two speed modules argue with each other and the higher
 * priority wins; a speed module and a glide never argue at all.
 *
 * <pre>{@code
 * @Subscribe
 * private void onTick(TickEvent event) {
 *     Services.motion().scaleHorizontal(this, 1.3d, ClaimPriority.NORMAL);
 * }
 * }</pre>
 *
 * <h2>Why a claim is an intent rather than a number</h2>
 * A module asking to go 30% faster does not know what "30% faster" is until the
 * velocity it is scaling exists, and that is not true when the module files
 * &mdash; the game has not run its movement code yet. So a claim says
 * <em>scale by 1.3</em> and the arithmetic happens at the apply point. Filing a
 * pre-multiplied number would scale last tick's velocity.
 *
 * <h2>Where it applies</h2>
 * At {@link PlayerMoveEvent} {@link Stage#PRE}, at {@link Priority#LOWEST} so
 * every module has filed first. That is before the game turns held keys into
 * acceleration and before it moves, so a scaled velocity still gets the game's
 * own friction and gravity applied on top &mdash; which is what makes the result
 * look like movement rather than a teleport.
 */
public final class MotionService implements Service {

    /**
     * The two independent contests.
     *
     * <p>Horizontal is X and Z together, because a module that wanted only one of
     * them would be asking to move diagonally for reasons no movement feature has
     * ever had.
     */
    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    private final EventBus bus;

    private final Claims<Motion> horizontal = new Claims<>();
    private final Claims<Motion> vertical = new Claims<>();

    private Subscription tickSubscription;
    private Subscription moveSubscription;
    private Subscription worldSubscription;

    /** What the last apply actually produced, for a HUD readout or a debug command. */
    @Getter
    private Vec3 lastApplied = Vec3.ZERO;

    public MotionService(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public String getName() {
        return "Motion";
    }

    @Override
    public void start() {
        tickSubscription = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                horizontal.beginTick();
                vertical.beginTick();
            }
        });
        // LOWEST: every module subscribing at the default priority has filed by
        // the time this runs, so nothing is ever a tick late.
        moveSubscription = bus.on(PlayerMoveEvent.class, Priority.LOWEST, this::apply);
        worldSubscription = bus.on(WorldEvent.class, event -> clear());
    }

    @Override
    public void stop() {
        closeAll();
        clear();
    }

    // ------------------------------------------------------------- requests

    /** Multiplies the horizontal velocity. The shape a speed module wants. */
    public void scaleHorizontal(Object owner, double factor, int priority) {
        horizontal.file(owner, Motion.scale(factor, factor), priority, 1);
    }

    /** Replaces the horizontal velocity outright, in blocks per tick. */
    public void setHorizontal(Object owner, double x, double z, int priority) {
        horizontal.file(owner, Motion.set(x, z), priority, 1);
    }

    /** Multiplies the vertical velocity. Negative factors invert a fall, which is rarely meant. */
    public void scaleVertical(Object owner, double factor, int priority) {
        vertical.file(owner, Motion.scale(factor, factor), priority, 1);
    }

    /**
     * Replaces the vertical velocity, in blocks per tick.
     *
     * <p>Zero hovers, a small negative glides, and a positive value launches. The
     * game still applies gravity and drag afterwards, so holding a height means
     * asking every tick rather than asking once.
     */
    public void setVertical(Object owner, double y, int priority) {
        vertical.file(owner, Motion.set(y, y), priority, 1);
    }

    /** Lets go of both axes early. Never required &mdash; claims expire on their own. */
    public boolean release(Object owner) {
        boolean had = horizontal.release(owner);
        return vertical.release(owner) || had;
    }

    public boolean release(Object owner, Axis axis) {
        return claimsFor(axis).release(owner);
    }

    // -------------------------------------------------------------- reading

    public boolean isActive() {
        return !horizontal.isEmpty() || !vertical.isEmpty();
    }

    public boolean isHeldBy(Object owner, Axis axis) {
        return claimsFor(axis).isHeldBy(owner);
    }

    /** @return a name for whoever holds this axis, or null */
    public String getHolderName(Axis axis) {
        Claims.Claim<Motion> winner = claimsFor(axis).best();
        return winner == null ? null : winner.getOwnerName();
    }

    // ------------------------------------------------------------ internals

    /**
     * Folds both winners into the tick's velocity.
     *
     * <p>Only writes when something actually claimed, so a client with no
     * movement modules running costs one event dispatch and no assignment.
     */
    private void apply(PlayerMoveEvent event) {
        if (event.getStage() != Stage.PRE) {
            return;
        }
        Claims.Claim<Motion> h = horizontal.best();
        Claims.Claim<Motion> v = vertical.best();
        if (h == null && v == null) {
            return;
        }

        Vec3 velocity = event.getVelocity();
        double x = velocity.getX();
        double y = velocity.getY();
        double z = velocity.getZ();

        if (h != null) {
            Motion motion = h.getValue();
            x = motion.applyFirst(x);
            z = motion.applySecond(z);
        }
        if (v != null) {
            y = v.getValue().applyFirst(y);
        }

        lastApplied = Vec3.of(x, y, z);
        event.setVelocity(lastApplied);
    }

    private Claims<Motion> claimsFor(Axis axis) {
        return axis == Axis.HORIZONTAL ? horizontal : vertical;
    }

    private void clear() {
        horizontal.clear();
        vertical.clear();
        lastApplied = Vec3.ZERO;
    }

    private void closeAll() {
        if (tickSubscription != null) {
            tickSubscription.close();
            tickSubscription = null;
        }
        if (moveSubscription != null) {
            moveSubscription.close();
            moveSubscription = null;
        }
        if (worldSubscription != null) {
            worldSubscription.close();
            worldSubscription = null;
        }
    }

    /**
     * What a claim wants done, evaluated when the velocity exists.
     *
     * <p>Two components rather than three: the horizontal contest uses both for X
     * and Z, and the vertical one uses only the first. A type per axis would be
     * two classes that differ by a field name.
     */
    @Getter
    public static final class Motion {

        private final boolean scaling;
        private final double first;
        private final double second;

        private Motion(boolean scaling, double first, double second) {
            this.scaling = scaling;
            this.first = first;
            this.second = second;
        }

        static Motion set(double first, double second) {
            return new Motion(false, first, second);
        }

        static Motion scale(double first, double second) {
            return new Motion(true, first, second);
        }

        double applyFirst(double current) {
            return scaling ? current * first : first;
        }

        double applySecond(double current) {
            return scaling ? current * second : second;
        }

        @Override
        public String toString() {
            return scaling
                    ? String.format("scale(%.3f, %.3f)", first, second)
                    : String.format("set(%.3f, %.3f)", first, second);
        }
    }
}
