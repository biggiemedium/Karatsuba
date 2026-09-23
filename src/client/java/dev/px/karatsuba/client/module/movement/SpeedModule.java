package dev.px.karatsuba.client.module.movement;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Moves the player faster than the game does.
 *
 * <p>The worked example for {@code MotionService}, and for why a motion claim is
 * an <b>intent</b> rather than a number. This module wants to go 30% faster; it
 * does not know what that is until the velocity it is scaling exists, and that
 * has not happened when the claim is filed &mdash; the game turns held keys into
 * acceleration later in the same tick. So the claim says <em>scale by 1.3</em>
 * and the arithmetic happens at the apply point.
 *
 * <p>It claims {@link dev.px.karatsuba.client.service.MotionService.Axis#HORIZONTAL}
 * only, which is the other half of the point. A glide or a hover claims the
 * vertical axis and the two coexist without either knowing about the other;
 * a second horizontal module would contend, and priority would settle it.
 *
 * <h2>Why it only acts while moving on the ground</h2>
 * Scaling a velocity of nearly zero achieves nothing, and scaling in the air
 * compounds every tick because there is no friction to damp it &mdash; a
 * constant multiplier applied mid-jump accelerates without limit and the server
 * rejects the result. Ground-only is the honest version of this module; the
 * airborne one is a different feature with a different shape.
 */
@ModuleInfo(name = "Speed", description = "Moves faster on the ground", category = "Movement")
public final class SpeedModule extends Module {

    private final NumberSetting<Double> multiplier = decimal("Multiplier", 1.3d, 1.05d, 3d)
            .step(0.05d)
            .describe("How much faster than the game's own speed");

    private final BooleanSetting onlySprinting = bool("Only Sprinting", true)
            .describe("Leave walking alone, so it reads as a fast sprint rather than a glitch");

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        if (!shouldBoost()) {
            return;
        }
        // NORMAL: a dash or a launch should be able to outrank a standing speed
        // boost, and both are the sort of thing that comes later.
        Services.motion().scaleHorizontal(this, multiplier.getDouble(), ClaimPriority.NORMAL);
    }

    private boolean shouldBoost() {
        if (!Players.present() || !Players.onGround() || !Inputs.anyMovement()) {
            return false;
        }
        return !onlySprinting.isOn() || Players.sprinting();
    }

    @Override
    public String getDisplayInfo() {
        return multiplier.displayValue() + "x";
    }
}
