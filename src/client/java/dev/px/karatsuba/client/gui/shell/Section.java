package dev.px.karatsuba.client.gui.shell;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;

import java.util.Set;

/**
 * One destination in the GUI: Modules, HUD, Layout, Themes, and later
 * Navigation. The shell shows one at a time, switched from its rail.
 *
 * <p>A section owns its content and its state; the shell owns the window, the
 * rail, the switching and the motion. So adding one is writing this interface
 * and a line in {@link Sections}: nothing about the shell changes, and every
 * section gets the same window, the same header style, the same spacing and the
 * same keys (Ctrl and its number, Escape to back out) for free.
 *
 * <p>Most sections live inside the window. A {@link #fullscreen()} one (the
 * Layout editor) has the whole screen: the window steps aside and the section
 * draws over the game, with the shell's floating toolbar for getting back.
 */
public interface Section {

    /** Where a section sits on the rail. */
    enum Group {
        /** The things you work on, in order. Navigation goes here. */
        TOP,
        /** Things about the GUI itself, at the foot of the rail. */
        BOTTOM
    }

    /** @return a stable id, for remembering which section was open */
    String id();

    /** @return the name on the rail and in the section's header: one short word */
    String title();

    Glyph glyph();

    default Group group() {
        return Group.TOP;
    }

    /** @return the section's content, built once and kept, so its state survives switching away */
    Widget content();

    /** @return whether the section takes the whole screen rather than the window */
    default boolean fullscreen() {
        return false;
    }

    /** Called each frame the section shows, before layout. */
    default void tick() {
    }

    /** Called when the section is switched to, and away from. */
    default void entered() {
    }

    default void left() {
    }

    /** One step back, for Escape. @return whether there was a step to take inside the section */
    default boolean back() {
        return false;
    }

    /** Ctrl+F: puts the caret in the section's search box. @return whether it has one */
    default boolean find() {
        return false;
    }

    /** A character typed with nothing focused: a section with a search box takes it there. */
    default boolean typed(char character) {
        return false;
    }

    // ---------------------------------------------- fullscreen sections only

    /** Draws the section over the game, under the toolbar. */
    default void drawScreen(float width, float height) {
    }

    /** Input the toolbar did not take. @return whether the section took it */
    default boolean press(float x, float y, MouseButton button) {
        return false;
    }

    default void release(float x, float y) {
    }

    default boolean scroll(float amount) {
        return false;
    }

    default boolean key(Key key, Set<Modifier> modifiers) {
        return false;
    }

    default void modifiers(Set<Modifier> modifiers) {
    }

    /** @return the toolbar's own content for this section, beside the rail's buttons; null for none */
    default Widget toolbar() {
        return null;
    }
}
