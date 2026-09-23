/**
 * The lease mechanism every arbiter in the package above is built on.
 *
 * <p>Separated because it is infrastructure rather than an arbiter: it knows
 * nothing about slots, velocity or swings, only about who asked for something,
 * how badly, and whether they are still asking.
 *
 * <p>The rule that makes the whole approach work is here rather than in any one
 * service: <b>a claim expires unless it is renewed.</b> A module keeps a thing
 * by continuing to want it and loses it by going quiet, so a module that is
 * switched off, returns early or throws cannot leave anything held. There is a
 * {@code release} and nothing depends on it being called.
 */
package dev.px.karatsuba.client.service.claim;
