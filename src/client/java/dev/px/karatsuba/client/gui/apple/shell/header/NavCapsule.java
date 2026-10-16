package dev.px.karatsuba.client.gui.apple.shell.header;

import dev.px.core.layout.Bounds;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;

/**
 * ‹ › in a small capsule of glass beside the page title: one of the few floating
 * controls that are glass ({@code gui/apple/DESIGN.md} §5.1).
 */
public final class NavCapsule extends Row implements GlassSurface {

    public NavCapsule(Shell shell) {
        add(new NavButton(Glyph.BACK, "Back", shell::canGoBack, shell::goBack));
        add(new NavButton(Glyph.FORWARD, "Forward", shell::canGoForward, shell::goForward));
    }

    @Override
    public Bounds glassBounds() {
        return part(PART);
    }

    @Override
    public float glassRadius() {
        Bounds glass = glassBounds();
        return glass == null ? 0f : glass.getHeight() / 2f;
    }
}
