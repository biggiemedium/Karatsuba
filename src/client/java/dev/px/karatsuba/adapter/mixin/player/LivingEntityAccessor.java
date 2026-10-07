package dev.px.karatsuba.adapter.mixin.player;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where the server last put another living entity, which the game keeps
 * protected.
 *
 * <p>The game eases a remote entity toward the position the server sent over a
 * few ticks, so {@code getX()} is smoothed and behind. Core's prediction wants
 * the position the server sent: while {@code bodyTrackingIncrements} is above
 * zero the entity is still easing toward {@code server*}; at zero it has arrived
 * and {@code getX()} is that position.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("serverX")
    double karatsuba$getServerX();

    @Accessor("serverY")
    double karatsuba$getServerY();

    @Accessor("serverZ")
    double karatsuba$getServerZ();

    @Accessor("bodyTrackingIncrements")
    int karatsuba$getBodyTrackingIncrements();
}
