package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A group's settings, under its heading, folding open and shut.
 *
 * <p>Its height eases between nothing and its content's, with the content cut
 * off at the edge meanwhile, so the rows below slide up or down with it rather
 * than jumping. Settled open it is an ordinary column; settled shut it takes no
 * room and is not drawn.
 */
public final class Fold extends Column {

    private final BooleanSupplier open;
    private final Motion openness;
    private Bounds slot;

    /** The openness read this frame, so every measurement in one update agrees. */
    private float frameOpenness;
    private long frameMillis = -1L;

    public Fold(BooleanSupplier open) {
        this.open = open;
        this.openness = new Motion(Animations.FOLD, open.getAsBoolean() ? 1f : 0f);
    }

    /** @return how open it is now, 0 shut to 1 open */
    public float openness() {
        long now = System.nanoTime() / 1_000_000L;
        if (now != frameMillis) {
            frameMillis = now;
            frameOpenness = openness.toward(open.getAsBoolean());
        }
        return frameOpenness;
    }

    /** @return whether there is anything of it to show: open, or still folding */
    public boolean showing() {
        return open.getAsBoolean() || openness() > 0f;
    }

    @Override
    protected float childrenHeight(List<Widget> visible, float width) {
        return super.childrenHeight(visible, width) * openness();
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        Size full = super.childrenNatural(visible);
        return Size.of(full.getWidth(), full.getHeight() * openness());
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds area) {
        this.slot = area;
        float full = super.childrenHeight(visible, area.getWidth());
        super.arrange(visible, Bounds.of(area.getX(), area.getY(), area.getWidth(), full));
    }

    @Override
    protected Bounds clipChildren() {
        return openness() < 1f ? slot : null;
    }
}
