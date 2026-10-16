package dev.px.karatsuba.client.gui.stable.shell.rail;

/**
 * The GUI's icons, drawn from lines and shapes rather than images, so they take
 * any colour (the accent when chosen), stay sharp at any GUI scale, and need no
 * files. Each is drawn in a square by the look.
 */
public enum Glyph {
    /** Four tiles: the modules. */
    MODULES,
    /** A screen with readouts in its corners: the HUD. */
    HUD,
    /** Corner brackets round a crosshair: arranging things on screen. */
    LAYOUT,
    /** A circle split light and dark: themes. */
    THEMES,
    /** A compass needle: navigation, when it comes. */
    NAVIGATION,
    /** A curved arrow back: undo. */
    UNDO,
    /** A curved arrow forward: redo. */
    REDO,
    /** A magnet: snapping. */
    SNAP,
    /** An arrow turning back on itself: reset. */
    RESET,
    /** A pencil: edit. */
    EDIT,
    /** An eye: show. */
    EYE,
    /** A tick: done. */
    CHECK,
    /** A gear: settings. */
    SETTINGS,
    /** Two people: friends. */
    PEOPLE,
    /** A plus: add. */
    ADD,
    /** A cross: remove. */
    REMOVE,
    /** Two overlapping sheets: copy. */
    COPY,
    /** A house: home. */
    HOME
}
