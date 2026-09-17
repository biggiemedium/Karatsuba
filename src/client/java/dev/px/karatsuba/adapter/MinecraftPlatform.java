package dev.px.karatsuba.adapter;

import dev.px.core.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;

import java.io.File;

/**
 * Core's narrow seam into the running game.
 *
 * <p>Five questions Core cannot answer on its own: where to write config, how big
 * the screen is, where the cursor is, what to call the user, and how to say
 * something to them. That is the entire interface, and keeping it that small is
 * what lets Core compile with no Minecraft on its classpath.
 *
 * <p>Every getter here is called on the render thread, most of them every frame,
 * so nothing in this class allocates or blocks.
 */
public final class MinecraftPlatform implements Platform {

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final File dataDirectory;

    public MinecraftPlatform(String folderName) {
        this.dataDirectory = new File(FabricLoader.getInstance().getGameDir().toFile(), folderName);
    }

    @Override
    public String getGameVersion() {
        // Not MinecraftClient.getGameVersion(): in a Fabric environment that returns
        // the launcher brand ("Fabric"), which is not what Core prints at startup.
        return "Minecraft " + SharedConstants.getGameVersion().getName();
    }

    @Override
    public File getDataDirectory() {
        return dataDirectory;
    }

    @Override
    public String getUsername() {
        return client.getSession().getUsername();
    }

    @Override
    public boolean isInGame() {
        return client.world != null && client.player != null;
    }

    // ------------------------------------------------------------- screen

    @Override
    public float getScreenWidth() {
        return client.getWindow().getScaledWidth();
    }

    @Override
    public float getScreenHeight() {
        return client.getWindow().getScaledHeight();
    }

    @Override
    public float getScreenScale() {
        return (float) client.getWindow().getScaleFactor();
    }

    @Override
    public float getMouseX() {
        Window window = client.getWindow();
        // Mouse.getX is in raw window pixels; Core draws in scaled units. Dividing
        // by the real window size rather than the scale factor is what the game
        // itself does, and it stays right when the window is an odd size.
        return (float) (client.mouse.getX() * window.getScaledWidth() / window.getWidth());
    }

    @Override
    public float getMouseY() {
        Window window = client.getWindow();
        return (float) (client.mouse.getY() * window.getScaledHeight() / window.getHeight());
    }

    // --------------------------------------------------------------- chat

    @Override
    public void printMessage(String message) {
        // Command output arrives before the HUD exists during early startup, and
        // after it is gone during shutdown.
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(message));
        }
    }

    @Override
    public void sendChatMessage(String message) {
        if (client.getNetworkHandler() == null) {
            return;
        }
        // The server takes commands without the slash, and rejects chat that has one.
        if (message.startsWith("/")) {
            client.getNetworkHandler().sendChatCommand(message.substring(1));
        } else {
            client.getNetworkHandler().sendChatMessage(message);
        }
    }

    @Override
    public void setClipboard(String text) {
        client.keyboard.setClipboard(text);
    }

    @Override
    public String getClipboard() {
        return client.keyboard.getClipboard();
    }
}
