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
 *   <li>{@link dev.px.karatsuba.adapter.platform.MinecraftPlatform} &mdash; data directory,
 *       screen metrics, cursor, chat, username, in-game flag.</li>
 *   <li>{@link dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D} &mdash; the 2D backend.</li>
 *   <li>{@link dev.px.karatsuba.adapter.render.WorldRender3D} &mdash; world drawing
 *       and projection.</li>
 *   <li>{@link dev.px.karatsuba.adapter.render.nanovg.NanoVGFontProvider} &mdash; font loading
 *       for that backend.</li>
 *   <li>{@link dev.px.karatsuba.adapter.platform.MinecraftLogger} &mdash; Core's log, routed
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
 *   <li>{@link dev.px.karatsuba.adapter.movement.MotionBridge} &mdash; what feeds
 *       {@code SimulationService}: the player's own movement, scored every tick
 *       against Core's simulation under a physics profile read from the player's
 *       attributes.</li>
 *   <li>{@link dev.px.karatsuba.adapter.network.MinecraftPacketDescriber} &mdash;
 *       what Core needs to read a packet: the timeline recorder, and the roles
 *       {@code Core.tps()}, {@code Core.lag()}, {@code Core.server()} and
 *       {@code Core.anticheat()} read. Core's own {@code PacketEvent} and
 *       {@code MotionUpdateEvent} are posted from the network and player
 *       mixins.</li>
 *   <li>{@link dev.px.karatsuba.adapter.network.NetworkBridge} &mdash; what those
 *       services need that no packet carries: the channels a server registers,
 *       which Fabric decodes itself, and the world's tick rate.</li>
 * </ol>
 *
 * <h2>The one seam this package declares</h2>
 * {@link dev.px.karatsuba.adapter.screen.Overlay} is the exception to the list
 * above: a seam the adapter declares rather than one Core does, because being
 * drawn into a running game is something Core has no concept of. It is how the
 * client gets a frame and the input, and it is the reason nothing in this package
 * names a screen, a HUD element, a key map or a colour.
 *
 * <p>Everything that decides how the client <em>looks</em> or what a key
 * <em>does</em> is an {@code Overlay} implementation in
 * {@code dev.px.karatsuba.client.screen}. Everything that decides <em>when</em> a
 * frame happens and <em>which</em> physical key was struck is here. If a change is
 * about appearance or behaviour and it is being made in this package, it is being
 * made in the wrong one.
 *
 * <h2>The layer Core leaves out</h2>
 * Core's README ends with a list of what it does not include, and the first line
 * is "Adapter layer &mdash; {@code Player}, {@code World}, {@code Entity}". That
 * is deliberate: an entity type is the one thing that cannot be made version
 * independent, so Core declines to have one and the adapter supplies it.
 *
 * <p>{@code game/} is that layer. {@link dev.px.karatsuba.adapter.game.player.Players}
 * answers questions about the local player,
 * {@link dev.px.karatsuba.adapter.game.entity.Entities} hands out
 * {@link dev.px.karatsuba.adapter.game.entity.GameEntity} snapshots, and
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
 *   <tr><td>{@code platform/}</td><td>the two seams Core needs before anything
 *       else: the {@code Platform} itself and the logger, both handed to
 *       {@code Core.builder(...)}</td></tr>
 *   <tr><td>{@code render/}</td><td>the world-space 3D backend;
 *       {@code render/nanovg/} is the 2D one and everything that exists only
 *       because it is NanoVG &mdash; fonts, textures, the GL state fence</td></tr>
 *   <tr><td>{@code game/}</td><td>the adapter layer Core leaves out, in Core's
 *       types. {@code game/player/} is the local player and what it does,
 *       {@code game/entity/} is everything else alive plus the crosshair,
 *       {@code game/item/} is what is held and worn; {@code Options} is the
 *       game's own settings</td></tr>
 *   <tr><td>{@code event/}</td><td>game callbacks to Core events, and the frame
 *       loop; {@code event/game/} holds the events that name a game concept and so
 *       cannot live in Core, split into {@code player/}, {@code network/},
 *       {@code render/} and {@code sound/}</td></tr>
 *   <tr><td>{@code input/}</td><td>GLFW to Core: {@code Keys} is the code-to-enum
 *       table, {@code InputBridge} decides whether a keystroke goes to the bus or
 *       to an overlay, {@code ActionBinds} runs the binds Core's own routing
 *       cannot</td></tr>
 *   <tr><td>{@code mixin/}</td><td>only what Fabric API does not expose: raw
 *       keyboard and mouse input, the screen swap, and write access to the
 *       session. Split by what is mixed into, with the same names as
 *       {@code event/game/} where the two overlap: {@code input/},
 *       {@code network/}, {@code player/}, {@code render/}, {@code sound/}, and
 *       {@code game/} for the client object and its options</td></tr>
 *   <tr><td>{@code screen/}</td><td>the bare game screen, and the {@code Overlay}
 *       seam the client draws through</td></tr>
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
