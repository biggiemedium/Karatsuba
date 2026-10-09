package dev.px.karatsuba.client.gui.component;

import dev.px.core.Core;
import dev.px.gui.legacy.Component;
import dev.px.gui.legacy.GuiStyle;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;

/**
 * A labelled button.
 *
 * <p>Core ships the click GUI but deliberately not a widget kit &mdash; it was
 * built downward from that one screen, so a component exists there only because
 * that screen needed it. A button is the first thing a screen of your own needs,
 * so it lives here rather than in the library.
 *
 * <p>One method is the contract, the same promise a HUD element makes: describe
 * what you are made of. The height is what that description measures and the
 * appearance is that same description drawn, so the two cannot drift apart.
 * {@code onClick} is the one addition anything interactive makes, and returning
 * false hands the press back to the parent rather than swallowing it.
 */
public final class Button extends Component {

    private final String label;
    private final Runnable action;
    private final Animation hover = Animation.fade(120L, Easing.QUAD_OUT);

    private String tooltip = "";

    public Button(String label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public Button tooltip(String text) {
        this.tooltip = text;
        return this;
    }

    @Override
    protected void content(Content content) {
        // Time-based, so the fade takes the same wall-clock time at 30 and 240 FPS.
        hover.target(getBounds().contains(Core.platform().getMouseX(), Core.platform().getMouseY()));

        // The grid every other component is laid out on. Inventing a height here is
        // how a panel stops lining up with itself.
        content.height(GuiStyle.rowHeight());
        content.background(GuiStyle.surface().lerp(GuiStyle.accent(), hover.get()), GuiStyle.radius());

        // Centred both ways: align centres the label across the button, and the
        // two fills split the leftover height above and below it.
        content.align(Align.CENTER);
        content.fill();
        content.text(label, GuiStyle.text());
        content.fill();
    }

    @Override
    protected boolean onClick(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        action.run();
        return true;
    }

    @Override
    public String getTooltip() {
        return tooltip;
    }
}
