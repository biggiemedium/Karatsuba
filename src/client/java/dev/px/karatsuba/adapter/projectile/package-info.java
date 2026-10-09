/**
 * The projectile library ({@code dev.px.projectile}), wired to 1.21.4.
 *
 * <p>The library supplies mechanisms &mdash; stepping a projectile in any order,
 * the line test against blocks and entities, the spread bound, an aim solver
 * &mdash; and asks the client for the facts. This package is those facts, each
 * read from the game's bytecode for this version, not the wiki.
 *
 * <table>
 *   <caption>What is here</caption>
 *   <tr><td>{@link dev.px.karatsuba.adapter.projectile.Projectiles}</td><td>where a module gets the kit</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.projectile.ProjectileKit}</td><td>everything wired; the entry
 *       point for Trajectories, BowAim and pearl-following modules</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.projectile.ProjectileType}</td><td>gravity, drag, step order
 *       and entity margin per kind of projectile</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.projectile.Shot}</td><td>an item's launch and the flight it
 *       takes</td></tr>
 *   <tr><td>{@code ProjectileBlocks}</td><td>whole collision shapes, and water for drag</td></tr>
 * </table>
 *
 * <p>Underneath: {@code game.entity.ProjectileTracker} for projectiles in the air,
 * and Core's prediction for {@code lookahead()}.
 *
 * <p><b>When Mojang changes something</b>, change the one rule that changed, in
 * {@code ProjectileType} or {@code MinecraftProjectileKit}; the projectile README's
 * section 6 explains how.
 */
package dev.px.karatsuba.adapter.projectile;
