package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.karatsuba.client.gui.stable.shell.style.Palette;

/** The search box and the modules, in the middle. Its width is set each frame, as the sidebar's is. */
public final class ModuleColumn extends RisingColumn {

    private float width = Palette.LIST_WIDTH;

    public ModuleColumn(Reveal reveal) {
        super(reveal);
    }

    public float width() {
        return width;
    }

    public void width(float newWidth) {
        this.width = newWidth;
    }
}
