package dev.px.karatsuba.adapter.movement;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.timeline.PacketDescriber;
import dev.px.core.movement.timeline.PacketDescription;
import dev.px.core.movement.timeline.PacketKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * What a 1.21.4 packet means for movement, in Core's vocabulary.
 *
 * <p>The one piece of version knowledge the timeline recorder needs. Everything
 * downstream &mdash; corrections, reply matching, filters &mdash; works off the
 * {@link PacketKind} and the typed slots this fills in, so this is the only file
 * that changes when the protocol does.
 *
 * <h2>Names are literals</h2>
 * In a production jar these classes are called {@code class_2828} and so on, and a
 * recording full of those is unreadable. Every packet described here is named by a
 * string, so a recording reads the same in development and in the shipped mod.
 * Anything not listed falls back to Core's default, the runtime class name.
 *
 * <h2>Correlation keys</h2>
 * Given in the same form on both sides of an exchange &mdash; {@code teleport:<id>},
 * {@code keepalive:<id>}, {@code ping:<id>} &mdash; so {@code Timeline.correlated}
 * pairs a teleport with its confirm, or a ping with its pong, exactly.
 *
 * <h2>Threading</h2>
 * Called on the posting thread, which for an inbound packet is the Netty worker.
 * It reads fields off the packet and the local player's entity id, and touches
 * nothing else.
 */
public final class MinecraftPacketDescriber implements PacketDescriber {

    public static final MinecraftPacketDescriber INSTANCE = new MinecraftPacketDescriber();

    private MinecraftPacketDescriber() {
    }

    @Override
    public PacketDescription describe(Object packet) {
        PacketDescription outbound = describeOutbound(packet);
        return outbound != null ? outbound : describeInbound(packet);
    }

    // ------------------------------------------------------------ outbound

    private static PacketDescription describeOutbound(Object packet) {
        if (packet instanceof PlayerMoveC2SPacket move) {
            PacketDescription description = PacketDescription.of(moveName(move), PacketKind.MOVEMENT)
                    .withOnGround(move.isOnGround())
                    .with("horizontalCollision", move.horizontalCollision());
            if (move.changesPosition()) {
                description = description.withPosition(Vec3.of(move.getX(0d), move.getY(0d), move.getZ(0d)));
            }
            if (move.changesLook()) {
                description = description.withRotation(Vec2.rotation(move.getYaw(0f), move.getPitch(0f)));
            }
            return description;
        }
        if (packet instanceof VehicleMoveC2SPacket move) {
            return PacketDescription.of("VehicleMoveC2SPacket", PacketKind.MOVEMENT)
                    .withPosition(vec(move.position()))
                    .withRotation(Vec2.rotation(move.yaw(), move.pitch()))
                    .withOnGround(move.onGround());
        }
        if (packet instanceof PlayerInputC2SPacket input) {
            var keys = input.input();
            return PacketDescription.of("PlayerInputC2SPacket", PacketKind.ACTION)
                    .with("forward", keys.forward())
                    .with("backward", keys.backward())
                    .with("left", keys.left())
                    .with("right", keys.right())
                    .with("jump", keys.jump())
                    .with("sneak", keys.sneak())
                    .with("sprint", keys.sprint());
        }
        if (packet instanceof ClientCommandC2SPacket command) {
            return PacketDescription.of("ClientCommandC2SPacket", PacketKind.ACTION)
                    .with("mode", command.getMode().name());
        }
        if (packet instanceof PlayerActionC2SPacket action) {
            return PacketDescription.of("PlayerActionC2SPacket", PacketKind.ACTION)
                    .with("action", action.getAction().name())
                    .with("pos", action.getPos().toShortString())
                    .with("sequence", action.getSequence());
        }
        if (packet instanceof HandSwingC2SPacket swing) {
            return PacketDescription.of("HandSwingC2SPacket", PacketKind.ACTION)
                    .with("hand", swing.getHand().name());
        }
        if (packet instanceof PlayerInteractEntityC2SPacket interact) {
            return PacketDescription.of("PlayerInteractEntityC2SPacket", PacketKind.ACTION)
                    .with("sneaking", interact.isPlayerSneaking());
        }
        if (packet instanceof PlayerInteractBlockC2SPacket interact) {
            return PacketDescription.of("PlayerInteractBlockC2SPacket", PacketKind.ACTION)
                    .with("hand", interact.getHand().name())
                    .with("pos", interact.getBlockHitResult().getBlockPos().toShortString())
                    .with("sequence", interact.getSequence());
        }
        if (packet instanceof PlayerInteractItemC2SPacket interact) {
            return PacketDescription.of("PlayerInteractItemC2SPacket", PacketKind.ACTION)
                    .withRotation(Vec2.rotation(interact.getYaw(), interact.getPitch()))
                    .with("hand", interact.getHand().name())
                    .with("sequence", interact.getSequence());
        }
        if (packet instanceof UpdateSelectedSlotC2SPacket slot) {
            return PacketDescription.of("UpdateSelectedSlotC2SPacket", PacketKind.ACTION)
                    .with("slot", slot.getSelectedSlot());
        }
        if (packet instanceof TeleportConfirmC2SPacket confirm) {
            return PacketDescription.of("TeleportConfirmC2SPacket", PacketKind.TRANSACTION)
                    .withCorrelationKey("teleport:" + confirm.getTeleportId());
        }
        if (packet instanceof KeepAliveC2SPacket keepAlive) {
            return PacketDescription.of("KeepAliveC2SPacket", PacketKind.TRANSACTION)
                    .withCorrelationKey("keepalive:" + keepAlive.getId());
        }
        if (packet instanceof CommonPongC2SPacket pong) {
            return PacketDescription.of("CommonPongC2SPacket", PacketKind.TRANSACTION)
                    .withCorrelationKey("ping:" + pong.getParameter());
        }
        return null;
    }

