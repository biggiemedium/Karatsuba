package dev.px.karatsuba.adapter.mixin;

import dev.px.karatsuba.adapter.event.CorePacketEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The moment an inbound packet is handled on the game thread.
 *
 * <p>Nearly every handler opens with {@code forceMainThread}. Called from the
 * Netty worker it schedules the packet and throws; called again from the game
 * thread, when the scheduled task runs, it returns and the handler carries on.
 * So the second call, and only that one, is the packet taking effect &mdash; one
 * injection here instead of one per handler.
 *
 * <p>Filtered to the client's own executor: the integrated server funnels its
 * packets through the same method in singleplayer, and those are the server
 * hearing the client, not the client hearing the server.
 */
@Mixin(NetworkThreadUtils.class)
public abstract class NetworkThreadUtilsMixin {

    @Inject(method = "forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V",
            at = @At("HEAD"))
    private static <T extends PacketListener> void karatsuba$forceMainThread(Packet<T> packet, T listener,
                                                                             ThreadExecutor<?> engine,
                                                                             CallbackInfo callback) {
        if (engine == MinecraftClient.getInstance() && engine.isOnThread()) {
            CorePacketEvents.applied(packet);
        }
    }
}
