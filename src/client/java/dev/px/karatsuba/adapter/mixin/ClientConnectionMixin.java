package dev.px.karatsuba.adapter.mixin;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.ClientCommandEvent;
import dev.px.karatsuba.adapter.event.game.MovementPacketEvent;
import dev.px.karatsuba.adapter.event.game.PacketEvent;
import dev.px.karatsuba.adapter.event.game.ScreenCloseEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every packet, in both directions.
 *
 * <p>Two injections, chosen because they are the funnels rather than one of the
 * several convenience overloads above them. {@code send(Packet)} and
 * {@code send(Packet, PacketCallbacks)} both delegate to the three-argument form,
 * so injecting there sees each packet exactly once; {@code channelRead0} is where
 * everything inbound arrives before the game has looked at it.
 *
 * <h2>Threading</h2>
 * <b>{@code channelRead0} runs on a Netty worker</b>, so {@link PacketEvent.Receive}
 * is posted off the game thread. That is not a mistake that can be fixed here:
 * moving the post to the game thread would mean the packet has already been
 * handled by the time a subscriber sees it, which defeats the point of a
 * cancellable event. The event says so, loudly.
 *
 * <p>Sending happens on whichever thread called, which for anything a module
 * causes is the game thread.
 *
 * <h2>Why a semantic event rides along</h2>
 * {@link ScreenCloseEvent} is posted from the same place, because working out
 * whether a container close is the player's own needs a sync id comparison that
 * would otherwise have to happen in a module &mdash; and a module that compares
 * sync ids is a module holding a screen handler. Doing it here is two lines and
 * it keeps the client free of the game.
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    /**
     * Outgoing packets.
     *
     * <p>The three-argument form is the one every other {@code send} funnels into.
     * Injecting at the overloads instead would post twice for the same packet.
     */
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void karatsuba$send(Packet<?> packet, PacketCallbacks callbacks, boolean flush,
                                CallbackInfo callback) {
        if (Events.cancelled(new PacketEvent.Send(packet))) {
            callback.cancel();
            return;
        }
        if (packet instanceof CloseHandledScreenC2SPacket close
                && postScreenClose(close.getSyncId())) {
            callback.cancel();
            return;
        }
        if (packet instanceof ClientCommandC2SPacket command
                && Events.cancelled(new ClientCommandEvent(actionOf(command.getMode())))) {
            callback.cancel();
            return;
        }
        if (packet instanceof PlayerMoveC2SPacket move && postMovement(move)) {
            callback.cancel();
        }
    }

    /**
     * @return whether a handler refused the packet
     *
     * <p>Writes any change back into the packet before it goes, which is what
     * {@link PlayerMoveC2SPacketAccessor} is for. Only the fields this packet
     * actually carries are touched: writing a position into a look-only packet
     * would set a field the server never reads.
     */
    private static boolean postMovement(PlayerMoveC2SPacket packet) {
        MovementPacketEvent event = new MovementPacketEvent(
                packet.changesPosition(),
                packet.changesLook(),
                Vec3.of(packet.getX(0d), packet.getY(0d), packet.getZ(0d)),
                Vec2.rotation(packet.getYaw(0f), packet.getPitch(0f)),
                packet.isOnGround());

        if (Events.cancelled(event)) {
            return true;
        }
        if (!event.isModified()) {
            return false;
        }
        PlayerMoveC2SPacketAccessor writable = (PlayerMoveC2SPacketAccessor) packet;
        writable.karatsuba$setOnGround(event.isOnGround());

        if (packet.changesPosition()) {
            Vec3 position = event.getPosition();
            writable.karatsuba$setX(position.getX());
            writable.karatsuba$setY(position.getY());
            writable.karatsuba$setZ(position.getZ());
        }
        return false;
    }

    /**
     * Incoming packets, before the game handles them.
     *
     * <p>On a Netty worker. See the note on this class.
     */
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void karatsuba$receive(ChannelHandlerContext context, Packet<?> packet,
                                   CallbackInfo callback) {
        if (Events.cancelled(new PacketEvent.Receive(packet))) {
            callback.cancel();
        }
    }

    /**
     * The one place the game's mode enum is read.
     *
     * <p>Mapped rather than passed through, so a module never imports it and a
     * mode added in a future version arrives as {@code OTHER} instead of as a
     * compile error.
     */
    private static ClientCommandEvent.Action actionOf(ClientCommandC2SPacket.Mode mode) {
        return switch (mode) {
            case START_SPRINTING -> ClientCommandEvent.Action.START_SPRINTING;
            case STOP_SPRINTING -> ClientCommandEvent.Action.STOP_SPRINTING;
            case PRESS_SHIFT_KEY -> ClientCommandEvent.Action.START_SNEAKING;
            case RELEASE_SHIFT_KEY -> ClientCommandEvent.Action.STOP_SNEAKING;
            case START_FALL_FLYING -> ClientCommandEvent.Action.START_FALL_FLYING;
            default -> ClientCommandEvent.Action.OTHER;
        };
    }

    /**
     * @return whether a handler refused the close
     *
     * <p>A container the player opened has a sync id the server allocated; the
     * player's own inventory keeps the one it was born with. Comparing them is
     * the whole distinction, and it is why cancelling this is safe for the
     * inventory and ruinous for a chest.
     */
    private static boolean postScreenClose(int syncId) {
        return Events.cancelled(new ScreenCloseEvent(syncId, syncId == Inventories.playerScreenSyncId()));
    }
}
