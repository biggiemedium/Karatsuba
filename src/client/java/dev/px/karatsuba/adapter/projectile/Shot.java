package dev.px.karatsuba.adapter.projectile;

import dev.px.core.math.Vec2;
import dev.px.projectile.LaunchRules;
import dev.px.projectile.Shooter;
import dev.px.projectile.flight.Flight;
import dev.px.projectile.flight.Trajectory;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.function.BooleanSupplier;

/**
 * Something you can launch: which item, the projectile it makes, how it leaves
 * you, and the flight it takes. Built by {@link ProjectileKit}, one per item,
 * reused.
 *
 * <pre>{@code
 * Trajectory path = shot.trajectory(kit.you(), Players.rotation());
 * }</pre>
 */
@Getter
public final class Shot {

    /**
     * Every item that launches a projectile the library can fly, with its launch
     * read from 1.21.4's {@code use} / {@code onStoppedUsing}. Spread is always
     * {@code 0.0172275} times a divergence of 1, added before the speed scales it.
     */
    public enum Item {
        /** Power 0 to 3 with the draw ({@code BowItem.getPullProgress} &times; 3); fires from 0.1 of a draw. */
        BOW,
        /** Power 3.15, and the only launch that keeps none of your motion ({@code CrossbowItem.shoot}). */
        CROSSBOW,
        /** Power 2.5, once held 10 ticks; never thrown while riptide would launch you instead. */
        TRIDENT,
        /** Power 1.5. */
        SNOWBALL,
        /** Power 1.5. */
        EGG,
        /** Power 1.5. */
        ENDER_PEARL,
        /** Power 0.5, pitched up 20 degrees. */
        SPLASH_POTION,
        /** Power 0.5, pitched up 20 degrees. */
        LINGERING_POTION,
        /** Power 0.7, pitched up 20 degrees. */
        EXPERIENCE_BOTTLE
    }

    private final Item item;
    private final ProjectileType type;
    private final LaunchRules launch;
    private final Flight flight;

    /** False while it would not fire yet: a bow drawn under 0.1, a trident held under 10 ticks. */
    @Getter(AccessLevel.NONE)
    private final BooleanSupplier ready;

    Shot(Item item, ProjectileType type, LaunchRules launch, Flight flight, BooleanSupplier ready) {
        this.item = item;
        this.type = type;
        this.launch = launch;
        this.flight = flight;
        this.ready = ready;
    }

    /** @return whether letting go (or using it) now would launch it */
    public boolean isReady() {
        return ready.getAsBoolean();
    }

    /** Where it goes if {@code shooter} launches it facing {@code rotation} now. Never hits the shooter. */
    public Trajectory trajectory(Shooter shooter, Vec2 rotation) {
        return flight.launch(launch.launch(shooter, rotation));
    }

    @Override
    public String toString() {
        return "Shot(" + item + ", " + type + ")";
    }
}
