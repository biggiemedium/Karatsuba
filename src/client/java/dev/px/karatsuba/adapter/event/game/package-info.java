/**
 * The events that name a game concept, and therefore cannot live in Core.
 *
 * <p>Core ships the events every Minecraft version has &mdash; tick, render,
 * input, chat, world, screen &mdash; and stops there, because the whole reason it
 * compiles without Minecraft on the classpath is that no event in it mentions a
 * packet, a sound or a camera. This package is where those go.
 *
 * <h2>Where things are</h2>
 * Split by what the event is about, with the same names as the {@code mixin/}
 * folders that post them, so an event and its call site are found under one word.
 *
 * <table>
 *   <caption>What each folder holds</caption>
 *   <tr><td>{@code player/}</td><td>the local player's tick, movement and
 *       jump, what it hits, and what hits it</td></tr>
 *   <tr><td>{@code network/}</td><td>packets, and the semantic events read out
 *       of them: the movement packet, client commands, a container closing</td></tr>
 *   <tr><td>{@code render/}</td><td>the camera and what the game draws in the
 *       world</td></tr>
 *   <tr><td>{@code sound/}</td><td>what the client plays</td></tr>
 * </table>
 *
 * <h2>The shape every event here takes</h2>
 * <ol>
 *   <li><b>Core types on the outside.</b> A field is a {@code Vec3}, a
 *       {@code Vec2}, a {@code Box}, a {@code Vec3i} or a plain
 *       {@code String}/{@code int} &mdash; never a {@code net.minecraft} type.
 *       That is what lets a module in {@code dev.px.karatsuba.client} subscribe
 *       to one without importing the game, and it is why a module survives a
 *       mapping change that this package absorbs.</li>
 *   <li><b>A snapshot, not a handle.</b> The values are read once, when the event
 *       is posted. A handler that needs live state asks
 *       {@code adapter.game.Players} or {@code adapter.game.Entities}; what is
 *       carried here is what was true at the moment the event fired, which is
 *       what makes a PRE and a POST worth comparing.</li>
 *   <li><b>Lombok for the plumbing.</b> {@code @Getter} and
 *       {@code @RequiredArgsConstructor} write the accessors and the constructor,
 *       so an event is its field list and its documentation and nothing else.
 *       Anything hand-written in one of these classes is there because it does
 *       something &mdash; a derived value, a setter that also raises a flag.</li>
 * </ol>
 *
 * <h2>Choosing a base</h2>
 * <table>
 *   <caption>Which Core base an event extends</caption>
 *   <tr><th>Base</th><th>Use when</th></tr>
 *   <tr><td>{@code Event}</td><td>a pure notification &mdash;
 *       {@link dev.px.karatsuba.adapter.event.game.render.CameraEvent}, which is
 *       mutated rather than cancelled</td></tr>
 *   <tr><td>{@code CancellableEvent}</td><td>a handler may suppress the action
 *       &mdash; {@link dev.px.karatsuba.adapter.event.game.sound.SoundPlayEvent},
 *       {@link dev.px.karatsuba.adapter.event.game.render.BlockOutlineEvent}</td></tr>
 *   <tr><td>{@code StagedEvent}</td><td>the same call site fires before and after
 *       the action &mdash;
 *       {@link dev.px.karatsuba.adapter.event.game.player.PlayerUpdateEvent},
 *       {@link dev.px.karatsuba.adapter.event.game.player.PlayerMoveEvent}</td></tr>
 * </table>
 *
 * <p>A handler may declare a supertype and receive every subclass, which is why
 * {@link dev.px.karatsuba.adapter.event.game.network.PacketEvent} is one class with two
 * nested halves: {@code @Subscribe} on {@code PacketEvent} sees both directions,
 * and on {@code PacketEvent.Send} sees only outgoing.
 *
 * <h2>Threading</h2>
 * Everything here is posted on the game thread except
 * {@link dev.px.karatsuba.adapter.event.game.network.PacketEvent.Receive}, which arrives
 * on a Netty worker. That one is marked, and the rule for it is the ordinary one:
 * hand anything touching game state to {@code Core.threads().sync(...)}.
 */
package dev.px.karatsuba.adapter.event.game;
