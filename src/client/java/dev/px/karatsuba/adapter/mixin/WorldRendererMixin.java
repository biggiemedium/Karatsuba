package dev.px.karatsuba.adapter.mixin;

import dev.px.core.math.Box;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.BlockOutlineEvent;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The black wireframe around the targeted block.
 *
 * <p>{@code drawBlockOutline} is the inner draw, which is the useful injection
 * point of the two: the outer {@code renderTargetBlockOutline} knows only that
 * <em>an</em> outline is due, while this one has been handed the position and the
 * state. So one injection both reports what is targeted and offers the chance to
 * suppress the vanilla look.
 *
 * <p>Fires only when the game would have drawn an outline: looking at a block,
 * not in spectator, with the setting on. A module that wants the target
 * regardless asks {@code adapter.game.Targets}.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void karatsuba$drawBlockOutline(MatrixStack matrices, VertexConsumer vertices, Entity entity,
                                            double cameraX, double cameraY, double cameraZ,
                                            BlockPos pos, BlockState state, int color,
                                            CallbackInfo callback) {
        // Before building anything: resolving the outline shape is wasted work for
        // a client that has not booted, and this runs once per frame.
        ClientWorld world = MinecraftClient.getInstance().world;
        if (!Events.isArmed() || world == null) {
            return;
        }
        BlockOutlineEvent event = new BlockOutlineEvent(Voxels.toVec3i(pos), outlineOf(world, pos, state));
        if (Events.cancelled(event)) {
            callback.cancel();
        }
    }

    /**
     * The block's outline box in world space.
     *
     * <p>Built from the state's own shape, so a slab is half height and a fence is
     * a post &mdash; the same box the vanilla wireframe traces, which is what lets
     * a replacement overlay line up exactly. A shape that is empty, which some
     * blocks genuinely have, falls back to the full cube rather than to nothing.
     */
    private static Box outlineOf(ClientWorld world, BlockPos pos, BlockState state) {
        var shape = state.getOutlineShape(world, pos);
        if (shape.isEmpty()) {
            return Box.of(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1d, pos.getY() + 1d, pos.getZ() + 1d);
        }
        net.minecraft.util.math.Box bounds = shape.getBoundingBox();
        return Box.of(
                pos.getX() + bounds.minX, pos.getY() + bounds.minY, pos.getZ() + bounds.minZ,
                pos.getX() + bounds.maxX, pos.getY() + bounds.maxY, pos.getZ() + bounds.maxZ);
    }
}
