package dev.px.karatsuba.adapter;

import dev.px.core.Core;
import dev.px.core.render.Render;
import dev.px.core.util.CoreLogger;
import dev.px.core.util.net.Http;
import dev.px.karatsuba.adapter.account.MicrosoftAuth;
import dev.px.karatsuba.adapter.account.MinecraftAuthProvider;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.GameEventBridge;
import dev.px.karatsuba.adapter.game.player.Rotations;
import dev.px.karatsuba.adapter.network.MinecraftPacketDescriber;
import dev.px.karatsuba.adapter.network.NetworkBridge;
import dev.px.karatsuba.adapter.movement.MotionTracking;
import dev.px.karatsuba.adapter.platform.MinecraftLogger;
import dev.px.karatsuba.adapter.platform.MinecraftPlatform;
import dev.px.karatsuba.adapter.render.WorldRender3D;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGFontProvider;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D;
import dev.px.karatsuba.adapter.world.WorldCollisionSpace;
import net.minecraft.SharedConstants;

/**
 * Plugs Minecraft into Core.
 *
 * <p>Two calls, and the order matters:
 *
 * <ol>
 *   <li>{@link #platform(String)} before {@code Core.builder(...).build()}, because
 *       Core needs the data directory to exist before its services start.</li>
 *   <li>{@link #install(Core)} between {@code build()} and {@code start()}, because
 *       {@code start()} asks the font provider for the default font, and because a
 *       module registered before then may draw on its very first frame.</li>
 * </ol>
 *
 * <p>Nothing here is lazy. If the GL context is not current when {@code install}
 * runs, NanoVG fails loudly rather than leaving a backend that silently draws
 * nothing, which would be indistinguishable from Core's own no-op default.
 */
public final class MinecraftAdapter {

    private static NanoVGRender2D render2D;
    private static WorldRender3D render3D;

    private MinecraftAdapter() {
    }

    /** The game seam. Build Core with this. */
    public static MinecraftPlatform platform(String dataFolderName) {
        return new MinecraftPlatform(dataFolderName);
    }

    /**
     * The game's logger, wrapped for Core.
     *
     * <p>The one seam that cannot be filled from {@link #install}: Core resolves
     * its logger inside the constructor and hands it to every service there, so it
     * has to arrive through {@code Core.builder(...).logger(...)}. Without it Core
     * falls back to {@code System.out}, which reaches neither {@code latest.log}
     * nor a crash report.
     */
    public static CoreLogger logger(String clientName) {
        return new MinecraftLogger(clientName);
    }

    /**
     * Installs everything Core needs from the game.
     *
     * <p>Call between {@code build()} and {@code start()}, on the render thread.
     * The order within is not significant &mdash; nothing here runs until the game
     * calls back &mdash; except that the font provider must be set before
     * {@code start()} resolves the default font.
     */
    public static void install(Core core) {
        render2D = NanoVGRender2D.create();
        render3D = new WorldRender3D();

        Render.install(render2D);
        Render.install(render3D);

        // Before start(): FontService resolves its default during service startup,
        // and throws if no provider is set by then.
        core.getFontService().setProvider(new NanoVGFontProvider(render2D));

        // The login half of AccountService. Without a provider the saved account
        // list loads and every login returns "no authentication provider".
        core.getAccountService().setProvider(new MinecraftAuthProvider());

        // Identifies us to the services Core's Http talks to. Several reject or
        // throttle the default agent, and an unnamed client cannot be told apart
        // from a scraper when a host starts refusing requests.
        Http.setUserAgent(core.getClientName() + "/" + core.getClientVersion());

        // The two movement seams. Both are optional to Core and inert until
        // filled: without a sink, rotation claims are arbitrated and never
        // applied; without a collision space, a simulation is a trajectory
        // through solid rock. Neither throws when absent, which is why they can
        // be installed here rather than needing to exist before build().
        Rotations.install(core);
        core.getSimulationService().setCollisionSpace(WorldCollisionSpace.INSTANCE);

        // What feeds the simulation: the player's own movement is observed and
        // scored by MotionBridge from the player mixin, and everything else near
        // them is recorded here, once a tick, for predict() and coast().
        MotionTracking.install(core);

        // The one piece of packet knowledge, installed on the network service so
        // the timeline, TPS, lag, server and anticheat services all read through
        // it. Without it lag still works from arrivals alone, and a recording
        // names every packet by its (obfuscated) class.
        core.getNetworkService().setDescriber(MinecraftPacketDescriber.INSTANCE);

        // What the packets do not carry: the server's registered channels, which
        // Fabric decodes itself, and the world's tick rate.
        new NetworkBridge().install();
        core.getTimelineRecorder().putMetadata("gameVersion", SharedConstants.getGameVersion().getName());
        core.getTimelineRecorder().putMetadata("client", core.getClientName() + " " + core.getClientVersion());

        new GameEventBridge(render3D).install();

        // Last: the mixins have been live since the loader ran, and this is the
        // switch that lets them start posting. Everything above has to exist
        // before the first packet or keystroke gets through.
        Events.arm();

        // A crash or a killed process never reaches CLIENT_STOPPING, and the config
        // is only written on stop. The hook is a no-op once stop() has run, so the
        // ordinary path is unaffected.
        core.installShutdownHook();
    }

    /**
     * Releases what the JVM cannot.
     *
     * <p>NanoVG's context, its textures and its font buffers all live outside the
     * heap, so nothing here is reclaimed by shutting down normally. Called from the
     * event bridge once Core has stopped.
     */
    public static void shutdown() {
        Assets.dispose();
        if (render2D != null) {
            render2D.destroy();
            render2D = null;
        }
        render3D = null;
    }

    /**
     * Points Microsoft sign-ins at a particular Azure application.
     *
     * <p>Separate from {@link #install} because it is a deployment detail rather
     * than a seam: the client works without it, and the client that needs it
     * needs exactly one value. Null or blank keeps the default.
     */
    public static void microsoftClientId(String clientId) {
        MicrosoftAuth.setClientId(clientId);
    }

    /**
     * The live 2D backend, for loading textures against its context.
     *
     * <p>The only piece of the backend anything needs a direct handle to: NanoVG
     * keys textures and fonts to the context that created them, so {@code Assets}
     * has to name it. Everything else draws through Core's {@code Render} facade
     * and never learns which backend is installed.
     */
    public static NanoVGRender2D render2D() {
        return render2D;
    }
}
