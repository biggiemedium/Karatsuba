package dev.px.karatsuba.adapter.mixin.player;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.LookEvent;
import dev.px.karatsuba.adapter.movement.Displacement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The movement the local player's own move is about to make, for {@link Displacement},
 * and the mouse turning their head, as a {@link LookEvent}.
 *
 * <p>Runs for every entity, so both return straight away for anyone else: one
 * field read or one comparison.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    /** What the game multiplies the cursor's movement by to get degrees. */
    private static final float DEGREES_PER_UNIT = 0.15f;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3d karatsuba$displace(Vec3d movement, MovementType type) {
        return Displacement.apply((Entity) (Object) this, type, movement);
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void karatsuba$look(double cursorDeltaX, double cursorDeltaY, CallbackInfo callback) {
        if ((Object) this != MinecraftClient.getInstance().player) {
            return;
        }
        LookEvent event = new LookEvent((float) cursorDeltaX * DEGREES_PER_UNIT,
                (float) cursorDeltaY * DEGREES_PER_UNIT);
        if (Events.cancelled(event)) {
            callback.cancel();
        }
    }
}
