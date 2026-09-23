package dev.px.karatsuba.adapter.mixin;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.CameraEvent;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the camera ends up each frame.
 *
 * <p>At TAIL rather than HEAD, so the event carries the position the game
 * actually resolved &mdash; eye height, sneaking, riding, third-person distance
 * and the wall-clipping raycast have all been applied by then. A handler is
 * changing a finished answer rather than guessing at an unfinished one.
 *
 * <p>{@code setPos} and {@code setRotation} are protected, which is why this
 * mixes into {@code Camera} itself rather than living behind an accessor: a
 * shadowed protected member is reachable from the class it belongs to and nowhere
 * else, and that is the right amount of access for this.
 *
 * <p>The write-back is guarded by {@code isModified()}, so a frame nobody asked
 * to change costs one event post and nothing else.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract net.minecraft.util.math.Vec3d getPos();

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void karatsuba$update(BlockView area, Entity focused, boolean thirdPerson,
                                  boolean inverseView, float tickDelta, CallbackInfo callback) {
        if (!Events.isArmed()) {
            return;
        }
        net.minecraft.util.math.Vec3d position = getPos();

        CameraEvent event = new CameraEvent(
                tickDelta,
                thirdPerson,
                Vec3.of(position.x, position.y, position.z),
                Vec2.rotation(getYaw(), getPitch()));

        Events.post(event);

        if (!event.isModified()) {
            return;
        }
        Vec3 moved = event.getPosition();
        Vec2 turned = event.getRotation();
        setPos(moved.getX(), moved.getY(), moved.getZ());
        setRotation(turned.getYaw(), turned.getPitch());
    }
}
