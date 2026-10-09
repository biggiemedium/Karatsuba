package dev.px.karatsuba.adapter.platform;

import dev.px.core.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.util.function.Supplier;

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

    /** The name the client signs its chat messages with, and its colour; a null name signs nothing. */
    private static volatile Supplier<String> tagName = () -> null;
    private static volatile Supplier<Integer> tagColour = () -> 0xFFFFFF;

    /**
     * Signs every message the client prints to chat, "[Name] message", the name in
     * {@code colour} (RGB). Read at each message, so a setting switching it off or
     * a theme change applies at once.
     */
    public static void tagMessages(Supplier<String> name, Supplier<Integer> colour) {
        tagName = name == null ? () -> null : name;
        tagColour = colour == null ? () -> 0xFFFFFF : colour;
    }

    @Override
    public void printMessage(String message) {
        // Command output arrives before the HUD exists during early startup, and
        // after it is gone during shutdown.
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(tagged(message));
        }
    }

    private static Text tagged(String message) {
        String name = tagName.get();
        if (name == null || name.isEmpty()) {
            return Text.literal(message);
        }
        MutableText tag = Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(name).styled(style -> style.withColor(tagColour.get() & 0xFFFFFF)))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
        return tag.append(Text.literal(message).formatted(Formatting.WHITE));
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
