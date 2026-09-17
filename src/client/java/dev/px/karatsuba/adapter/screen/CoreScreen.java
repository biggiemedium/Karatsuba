package dev.px.karatsuba.adapter.screen;

import dev.px.core.Core;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * The bare game screen that Core's click GUI and HUD editor run inside.
 *
 * <p>It draws nothing and lays out nothing. Core subscribes to nothing and draws
 * nothing on its own, so the GUI and the editor are both drawn from
 * {@code GameEventBridge}'s 2D frame; all this screen does is exist, so that the
 * cursor unlocks, the world stops taking clicks, and there is something for
 * {@code Escape} to close.
 *
 * <p>Input is <em>not</em> posted from here &mdash; {@code InputBridge} does that
 * from the GLFW callbacks, and this screen is on its allowlist. What the overrides
 * below do is swallow: returning true stops the game acting on a key or a click
 * that Core has already been told about.
 *
 * <p>One class serves both consumers because they need exactly the same thing,
 * which is also why closing it has to close whichever one opened it.
 */
public final class CoreScreen extends Screen {

    public CoreScreen() {
        super(Text.literal("Karatsuba"));
    }

    /**
     * Ensures a bare screen is showing, for a caller that will open something in
     * Core itself.
     *
     * <p>Order matters: the game screen goes up first, because the frame that draws
     * Core's screen is only reached once a screen is showing.
     *
     * <p>Idempotent, and it has to be. Replacing one of these with another calls
     * {@link #removed()} on the outgoing one, which shuts down whatever Core had
     * open &mdash; so a second call would close the very thing the caller is about
     * to open.
     */
    public static void open() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof CoreScreen)) {
            client.setScreen(new CoreScreen());
        }
    }

    /** Opens the click GUI inside a bare screen. */
    public static void openClickGui() {
        open();
        Core.gui().openClickGui();
    }

    /** Opens the HUD editor inside a bare screen. */
    public static void openHudEditor() {
        open();
        Core.hud().openEditor();
    }

    /**
     * Opens the click GUI, or closes it if it is already showing.
     *
     * <p>Nothing dismisses the game screen here. Core decides it has nothing open,
     * and {@code GameEventBridge} takes the empty screen down on the next frame
     * &mdash; the same path Escape already takes, rather than a second one that
     * could disagree with it.
     */
    public static void toggleClickGui() {
        open();
        Core.gui().toggleClickGui();
    }

    /** Opens the HUD editor, or closes it if it is already showing. */
    public static void toggleHudEditor() {
        open();
        HudEditorHost.toggle();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Deliberately empty: no vanilla background, no widgets. Core's own screen
        // draws its backdrop, and the HUD editor wants the world visible behind it.
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
        // act on it, and let Core decide what Escape means.
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
    public void close() {
        // Escape reaches Core through InputBridge and is handled there: it clears a
        // selection, backs out of a text field, and only then closes. Reaching this
        // method means something else dismissed the screen, so both consumers are
        // shut down -- closing one that is not open is a no-op in Core.
        Core.gui().close();
        Core.hud().closeEditor();
        super.close();
    }

    @Override
    public void removed() {
        Core.gui().close();
        Core.hud().closeEditor();
    }
}
