package dev.px.karatsuba.adapter.mixin.world;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Who a firework is attached to, which 1.21.4 keeps private. Resolved on the
 * client in the rocket's first tick; {@code Players.fireworkBoosting} reads it.
 */
@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {

    @Accessor("shooter")
    LivingEntity karatsuba$getShooter();
}
