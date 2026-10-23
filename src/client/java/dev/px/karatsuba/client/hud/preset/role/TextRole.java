package dev.px.karatsuba.client.hud.preset.role;

/**
 * What a piece of HUD text is, so the preset can decide how it looks
 * ({@code hud/DESIGN.md} §3.2). Elements name a role, never a colour or a font.
 */
public enum TextRole {
    /** What the element is mainly about: a module's name, a toast's message. */
    PRIMARY,
    /** Supporting text: a version, an artist, a step being worked on. */
    SECONDARY,
    /** Something inactive, and only that. Never a lower level than this. */
    TERTIARY,
    /** A reading that changes: "42ms", "19.8". Usually in a fixed-width slot. */
    VALUE,
    /** What a value is: "Ping", "X". Legacy draws it in the accent; Apple as secondary. */
    LABEL,
    /** The heading of a title block: a track's title. */
    TITLE,
    /** The line under a title: a track's artist. */
    SUBTITLE
}
