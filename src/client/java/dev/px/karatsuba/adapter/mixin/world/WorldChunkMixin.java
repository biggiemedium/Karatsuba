package dev.px.karatsuba.adapter.mixin.world;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.world.BlockChangeEvent;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change in a loaded chunk, as {@link BlockChangeEvent}.
 *
 * <p>{@code WorldChunk.setBlockState} is where every change lands on the client,
 * whichever packet carried it (one block, a section's worth, a prediction of the
 * player's own), so one injection hears all of them. It returns the state it
 * replaced, or null when nothing changed. The same class serves the integrated
 * server in singleplayer, which is filtered out.
 */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {

    @Shadow
    @Final
    World world;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void karatsuba$setBlockState(BlockPos pos, BlockState state, boolean moved,
                                         CallbackInfoReturnable<BlockState> callback) {
        BlockState previous = callback.getReturnValue();
        if (previous == null || !world.isClient() || !Events.isArmed() || previous.getBlock() == state.getBlock()) {
            return;
        }
        Events.post(new BlockChangeEvent(Voxels.toVec3i(pos),
                Registries.BLOCK.getId(previous.getBlock()).toString(),
                Registries.BLOCK.getId(state.getBlock()).toString()));
    }
}
