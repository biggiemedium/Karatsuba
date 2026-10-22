package dev.px.karatsuba.client.service.dig;

/**
 * How {@code DigService} digs, for every caller: the mining half of the client's
 * rules, as {@code ActionProfile} is the clicking half.
 *
 * <p>Filled by Client Settings &gt; Mining ({@code MiningSettings}), which hands
 * itself to the service at boot. Until then these defaults apply: dig the way the
 * game does, nothing early.
 */
public interface DigProfile {

    /**
     * Look at the block and swing only as a dig starts and finishes, which are the
     * only dig packets Grim's break checks read; the head and arm are free between.
     */
    default boolean quiet() {
        return false;
    }

    /** Finish blocks early, inside the allowance Grim's FastBreak gives. */
    default boolean earlyBreak() {
        return false;
    }

    /** Milliseconds of Grim's 1000 ms early allowance to spend. */
    default int earlyBudget() {
        return 600;
    }

    /** The least share of a dig to finish at; the server refuses under 70%. */
    default double minProgress() {
        return 0.8d;
    }
}
