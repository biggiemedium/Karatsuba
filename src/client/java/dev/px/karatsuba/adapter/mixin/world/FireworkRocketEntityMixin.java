package dev.px.karatsuba.adapter.mixin.world;

import dev.px.core.math.Vec2;
import dev.px.karatsuba.adapter.game.player.Rotations;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A firework boosting the local player's glide pushes along the look the server
 * was sent, not the camera's.
 *
 * <p>The boost is applied in the rocket's own tick ({@code FireworkRocketEntity.tick}),
 * outside the player's, where a silent rotation is no longer swapped in. Left
 * alone it pushes where the camera looks while the server, and Grim, were told
 * another look: Grim 2.3.73 bounds a boosted glide by the current and last
 * packet looks ({@code UncertaintyHandler.tickFireworksBox}), so a camera turned
 * away from them is a simulation failure. With no silent rotation held, the
 * camera's look is the one that was sent, and the game's own call stands.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d karatsuba$sentLook(LivingEntity shooter) {
        if (shooter == MinecraftClient.getInstance().player && Rotations.sentSilent()) {
            Vec2 sent = MotionBridge.lastSentRotation();
            if (sent != null) {
                return Vec3d.fromPolar(sent.getPitch(), sent.getYaw());
            }
        }
        return shooter.getRotationVector();
    }
}
