package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;

import java.util.List;

/**
 * A column whose content rises a few pixels into place while its {@link Reveal}
 * runs, and is cut off at its own edges meanwhile, so nothing spills over the
 * window's border on the way.
 */
public class RisingColumn extends Column {

    private final Reveal reveal;
    private Bounds slot;

    public RisingColumn(Reveal reveal) {
        this.reveal = reveal;
    }

    public Reveal reveal() {
        return reveal;
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds area) {
        this.slot = area;
        float rise = Animations.VIEW_RISE * (1f - reveal.progress());
        super.arrange(visible, Bounds.of(area.getX(), area.getY() + rise, area.getWidth(), area.getHeight()));
    }

    @Override
    protected Bounds clipChildren() {
        return reveal.active() ? slot : null;
    }
}
