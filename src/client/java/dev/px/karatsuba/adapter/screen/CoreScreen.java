package dev.px.karatsuba.adapter.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * The bare game screen an {@link Overlay} is shown inside.
 *
 * <p>It draws nothing, lays out nothing and knows about nothing the client shows.
 * All it does is exist, so that the cursor unlocks, the world stops taking clicks,
 * and the game has something it can close. What gets drawn on top of it is
 * {@code GameEventBridge}'s 2D frame, and what the client puts there is the
 * client's business.
 *
 * <p>Input is <em>not</em> posted from here &mdash; {@code InputBridge} does that
 * from the GLFW callbacks, and this screen is on its allowlist. What the overrides
 * below do is swallow: returning true stops the game acting on a key or a click
 * that has already been delivered.
 *
 * <p>One screen serves every overlay, because they all need exactly the same
 * thing from the game and nothing more.
 */
public final class CoreScreen extends Screen {

    public CoreScreen() {
        super(Text.literal("Karatsuba"));
    }

    /**
     * Ensures a bare screen is showing, for a caller about to open an overlay.
     *
     * <p>Order matters: the game screen goes up first, because the frame that
     * draws overlays is only reached once a screen is showing.
     *
     * <p>Idempotent, and it has to be. Replacing one of these with another calls
     * {@link #removed()} on the outgoing one, which dismisses every overlay
     * &mdash; so a second call would close the very thing the caller is about to
     * open.
     */
    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!isShowing(client)) {
            client.setScreen(new CoreScreen());
        }
    }

    /**
     * Takes the screen down if it is ours and nothing is using it any more.
     *
     * <p>Called from the frame loop rather than from whatever closed the overlay,
     * so there is one path out and not two that could disagree. Escape reaches the
     * client, the client decides it has nothing left open, and the screen goes on
     * the next frame.
     */
    public static void closeIfIdle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (isShowing(client) && !OverlayHost.isCapturing()) {
            client.setScreen(null);
        }
    }

    private static boolean isShowing(MinecraftClient client) {
        return client.currentScreen instanceof CoreScreen;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Deliberately empty: no vanilla background, no widgets. An overlay draws
        // its own backdrop if it wants one, and the HUD editor wants the world
        // visible behind it.
    }

    @Override
    public boolean shouldPause() {
        // Pausing would freeze the world behind the HUD editor, which is the one
        // thing someone positioning a HUD needs to keep moving.
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // InputBridge already delivered this. Swallow it so the game cannot also
        // act on it, and let the client decide what Escape means.
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return true;
    }

    @Override
    public void removed() {
        // Reaching here means something other than the client dismissed the screen
        // -- a disconnect, a death screen, a menu opened by the game. Whatever was
        // showing has lost its screen, so it is closed rather than left believing
        // it is still up. Dismissing something that is not open is a no-op.
        OverlayHost.dismissAll();
    }
}
