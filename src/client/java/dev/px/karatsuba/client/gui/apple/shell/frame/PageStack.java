package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Container;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Easing;

import java.util.List;

/**
 * Pages that push and pop, as Tahoe's System Settings does
 * ({@code gui/apple/DESIGN.md} §8.3): one shows at a time, filling the stack. Going
 * to another slides the new page in from the right while the old one slides out to
 * the left, both the full width, on a short, sharp ease-out; going back runs it the
 * other way. Pages are clipped to the stack, so neither shows past its edge.
 *
 * <p>The slide happens in {@link #arrange}, so what is drawn and what is pressed
 * stay the same place throughout. With Reduce Motion on it switches at once.
 */
public final class PageStack extends Container {

    private java.util.function.BooleanSupplier sideBySide = () -> false;
    private float firstWidth;
    private float gap;

    private Widget showing;
    private Widget leaving;
    private int direction = 1;
    private long started;

    /** Adds a page, hidden until {@link #show shown}; the first added shows first. */
    public <W extends Widget> W page(W page) {
        add(page);
        page.visibleWhen(() -> page == showing || page == leaving || sideBySide.getAsBoolean());
        if (showing == null) {
            showing = page;
        }
        return page;
    }

    /**
     * Shows the first two pages side by side while {@code on} holds, the first
     * {@code width} wide and the second filling the rest past {@code between}: a split
     * view, with nothing sliding. Pushes and pops then only change which page is
     * {@link #showing()}, for when the split ends.
     */
    public PageStack sideBySide(java.util.function.BooleanSupplier on, float width, float between) {
        this.sideBySide = on;
        this.firstWidth = width;
        this.gap = between;
        return this;
    }

    /** @return whether the pages show side by side now */
    public boolean isSideBySide() {
        return sideBySide.getAsBoolean();
    }

    /** @return the page showing, or arriving */
    public Widget showing() {
        return showing;
    }

    /** Pushes {@code page} in from the right: going deeper. */
    public void push(Widget page) {
        go(page, 1);
    }

    /** Pops back to {@code page}, sliding in from the left. */
    public void pop(Widget page) {
        go(page, -1);
    }

    /** Shows {@code page} with no slide: a jump from somewhere else. */
    public void jump(Widget page) {
        showing = page;
        leaving = null;
        started = 0L;
    }

    private void go(Widget page, int towards) {
        if (page == showing) {
            return;
        }
        if (!Animations.enabled() || sideBySide.getAsBoolean()) {
            jump(page);
            return;
        }
        leaving = showing;
        showing = page;
        direction = towards;
        started = System.nanoTime();
    }

    /** @return how far the slide has gone, 0 to 1, eased; 1 at rest */
    private float progress() {
        if (started == 0L) {
            return 1f;
        }
        float raw = Animations.progress(started, direction > 0 ? Animations.PUSH : Animations.POP);
        if (raw >= 1f) {
            started = 0L;
            leaving = null;
            return 1f;
        }
        return Easing.PUSH.apply(raw);
    }

    @Override
    protected float childrenHeight(List<Widget> visible, float available) {
        float height = 0f;
        for (Widget child : visible) {
            height = Math.max(height, heightOf(child, available));
        }
        return height;
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        float width = 0f;
        float height = 0f;
        for (Widget child : visible) {
            Size natural = naturalSizeOf(child);
            width = Math.max(width, natural.getWidth());
            height = Math.max(height, natural.getHeight());
        }
        return Size.of(width, height);
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds slot) {
        if (sideBySide.getAsBoolean() && visible.size() >= 2) {
            float first = Math.min(firstWidth, slot.getWidth() * 0.45f);
            place(visible.get(0), Bounds.of(slot.getX(), slot.getY(), first, slot.getHeight()));
            place(visible.get(1), Bounds.of(slot.getX() + first + gap, slot.getY(),
                    Math.max(0f, slot.getWidth() - first - gap), slot.getHeight()));
            return;
        }
        float t = progress();
        float w = slot.getWidth();
        for (Widget child : visible) {
            float x;
            if (child == showing) {
                x = direction * w * (1f - t);
            } else if (child == leaving) {
                x = -direction * w * t;
            } else {
                continue;
            }
            place(child, Bounds.of(slot.getX() + x, slot.getY(), w, slot.getHeight()));
        }
    }

    @Override
    protected Bounds clipChildren() {
        return getBounds();
    }
}
