package dev.px.karatsuba.client.settings.interaction;

import dev.px.core.Core;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.adapter.game.player.Rotations;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.ActionProfile;
import dev.px.karatsuba.client.service.action.SwapMode;
import dev.px.karatsuba.client.settings.ClientSection;

/**
 * How every module's attacks, clicks, swaps and rotations reach the server: the
 * profile {@code Services.actions()} checks and sends under, and the Move Fix for
 * {@code Rotations}.
 *
 * <p>A client setting, not a module: it does nothing in game by itself, it governs
 * how everything else does. It used to be the hidden Interact module, whose on/off
 * toggle really meant "checks on"; that is now the explicit {@link #checks()}
 * setting, so it can no longer be switched off by accident with every module
 * quietly becoming unsafe.
 *
 * <p>The defaults are for Grim: look at what you click, from within reach, one
 * attack and one right-click a tick, never while eating or in a container, and walk
 * by a silent rotation with the keys corrected to match.
 */
public final class InteractionSettings extends ClientSection implements ActionProfile {

    private final BooleanSetting checks = bool("Checks", true)
            .describe("Send an attack or click only when a player could have: looking at it, in reach, hands free");

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
            .describe("Only act once the rotation the server has looks at the target")
            .visibleWhen(checks);

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
            .describe("Act while eating, drinking or blocking")
            .visibleWhen(checks);

    private final BooleanSetting airPlace = bool("Air Place", false)
            .describe("Allow clicks against nothing; most anticheats refuse them")
            .visibleWhen(checks);

    public InteractionSettings() {
        super("interaction", "Interaction", "How every module's rotations, clicks and packets are checked and sent");
    }

    /**
     * Becomes the rules every module plays by: the action service's profile, and
     * whether rotations steer movement. Both read these settings live, so a change
     * applies on the next tick.
     */
    @Override
    protected void onRegister(Core core) {
        Services.actions().setProfile(this);
        Rotations.movementCorrection(this::moveFix);
    }

    /** @return whether silent rotations steer the movement too, with the keys corrected */
    public boolean moveFix() {
        return moveFix.isOn();
    }

    @Override
    public boolean checks() {
        return checks.isOn();
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
