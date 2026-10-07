package dev.px.karatsuba.client.module.player;

import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.adapter.game.player.Rotations;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.ActionProfile;
import dev.px.karatsuba.client.service.action.SwapMode;

/**
 * How every module's attacks and clicks reach the server: the settings behind
 * {@code Services.actions()}.
 *
 * <p>Not a feature of its own. Every module that attacks, places or uses goes
 * through the action service, and this is where the player says how careful it
 * must be on the server they are on. One place, so an aura and a surround can
 * never disagree about what the server allows.
 *
 * <p>The toggle is the checks: on (the default), an attack or click is sent only
 * when a player could have sent it &mdash; looking at it, in reach, hands free.
 * Off, the winner is sent as soon as it has its item. The defaults are for Grim.
 *
 * <p>Hidden from the module list, since it is always there.
 */
@ModuleInfo(name = "Interact",
        description = "How attacks and clicks are checked and sent; off sends them unchecked",
        category = "Player",
        enabled = true,
        visible = false)
public final class InteractModule extends Module implements ActionProfile {

    private final BooleanSetting rotate = bool("Rotate", true)
            .describe("Turn to face what is attacked or clicked");

    private final EnumSetting<RotationMode> rotationMode = enumOf("Rotation Mode", RotationMode.SILENT)
            .describe("SILENT turns only what the server sees; CLIENT turns the camera")
            .visibleWhen(rotate);

    private final NumberSetting<Float> turnSpeed = number("Turn Speed", 180f, 10f, 180f)
            .step(5f)
            .describe("Degrees per tick; 180 snaps")
            .visibleWhen(rotate);

    private final BooleanSetting moveFix = bool("Move Fix", true)
            .describe("Walk by a silent rotation and correct the keys to match; off, Grim's Simulation flags walking while aiming");

    private final BooleanSetting raytrace = bool("Raytrace", true)
            .describe("Only act once the rotation the server has looks at the target");

    private final EnumSetting<SwapMode> swap = enumOf("Swap", SwapMode.SILENT)
            .describe("SILENT swaps for the click and back; HOLD keeps the item held; OFF never swaps");

    private final EnumSetting<Interactions.Swing> swing = enumOf("Swing", Interactions.Swing.CLIENT)
            .describe("CLIENT swings as vanilla; PACKET tells the server only; NONE skips it");

    private final NumberSetting<Integer> clickDelay = integer("Click Delay", 0, 0, 10)
            .describe("Ticks between right-clicks");

    private final NumberSetting<Integer> attackDelay = integer("Attack Delay", 0, 0, 10)
            .describe("Ticks between attacks");

    private final BooleanSetting sameTick = bool("Same Tick", true)
            .describe("Attack and right-click in one tick, as vanilla allows");

    private final BooleanSetting multitask = bool("Multitask", false)
            .describe("Act while eating, drinking or blocking");

    private final BooleanSetting airPlace = bool("Air Place", false)
            .describe("Allow clicks against nothing; most anticheats refuse them");

    public InteractModule() {
        // Services are registered before modules, so the service is there to tell.
        Services.actions().setProfile(this);
        // Every silent rotation, whoever holds it: the adapter reads it each tick.
        Rotations.movementCorrection(moveFix::isOn);
    }

    @Override
    public boolean checks() {
        return isEnabled();
    }

    @Override
    public boolean rotate() {
        return rotate.isOn();
    }

    @Override
    public RotationMode rotationMode() {
        return rotationMode.get();
    }

    @Override
    public float turnSpeed() {
        return turnSpeed.getFloat();
    }

    @Override
    public boolean raytrace() {
        return raytrace.isOn();
    }

    @Override
    public SwapMode swap() {
        return swap.get();
    }

    @Override
    public Interactions.Swing swing() {
        return swing.get();
    }

    @Override
    public int clickDelay() {
        return clickDelay.getInt();
    }

    @Override
    public int attackDelay() {
        return attackDelay.getInt();
    }

    @Override
    public boolean sameTick() {
        return sameTick.isOn();
    }

    @Override
    public boolean multitask() {
        return multitask.isOn();
    }

    @Override
    public boolean airPlace() {
        return airPlace.isOn();
    }
}
