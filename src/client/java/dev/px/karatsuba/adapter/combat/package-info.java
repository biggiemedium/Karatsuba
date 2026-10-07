/**
 * The combat library ({@code dev.px.combat}), wired to 1.21.4.
 *
 * <p>The library supplies mechanisms &mdash; ray traversal, sampled exposure,
 * branch-and-bound searches, hole prediction, placement planning, a damage
 * monitor &mdash; and asks the client for the facts: which blocks stop a ray, the
 * damage formula, armour, which blocks hold a crystal. This package is those
 * facts, each read from the game's own code for this version.
 *
 * <table>
 *   <caption>What is here</caption>
 *   <tr><td>{@link dev.px.karatsuba.adapter.combat.Combat}</td><td>where a module gets the kit</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.combat.CombatKit}</td><td>everything wired, typed so the
 *       {@code client} package never names a game class; the entry point for every aura</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.combat.GameBlocks}</td><td>block shapes for rays, and the
 *       cell tests: air, replaceable, crystal base, blast proof, support</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.combat.ExplosionProfile}</td><td>the damage curve, exposure
 *       grid and mitigation steps</td></tr>
 *   <tr><td>{@code CombatBridge}</td><td>crystals tracked on spawn; explosions and totem pops fed to
 *       the damage monitor</td></tr>
 * </table>
 *
 * <p>Underneath, and shared with anything else that uses Core's entities:
 * {@code game.entity.MinecraftEntitySource} and its trackers, and
 * {@code movement.EntityRules} for {@code Core.prediction()}. To send what a
 * search planned, {@code game.player.Interactions.click} takes the library's
 * {@code Click}.
 *
 * <p><b>When Mojang changes something</b>, the combat README's section 10 says
 * which rule to swap; each lives in one place here. Re-record a few test vectors
 * on the new version and replay them against
 * {@link dev.px.karatsuba.adapter.combat.ExplosionProfile#replayModel()}.
 */
package dev.px.karatsuba.adapter.combat;
