package dev.px.karatsuba.adapter.event;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.ChatReceiveEvent;
import dev.px.core.event.impl.ChatSendEvent;
import dev.px.core.event.impl.Render2DEvent;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.karatsuba.adapter.network.PacketHold;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.event.game.render.WorldOverlayEvent;
import dev.px.karatsuba.adapter.event.game.world.ChunkEvent;
import dev.px.karatsuba.adapter.render.GuiFrame;
import dev.px.karatsuba.adapter.render.WorldRender3D;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.OverlayHost;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;

/**
 * Turns the game's callbacks into Core's events.
 *
 * <p>Core's events are the version-agnostic half of the contract: a module writes
 * {@code @Subscribe void onTick(TickEvent)} once and it keeps working across game
 * versions because only this file knows what a Fabric callback is. Everything here
 * is Fabric API rather than a mixin, except raw input, which Fabric does not expose
 * &mdash; see {@code InputBridge}.
 *
 * <p>It decides <em>when</em> a frame happens and nothing about what is in one.
 * Modules draw from {@code Render2DEvent} and the client's interface draws through
 * {@link OverlayHost}, so nothing here names a screen, a HUD element or a colour.
 */
public final class GameEventBridge {

    /** The installed bridge, for the one frame hook a mixin has to call: {@link #beforeChat}. */
    private static GameEventBridge installed;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final WorldRender3D render3D;

    /**
     * The frame each layer was last drawn in, so a layer drawn early (under the
     * chat) is not drawn again later in the same frame. The game builds one
     * {@code DrawContext} per frame and hands the same one to the HUD and the
     * screen, which makes it the frame's identity.
     */
    private DrawContext worldLayerDrawnIn;
    private DrawContext topLayerDrawnIn;

    public GameEventBridge(WorldRender3D render3D) {
        this.render3D = render3D;
    }

    public void install() {
        installed = this;
        installTicks();
        install2D();
        install3D();
        installChat();
        installWorld();
    }

    // ----------------------------------------------------------------- ticks

    private void installTicks() {
        ClientTickEvents.START_CLIENT_TICK.register(ignored -> Core.bus().post(new TickEvent(Stage.PRE)));
        ClientTickEvents.END_CLIENT_TICK.register(ignored -> {
            Core.bus().post(new TickEvent(Stage.POST));
            // After every handler: a hold nobody let go of ends by itself.
            PacketHold.tick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> {
            // Core first: stop() saves the config, and it needs its services up to
            // do that. Only then is it safe to tear the render backend down.
            Core.get().stop();
            MinecraftAdapter.shutdown();
        });
    }

    // -------------------------------------------------------------- 2D frame

    private void install2D() {
        // The HUD renders before any screen does, so this fires with a screen open
        // too. The world layer is drawn here every time, which is what puts it
        // under the screen; the top layer only when no screen will draw it later.
        // While playing, both were usually drawn already, under the chat.
        HudRenderCallback.EVENT.register((context, tickCounter) ->
                renderFrame2D(context, true, client.currentScreen == null));

        // With a screen open the top layer comes from the screen instead, after it
        // has drawn, so the client's own overlays sit over it. Registering per
        // screen is how Fabric exposes it.
        ScreenEvents.AFTER_INIT.register((ignored, screen, width, height) ->
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) ->
                        renderFrame2D(context, false, true)));
    }

    /**
     * The chat is about to be drawn: the client's layers go first, so the chat
     * sits over the HUD rather than under it. From {@code ChatHudMixin}.
     *
     * <p>The game draws the chat from the HUD pass while playing and from
     * {@code ChatScreen} while typing, both after the callbacks above would have
     * drawn, so whichever comes first this frame draws the layers here and the
     * later callback finds them done. Only while playing or typing: under any
     * other screen the chat is part of the HUD the screen covers, and the top
     * layer still belongs over that screen.
     */
    public static void beforeChat(DrawContext context) {
        GameEventBridge bridge = installed;
        if (bridge == null) {
            return;
        }
        Screen screen = bridge.client.currentScreen;
        if (screen != null && !(screen instanceof ChatScreen)) {
            return;
        }
        // Paint what the game drew before the chat, so it lands under the client's
        // layers rather than being flushed over them once the frame ends.
        context.draw();
        // The world layer only while playing: with chat open it was drawn from the
        // HUD pass, and with the HUD hidden (F1) there is none to draw.
        bridge.renderFrame2D(context, screen == null, true);
    }

    /**
     * One 2D frame, in up to two layers.
     *
     * <ol>
     *   <li><b>World layer</b> &mdash; {@link WorldOverlayEvent}: name tags, 2D
     *       boxes, anything projected from the world. Drawn from the HUD pass, so a
     *       screen covers it like it covers the game's own HUD.</li>
     *   <li><b>Top layer</b> &mdash; Core's {@code Render2DEvent}, then the
     *       client's overlays: the HUD elements and the client's menus, which have
     *       to stay visible over screens. Under the chat, though, while playing or
     *       typing in it; see {@link #beforeChat}.</li>
     * </ol>
     *
     * <p>The order is the only thing decided here, and it is the order they stack.
     * What an overlay is and what it draws is the client's; see
     * {@link dev.px.karatsuba.adapter.screen.Overlay}.
     *
     * <p>A layer already drawn this frame is skipped, so asking for it again from a
     * later callback is safe.
     */
    private void renderFrame2D(DrawContext context, boolean worldLayer, boolean topLayer) {
        worldLayer &= worldLayerDrawnIn != context;
        topLayer &= topLayerDrawnIn != context;
        if (!worldLayer && !topLayer) {
            return;
        }
        if (worldLayer) {
            worldLayerDrawnIn = context;
        }
        if (topLayer) {
            topLayerDrawnIn = context;
        }

        boolean capturing = OverlayHost.isCapturing();

        if (topLayer) {
            // Before the early return below, not after it: out of a world there is
            // nothing to draw, and that is exactly when a leftover empty screen
            // would never get dismissed.
            CoreScreen.closeIfIdle();
        }

        boolean inWorld = client.world != null;
        // Nothing to draw on the title screen: no world behind it, and nothing of
        // ours open over it.
        if (!inWorld && !(topLayer && capturing)) {
            return;
        }

        float width = Core.platform().getScreenWidth();
        float height = Core.platform().getScreenHeight();
        float partialTicks = client.getRenderTickCounter().getTickDelta(false);

        Render.begin2D(width, height, Core.platform().getScreenScale());
        // Open for the frame so a module can draw what only the game can, such as
        // item icons; see GuiFrame.
        GuiFrame.open(context);
        try {
            if (worldLayer && inWorld) {
                Core.bus().post(new WorldOverlayEvent(partialTicks, width, height));
            }
            if (topLayer) {
                Core.bus().post(new Render2DEvent(partialTicks, width, height));
                OverlayHost.render(width, height);
            }
        } finally {
            // NanoVG paints first; then the game's deferred draws (item icons) go
            // on top, with the game's GL state already back as it left it.
            Render.end2D();
            GuiFrame.close();
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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, ignored) -> {
            PacketHold.clear();
            Core.bus().post(new WorldEvent(false, null));
        });

        // Chunks coming and going, for anything that remembers what is in them.
        // Block changes inside a loaded chunk come from WorldChunkMixin.
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) ->
                Events.post(new ChunkEvent(chunk.getPos().x, chunk.getPos().z, true)));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) ->
                Events.post(new ChunkEvent(chunk.getPos().x, chunk.getPos().z, false)));
    }
}
