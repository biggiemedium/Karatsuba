package dev.px.karatsuba.adapter.game.player;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;

/**
 * The local player, as questions with Core-typed answers.
 *
 * <p>Core stops at {@code Platform}: a data directory, a screen size, a username,
 * an in-game flag. It has no player, and the README says so &mdash; "Not yet
 * included: Adapter layer &mdash; Player, World, Entity". This is that layer for
 * the player half, and {@link dev.px.karatsuba.adapter.game.entity.Entities Entities} is the other.
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

    /**
     * Feet position, as the last tick left it.
     *
     * <p>Not interpolated, so it is the right answer for logic and the wrong one
     * for drawing: a box drawn here at 240 FPS off 20 ticks a second visibly lags
     * the player inside it. {@link dev.px.karatsuba.adapter.game.entity.Entities#self(float)} is the smoothed version.
     */
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

    /** @return the player's entity id, or -1 in a menu */
    public static int entityId() {
        ClientPlayerEntity self = player();
        return self == null ? -1 : self.getId();
    }

    public static float health() {
        ClientPlayerEntity self = player();
        return self == null ? 0f : self.getHealth();
    }

    public static float maxHealth() {
        ClientPlayerEntity self = player();
        return self == null ? 0f : self.getMaxHealth();
    }

    /**
     * @return motion in blocks per tick, or {@link Vec3#ZERO} in a menu
     *
     * <p>Per tick, not per second: multiply by 20 for the number a speedometer
     * wants. Y carries gravity, so it is rarely zero even standing still.
     */
    public static Vec3 velocity() {
        ClientPlayerEntity self = player();
        return self == null ? Vec3.ZERO : Vec3.of(self.getVelocity().x, self.getVelocity().y, self.getVelocity().z);
    }

    /**
     * Replaces the player's motion.
     *
     * <p>Client-side only: the server learns about it from the position packets
     * that follow, which is why a value it considers impossible gets corrected.
     * Prefer editing {@code PlayerMoveEvent} where there is one, so the change
     * lands at the point the game was about to read it.
     */
    public static void setVelocity(Vec3 velocity) {
        ClientPlayerEntity self = player();
        if (self != null) {
            self.setVelocity(velocity.getX(), velocity.getY(), velocity.getZ());
        }
    }

    /**
     * @return how far through the attack cooldown the player is, 0 to 1
     *
     * <p>1 means the weapon has fully recharged and a hit lands at full damage;
     * anything less is a partial hit. Attacking at 1 rather than as fast as
     * possible is the difference between the damage a player actually deals and
     * a stream of scratches, which is why this exists rather than a timer.
     *
     * <p>Measured from the tick, so it does not need a partial-tick argument: a
     * caller deciding whether to swing this tick wants the tick's answer.
     */
    public static float attackCooldown() {
        ClientPlayerEntity self = player();
        return self == null ? 0f : self.getAttackCooldownProgress(0f);
    }

    /**
     * Starts the attack cooldown over, as landing a hit does.
     *
     * <p>For a hit the game never processed &mdash; one a module intercepted
     * &mdash; so the next swing is timed as if this one had landed.
     */
    public static void resetAttackCooldown() {
        ClientPlayerEntity self = player();
        if (self != null) {
            self.resetLastAttackedTicks();
        }
    }

    /**
     * @return the player's attack damage with what they are holding, before the
     *         cooldown, criticals and enchantments are applied; 0 in a menu
     */
    public static float attackDamage() {
        ClientPlayerEntity self = player();
        return self == null ? 0f : (float) self.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
    }

    /** @return whether the player is standing in water */
    public static boolean inWater() {
        ClientPlayerEntity self = player();
        return self != null && self.isTouchingWater();
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
     * @return whether the game would let the player sprint, as far as hunger goes:
     *         more than six food, flying allowed, or riding
     *
     * <p>The rule {@code ClientPlayerEntity.canSprint} applies, for a planner
     * deciding whether to plan sprints at all. Blindness, using an item and the
     * rest are checked by the game as it moves.
     */
    public static boolean canSprint() {
        ClientPlayerEntity self = player();
        return self != null && (self.hasVehicle()
                || self.getHungerManager().getFoodLevel() > 6
                || self.getAbilities().allowFlying);
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

    /**
     * Moves the player outright.
     *
     * <p>A teleport, not a walk: nothing about it goes through the movement code,
     * so the server sees a jump in position and will correct anything it does not
     * believe. Its honest use is putting the player back where they were &mdash;
     * restoring a position a module borrowed.
     */
    public static void setPosition(Vec3 position) {
        ClientPlayerEntity self = player();
        if (self != null) {
            self.setPosition(position.getX(), position.getY(), position.getZ());
        }
    }

    /**
     * @return the rotation the server was last sent, which is the one it checks
     *         a click against; the player's own rotation before anything was sent
     *
     * <p>Differs from {@link #rotation()} whenever a silent rotation is held: the
     * camera stays where the player put it, and this is where the server thinks
     * they are looking.
     */
    public static Vec2 serverRotation() {
        Vec2 sent = MotionBridge.lastSentRotation();
        return sent == null ? rotation() : sent;
    }

    /** @return how far the player reaches entities: the attribute, 3 unless a server changed it */
    public static double entityReach() {
        ClientPlayerEntity self = player();
        return self == null ? 3d : self.getEntityInteractionRange();
    }

    /** @return how far the player reaches blocks: the attribute, 4.5 unless a server changed it */
    public static double blockReach() {
        ClientPlayerEntity self = player();
        return self == null ? 4.5d : self.getBlockInteractionRange();
    }

    /**
     * @return how far the player has fallen since last on the ground, in blocks;
     *         landing takes roughly this less three as damage, before armour
     */
    public static double fallDistance() {
        ClientPlayerEntity self = player();
        return self == null ? 0d : self.fallDistance;
    }

    /** @return whether the player is gliding on an elytra */
    public static boolean gliding() {
        ClientPlayerEntity self = player();
        return self != null && self.isGliding();
    }

    /** @return health plus absorption: everything a hit has to get through */
    public static float healthPool() {
        ClientPlayerEntity self = player();
        return self == null ? 0f : self.getHealth() + self.getAbsorptionAmount();
    }

    /** @return the hunger bar, 0 to 20; 20 out of a world, where nothing is hungry */
    public static int foodLevel() {
        ClientPlayerEntity self = player();
        return self == null ? 20 : self.getHungerManager().getFoodLevel();
    }

    /**
     * Closes the open container the way the player's Escape does: the screen
     * goes, and the server is told the window closed.
     *
     * <p>Grim flags a close sent while the last input packet held a movement key
     * (MultiActionsD), so a caller stands the player still first.
     */
    public static void closeContainer() {
        ClientPlayerEntity self = player();
        if (self != null && inOtherContainer()) {
            self.closeHandledScreen();
        }
    }

    /** @return whether an item is in use: eating, drinking, blocking, drawing a bow */
    public static boolean usingItem() {
        ClientPlayerEntity self = player();
        return self != null && self.isUsingItem();
    }

    /** @return whether a container or inventory screen is open, which a player cannot click the world through */
    public static boolean inContainer() {
        return MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>;
    }

    /** @return whether the player's own inventory screen is open, survival or creative */
    public static boolean inOwnInventory() {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
    }

    /**
     * @return whether another container is open on the server's side: a chest, a
     *         furnace, anything but the player's own inventory
     *
     * <p>While one is, a click on the player's own inventory names a window the
     * server is not showing, so nothing may click it.
     */
    public static boolean inOtherContainer() {
        ClientPlayerEntity self = player();
        return self != null && self.currentScreenHandler != self.playerScreenHandler;
    }

    /**
     * Turns the player's head.
     *
     * <p>Client-side; the next movement packet carries it to the server. Use
     * {@code RotationMath.step} to trace a turn out over several ticks rather
     * than snapping, which is what makes the difference visible to anything
     * watching.
     */
    public static void setRotation(Vec2 rotation) {
        ClientPlayerEntity self = player();
        if (self != null) {
            self.setYaw(rotation.getYaw());
            self.setPitch(rotation.getPitch());
        }
    }
}
