package dev.px.karatsuba.client.gui.apple.shell;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;

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

    /** @return the page title in the header: the section's name unless it is showing something more particular */
    default String heading() {
        return title();
    }

    /**
     * @return where the section is inside itself, for the back and forward history:
     *         Modules' category, say. Empty for a section with one place. Read each
     *         frame; a change is recorded as a step in the history.
     */
    default String place() {
        return "";
    }

    /** Goes back to a {@link #place()} it reported earlier, from the history. */
    default void restore(String place) {
    }

    /**
     * Goes back to a place from the history, moving the way the history moved:
     * {@code direction} is 1 forward, -1 back, 0 for a jump with no motion (arriving
     * from another section, which crossfades instead).
     */
    default void restore(String place, int direction) {
        restore(place);
    }

    /**
     * @return whether the sidebar's search searches this section's own things
     *         (modules, settings) rather than filtering the sidebar's entries
     */
    default boolean ownsSearch() {
        return false;
    }

    /** Enter in the sidebar's search, while the section owns it: open the first thing found. @return whether it did */
    default boolean submitSearch() {
        return false;
    }

    /** @return whether the section slides between its own places itself (pushed pages), so the shell should not */
    default boolean slidesPlaces() {
        return false;
    }

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

    /**
     * Draws the section over the game, under the toolbar, faded by {@code alpha} (0 to
     * 1) as the window steps aside. The section applies the fade itself, so that glass
     * can be drawn outside it: a pass refuses inside any alpha push
     * ({@code gui/apple/DESIGN.md} §1.4), and glass forms by lensing, not opacity (§6.5).
     */
    default void drawScreen(float width, float height, float alpha) {
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

    /**
     * Draws the glass behind the section's toolbar, before the toolbar's own fade and
     * slide, since no pass runs inside either ({@code gui/apple/DESIGN.md} §1.4).
     *
     * @param alpha  0 to 1: how present the toolbar is; glass forms by that much (§6.5)
     * @param shiftY how far the toolbar is drawn from where it is laid out, as it arrives
     */
    default void drawToolbarGlass(float alpha, float shiftY) {
    }

    /** @return the toolbar's own content for this section, beside the rail's buttons; null for none */
    default Widget toolbar() {
        return null;
    }
}
