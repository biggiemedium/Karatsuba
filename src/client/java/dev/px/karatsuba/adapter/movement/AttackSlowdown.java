package dev.px.karatsuba.adapter.movement;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Whether a sprinting hit slows the player.
 *
 * <p>Vanilla multiplies the player's horizontal velocity by 0.6 and stops the
 * sprint after a sprinting hit with knockback ({@code PlayerEntity.attack}).
 * Grim treats that as optional for 1.21+ clients on 1.9+ servers: an attack only
 * raises {@code maxAttackSlow}, and {@code PredictionEngine.addAttackSlowToPossibilities}
 * keeps the unslowed velocity as a candidate. So skipping it is a legal move.
 *
 * <p>The server still ends its own copy of the sprint on the hit, so later hits
 * lose the sprint knockback bonus until the sprint is restarted. Nothing flags.
 */
public final class AttackSlowdown {

    private static final List<BooleanSupplier> SKIP = new CopyOnWriteArrayList<>();

    private AttackSlowdown() {
    }

    /** Skips the slowdown whenever {@code condition} is true. Register once, for the life of the client. */
    public static void skipWhen(BooleanSupplier condition) {
        SKIP.add(condition);
    }

    /** @return whether anything wants the slowdown skipped right now. Read by the player mixin. */
    public static boolean skipped() {
        for (BooleanSupplier condition : SKIP) {
            if (condition.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }
}
