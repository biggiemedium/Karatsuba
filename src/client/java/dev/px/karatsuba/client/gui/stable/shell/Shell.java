package dev.px.karatsuba.client.gui.stable.shell;

import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.frame.Reveal;

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
}
