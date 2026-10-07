package dev.px.karatsuba.client.module.combat;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.module.combat.velocity.CancelVelocity;
import dev.px.karatsuba.client.module.combat.velocity.JumpResetVelocity;
import dev.px.karatsuba.client.module.mode.ModalModule;

/**
 * Takes less knockback than the server intended.
 *
 * <p>The worked example for {@link ModalModule}. The two ways below are not two
 * settings of one algorithm &mdash; they work at different layers and share no
 * state:
 *
 * <table>
 *   <caption>What each mode actually does</caption>
 *   <tr><th>Mode</th><th>Layer</th><th>How it works</th></tr>
 *   <tr><td>Jump Reset</td><td>the controls</td>
 *       <td>takes the push and jumps on it, which bleeds it off through friction.
 *           What an anticheat that checks knockback is taken (Grim) accepts</td></tr>
 *   <tr><td>Cancel</td><td>semantic event</td>
 *       <td>drops the push entirely. Grim sees the push was not taken and sets
 *           the player back to where it would have put them</td></tr>
 * </table>
 *
 * <p><b>This file has no logic in it at all</b>, which is the point. Adding a
 * mode is a constant, a class and two lines here.
 */
@ModuleInfo(name = "Velocity", description = "Reduces knockback", category = "Combat")
public final class VelocityModule extends ModalModule<VelocityModule.Mode> {

    public enum Mode {
        JUMP_RESET,
        CANCEL
    }

    // Field order is the order the panels appear in, so it matches the enum.
    private final JumpResetVelocity jumpReset = attach(new JumpResetVelocity());
    private final GroupSetting jumpResetPanel = panelFor(jumpReset);

    private final CancelVelocity cancel = attach(new CancelVelocity());

    public VelocityModule() {
        super(Mode.JUMP_RESET);
    }
}
