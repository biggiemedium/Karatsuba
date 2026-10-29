package dev.px.karatsuba.adapter.movement;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Whether the player is pushed out of the blocks they are inside.
 *
 * <p>Each tick, vanilla looks at four points round the player's box
 * ({@code ClientPlayerEntity.pushOutOfBlocks}, ±0.35 of the width) and, for any
 * column that collides, sets the velocity on one axis to 0.1 toward the nearest
 * open neighbour. It is client-side only: the server never pushes a player out.
 *
 * <p>Grim treats it as optional for 1.14+ clients
 * ({@code PlayerBaseTick.moveTowardsClosestSpaceModern}): where it would push, it
 * only widens that axis by ±0.15 of uncertainty and predicts no push at all. So
 * skipping it is a legal move, and matches Grim's own prediction more closely
 * than vanilla does. The vanilla server objects only to a move into a block the
 * player was not already in, which standing still never is.
 */
public final class BlockPushOut {

    private static final List<BooleanSupplier> SKIP = new CopyOnWriteArrayList<>();

    private BlockPushOut() {
    }

    /** Skips the push whenever {@code condition} is true. Register once, for the life of the client. */
    public static void skipWhen(BooleanSupplier condition) {
        SKIP.add(condition);
    }

    /** @return whether anything wants the push skipped right now. Read by the player mixin. */
    public static boolean skipped() {
        for (BooleanSupplier condition : SKIP) {
            if (condition.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }
}
