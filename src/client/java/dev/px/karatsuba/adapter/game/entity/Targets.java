package dev.px.karatsuba.adapter.game.entity;

import dev.px.core.math.Box;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.world.Voxels;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * What the crosshair is on.
 *
 * <p>The game answers this once a frame and leaves the result in a field, as one
 * of three shapes: nothing, a block, or an entity. Every module that wants it
 * otherwise writes the same null check, the same type test and the same cast
 * &mdash; and gets a raw {@code BlockPos} or {@code Entity} at the end of it,
 * which is exactly what a module should not be holding.
 *
 * <p>So this reads the field once and hands back Core types. {@link #block()} and
 * {@link #entity(float)} return null rather than empty values, because "looking
 * at nothing" is a real and common answer that a caller has to branch on anyway.
 *
 * <pre>{@code
 * Targets.Block looking = Targets.block();
 * if (looking != null) {
 *     Render.boxOutline(looking.getShape(), 1.5f, colour);
 * }
 * }</pre>
 *
 * <p><b>Game thread only.</b> The crosshair target is recomputed every frame.
 */
public final class Targets {

    /** What {@link #entityAlong} answers when the ray meets no entity. */
    public static final int NO_ENTITY = -1;

    private Targets() {
    }

    /** A block under the crosshair, in Core's vocabulary. */
    @Getter
    @RequiredArgsConstructor
    public static final class Block {

        /** Grid position of the block. */
        private final Vec3i position;

        /** Which face the ray entered through. */
        private final Direction face;

        /**
         * The block's outline in world space, already offset onto its position.
         *
         * <p>The real shape rather than a unit cube, so a slab is half height and
         * a fence is a post. This is the same box the vanilla wireframe traces,
         * which is why an overlay drawn to it lines up exactly.
         */
        private final Box shape;

        /** Registry id, for example {@code minecraft:oak_stairs}. */
        private final String blockId;
    }

    /** Where a look ray first meets a block. */
    @Getter
    @RequiredArgsConstructor
    public static final class Hit {

        /** The block the ray met. */
        private final Vec3i block;

        /** The face it entered through. */
        private final Direction face;

        /** The exact point on that face, in world space: what a click sends. */
        private final Vec3 point;
    }

    /**
     * Casts a look ray the way the game picks the block under the crosshair:
     * against outline shapes, through fluids, to the first block in the way.
     *
     * <p>For checking a click against a rotation that is not the camera's &mdash;
     * the one the server was last sent &mdash; and for sending the point the ray
     * actually lands on rather than one it would not.
     *
     * @return the first block within {@code range}, or null when the ray meets none
     */
    public static Hit raycast(Vec3 eye, Vec2 rotation, double range) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return null;
        }
        Vec3d from = Voxels.toVec3d(eye);
        Vec3d to = from.add(Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw()).multiply(range));
        BlockHitResult hit = world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return new Hit(Voxels.toVec3i(hit.getBlockPos()), Voxels.toCore(hit.getSide()), Voxels.toVec3(hit.getPos()));
    }

    /**
     * Finds the entity a look ray meets first, the way the game picks the entity
     * under the crosshair: any entity that can be hit, not spectating, and not
     * behind a block the ray meets sooner.
     *
     * <p>For acting along a rotation that is not the camera's &mdash; the one the
     * server was last sent &mdash; where the crosshair would name the wrong thing.
     *
     * @return the entity's id, or {@link #NO_ENTITY} when the ray meets none
     *         within {@code range}
     */
    public static int entityAlong(Vec3 eye, Vec2 rotation, double range) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return NO_ENTITY;
        }
        Vec3d from = Voxels.toVec3d(eye);
        Vec3d direction = Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw());
        Vec3d to = from.add(direction.multiply(range));
        double limit = range * range;
        BlockHitResult block = world.raycast(new RaycastContext(from, to,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
        if (block.getType() != HitResult.Type.MISS) {
            to = block.getPos();
            limit = to.squaredDistanceTo(from);
        }
        net.minecraft.util.math.Box swept = client.player.getBoundingBox()
                .stretch(direction.multiply(range)).expand(1d);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, from, to, swept,
                entity -> !entity.isSpectator() && entity.canHit(), limit);
        return hit == null ? NO_ENTITY : hit.getEntity().getId();
    }

    /** @return whether the crosshair is on a block */
    public static boolean hasBlock() {
        return hitResult() instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
    }

    /** @return whether the crosshair is on an entity */
    public static boolean hasEntity() {
        return hitResult() instanceof EntityHitResult;
    }

    /**
     * @return the block under the crosshair, or null
     *
     * <p>Null both when nothing is targeted and when the world has gone, so a
     * caller needs one check rather than three.
     */
    public static Block block() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !(hitResult() instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        return new Block(
                Voxels.toVec3i(pos),
                Voxels.toCore(hit.getSide()),
                outlineOf(world, pos, state),
                net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString());
    }

    /**
     * @param partialTicks the frame's interpolation; pass 1 outside a render pass
     * @return the entity under the crosshair as a snapshot, or null
     */
    public static GameEntity entity(float partialTicks) {
        if (!(hitResult() instanceof EntityHitResult hit)) {
            return null;
        }
        Entity found = hit.getEntity();
        return found == null ? null : GameEntity.of(found, partialTicks);
    }

    /**
     * The block's outline box, in world space.
     *
     * <p>Taken from the game's own outline shape so it matches the wireframe
     * exactly. An empty shape &mdash; which some blocks genuinely have &mdash;
     * falls back to the full cube, because an overlay of zero size reads as a bug
     * rather than as the block having no outline.
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

    private static HitResult hitResult() {
        return MinecraftClient.getInstance().crosshairTarget;
    }
}
