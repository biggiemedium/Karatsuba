package dev.px.karatsuba.adapter.world;

/**
 * How a block stands up to an explosion, as far as a wall is concerned.
 *
 * <p>Read through {@link Blocks#blastResistance}. Only full blocks that an end
 * crystal or a bed cannot break count as anything but {@link #NONE}: those are the
 * blocks a hole is safe to stand in when it is walled with them.
 */
public enum BlastResistance {

    /** An explosion breaks it, or it is not a full block. Not a safe wall. */
    NONE,

    /** Survives explosions but can be mined: obsidian, crying obsidian, ender chests. */
    RESISTANT,

    /** Cannot be broken at all: bedrock. */
    UNBREAKABLE
}
