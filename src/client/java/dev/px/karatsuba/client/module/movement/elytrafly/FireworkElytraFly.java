package dev.px.karatsuba.client.module.movement.elytrafly;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec2;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.PlayerMoveEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.ElytraFlyModule;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.inventory.InventoryClick;
import dev.px.karatsuba.client.settings.ClientSettings;

/**
 * Flies on fireworks and steers with the movement keys, as if the elytra were
 * creative flight.
 *
 * <p>Lambda's firework elytra fly. While gliding, the keys pick a direction
 * relative to where the camera looks: W, A, S and D along the ground, jump to
 * climb ({@code Climb Angle} with a direction, straight up without one), sneak to
 * dive ({@code Dive Angle}, or straight down). The head turns silently to that
 * direction and a firework is kept burning, so the rocket carries you along it.
 * The camera stays yours. With no key held, {@code When Still} decides what
 * happens.
 *
 * <h2>Why it is safe on Grim</h2>
 * Grim 2.3.73 does not simulate a firework boost. While it counts a rocket on you
 * it accepts any glide velocity inside a box built from the current and last
 * packet looks, 1.7 blocks a tick along each ({@code UncertaintyHandler.tickFireworksBox}).
 * Everything this mode does stays inside that box:
 *
 * <ul>
 *   <li><b>The look is the one sent.</b> A firework pushes along the shooter's
 *       look in the rocket's own tick, outside the player's, where the silent
 *       rotation is no longer in place. The adapter makes the rocket read the
 *       look the server was sent ({@code FireworkRocketEntityMixin}), so the push
 *       is inside the box by construction. That needs Move Fix, which carries the
 *       silent look through the glide itself; without it this mode does nothing.</li>
 *   <li><b>Hovering stands still only under a rocket.</b> The box always contains
 *       zero, so standing still in mid-air is accepted while Grim counts a rocket,
 *       and only then. The body is frozen only while the client sees its rocket,
 *       which is when Grim counts it: added once the spawn's transaction is
 *       answered, kept a tick past its removal ({@code CompensatedFireworks}).
 *       Between rockets the glide carries on as normal.</li>
 *   <li><b>Fireworks go out as uses</b> through {@code Services.actions()}: from
 *       the hotbar, at the start of a tick, never after the tick's end (Post).
 *       Refilling the hotbar from the inventory is one shift-click through
 *       {@code Services.inventory()}, which stands you still for that tick; the
 *       keys do nothing to a glide, so it costs nothing.</li>
 * </ul>
 *
 * <h2>What Lambda does that this does not</h2>
 * <ul>
 *   <li><b>Holding still by withholding movement packets</b> (its default, "None",
 *       and "Packet Gap"). With no rocket Grim simulates the glide, so a player
 *       who stops sending movement while gliding is a player who did not fall.</li>
 *   <li><b>Setting velocity</b> to the firework's top speed when you start moving
 *       again. Inside the box while a rocket burns, but a velocity edit all the
 *       same: here the rocket gets you there in about three ticks.</li>
 *   <li><b>Rewriting every movement packet</b> as a full one with a 0.0001&deg; yaw
 *       wobble. The game already sends the look whenever it changes, and the
 *       wobble only exists to dodge AimDuplicateLook on packets it forced.</li>
 * </ul>
 */
public final class FireworkElytraFly extends SubMode<ElytraFlyModule.Mode> {

    private static final String FIREWORK = "firework_rocket";

    /** Ticks to wait for a launched rocket to appear before launching another. */
    private static final int LAUNCH_WAIT = 20;

    public enum Still {
        /** Let the elytra glide. */
        GLIDE,
        /** Hold position, launching rockets to keep it. */
        HOVER,
        /** Hold position while a rocket is already burning; launch no more. */
        HOVER_ON_BOOST
    }

    private final BooleanSetting inventory = bool("Inventory", true)
            .describe("Move fireworks to the hotbar from your inventory when it has none; needs a free hotbar slot");

    private final NumberSetting<Float> climbAngle = number("Climb Angle", 45f, 0f, 90f)
            .step(1f)
            .describe("How steeply to climb when holding jump with a direction");

    private final NumberSetting<Float> diveAngle = number("Dive Angle", 20f, 0f, 90f)
            .step(1f)
            .describe("How steeply to dive when holding sneak with a direction");

    private final EnumSetting<Still> whenStill = enumOf("When Still", Still.GLIDE)
            .describe("With no key held: glide on, hover using fireworks, or hover only while a rocket is still burning");

    /** Ticks since enabling, for the launch wait. */
    private long tick;

    /** The tick a launch was sent, while waiting for its rocket. */
    private long launchedAt = Long.MIN_VALUE / 2;

