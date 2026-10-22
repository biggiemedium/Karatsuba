package dev.px.karatsuba.client.module.movement;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.module.mode.ModalModule;
import dev.px.karatsuba.client.module.movement.strafe.GrimStrafe;
import dev.px.karatsuba.client.module.movement.strafe.VanillaStrafe;

/**
 * Steers the player's horizontal motion with the movement keys mid-air.
 *
 * <table>
 *   <caption>What each mode does</caption>
 *   <tr><th>Mode</th><th>How it works</th></tr>
 *   <tr><td>Vanilla</td>
 *       <td>bunny hops and rotates the velocity onto the keys every tick.
 *           Fast and flagged by any simulating anticheat</td></tr>
 *   <tr><td>Grim</td>
 *       <td>silently faces the way the keys point and lets Move Fix turn them
 *           into W, so sprint, the sprint jump and air control all push that
 *           way. Never touches velocity, except the opt-in extras that stay
 *           inside an allowance Grim grants itself</td></tr>
 * </table>
 *
 * <p>Like Velocity and Elytra Fly, this file has no logic in it. Adding a mode
 * is a constant, a class in {@code strafe/} and two lines here.
 */
@ModuleInfo(name = "Strafe", description = "Steers your motion with the movement keys", category = "Movement")
public final class StrafeModule extends ModalModule<StrafeModule.Mode> {

    public enum Mode {
        VANILLA,
        GRIM
    }

    // Field order is the order the panels appear in, so it matches the enum.
    private final VanillaStrafe vanilla = attach(new VanillaStrafe());
    private final GroupSetting vanillaPanel = panelFor(vanilla);

    private final GrimStrafe grim = attach(new GrimStrafe());
    private final GroupSetting grimPanel = panelFor(grim);

    public StrafeModule() {
        super(Mode.VANILLA);
    }

    /** Grim says when Move Fix is off, since it does nothing without it. */
    @Override
    public String getDisplayInfo() {
        return getMode() == Mode.GRIM ? grim.status() : super.getDisplayInfo();
    }

    @Override
    protected void onDisable() {
        // Motion and rotation claims lapse on their own; held keys do not.
        vanilla.stop();
        grim.stop();
    }
}
