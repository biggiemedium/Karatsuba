package dev.px.karatsuba.adapter.mixin.render;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.NameTagEvent;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla name label over an entity.
 *
 * <p>Since 1.21.2 a renderer decides the label while filling in the render state:
 * {@code displayName} is set when there should be one and left null when not, and
 * the draw that follows only labels a state that has one. So clearing it here, at
 * the end of the base method every renderer calls, removes the label for every
 * kind of entity without touching each renderer's own {@code hasLabel} override.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void karatsuba$updateRenderState(T entity, S state, float tickDelta, CallbackInfo callback) {
        if (state.displayName != null
                && Events.cancelled(new NameTagEvent(entity.getId(), entity instanceof PlayerEntity,
                        Vec3.of(entity.getX(), entity.getY(), entity.getZ())))) {
            state.displayName = null;
        }
    }
}
