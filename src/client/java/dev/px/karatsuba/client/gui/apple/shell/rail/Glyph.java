package dev.px.karatsuba.client.gui.apple.shell.rail;

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
    /** One person: an account. */
    PERSON,
    /** Three sheets stacked: profiles. */
    LAYERS,
    /** A plus: add. */
    ADD,
    /** A cross: remove. */
    REMOVE,
    /** Two overlapping sheets: copy. */
    COPY,
    /** A house: home. */
    HOME,
    /** A blade on a crossguard: combat. */
    SWORD,
    /** Two chevrons pointing on: movement. */
    MOVEMENT,
    /** A cube in outline: building. */
    CUBE,
    /** A pickaxe: mining. */
    PICKAXE,
    /** A chest with its lid and latch: the inventory. */
    CHEST,
    /** A wrench: utility. */
    WRENCH,
    /** A robot's head: automation. */
    ROBOT,
    /** A magnifier: search. */
    SEARCH,
    /** A chevron pointing left: back. */
    BACK,
    /** A chevron pointing right: forward. */
    FORWARD;

    /** @return the glyph for a category's icon key ({@code Categories}), or {@link #MODULES} for one it does not know */
    public static Glyph forIcon(String icon) {
        return switch (icon == null ? "" : icon) {
            case "sword" -> SWORD;
            case "movement" -> MOVEMENT;
            case "cube" -> CUBE;
            case "pickaxe" -> PICKAXE;
            case "chest" -> CHEST;
            case "eye" -> EYE;
            case "wrench" -> WRENCH;
            case "robot" -> ROBOT;
            default -> MODULES;
        };
    }
}
