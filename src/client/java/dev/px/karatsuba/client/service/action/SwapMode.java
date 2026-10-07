package dev.px.karatsuba.client.service.action;

/** How an {@link Action} that needs an item gets it into the hand. */
public enum SwapMode {

    /**
     * Select the slot, act, and select the old one back, telling the server of
     * both in the same call through {@code Services.inventory().silently}, never
     * the same slot twice running. The player's hotbar never visibly moves.
     */
    SILENT,

    /**
     * Claim the slot through {@code Services.inventory()} and act once it is held.
     * The hotbar visibly follows, and goes back when nothing asks any more.
     */
    HOLD,

    /** Never switch: act only when the item is already in a hand. */
    OFF
}
