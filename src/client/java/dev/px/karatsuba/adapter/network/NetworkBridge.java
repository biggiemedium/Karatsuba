package dev.px.karatsuba.adapter.network;

import dev.px.core.Core;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.C2SConfigurationChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Feeds Core's network services what the packets alone do not carry.
 *
 * <p>Most of what {@code Core.tps()}, {@code Core.lag()}, {@code Core.server()}
 * and {@code Core.anticheat()} need arrives as packet roles, given by
 * {@link MinecraftPacketDescriber} and delivered by the {@code PacketEvent}s the
 * network mixin already posts. Two things do not:
 *
 * <ul>
 *   <li><b>Channels.</b> A server registers its plugin channels with a
 *       {@code minecraft:register} payload that Fabric consumes and decodes
 *       itself, then announces. Reading the announcement is simpler and more
 *       reliable than parsing the payload a second time. Both phases are
 *       listened to, because a server may register in either; Core holds ones
 *       that arrive before the world does until it loads.</li>
 *   <li><b>The tick rate.</b> Core assumes no rate, and since 1.20.3 a server can
 *       change its own with {@code /tick rate}. The world's tick manager is
 *       already kept in sync by the game, so its rate is read each tick and
 *       given to Core as the target, which caps the estimate and is what
 *       {@code getTps()} reports before the first measurement.</li>
 * </ul>
 *
 * <p>Everything Core decides from these &mdash; which software a brand names,
 * which anticheat the evidence points to &mdash; is the client's to configure, not
 * this class's. It only reports what the game said.
 */
public final class NetworkBridge {

    /** What the game runs at when nothing has changed it. */
    private static final float DEFAULT_TICK_RATE = 20f;

    private final MinecraftClient client = MinecraftClient.getInstance();

    /** The rate last given to Core, so an unchanged one is not set every tick. */
    private float reportedTickRate = Float.NaN;

    public void install() {
        C2SPlayChannelEvents.REGISTER.register((handler, sender, ignored, channels) -> reportChannels(channels));
        C2SConfigurationChannelEvents.REGISTER.register((handler, sender, ignored, channels) -> reportChannels(channels));

        ClientTickEvents.START_CLIENT_TICK.register(ignored -> reportTickRate());
    }

    private static void reportChannels(List<Identifier> channels) {
        Core.server().reportChannels(channels.stream().map(Identifier::toString).collect(Collectors.toList()));
    }

    private void reportTickRate() {
        ClientWorld world = client.world;
        float rate = world != null ? world.getTickManager().getTickRate() : DEFAULT_TICK_RATE;
        if (rate > 0f && rate != reportedTickRate) {
            reportedTickRate = rate;
            Core.tps().setTargetTps(rate);
        }
    }
}
