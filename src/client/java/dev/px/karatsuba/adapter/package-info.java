/**
 * The Minecraft half of the client: everything that knows what {@code net.minecraft} is.
 *
 * <p>Core is a library with no game on its classpath. {@code Render2D.drawRect} is
 * a signature and nothing else until something turns it into pixels; {@code Platform}
 * is five questions nobody can answer without a running game. This package answers
 * them. It is what gives Core its value, and it is the only place in the client
 * where a Minecraft import is load-bearing.
 *
 * <h2>What is installed</h2>
 * Core declares its seams as interfaces it never implements and events it never
 * posts. Each one below is one of those, filled in here:
 *
 * <ol>
 *   <li>{@link dev.px.karatsuba.adapter.MinecraftPlatform} &mdash; data directory,
 *       screen metrics, cursor, chat, username, in-game flag.</li>
 *   <li>{@link dev.px.karatsuba.adapter.render.NanoVGRender2D} &mdash; the 2D backend.</li>
 *   <li>{@link dev.px.karatsuba.adapter.render.WorldRender3D} &mdash; world drawing
 *       and projection.</li>
 *   <li>{@link dev.px.karatsuba.adapter.render.NanoVGFontProvider} &mdash; font loading
 *       for that backend.</li>
 *   <li>{@link dev.px.karatsuba.adapter.MinecraftLogger} &mdash; Core's log, routed
 *       into the game's rather than {@code System.out}.</li>
 *   <li>{@link dev.px.karatsuba.adapter.account.MinecraftAuthProvider} &mdash; the
 *       login flow and the session swap, which is the half of
 *       {@code AccountService} Core cannot supply.</li>
 *   <li>{@link dev.px.karatsuba.adapter.concurrent.GameThread} &mdash; the return
 *       path from {@code ThreadService}, without which background work has no safe
 *       way back onto the game thread.</li>
 *   <li>{@link dev.px.karatsuba.adapter.world.WorldPathSpace} &mdash; what
 *       {@code AStar} is allowed to ask about the world, which is what makes Core's
 *       spatial utilities reachable from a running game at all.</li>
 * </ol>
 *
 * <h2>The layer Core leaves out</h2>
 * Core's README ends with a list of what it does not include, and the first line
 * is "Adapter layer &mdash; {@code Player}, {@code World}, {@code Entity}". That
 * is deliberate: an entity type is the one thing that cannot be made version
 * independent, so Core declines to have one and the adapter supplies it.
 *
 * <p>{@code game/} is that layer. {@link dev.px.karatsuba.adapter.game.Players}
 * answers questions about the local player,
 * {@link dev.px.karatsuba.adapter.game.Entities} hands out
 * {@link dev.px.karatsuba.adapter.game.GameEntity} snapshots, and
 * {@link dev.px.karatsuba.adapter.game.Options} covers the game settings a module
 * may borrow. All three speak only Core's types &mdash; {@code Vec3}, {@code Vec2},
 * {@code Box}, {@code Color} &mdash; which is what lets the whole {@code client}
 * package compile without a single {@code net.minecraft} import.
 *
 * <p>Snapshots, not wrappers. A {@code GameEntity} is read once on the game thread
 * and is then a value: it cannot be dereferenced from a worker thread three frames
 * later, because there is nothing left to dereference.
 *
 * <p>{@code event/game/} is the same idea for events. Core ships the events every
 * version has and stops; anything naming a game concept &mdash; a player tick, a
 * packet, an attack &mdash; is declared here and posted from {@code mixin/}.
 *
 * <p>Plus the event bridges: Core's {@code TickEvent}, {@code Render2DEvent},
 * {@code ScreenEvent} and the rest are posted from here, because Core cannot see
 * the game's callbacks.
 *
 * <p>Two Core seams are deliberately empty. {@code PresenceProvider} and
 * {@code MediaProvider} are external services rather than game state &mdash; they
 * need credentials and a choice of vendor, and Core is identical with and without
 * them, which is why it treats both as optional.
 *
 * <h2>Layout</h2>
 * <table>
 *   <caption>What each folder is for</caption>
 *   <tr><td>{@code render/}</td><td>the NanoVG 2D backend, the world-space 3D
 *       backend, fonts, textures, and the GL state fence between NanoVG and the
 *       game</td></tr>
 *   <tr><td>{@code game/}</td><td>the player, entities and game settings, in Core's
 *       types &mdash; the adapter layer Core leaves out</td></tr>
 *   <tr><td>{@code event/}</td><td>game callbacks to Core events; {@code Keys} is
 *       the GLFW-to-Core translation table; {@code event/game/} holds the events
 *       that name a game concept and so cannot live in Core</td></tr>
 *   <tr><td>{@code mixin/}</td><td>only what Fabric API does not expose: raw
 *       keyboard and mouse input, the screen swap, and write access to the
 *       session</td></tr>
 *   <tr><td>{@code screen/}</td><td>the bare game screen Core's GUI and HUD editor
 *       are drawn into</td></tr>
 *   <tr><td>{@code asset/}</td><td>images and fonts out of the mod jar, decoded
 *       straight to the backend rather than through the resource manager</td></tr>
 *   <tr><td>{@code concurrent/}</td><td>the game thread, as something background
 *       work can hand a result back to</td></tr>
 *   <tr><td>{@code account/}</td><td>the Microsoft login chain and the session
 *       swap it ends in</td></tr>
 *   <tr><td>{@code world/}</td><td>block and grid queries, in the types Core's
 *       spatial utilities are written against</td></tr>
 * </table>
 *
 * <p><strong>The dependency runs one way.</strong> {@code adapter} never imports
 * {@code client}. A module is free to use Minecraft directly, but nothing in this
 * package may reach up into the client implementation, or the seam stops being a
 * seam.
 */
package dev.px.karatsuba.adapter;
