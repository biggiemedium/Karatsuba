package dev.px.karatsuba.client.gui.apple.shell.sidebar;

import dev.px.karatsuba.client.gui.apple.shell.frame.SmoothScroll;

/**
 * The sidebar's list of entries. Its own type only so its look can balance the
 * room its scroll bar keeps on the right with as much on the left while the
 * sidebar shows icons alone, so each icon sits on the pane's centre line.
 */
public final class SidebarScroll extends SmoothScroll {

    private final SidebarPane pane;

    SidebarScroll(SidebarPane pane) {
        this.pane = pane;
    }

    public boolean isCollapsed() {
        return pane.isCollapsed();
    }
}
