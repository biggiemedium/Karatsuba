package dev.px.karatsuba.adapter.mixin.network;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.CorePacketEvents;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.network.ClientCommandEvent;
import dev.px.karatsuba.adapter.event.game.network.MovementPacketEvent;
import dev.px.karatsuba.adapter.event.game.network.PacketEvent;
import dev.px.karatsuba.adapter.event.game.network.ScreenCloseEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.network.PacketHold;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
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
 * <p>Each posts the adapter's {@code PacketEvent} for modules and then Core's
 * for Core itself (see {@link CorePacketEvents}); a third injection marks the
 * inbound packets the game handles inline, for Core's applied phase.
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
        if (PacketHold.isReleasing()) {
            // A held packet going out: every handler saw it when it was sent.
            return;
        }
        boolean refused = refusedOnSend(packet);
        // After the adapter's handlers, so Core sees the packet as it will
        // actually leave -- a rewritten position included.
        if (isClientSide() && CorePacketEvents.sent(packet, refused)) {
            refused = true;
        }
        if (refused || isClientSide() && PacketHold.take((ClientConnection) (Object) this, packet, callbacks, flush)) {
            callback.cancel();
        }
    }

    /** @return whether one of the adapter's handlers refused an outgoing packet */
    private static boolean refusedOnSend(Packet<?> packet) {
        if (Events.cancelled(new PacketEvent.Send(packet))) {
            return true;
        }
        if (packet instanceof CloseHandledScreenC2SPacket close
                && postScreenClose(close.getSyncId())) {
            return true;
        }
        if (packet instanceof ClientCommandC2SPacket command
                && Events.cancelled(new ClientCommandEvent(actionOf(command.getMode())))) {
            return true;
        }
        return packet instanceof PlayerMoveC2SPacket move && postMovement(move);
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
        boolean refused = Events.cancelled(new PacketEvent.Receive(packet));
        if (isClientSide() && CorePacketEvents.received(packet, refused)) {
            refused = true;
        }
        if (refused) {
            callback.cancel();
        }
    }

    /**
     * A packet the game handled right here on the Netty worker.
     *
     * <p>Most handlers begin with {@code NetworkThreadUtils.forceMainThread},
     * which throws on this thread and re-runs the handler on the game thread;
     * {@code NetworkThreadUtilsMixin} posts those as applied when they get there.
     * The throw also means this point is never reached for them, because it sits
     * inside the {@code try} that catches it. What does reach it is exactly the
     * packets handled inline &mdash; keep-alives, pings &mdash; and Core's
     * contract for those is received, then applied immediately after.
     */
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V",
                    shift = At.Shift.AFTER))
    private void karatsuba$handledInline(ChannelHandlerContext context, Packet<?> packet,
                                         CallbackInfo callback) {
        if (isClientSide()) {
            CorePacketEvents.applied(packet);
        }
    }

    /**
     * Whether this is the client's own connection.
     *
     * <p>{@code ClientConnection} is both ends of the pipe: in singleplayer the
     * integrated server has one too, and its traffic runs through these same
     * injections in the other direction. Core's events describe what the client
     * said and heard, so the server's half is kept out of them.
     */
    private boolean isClientSide() {
        return ((ClientConnection) (Object) this).getSide() == NetworkSide.CLIENTBOUND;
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