    /** Whether this tick's movement is skipped: hovering under a rocket. */
    private boolean frozen;

    /** Which way the hover faces this tick: flips every tick. */
    private boolean flip;

    public FireworkElytraFly() {
        super(ElytraFlyModule.Mode.FIREWORK);
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        frozen = false;
        if (!Players.present() || !Players.gliding() || !ClientSettings.interaction().moveFix()) {
            return;
        }
        boolean boosting = Players.fireworkBoosting();
        if (boosting) {
            launchedAt = Long.MIN_VALUE / 2;
        }

        Vec2 heading = heading();
        if (heading == null) {
            hover(boosting);
            return;
        }
        if (!boosting) {
            launch();
        }
        look(heading);
    }

    /** With no key held. */
    private void hover(boolean boosting) {
        Still still = whenStill.get();
        if (still == Still.GLIDE || still == Still.HOVER_ON_BOOST && !boosting) {
            return;
        }
        // Facing one way then the other, the rocket's pushes cancel, and Grim's
        // box spans both directions.
        look(Vec2.rotation(flip ? 0f : 180f, 0f));
        flip = !flip;
        // Only under a rocket the server knows about: the box exists only then.
        frozen = boosting;
        if (!boosting && still == Still.HOVER) {
            launch();
        }
    }

    /**
     * @return the look the held keys ask for, or null with none held
     *
     * <p>Relative to where the camera looks, as Lambda's: forward, back and the
     * sides add up to a direction along the ground; jump tilts it up by
     * {@code Climb Angle}, sneak down by {@code Dive Angle}, each straight up or
     * down with no direction. Sneak wins over jump.
     */
    private Vec2 heading() {
        float camera = Players.rotation().getYaw();
        double x = 0d;
        double z = 0d;
        if (Inputs.pressed(Inputs.Control.FORWARD)) {
            x -= Math.sin(Math.toRadians(camera));
            z += Math.cos(Math.toRadians(camera));
        }
        if (Inputs.pressed(Inputs.Control.BACK)) {
            x += Math.sin(Math.toRadians(camera));
            z -= Math.cos(Math.toRadians(camera));
        }
        if (Inputs.pressed(Inputs.Control.LEFT)) {
            x -= Math.sin(Math.toRadians(camera - 90f));
            z += Math.cos(Math.toRadians(camera - 90f));
        }
        if (Inputs.pressed(Inputs.Control.RIGHT)) {
            x -= Math.sin(Math.toRadians(camera + 90f));
            z += Math.cos(Math.toRadians(camera + 90f));
        }
        boolean direction = x * x + z * z > 1.0E-4d;
        float yaw = direction ? (float) Math.toDegrees(Math.atan2(-x, z)) : camera;

        if (Inputs.pressed(Inputs.Control.SNEAK)) {
            return Vec2.rotation(yaw, direction ? diveAngle.getFloat() : 90f);
        }
        if (Inputs.pressed(Inputs.Control.JUMP)) {
            return Vec2.rotation(yaw, direction ? -climbAngle.getFloat() : -90f);
        }
        return direction ? Vec2.rotation(yaw, 0f) : null;
    }

    /** Silent and snapped, as Lambda's: the camera stays the player's, and the server sees where the rocket pushes. */
    private void look(Vec2 rotation) {
        Core.rotations().request(this, RotationRequest.at(rotation)
                .priority(RotationPriority.HIGH)
                .step(RotationRequest.SNAP)
                .mode(RotationMode.SILENT));
    }

    /** Lights a firework from the hotbar, or brings one there from the inventory. */
    private void launch() {
        if (tick - launchedAt < LAUNCH_WAIT) {
            // One is on its way; the rocket appears a round trip after the use.
            return;
        }
        if (Inventories.findInHotbar(FIREWORK) == Inventories.NOT_FOUND) {
            int stored = inventory.isOn() ? Inventories.findInInventory(FIREWORK) : Inventories.NOT_FOUND;
            if (stored != Inventories.NOT_FOUND) {
                Services.inventory().click(this, InventoryClick.quickMove(stored).stopping(), ClaimPriority.NORMAL);
            }
            return;
        }
        Services.actions().submit(this, Action.use()
                .holding(FIREWORK)
                .rotate(false)
                .priority(ClaimPriority.HIGH)
                .then(outcome -> {
                    if (outcome == Outcome.SENT) {
                        launchedAt = tick;
                    }
                }));
    }

    /** Hovering under a rocket: the body stays where it is, as Free Cam's does. */
    @Subscribe(stage = Stage.PRE)
    private void onMove(PlayerMoveEvent event) {
        if (frozen) {
            event.cancel();
        }
    }

    @Override
    protected void onSelected() {
        frozen = false;
        launchedAt = Long.MIN_VALUE / 2;
    }
}
