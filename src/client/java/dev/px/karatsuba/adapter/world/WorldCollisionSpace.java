package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.movement.simulation.CollisionSpace;
import dev.px.core.movement.simulation.Simulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What Core's {@link Simulation} is allowed to ask about a Minecraft world.
 *
 * <p>One method, the same shape as {@link dev.px.core.util.spatial.PathSpace}:
 * sweeping a hitbox against obstacles is arithmetic and lives in Core, while
 * knowing that the block at these coordinates is a fence rather than a slab is
 * the adapter's business.
 *
 * <h2>Real shapes, because that is where the accuracy is</h2>
 * Core's own notes are blunt about this: <em>collision shape fidelity matters
 * more for accuracy than any constant we ship</em>. An implementation that
 * answers with a full cube per solid block models a slab as a block, a fence as
 * waist-high instead of 1.5 tall, a carpet as a step and every stair as a solid
 * lump &mdash; and a prediction then walks the player up things they would bump
 * into and through things they would stand on.
 *
 * <p>So this does not iterate blocks and build boxes. It asks
 * {@code getBlockCollisions}, which is the query the game's own movement code
 * uses, and unpacks the shapes it hands back. Non-cube blocks are therefore
 * exactly right for free, and so is anything a future version changes.
 *
 * <h2>Stateless, so it survives a world change</h2>
 * Installed once at boot and never replaced, so it holds no world reference
 * &mdash; the world is read per call and a null one answers "nothing solid".
 * Holding one would mean re-installing on every join and leaving a dead
 * {@code ClientWorld} reachable in between.
 *
 * <p><b>Game thread only.</b> Chunk access is not safe off it, and Core calls
 * this once per simulated tick from wherever the caller is running.
 */
public final class WorldCollisionSpace implements CollisionSpace {

    /** Stateless, so one instance serves the whole session. */
    public static final WorldCollisionSpace INSTANCE = new WorldCollisionSpace();

    private WorldCollisionSpace() {
    }

    /**
     * Every solid box intersecting the region.
     *
     * <p>The entity handed to {@code getBlockCollisions} is the local player, so
     * blocks whose collision depends on who is touching them answer for the thing
     * being simulated. Null is legal there and would be the honest argument for
     * predicting something else, but the overwhelmingly common caller is "where
     * will I end up".
     */
    @Override
    public List<Box> boxesIn(Box region) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return Collections.emptyList();
        }

        List<Box> boxes = new ArrayList<>();
        for (VoxelShape shape : world.getBlockCollisions(MinecraftClient.getInstance().player,
                Voxels.toGameBox(region))) {
            if (shape.isEmpty()) {
                continue;
            }
            // A shape is one or more boxes -- a stair is two, a fence is several --
            // and they arrive already offset into world space.
            for (net.minecraft.util.math.Box part : shape.getBoundingBoxes()) {
                boxes.add(Voxels.toBox(part));
            }
        }
        return boxes;
    }

    /**
     * How slippery the surface underfoot is.
     *
     * <p>Core asks for the block below the hitbox and only while on the ground, so
     * this is a single block lookup rather than a search. Ice is 0.98, slime 0.8,
     * everything else 0.6 &mdash; the values come from the block itself, so a
     * modded block with its own friction is right without this knowing about it.
     */
    @Override
    public double slipperinessAt(Vec3 position) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return CollisionSpace.super.slipperinessAt(position);
        }
        BlockPos pos = BlockPos.ofFloored(position.getX(), position.getY(), position.getZ());
        return world.getBlockState(pos).getBlock().getSlipperiness();
    }
}
