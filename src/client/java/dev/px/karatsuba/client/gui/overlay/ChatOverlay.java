package dev.px.karatsuba.client.gui.overlay;

import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.screen.ChatChrome;
import dev.px.karatsuba.client.command.CommandLine;
import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.gui.stable.shell.style.Easing;

/**
 * The chat box's chrome: the input bar rising into place as the chat opens, a
 * white outline round it while what is typed is a client command, and the rest of
 * the command predicted in grey as it is typed.
 *
 * <p>The game's chat is otherwise its own; see the adapter's {@link ChatChrome}.
 *
 * <h2>Commands</h2>
 * Both the outline and the prediction are {@link CommandLine}'s answers, which
 * are Core's: the outline shows exactly when pressing Enter would run a command
 * rather than send a message (from the first letter, with the default
 * single-character prefix), and Tab takes the prediction.
 */
public final class ChatOverlay implements ChatChrome {

    /** When the chat last opened, for the rise. */
    private long openedAt = System.nanoTime();

    /** Whether the last frame's line was a command, and when that last changed, for the fade. */
    private boolean command;
    private long commandChangedAt;

    @Override
    public void opened() {
        openedAt = System.nanoTime();
        // A fresh chat starts without the outline rather than fading one out.
        command = false;
        commandChangedAt = 0L;
    }

    @Override
    public float slide() {
        if (!Animations.enabled()) {
            return 0f;
        }
        float rise = Easing.DECELERATE.apply(Animations.progress(openedAt, OverlayStyle.CHAT_RISE));
        return OverlayStyle.CHAT_RISE_DISTANCE * (1f - rise);
    }

    @Override
    public void renderInput(float x, float y, float width, float height, String text) {
        boolean nowCommand = CommandLine.isCommand(text);
        if (nowCommand != command) {
            command = nowCommand;
            commandChangedAt = System.nanoTime();
        }

        float shown = outlineOpacity();
        if (shown <= 0f) {
            return;
        }
        Render.rectOutline(x, y, width, height, OverlayStyle.CHAT_OUTLINE_WIDTH,
                OverlayStyle.CHAT_COMMAND_OUTLINE.withAlpha(shown));
    }

    /** @return the outline's opacity, 0 to 1: fading in as a command starts, out as it stops */
    private float outlineOpacity() {
        if (!Animations.enabled()) {
            return command ? 1f : 0f;
        }
        if (command) {
            return Easing.STANDARD.apply(Animations.progress(commandChangedAt, OverlayStyle.CHAT_OUTLINE_IN));
        }
        return 1f - Easing.STANDARD.apply(Animations.progress(commandChangedAt, OverlayStyle.CHAT_OUTLINE_OUT));
    }

    @Override
    public String predict(String text) {
        return CommandLine.predict(text);
    }
}
