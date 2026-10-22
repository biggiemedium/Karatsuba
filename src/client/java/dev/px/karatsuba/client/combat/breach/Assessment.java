package dev.px.karatsuba.client.combat.breach;

import java.util.List;

/**
 * What it takes to get a crystal onto one target.
 *
 * @param scenario   how they are defended
 * @param trapped    whether the head ring and roof are up: they cannot step or jump out,
 *                   so whatever is opened stays open to them
 * @param candidates the blocks to break, best first: in reach, then at the feet before the
 *                   face, then quickest to mine, then nearest; empty for {@link Scenario#OPEN}
 *                   and for a target with nothing to break
 */
public record Assessment(Scenario scenario, boolean trapped, List<Breach> candidates) {

    /** @return the block to break first, or null when there is none */
    public Breach best() {
        return candidates.isEmpty() ? null : candidates.get(0);
    }
}
