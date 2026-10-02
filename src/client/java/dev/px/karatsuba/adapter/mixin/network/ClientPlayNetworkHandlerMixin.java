package dev.px.karatsuba.adapter.mixin.network;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.network.WeatherEvent;
import dev.px.karatsuba.adapter.event.game.network.WorldTimeEvent;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inbound packets, at the point the game handles them rather than the point they
 * arrive.
 *
 * <p>The distinction matters more than it looks. {@code ClientConnection} sees a
 * packet on a Netty worker, before anything has been decided about it &mdash;
 * that is the only place a packet can be refused outright, and the wrong place
 * to decide anything that depends on game state. The handler methods here run
 * after the game has marshalled the packet onto the main thread, so an event
 * posted from one can read the world, look at settings, and touch the player.
 *
 * <p>So this class is where the <em>semantic</em> inbound events live, and
 * {@code ClientConnectionMixin} keeps the raw ones.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    /**
     * A velocity update aimed at the local player.
     *
     * <p>Filtered to the player before anything is posted: the same handler runs
     * for every entity the server pushes, and an event storm for other people's
     * knockback would be both useless and expensive.
     *
     * <p>A handler that scales the push has its value applied here rather than
     * being written back into the packet, and the vanilla body is skipped. That
     * keeps the packet immutable and puts the one assignment in the one place,
     * using the same setter the game would have used.
     */
    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void karatsuba$onEntityVelocityUpdate(EntityVelocityUpdateS2CPacket packet,
                                                  CallbackInfo callback) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (!Events.isArmed() || player == null || packet.getEntityId() != player.getId()) {
            return;
        }

        KnockbackEvent event = new KnockbackEvent(
                Vec3.of(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ()));

        if (Events.cancelled(event)) {
            callback.cancel();
            return;
        }
        if (event.isModified()) {
            Vec3 scaled = event.getVelocity();
            // setVelocityClient, not setVelocity: it is what the vanilla body
            // calls, and the two differ for anything being ridden.
            player.setVelocityClient(scaled.getX(), scaled.getY(), scaled.getZ());
            callback.cancel();
        }
    }

    /**
     * The server's clock correction.
     *
     * <p>After {@code forceMainThread} rather than at HEAD: the handler is entered
     * twice, once on the Netty worker (where that call schedules the packet and
     * throws) and once on the game thread. Only the second gets past it, so
     * injecting there posts exactly once, on the thread the event promises.
     */
    @Inject(method = "onWorldTimeUpdate",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V"),
            cancellable = true)
    private void karatsuba$onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo callback) {
        if (Events.cancelled(new WorldTimeEvent(packet.time(), packet.timeOfDay()))) {
            callback.cancel();
        }
    }

    /**
     * The weather half of the general game state packet.
     *
     * <p>Every other reason &mdash; game mode, credits, respawn screen and the
     * rest &mdash; is passed straight through without posting anything. Same
     * injection point as {@link #karatsuba$onWorldTimeUpdate}, for the same reason.
     */
    @Inject(method = "onGameStateChange",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V"),
            cancellable = true)
    private void karatsuba$onGameStateChange(GameStateChangeS2CPacket packet, CallbackInfo callback) {
        WeatherEvent.Change change = weatherChangeOf(packet.getReason());
        if (change != null && Events.cancelled(new WeatherEvent(change, packet.getValue()))) {
            callback.cancel();
        }
    }

    /** @return the weather change a game state reason stands for, or null if it is not weather */
    private static WeatherEvent.Change weatherChangeOf(GameStateChangeS2CPacket.Reason reason) {
        if (reason == GameStateChangeS2CPacket.RAIN_STARTED) {
            return WeatherEvent.Change.RAIN_STARTED;
        }
        if (reason == GameStateChangeS2CPacket.RAIN_STOPPED) {
            return WeatherEvent.Change.RAIN_STOPPED;
        }
        if (reason == GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED) {
            return WeatherEvent.Change.RAIN_LEVEL;
        }
        if (reason == GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED) {
            return WeatherEvent.Change.THUNDER_LEVEL;
        }
        return null;
    }
}
