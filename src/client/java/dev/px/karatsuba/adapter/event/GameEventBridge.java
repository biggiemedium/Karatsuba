package dev.px.karatsuba.adapter.event;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.ChatReceiveEvent;
import dev.px.core.event.impl.ChatSendEvent;
import dev.px.core.event.impl.Render2DEvent;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.WorldRender3D;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.HudEditorHost;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Turns the game's callbacks into Core's events.
 *
 * <p>Core's events are the version-agnostic half of the contract: a module writes
 * {@code @Subscribe void onTick(TickEvent)} once and it keeps working across game
 * versions because only this file knows what a Fabric callback is. Everything here
 * is Fabric API rather than a mixin, except raw input, which Fabric does not expose
 * &mdash; see {@code InputBridge}.
 */
public final class GameEventBridge {

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final WorldRender3D render3D;

    public GameEventBridge(WorldRender3D render3D) {
        this.render3D = render3D;
    }

    public void install() {
        installTicks();
        install2D();
        install3D();
        installChat();
        installWorld();
    }

    // ----------------------------------------------------------------- ticks

    private void installTicks() {
        ClientTickEvents.START_CLIENT_TICK.register(ignored -> Core.bus().post(new TickEvent(Stage.PRE)));
        ClientTickEvents.END_CLIENT_TICK.register(ignored -> Core.bus().post(new TickEvent(Stage.POST)));

        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> {
            // Core first: stop() saves the config, and it needs its services up to
            // do that. Only then is it safe to tear the render backend down.
            Core.get().stop();
            MinecraftAdapter.shutdown();
        });
    }

    // -------------------------------------------------------------- 2D frame

    private void install2D() {
        // The HUD renders before the screen does, so this fires even with a screen
        // open. Bailing out there is what keeps it to one 2D frame per game frame.
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (client.currentScreen == null) {
                renderFrame2D();
            }
        });

        // With a screen open the HUD callback has been skipped, so the frame comes
        // from the screen instead. Registering per screen is how Fabric exposes it.
        ScreenEvents.AFTER_INIT.register((ignored, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> renderFrame2D()));
    }

    private void renderFrame2D() {
        boolean guiOpen = Core.gui().isOpen() || Core.hud().isEditing();

        // Before the early return below, not after it: out of a world there is
        // nothing to draw, and that is exactly when a leftover empty screen would
        // never get dismissed.
        closeScreenIfCoreIsDone(guiOpen);

        // Nothing to draw on the title screen: no HUD out of a world, and no GUI
        // unless someone opened one.
        if (!guiOpen && client.world == null) {
            return;
        }

        float width = Core.platform().getScreenWidth();
        float height = Core.platform().getScreenHeight();
        float partialTicks = client.getRenderTickCounter().getTickDelta(false);

        Render.begin2D(width, height, Core.platform().getScreenScale());
        try {
            // Modules first, under everything: Core subscribes to nothing and draws
            // nothing on its own, so the HUD, the editor and the GUI are all drawn
            // from here, in the order they stack.
            Core.bus().post(new Render2DEvent(partialTicks, width, height));

            if (Core.hud().isEditing()) {
                // Edit mode draws the elements itself, so that hidden ones can be
                // shown faintly and the chrome can go over the top of them.
                HudEditorHost.renderFrame(width, height);
            } else {
                Core.hud().drawAll();
            }

            Core.gui().renderFrame();
        } finally {
            Render.end2D();
        }
    }

    /**
     * Dismisses our screen once Core has nothing open in it.
     *
     * <p>Escape reaches Core, not the game, so Core is the one that decides the GUI
     * is finished. Without this the click GUI would vanish and leave an empty screen
     * over the world.
     */
    private void closeScreenIfCoreIsDone(boolean guiOpen) {
        if (!guiOpen && client.currentScreen instanceof CoreScreen) {
            client.setScreen(null);
        }
    }

    // -------------------------------------------------------------- 3D frame

    private void install3D() {
        WorldRenderEvents.LAST.register(context -> {
            // The camera and matrices are only valid inside this callback, which is
            // why modules draw synchronously from the event rather than queueing.
            render3D.updateFrame(context);

            float partialTicks = context.tickCounter().getTickDelta(false);
            Render.begin3D(partialTicks);
            try {
                Core.bus().post(new Render3DEvent(partialTicks));
            } finally {
                Render.end3D();
            }
        });
    }

    // ------------------------------------------------------------------ chat

    private void installChat() {
        // Returning false suppresses the message. Core's CommandRegistry cancels the
        // event for a line starting with the command prefix, which is what stops
        // ".toggle sprint" from being broadcast to the server.
        ClientSendMessageEvents.ALLOW_CHAT.register(message ->
                !Core.bus().post(new ChatSendEvent(message)).isCancelled());

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                overlay || !cancelledOnReceive(message.getString()));

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, parameters, timestamp) ->
                !cancelledOnReceive(message.getString()));
    }

    private boolean cancelledOnReceive(String plainText) {
        return Core.bus().post(new ChatReceiveEvent(plainText, plainText)).isCancelled();
    }

    // ----------------------------------------------------------------- world

    private void installWorld() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, ignored) -> {
            ServerInfo server = client.getCurrentServerEntry();
            // A null address is Core's signal for singleplayer.
            Core.bus().post(new WorldEvent(true, server == null ? null : server.address));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, ignored) ->
                Core.bus().post(new WorldEvent(false, null)));
    }
}
