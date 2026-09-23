package dev.px.karatsuba.client.module.combat;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.module.combat.velocity.BufferVelocity;
import dev.px.karatsuba.client.module.combat.velocity.CancelVelocity;
import dev.px.karatsuba.client.module.combat.velocity.JumpResetVelocity;
import dev.px.karatsuba.client.module.combat.velocity.ScaledVelocity;
import dev.px.karatsuba.client.module.mode.ModalModule;

/**
 * Takes less knockback than the server intended.
 *
 * <p>The worked example for {@link ModalModule}, and the reason it exists. The
 * four ways below are not four settings of one algorithm &mdash; they work at
 * different layers, fail differently, and share no state:
 *
 * <table>
 *   <caption>What each mode actually does</caption>
 *   <tr><th>Mode</th><th>Layer</th><th>How it works</th></tr>
 *   <tr><td>Cancel</td><td>semantic event</td>
 *       <td>drops the push entirely</td></tr>
 *   <tr><td>Scaled</td><td>semantic event</td>
 *       <td>keeps a fraction of it</td></tr>
 *   <tr><td>Buffer</td><td>outgoing packets</td>
 *       <td>stops reporting position for a moment, so the push is applied to a
 *           player the server has lost track of</td></tr>
 *   <tr><td>Jump Reset</td><td>motion arbiter</td>
 *       <td>jumps on the hit, which bleeds the push off through friction</td></tr>
 * </table>
 *
 * <p>Written as one class that would be four sets of fields, four branches in
 * every handler, and a fifth mode touching all of it. Written as four classes it
 * is four files that never mention each other, and this one, which names them and
 * nothing else.
 *
 * <p><b>This file has no logic in it at all</b>, which is the point. Adding a
 * fifth mode is a constant, a class and two lines here.
 */
@ModuleInfo(name = "Velocity", description = "Reduces knockback", category = "Combat")
public final class VelocityModule extends ModalModule<VelocityModule.Mode> {

    /**
     * The modes, in the order they appear in the dropdown.
     *
     * <p>Roughly least to most conspicuous: cancelling outright is the most
     * obvious thing a client can do, and jumping is what a good player does
     * anyway.
     */
    public enum Mode {
        CANCEL,
        SCALED,
        BUFFER,
        JUMP_RESET
    }

    // Field order is dropdown-independent -- the enum decides that -- but it is
    // the order the panels appear in, so it matches.
    private final CancelVelocity cancel = attach(new CancelVelocity());

    private final ScaledVelocity scaled = attach(new ScaledVelocity());
    private final GroupSetting scaledPanel = panelFor(scaled);

    private final BufferVelocity buffer = attach(new BufferVelocity());
    private final GroupSetting bufferPanel = panelFor(buffer);

    private final JumpResetVelocity jumpReset = attach(new JumpResetVelocity());
    private final GroupSetting jumpResetPanel = panelFor(jumpReset);

    public VelocityModule() {
        super(Mode.SCALED);
    }
}
