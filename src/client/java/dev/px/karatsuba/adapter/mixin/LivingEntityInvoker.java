package dev.px.karatsuba.adapter.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The game's own jump velocity, which it keeps protected.
 *
 * <p>Asked rather than recomputed because it is three things multiplied
 * together &mdash; the jump-strength attribute, the block underfoot (honey) and
 * Jump Boost &mdash; and each is something a version can change.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {

    @Invoker("getJumpVelocity")
    float karatsuba$getJumpVelocity();
}
