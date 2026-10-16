package dev.px.karatsuba.client.screen;

import dev.px.karatsuba.adapter.screen.Overlay;

/**
 * One of the client's interfaces on screen: the Classic GUI or the Apple GUI.
 *
 * <p>Whichever is active (Interface settings) is the one the open binds reach, so
 * a bind never needs to know which look is in use; see {@link Overlays#active()}.
 */
public interface InterfaceOverlay extends Overlay {

    /** Opens on the section it was last on, or closes it if it is showing. */
    void toggle();

    /**
     * Opens on a section, or switches to it if open; closes it if that section is
     * already showing, so one key both opens and shuts it.
     *
     * @param sectionId the section, or null for the one last open
     */
    void open(String sectionId);

    /** Closes with its closing animation. */
    void close();
}
