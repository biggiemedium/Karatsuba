package dev.px.karatsuba.client.module.movement;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.module.mode.ModalModule;
import dev.px.karatsuba.client.module.movement.elytrafly.BounceElytraFly;
import dev.px.karatsuba.client.module.movement.elytrafly.FireworkElytraFly;

/**
 * Flies an elytra for you, one of several ways.
 *
 * <p>Movement rather than Travel: every mode changes how you fly right now,
 * wherever you are going, and none of them has a destination.
 *
 * <table>
 *   <caption>What each mode does</caption>
 *   <tr><th>Mode</th><th>How it works</th></tr>
 *   <tr><td>Bounce</td>
 *       <td>skims the ground: lands, hops, and opens the elytra again, over and
 *           over, with forward held and the head at a fixed angle. Fast along a
 *           flat road or a Nether highway, using nothing but the game's own
 *           elytra physics</td></tr>
 *   <tr><td>Firework</td>
 *       <td>steers with the movement keys like creative flight: the head turns
 *           silently where the keys point and a firework is kept burning to
 *           carry you there. Can hover in place under a rocket</td></tr>
 * </table>
 *
 * <p>Every mode steers through {@code Core.controls()} and {@code Core.rotations()},
 * opens the elytra the way the game itself does, and lights fireworks as item
 * uses. No mode edits velocity. Grim simulates an unboosted glide exactly and
 * only bounds a boosted one by the looks the client sent; each mode stays inside
 * whichever applies, and each mode's class says how.
 *
 * <p>Like Velocity, this file has no logic in it. Adding a mode is a constant,
 * a class in {@code elytrafly/} and two lines here.
 */
@ModuleInfo(name = "Elytra Fly", description = "Flies your elytra for you", category = "Movement")
public final class ElytraFlyModule extends ModalModule<ElytraFlyModule.Mode> {

    public enum Mode {
        BOUNCE,
        FIREWORK
    }

    // Field order is the order the panels appear in, so it matches the enum.
    private final BounceElytraFly bounce = attach(new BounceElytraFly());
    private final GroupSetting bouncePanel = panelFor(bounce);

    private final FireworkElytraFly firework = attach(new FireworkElytraFly());
    private final GroupSetting fireworkPanel = panelFor(firework);

    public ElytraFlyModule() {
        super(Mode.BOUNCE);
    }

    /** Bounce shows its speed, which is the thing worth watching while it builds. */
    @Override
    public String getDisplayInfo() {
        return getMode() == Mode.BOUNCE ? "Bounce " + bounce.status() : super.getDisplayInfo();
    }

    @Override
    protected void onDisable() {
        // Claims lapse on their own; held packets and a borrowed game option do not.
        bounce.stop();
    }
}
