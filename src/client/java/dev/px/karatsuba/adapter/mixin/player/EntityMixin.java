package dev.px.karatsuba.adapter.mixin.player;

import dev.px.karatsuba.adapter.movement.Displacement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The movement the local player's own move is about to make, for {@link Displacement}.
 *
 * <p>Runs for every entity's move, so the first thing {@code Displacement.apply}
 * does is return when nothing is pending: one field read for everyone else.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3d karatsuba$displace(Vec3d movement, MovementType type) {
        return Displacement.apply((Entity) (Object) this, type, movement);
    }
}
