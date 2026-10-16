package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;

/**
 * The panel's own colour laid over a region and faded away, which is how new
 * content there fades in.
 *
 * <p>The gui library draws a widget's children itself, so there is no way to
 * draw a whole column at half opacity. Covering it with the background and
 * uncovering it looks the same, costs one rectangle, and is never pressed: its
 * hit shape is empty, so clicks go straight through to what is under it.
 */
public final class Veil extends Widget implements Floating {

    private final Reveal reveal;
    private final java.util.function.Supplier<Widget> over;

    /** @param over the widget whose area it covers, read each frame */
    public Veil(Reveal reveal, java.util.function.Supplier<Widget> over) {
        this.reveal = reveal;
        this.over = over;
        visibleWhen(reveal::active);
    }

    /** @return how much of the content it still hides, 1 to 0 */
    public float cover() {
        return 1f - reveal.progress();
    }

    @Override
    public Bounds placeIn(Bounds window) {
        Widget target = over.get();
        Bounds bounds = target == null ? null : target.getBounds();
        return bounds == null ? Bounds.of(window.getX(), window.getY(), 0f, 0f) : bounds;
    }
}
