package dev.px.karatsuba.adapter.mixin.world;

import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Whether an arrow or trident has stuck in a block, which 1.21.4 keeps protected.
 * {@code ProjectileTracker} drops stuck ones: they are no longer flying anywhere.
 */
@Mixin(PersistentProjectileEntity.class)
public interface PersistentProjectileEntityInvoker {

    @Invoker("isInGround")
    boolean karatsuba$isInGround();
}
