package dev.px.karatsuba.adapter.game;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * The local player, as questions with Core-typed answers.
 *
 * <p>Core stops at {@code Platform}: a data directory, a screen size, a username,
 * an in-game flag. It has no player, and the README says so &mdash; "Not yet
 * included: Adapter layer &mdash; Player, World, Entity". This is that layer for
 * the player half, and {@link Entities} is the other.
 *
 * <p><b>Why it is worth a class rather than six {@code MinecraftClient.getInstance()}
 * calls.</b> Every module that touches the player otherwise repeats the same three
 * lines: get the client, get the player, null-check it because there isn't one in a
 * menu. Repeated in six modules, that is six places to get the null check wrong and
 * six files to rewrite when a mapping changes. Here it is one.
 *
 * <p>Every reader is safe out of a world. Positions come back {@link Vec3#ZERO},
 * flags come back false, and a caller that genuinely needs to know asks
 * {@link #present()} rather than comparing against a sentinel.
 *
 * <p><b>Game thread only.</b> Everything here reads live game state. Background
 * work calls {@code Core.threads().sync(...)} first &mdash; see
 * {@link dev.px.karatsuba.adapter.concurrent.GameThread}.
 */
public final class Players {

    private Players() {
    }

    private static ClientPlayerEntity player() {
        return MinecraftClient.getInstance().player;
    }

    /** @return whether there is a player at all, which there is not in a menu */
    public static boolean present() {
        return player() != null;
    }

    /** @return the player's own name, or an empty string in a menu */
    public static String name() {
        ClientPlayerEntity self = player();
        return self == null ? "" : self.getName().getString();
    }

    /** Feet position, on the tick. For drawing, prefer {@link #eyePosition()}'s frame-smooth sibling in {@link Entities#self(float)}. */
    public static Vec3 position() {
        ClientPlayerEntity self = player();
        return self == null ? Vec3.ZERO : Vec3.of(self.getX(), self.getY(), self.getZ());
    }

    /** Where the camera is, which is what a trace or a reach check measures from. */
    public static Vec3 eyePosition() {
        ClientPlayerEntity self = player();
        return self == null ? Vec3.ZERO : Vec3.of(self.getX(), self.getEyeY(), self.getZ());
    }

    /** The block the player is standing in, for anything working on the grid. */
    public static Vec3i blockPosition() {
        Vec3 at = position();
        return Vec3i.of((int) Math.floor(at.getX()), (int) Math.floor(at.getY()), (int) Math.floor(at.getZ()));
    }

    /** Yaw and pitch, in Core's rotation vector rather than two loose floats. */
    public static Vec2 rotation() {
        ClientPlayerEntity self = player();
        return self == null ? Vec2.rotation(0f, 0f) : Vec2.rotation(self.getYaw(), self.getPitch());
    }

    public static float health() {
        ClientPlayerEntity self = player();
        return self == null ? 0f : self.getHealth();
    }

    public static boolean onGround() {
        ClientPlayerEntity self = player();
        return self != null && self.isOnGround();
    }

    public static boolean sneaking() {
        ClientPlayerEntity self = player();
        return self != null && self.isSneaking();
    }

    public static boolean sprinting() {
        ClientPlayerEntity self = player();
        return self != null && self.isSprinting();
    }

    /**
     * @return whether the player is asking to move forward this tick
     *
     * <p>Movement <em>input</em>, not velocity: a module wants to know what the
     * player is holding, not whether the game has let them go anywhere yet. Reading
     * velocity instead is why sprint modules stick on when you walk into a wall.
     */
    public static boolean movingForward() {
        ClientPlayerEntity self = player();
        return self != null && self.input.getMovementInput().y > 0f;
    }

    /**
     * Turns sprinting on or off.
     *
     * <p>One of the few writers here, and it exists so a module never reaches for
     * the player itself. Silently ignored out of a world, which is the same answer
     * every reader gives.
     */
    public static void setSprinting(boolean sprinting) {
        ClientPlayerEntity self = player();
        if (self != null) {
            self.setSprinting(sprinting);
        }
    }
}