    /** The subclass says which fields are real, so it is part of the name. */
    private static String moveName(PlayerMoveC2SPacket move) {
        if (move.changesPosition() && move.changesLook()) {
            return "PlayerMoveC2SPacket.Full";
        }
        if (move.changesPosition()) {
            return "PlayerMoveC2SPacket.PositionAndOnGround";
        }
        if (move.changesLook()) {
            return "PlayerMoveC2SPacket.LookAndOnGround";
        }
        return "PlayerMoveC2SPacket.OnGroundOnly";
    }

    // ------------------------------------------------------------- inbound

    private static PacketDescription describeInbound(Object packet) {
        if (packet instanceof PlayerPositionLookS2CPacket teleport) {
            PlayerPosition change = teleport.change();
            return PacketDescription.of("PlayerPositionLookS2CPacket", PacketKind.TELEPORT)
                    .withPosition(vec(change.position()))
                    .withVelocity(vec(change.deltaMovement()))
                    .withRotation(Vec2.rotation(change.yaw(), change.pitch()))
                    .withCorrelationKey("teleport:" + teleport.teleportId())
                    .with("relative", teleport.relatives().toString());
        }
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
            // Only the player's own is a correction. The same packet moves every
            // entity the server pushes, and those are traffic, not a verdict.
            boolean self = isSelf(velocity.getEntityId());
            return PacketDescription.of("EntityVelocityUpdateS2CPacket", self ? PacketKind.VELOCITY : PacketKind.OTHER)
                    .withVelocity(Vec3.of(velocity.getVelocityX(), velocity.getVelocityY(), velocity.getVelocityZ()))
                    .with("entity", velocity.getEntityId());
        }
        if (packet instanceof ExplosionS2CPacket explosion) {
            // No knockback means the player was out of range; nothing was overruled.
            Optional<Vec3d> push = explosion.playerKnockback();
            PacketDescription description = PacketDescription.of("ExplosionS2CPacket",
                            push.isPresent() ? PacketKind.EXPLOSION : PacketKind.OTHER)
                    .withPosition(vec(explosion.center()));
            return push.map(knockback -> description.withVelocity(vec(knockback))).orElse(description);
        }
        if (packet instanceof EntityDamageS2CPacket damage) {
            return PacketDescription.of("EntityDamageS2CPacket", PacketKind.OTHER)
                    .with("entity", damage.entityId())
                    .with("self", isSelf(damage.entityId()));
        }
        if (packet instanceof HealthUpdateS2CPacket health) {
            return PacketDescription.of("HealthUpdateS2CPacket", PacketKind.OTHER)
                    .with("health", health.getHealth());
        }
        if (packet instanceof PlayerAbilitiesS2CPacket abilities) {
            return PacketDescription.of("PlayerAbilitiesS2CPacket", PacketKind.ABILITIES)
                    .with("flying", abilities.isFlying())
                    .with("allowFlying", abilities.allowFlying())
                    .with("flySpeed", abilities.getFlySpeed())
                    .with("walkSpeed", abilities.getWalkSpeed());
        }
        if (packet instanceof EntityStatusEffectS2CPacket effect) {
            return PacketDescription.of("EntityStatusEffectS2CPacket", kindFor(effect.getEntityId(), PacketKind.EFFECT))
                    .with("entity", effect.getEntityId())
                    .with("effect", effect.getEffectId().getIdAsString())
                    .with("amplifier", effect.getAmplifier())
                    .with("duration", effect.getDuration());
        }
        if (packet instanceof RemoveEntityStatusEffectS2CPacket effect) {
            return PacketDescription.of("RemoveEntityStatusEffectS2CPacket", kindFor(effect.entityId(), PacketKind.EFFECT))
                    .with("entity", effect.entityId())
                    .with("effect", effect.effect().getIdAsString());
        }
        if (packet instanceof EntityAttributesS2CPacket attributes) {
            return PacketDescription.of("EntityAttributesS2CPacket", kindFor(attributes.getEntityId(), PacketKind.ATTRIBUTE))
                    .with("entity", attributes.getEntityId())
                    .with("count", attributes.getEntries().size());
        }
        if (packet instanceof BlockUpdateS2CPacket block) {
            return PacketDescription.of("BlockUpdateS2CPacket", PacketKind.WORLD_STATE)
                    .with("pos", block.getPos().toShortString());
        }
        if (packet instanceof ChunkDeltaUpdateS2CPacket) {
            return PacketDescription.of("ChunkDeltaUpdateS2CPacket", PacketKind.WORLD_STATE);
        }
        if (packet instanceof ChunkDataS2CPacket chunk) {
            return PacketDescription.of("ChunkDataS2CPacket", PacketKind.WORLD_STATE)
                    .with("chunk", chunk.getChunkX() + "," + chunk.getChunkZ());
        }
        if (packet instanceof UnloadChunkS2CPacket unload) {
            return PacketDescription.of("UnloadChunkS2CPacket", PacketKind.WORLD_STATE)
                    .with("chunk", unload.pos().x + "," + unload.pos().z);
        }
        if (packet instanceof PlayerRespawnS2CPacket) {
            return PacketDescription.of("PlayerRespawnS2CPacket", PacketKind.RESPAWN);
        }
        if (packet instanceof GameJoinS2CPacket join) {
            return PacketDescription.of("GameJoinS2CPacket", PacketKind.RESPAWN)
                    .with("entity", join.playerEntityId());
        }
        if (packet instanceof KeepAliveS2CPacket keepAlive) {
            return PacketDescription.of("KeepAliveS2CPacket", PacketKind.TRANSACTION)
                    .withCorrelationKey("keepalive:" + keepAlive.getId());
        }
        if (packet instanceof CommonPingS2CPacket ping) {
            return PacketDescription.of("CommonPingS2CPacket", PacketKind.TRANSACTION)
                    .withCorrelationKey("ping:" + ping.getParameter());
        }
        return null;
    }

    // ------------------------------------------------------------ helpers

    /** An effect or attribute is movement-relevant only when it is the player's own. */
    private static PacketKind kindFor(int entityId, PacketKind ifSelf) {
        return isSelf(entityId) ? ifSelf : PacketKind.OTHER;
    }

    private static boolean isSelf(int entityId) {
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        return self != null && self.getId() == entityId;
    }

    private static Vec3 vec(Vec3d vector) {
        return Vec3.of(vector.x, vector.y, vector.z);
    }
}
