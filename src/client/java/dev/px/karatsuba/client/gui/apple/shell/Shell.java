package dev.px.karatsuba.client.gui.apple.shell;

import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.frame.Reveal;

import java.util.List;
import java.util.function.Supplier;

/** What a {@link Section} may ask of the GUI around it. */
public interface Shell {

    /** @return every section, in rail order */
    List<Section> sections();

    Section active();

    /** @return the section with this id, or null */
    Section section(String sectionId);

    /** Switches to a section, by id. Switching to the one already showing does nothing. */
    void show(String sectionId);

    /** Leaves a full-screen section for the section it was entered from. */
    void returnFromFullscreen();

    /**
     * Fades new content in over {@code region} whenever {@code reveal} starts,
     * for a section's own changes (another module chosen, say).
     */
    void veil(Reveal reveal, Supplier<Widget> region);

    /** @return the window's width now, for a section that lays out by it */
    float windowWidth();

    /** @return whether there is a page to go back to, and forward to, in the history */
    boolean canGoBack();

    boolean canGoForward();

    /** One step back in the history, and forward: ‹ and › in the header, Ctrl+[ and Ctrl+]. */
    void goBack();

    void goForward();

    /** @return what the sidebar's search holds: the one search field on screen (PLAN.md step 8c) */
    String search();

    /** Puts {@code text} in the sidebar's search, or clears it with an empty string. */
    void search(String text);

    /** Ctrl+F: puts the caret in the sidebar's search, its text selected. @return whether it could */
    boolean focusSearch();

    /** A character typed with nothing focused: it goes into the sidebar's search. @return whether it took it */
    boolean typeIntoSearch(char character);
}
