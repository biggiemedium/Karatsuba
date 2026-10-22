package dev.px.karatsuba.client.combat.breach;

/** How a target is defended, as far as crystals go. */
public enum Scenario {

    /** A crystal fits beside their feet already: nothing needs breaking. */
    OPEN,

    /** Walled at the feet, and a wall block can be broken to open a crystal hole. */
    SURROUNDED,

    /** Standing inside a block: mine it out first. */
    BURROWED,

    /** No feet-ring block will do (bedrock, or no obsidian or bedrock under it): only face blocks, if any. */
    SEALED
}
