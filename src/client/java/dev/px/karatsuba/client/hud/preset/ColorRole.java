package dev.px.karatsuba.client.hud.preset;

/**
 * A colour that is not text, by what it means ({@code hud/DESIGN.md} §3.2). Each
 * preset maps every one; elements never pick a colour themselves.
 */
public enum ColorRole {
    /** The theme's accent, where the preset's emphasis rules allow it (§5b). */
    ACCENT,
    /** The filled part of a meter or a track. */
    METER_FILL,
    /** The empty part of a meter or a track. */
    METER_TRACK,
    /** A reading in good health. Never the only cue (§2.3). */
    GOOD,
    /** A reading worth watching. */
    FAIR,
    /** A reading in bad health. */
    BAD,
    /** A thin line between parts. */
    SEPARATOR
}
