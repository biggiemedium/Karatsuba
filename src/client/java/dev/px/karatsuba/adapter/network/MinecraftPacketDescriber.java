package dev.px.karatsuba.adapter.network;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.network.packet.PacketDescriber;
import dev.px.core.network.packet.PacketDescription;
import dev.px.core.network.packet.PacketKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.BrandCustomPayload;
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
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
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
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;

/**
 * What a 1.21.4 packet means, in Core's vocabulary.
 *
 * <p>The one piece of packet knowledge Core needs. Everything downstream works
 * off the {@link MinecraftPacketKind} kind, the roles and the typed slots this
 * fills in, so this is the only file that changes when the protocol does.
 *
 * <p>Installed on {@code Core.network()}, which the timeline and Core's network
 * services all read through.
 *
 * <h2>Roles</h2>
 * Core's services never look at a kind; they read roles, and this is where each
 * one is given:
 *
 * <table>
 *   <caption>Who reads what</caption>
 *   <tr><th>Role</th><th>Given to</th><th>Read by</th></tr>
 *   <tr><td>{@code withWorldAge}</td><td>{@code WorldTimeUpdateS2CPacket}, the
 *       server's clock, sent once a second</td><td>{@code Core.tps()}</td></tr>
 *   <tr><td>{@code withLatency}</td><td>{@code PlayerListS2CPacket}, the local
 *       player's own tab-list entry</td><td>{@code Core.lag()}</td></tr>
 *   <tr><td>{@code withBrand}</td><td>{@code CustomPayloadS2CPacket} carrying
 *       {@code minecraft:brand}</td><td>{@code Core.server()}</td></tr>
 *   <tr><td>{@code withTransaction}</td><td>{@code CommonPingS2CPacket}</td>
 *       <td>{@code Core.anticheat()}</td></tr>
 *   <tr><td>{@code asCorrection}</td><td>teleports, the player's own velocity,
 *       explosions that push</td><td>{@code Core.timeline()}</td></tr>
 * </table>
 *
 * <p>The ping is the transaction because since 1.17 it is the packet a server
 * sends purely to make the client answer, and vanilla never sends it on its own:
 * only a plugin timing the client does. The keep-alive is <em>not</em> one. It
 * arrives on the server's own fixed schedule, and counting it would make every
 * server look like it runs an anticheat.
 *
 * <p>Channels the server registers are not a packet role here. Fabric decodes
 * {@code minecraft:register} itself and announces it, so
 * {@link NetworkBridge} reports them from Fabric's event instead.
 *
 * <h2>Names are literals</h2>
 * In a production jar these classes are called {@code class_2828} and so on, and a
 * recording full of those is unreadable. Every packet described here is named by a
 * string, so a recording reads the same in development and in the shipped mod.
 * Anything not listed falls back to Core's default, the runtime class name.
 *
 * <h2>Correlation keys</h2>
 * Given in the same form on both sides of an exchange &mdash; {@code teleport:<id>},
 * {@code keepalive:<id>}, {@code transaction:<id>} &mdash; so {@code Timeline.correlated}
 * pairs a teleport with its confirm, or a ping with its pong, exactly. The ping's
 * key is the one {@code withTransaction} gives it, so the pong uses that form.
 *
 * <h2>Threading</h2>
 * Called on the posting thread, which for an inbound packet is the Netty worker.
 * It reads fields off the packet, the local player's entity id and the session's
 * profile id, and touches nothing else.
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
            PacketDescription description = PacketDescription.of(moveName(move), MinecraftPacketKind.MOVEMENT)
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
            return PacketDescription.of("VehicleMoveC2SPacket", MinecraftPacketKind.MOVEMENT)
                    .withPosition(vec(move.position()))
                    .withRotation(Vec2.rotation(move.yaw(), move.pitch()))
                    .withOnGround(move.onGround());
        }
        if (packet instanceof PlayerInputC2SPacket input) {
            var keys = input.input();
            return PacketDescription.of("PlayerInputC2SPacket", MinecraftPacketKind.ACTION)
                    .with("forward", keys.forward())
                    .with("backward", keys.backward())
                    .with("left", keys.left())
                    .with("right", keys.right())
                    .with("jump", keys.jump())
                    .with("sneak", keys.sneak())
                    .with("sprint", keys.sprint());
        }
        if (packet instanceof ClientCommandC2SPacket command) {
            return PacketDescription.of("ClientCommandC2SPacket", MinecraftPacketKind.ACTION)
                    .with("mode", command.getMode().name());
        }
        if (packet instanceof PlayerActionC2SPacket action) {
            return PacketDescription.of("PlayerActionC2SPacket", MinecraftPacketKind.ACTION)
                    .with("action", action.getAction().name())
                    .with("pos", action.getPos().toShortString())
                    .with("sequence", action.getSequence());
        }
        if (packet instanceof HandSwingC2SPacket swing) {
            return PacketDescription.of("HandSwingC2SPacket", MinecraftPacketKind.ACTION)
                    .with("hand", swing.getHand().name());
        }
        if (packet instanceof PlayerInteractEntityC2SPacket interact) {
            return PacketDescription.of("PlayerInteractEntityC2SPacket", MinecraftPacketKind.ACTION)
                    .with("sneaking", interact.isPlayerSneaking());
        }
        if (packet instanceof PlayerInteractBlockC2SPacket interact) {
            return PacketDescription.of("PlayerInteractBlockC2SPacket", MinecraftPacketKind.ACTION)
                    .with("hand", interact.getHand().name())
                    .with("pos", interact.getBlockHitResult().getBlockPos().toShortString())
                    .with("sequence", interact.getSequence());
        }
        if (packet instanceof PlayerInteractItemC2SPacket interact) {
            return PacketDescription.of("PlayerInteractItemC2SPacket", MinecraftPacketKind.ACTION)
                    .withRotation(Vec2.rotation(interact.getYaw(), interact.getPitch()))
                    .with("hand", interact.getHand().name())
                    .with("sequence", interact.getSequence());
        }
        if (packet instanceof UpdateSelectedSlotC2SPacket slot) {
            return PacketDescription.of("UpdateSelectedSlotC2SPacket", MinecraftPacketKind.ACTION)
                    .with("slot", slot.getSelectedSlot());
        }
        if (packet instanceof TeleportConfirmC2SPacket confirm) {
            return PacketDescription.of("TeleportConfirmC2SPacket", MinecraftPacketKind.TRANSACTION)
                    .withCorrelationKey("teleport:" + confirm.getTeleportId());
        }
        if (packet instanceof KeepAliveC2SPacket keepAlive) {
            return PacketDescription.of("KeepAliveC2SPacket", MinecraftPacketKind.TRANSACTION)
                    .withCorrelationKey("keepalive:" + keepAlive.getId());
        }
        if (packet instanceof CommonPongC2SPacket pong) {
            // The same key withTransaction gave the ping this answers.
            return PacketDescription.of("CommonPongC2SPacket", MinecraftPacketKind.TRANSACTION)
                    .withCorrelationKey("transaction:" + pong.getParameter());
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
            return PacketDescription.of("PlayerPositionLookS2CPacket", MinecraftPacketKind.TELEPORT)
                    .asCorrection()
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
            PacketDescription description = PacketDescription.of("EntityVelocityUpdateS2CPacket",
                            self ? MinecraftPacketKind.VELOCITY : PacketKind.OTHER)
                    .withVelocity(Vec3.of(velocity.getVelocityX(), velocity.getVelocityY(), velocity.getVelocityZ()))
                    .with("entity", velocity.getEntityId());
            return self ? description.asCorrection() : description;
        }
        if (packet instanceof ExplosionS2CPacket explosion) {
            // No knockback means the player was out of range; nothing was overruled.
            Optional<Vec3d> push = explosion.playerKnockback();
            PacketDescription description = PacketDescription.of("ExplosionS2CPacket",
                            push.isPresent() ? MinecraftPacketKind.EXPLOSION : PacketKind.OTHER)
                    .withPosition(vec(explosion.center()));
            return push.map(knockback -> description.asCorrection().withVelocity(vec(knockback))).orElse(description);
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
            return PacketDescription.of("PlayerAbilitiesS2CPacket", MinecraftPacketKind.ABILITIES)
                    .with("flying", abilities.isFlying())
                    .with("allowFlying", abilities.allowFlying())
                    .with("flySpeed", abilities.getFlySpeed())
                    .with("walkSpeed", abilities.getWalkSpeed());
        }
        if (packet instanceof EntityStatusEffectS2CPacket effect) {
            return PacketDescription.of("EntityStatusEffectS2CPacket", kindFor(effect.getEntityId(), MinecraftPacketKind.EFFECT))
                    .with("entity", effect.getEntityId())
                    .with("effect", effect.getEffectId().getIdAsString())
                    .with("amplifier", effect.getAmplifier())
                    .with("duration", effect.getDuration());
        }
        if (packet instanceof RemoveEntityStatusEffectS2CPacket effect) {
            return PacketDescription.of("RemoveEntityStatusEffectS2CPacket", kindFor(effect.entityId(), MinecraftPacketKind.EFFECT))
                    .with("entity", effect.entityId())
                    .with("effect", effect.effect().getIdAsString());
        }
        if (packet instanceof EntityAttributesS2CPacket attributes) {
            return PacketDescription.of("EntityAttributesS2CPacket", kindFor(attributes.getEntityId(), MinecraftPacketKind.ATTRIBUTE))
                    .with("entity", attributes.getEntityId())
                    .with("count", attributes.getEntries().size());
        }
        if (packet instanceof BlockUpdateS2CPacket block) {
            return PacketDescription.of("BlockUpdateS2CPacket", MinecraftPacketKind.WORLD_STATE)
                    .with("pos", block.getPos().toShortString());
        }
        if (packet instanceof ChunkDeltaUpdateS2CPacket) {
            return PacketDescription.of("ChunkDeltaUpdateS2CPacket", MinecraftPacketKind.WORLD_STATE);
        }
        if (packet instanceof ChunkDataS2CPacket chunk) {
            return PacketDescription.of("ChunkDataS2CPacket", MinecraftPacketKind.WORLD_STATE)
                    .with("chunk", chunk.getChunkX() + "," + chunk.getChunkZ());
        }
        if (packet instanceof UnloadChunkS2CPacket unload) {
            return PacketDescription.of("UnloadChunkS2CPacket", MinecraftPacketKind.WORLD_STATE)
                    .with("chunk", unload.pos().x + "," + unload.pos().z);
        }
        if (packet instanceof PlayerRespawnS2CPacket) {
            return PacketDescription.of("PlayerRespawnS2CPacket", MinecraftPacketKind.RESPAWN);
        }
        if (packet instanceof GameJoinS2CPacket join) {
            return PacketDescription.of("GameJoinS2CPacket", MinecraftPacketKind.RESPAWN)
                    .with("entity", join.playerEntityId());
        }
        if (packet instanceof KeepAliveS2CPacket keepAlive) {
            return PacketDescription.of("KeepAliveS2CPacket", MinecraftPacketKind.TRANSACTION)
                    .withCorrelationKey("keepalive:" + keepAlive.getId());
        }
        if (packet instanceof CommonPingS2CPacket ping) {
            // Also keys it as "transaction:<id>", which the pong above matches.
            return PacketDescription.of("CommonPingS2CPacket", MinecraftPacketKind.TRANSACTION)
                    .withTransaction(ping.getParameter());
        }
        if (packet instanceof WorldTimeUpdateS2CPacket time) {
            // time() is the world's total age; timeOfDay() can stand still under
            // the daylight gamerule, and would read as a frozen server.
            return PacketDescription.of("WorldTimeUpdateS2CPacket", MinecraftPacketKind.TIME)
                    .withWorldAge(time.time());
        }
        if (packet instanceof CustomPayloadS2CPacket payload) {
            // Payloads arrive decoded since 1.20.2, so the brand is a field read
            // rather than a buffer to copy before the game reads it.
            PacketDescription description = PacketDescription.of("CustomPayloadS2CPacket", MinecraftPacketKind.PAYLOAD)
                    .with("channel", payload.payload().getId().id().toString());
            return payload.payload() instanceof BrandCustomPayload brand && brand.brand() != null
                    ? description.withBrand(brand.brand())
                    : description;
        }
        if (packet instanceof PlayerListS2CPacket list) {
            return describePlayerList(list);
        }
        return null;
    }

    /**
     * The tab list, with the local player's latency when this update carries it.
     *
     * <p>The latency is the server's own measurement of its keep-alive round trip,
     * which is the only ping a client can honestly report. Only an update listing
     * {@code UPDATE_LATENCY} carries one: the other actions leave the field at
     * zero, which would read as a perfect connection.
     *
     * <p>The player is matched by the session's profile id rather than the
     * entity, because the first list arrives before the player entity exists.
     */
    private static PacketDescription describePlayerList(PlayerListS2CPacket list) {
        PacketDescription description = PacketDescription.of("PlayerListS2CPacket", MinecraftPacketKind.PLAYER_LIST)
                .with("entries", list.getEntries().size());
        if (!list.getActions().contains(PlayerListS2CPacket.Action.UPDATE_LATENCY)) {
            return description;
        }
        UUID self = MinecraftClient.getInstance().getSession().getUuidOrNull();
        if (self == null) {
            return description;
        }
        for (PlayerListS2CPacket.Entry entry : list.getEntries()) {
            if (self.equals(entry.profileId())) {
                return description.withLatency(entry.latency());
            }
        }
        return description;
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
