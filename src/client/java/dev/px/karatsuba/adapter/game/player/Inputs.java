package dev.px.karatsuba.adapter.game.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.PlayerInput;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the player is holding down, and how to hold something down for them.
 *
 * <p>The seam for the two questions a module asks about controls: <em>is the
 * player doing anything</em>, and <em>make the player do this</em>. Both would
 * otherwise be a reach into {@code MinecraftClient.getInstance().options} and a
 * field name that changes between versions, repeated in every module that wants
 * them.
 *
 * <h2>Forced controls are remembered</h2>
 * A {@code KeyBinding} that is told it is pressed stays pressed until something
 * tells it otherwise. A module that forces one and then gets disabled, or
 * crashes, or simply forgets, leaves the player walking into a wall forever
 * &mdash; and because the binding is shared, no other module can tell whose
 * fault it is.
 *
 * <p>So this class tracks what it forced. {@link #releaseAll()} undoes exactly
 * that and nothing else, which makes the correct {@code onDisable} one line and
 * leaves a key the player is genuinely holding alone.
 *
 * <pre>{@code
 * @Override protected void onDisable() {
 *     Inputs.releaseAll();
 * }
 * }</pre>
 *
 * <p><b>Game thread only.</b> These read and write live game state.
 */
public final class Inputs {

    /**
     * The controls worth naming, as Core-side constants rather than field names.
     *
     * <p>A module says {@code Control.FORWARD} and never learns that the game
     * calls it {@code forwardKey}; if that field is renamed, this enum is the one
     * place that changes.
     */
    public enum Control {

        FORWARD,
        BACK,
        LEFT,
        RIGHT,
        JUMP,
        SNEAK,
        SPRINT,
        ATTACK,
        USE,
        DROP,
        INVENTORY
    }

    /** The four that move the player, for {@link #anyMovement()}. */
    private static final Set<Control> MOVEMENT =
            EnumSet.of(Control.FORWARD, Control.BACK, Control.LEFT, Control.RIGHT);

    /** What a module has told the game is held. Never touched by the player. */
    private static final Set<Control> FORCED = EnumSet.noneOf(Control.class);

    private Inputs() {
    }

    // -------------------------------------------------------------- reading

    /** @return whether the control is down, whether by the player or by {@link #hold} */
    public static boolean pressed(Control control) {
        KeyBinding binding = bindingOf(control);
        return binding != null && binding.isPressed();
    }

    /** @return whether any of the four movement keys is down */
    public static boolean anyMovement() {
        for (Control control : MOVEMENT) {
            if (pressed(control)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the player themselves holds a movement key: one that is
     *         down and that nothing here forced
     *
     * <p>For handing control back when the player reaches for the keyboard.
     * Keys written for Core's movement claims never show here: those are written
     * to the game's input, not pressed on its bindings.
     */
    public static boolean playerMoving() {
        for (Control control : MOVEMENT) {
            if (!FORCED.contains(control) && pressed(control)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the player appears to be doing anything at all
     *
     * <p>Movement, jumping, attacking or using. What an idle check wants, and
     * deliberately not a complete list of keys: opening the inventory is not
     * "activity" in the sense anything asking this question means.
     */
    public static boolean anyActivity() {
        return anyMovement()
                || pressed(Control.JUMP)
                || pressed(Control.ATTACK)
                || pressed(Control.USE);
    }

    // -------------------------------------------------------------- forcing

    /**
     * Holds a control down until released.
     *
     * <p>Indistinguishable from the player holding it, which is the point: the
     * game's own movement code runs, so the result is a real walk rather than a
     * teleport the server will reject.
     */
    public static void hold(Control control) {
        KeyBinding binding = bindingOf(control);
        if (binding != null) {
            binding.setPressed(true);
            FORCED.add(control);
        }
    }

    /** Holds or releases, for a caller driving a control from a boolean. */
    public static void hold(Control control, boolean held) {
        if (held) {
            hold(control);
        } else {
            release(control);
        }
    }

    /**
     * Releases a control this class forced.
     *
     * <p>A control the player is actually holding is left alone: releasing it
     * would fight the keyboard, and the next poll would set it straight back.
     */
    public static void release(Control control) {
        if (!FORCED.remove(control)) {
            return;
        }
        KeyBinding binding = bindingOf(control);
        if (binding != null) {
            binding.setPressed(false);
        }
    }

    /**
     * Releases everything this class forced. The whole of a correct teardown.
     *
     * <p>Iterates a copy, because {@link #release} removes from the set it would
     * otherwise be walking.
     */
    public static void releaseAll() {
        for (Control control : EnumSet.copyOf(FORCED)) {
            release(control);
        }
    }

    /** @return whether anything is currently being forced */
    public static boolean isForcing() {
        return !FORCED.isEmpty();
    }

    // ------------------------------------------------------ what was reported

    /**
     * @return whether the keys last told to the server move the player: forward,
     *         back, left, right or jump
     *
     * <p>Not the keys held now. The game sends its input packet straight after the
     * movement tick, from the keys that tick used, so until the next one this is
     * exactly what the server last heard. Grim counts jump as moving too, which is
     * why it is in here and not in {@link #anyMovement()}: it flags an inventory
     * click or close while any of these was reported (MultiActionsC/D).
     */
    public static boolean reportedMovement() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.input == null) {
            return false;
        }
        PlayerInput keys = player.input.playerInput;
        return keys.forward() || keys.backward() || keys.left() || keys.right() || keys.jump();
    }

    /**
     * @return whether the keys last told to the server held jump
     *
     * <p>Read before this tick's movement, it is last tick's jump: the one the
     * game compares against to decide whether jump is a new press, which is what
     * opens an elytra. Grim compares the same pair (ElytraB): a take-off needs
     * jump let go in the last input packet and held in the next.
     */
    public static boolean reportedJump() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && player.input != null && player.input.playerInput.jump();
    }

    /** Whether the next movement tick reports no movement keys; see {@link #holdStill()}. */
    private static boolean holdingStill;

    /**
     * Stands the player still for the next movement tick: no movement keys, no
     * jump, no sprint, as if the player had let go for one tick.
     *
     * <p>For something that may only happen while the server believes the player
     * is still, such as an inventory click. The keys the game reports are the
     * ones it moves by, so the move, the input packet and the stop of the sprint
     * all agree and there is nothing for a simulation to object to. The keyboard
     * is left alone, so the player's keys apply again the tick after.
     */
    public static void holdStill() {
        holdingStill = true;
    }

    /**
     * Applies {@link #holdStill()} to the keys the game has just read. Called by
     * the player mixin straight after {@code Input.tick()}, after the Move Fix.
     */
    public static void applyHoldStill(Input input) {
        if (!holdingStill || input == null) {
            return;
        }
        holdingStill = false;
        PlayerInput keys = input.playerInput;
        input.playerInput = new PlayerInput(false, false, false, false, false, keys.sneak(), false);
        input.movementForward = 0f;
        input.movementSideways = 0f;
    }

    // ------------------------------------------------------------- internal

    private static KeyBinding bindingOf(Control control) {
        GameOptions options = MinecraftClient.getInstance().options;
        if (options == null) {
            return null;
        }
        return switch (control) {
            case FORWARD -> options.forwardKey;
            case BACK -> options.backKey;
            case LEFT -> options.leftKey;
            case RIGHT -> options.rightKey;
            case JUMP -> options.jumpKey;
            case SNEAK -> options.sneakKey;
            case SPRINT -> options.sprintKey;
            case ATTACK -> options.attackKey;
            case USE -> options.useKey;
            case DROP -> options.dropKey;
            case INVENTORY -> options.inventoryKey;
        };
    }
}
