package dev.px.karatsuba.adapter.screen;

import dev.px.core.Core;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D;
import net.minecraft.client.gui.DrawContext;

/**
 * Where the game's chat screen hands over to the client's {@link ChatChrome}.
 *
 * <p>{@code ChatScreenMixin} says where: the chat opening, the frame starting,
 * and the input bar's draws. This says what happens there, so the mixin holds no
 * logic of its own.
 */
public final class ChatChromeBridge {

    private ChatChromeBridge() {
    }

    /** The chat opened. */
    public static void opened() {
        ChatChrome chrome = ChatChrome.installed();
        if (chrome != null) {
            chrome.opened();
        }
    }

    /** @return how far down to draw the input bar this frame; 0 with nothing installed */
    public static float slide() {
        ChatChrome chrome = ChatChrome.installed();
        if (chrome == null) {
            return 0f;
        }
        try {
            return chrome.slide();
        } catch (RuntimeException failure) {
            uninstall(chrome, failure);
            return 0f;
        }
    }

    /**
     * @return the line {@code text} is predicted to become, or null for none, or
     *         for a prediction that does not carry on from what is typed
     */
    public static String predict(String text) {
        ChatChrome chrome = ChatChrome.installed();
        if (chrome == null) {
            return null;
        }
        String predicted;
        try {
            predicted = chrome.predict(text);
        } catch (RuntimeException failure) {
            uninstall(chrome, failure);
            return null;
        }
        boolean carriesOn = predicted != null && predicted.length() > text.length()
                && predicted.regionMatches(true, 0, text, 0, text.length());
        return carriesOn ? predicted : null;
    }

    /** @return what to show after {@code text} in grey: the rest of its prediction, or null */
    public static String suggestion(String text) {
        String predicted = predict(text);
        return predicted == null ? null : predicted.substring(text.length());
    }

    /**
     * Draws the installed chrome over the input bar the game has just drawn.
     *
     * <p>A chrome that throws is uninstalled, so the next frame is the game's own
     * chat rather than a broken one, and the log gets one stack trace rather than
     * one a frame.
     */
    public static void drawInput(DrawContext context, float x, float y, float width, float height, String text) {
        ChatChrome chrome = ChatChrome.installed();
        NanoVGRender2D backend = MinecraftAdapter.render2D();
        if (chrome == null || backend == null || backend.isInFrame()) {
            return;
        }

        // The game batches its GUI draws; flush the bar and its text so they land
        // under the chrome, not on top.
        context.draw();

        Render.begin2D(Core.platform().getScreenWidth(), Core.platform().getScreenHeight(),
                Core.platform().getScreenScale());
        try {
            chrome.renderInput(x, y, width, height, text);
        } catch (RuntimeException failure) {
            uninstall(chrome, failure);
        } finally {
            Render.end2D();
        }
    }

    private static void uninstall(ChatChrome chrome, RuntimeException failure) {
        ChatChrome.install(null);
        Core.log().error("Chat chrome " + chrome.getClass().getSimpleName()
                + " threw; the game's own chat is back", failure);
    }
}
