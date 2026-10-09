package dev.px.karatsuba.client.gui.shell.popup;

import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Popup;
import dev.px.gui.Screen;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Easing;

import java.util.List;

/**
 * Every popup's way in: it unrolls downward from its anchor over 180ms,
 * decelerating, and is cut off at its growing edge meanwhile.
 *
 * <p>A wrapper rather than a feature of each popup, so a menu, the colour picker
 * and the item picker all arrive the same way. Closing is instant, as the gui
 * library closes popups at once; that is also the advice, since a menu that has
 * done its job should get out of the way.
 */
public final class Unroll extends Column {

    private final long started = System.nanoTime();
    private Bounds slot;

    private Unroll(Widget content) {
        add(content);
    }

    /** Opens {@code content} as a popup, unrolling. @return the popup, to close it by */
    public static Unroll open(Screen screen, Widget content, Popup placement) {
        return screen.popup(new Unroll(content), placement);
    }

    /** @return how far it has unrolled, 0 to 1 */
    public float progress() {
        if (!Animations.enabled()) {
            return 1f;
        }
        return Easing.DECELERATE.apply(Animations.progress(started, Animations.POPUP));
    }

    @Override
    protected float childrenHeight(List<Widget> visible, float width) {
        return super.childrenHeight(visible, width) * progress();
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        Size full = super.childrenNatural(visible);
        return Size.of(full.getWidth(), full.getHeight() * progress());
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds area) {
        this.slot = area;
        float full = super.childrenHeight(visible, area.getWidth());
        super.arrange(visible, Bounds.of(area.getX(), area.getY(), area.getWidth(), full));
    }

    @Override
    protected Bounds clipChildren() {
        // Settled, the popup's shadow may fall outside it.
        return progress() < 1f ? slot : null;
    }
}
