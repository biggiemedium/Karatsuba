package dev.px.karatsuba.client.gui.apple.shell;

import java.util.ArrayList;
import java.util.List;

/**
 * The back and forward history of pages ({@code gui/apple/DESIGN.md} §2.3): which
 * section, and where inside it.
 *
 * <p>Nothing records into it directly. The shell {@link #observe observes} where
 * the GUI is once a frame, and a change from the page the history is on becomes a
 * new step, which drops whatever was ahead of it, as a browser does. Going back
 * and forward moves along it without adding; the next observation then finds the
 * GUI already where the history says.
 */
final class History {

    /** A page: a section, and its {@link Section#place()} inside. */
    record Page(String section, String place) {
    }

    /** As far back as it remembers. */
    private static final int LIMIT = 64;

    private final List<Page> pages = new ArrayList<>();
    private int at = -1;

    /** Records {@code now} as a new step if it is not the page the history is on. */
    void observe(Page now) {
        if (at >= 0 && pages.get(at).equals(now)) {
            return;
        }
        while (pages.size() > at + 1) {
            pages.remove(pages.size() - 1);
        }
        pages.add(now);
        if (pages.size() > LIMIT) {
            pages.remove(0);
        }
        at = pages.size() - 1;
    }

    boolean canGoBack() {
        return at > 0;
    }

    boolean canGoForward() {
        return at >= 0 && at < pages.size() - 1;
    }

    /** @return the page one step back, now the current one; null if there is none */
    Page back() {
        if (!canGoBack()) {
            return null;
        }
        return pages.get(--at);
    }

    /** @return the page one step forward, now the current one; null if there is none */
    Page forward() {
        if (!canGoForward()) {
            return null;
        }
        return pages.get(++at);
    }
}
