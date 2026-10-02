/**
 * The client itself: the modules, commands, HUD elements and screens that make
 * Karatsuba a thing rather than a framework.
 *
 * <p>Everything here is written against Core's API. Core supplies the machinery
 * &mdash; the event bus, settings that register by being declared, config
 * profiles, the command dispatcher, the HUD layout engine, the click GUI &mdash;
 * and this package supplies the content.
 *
 * <h2>Layout</h2>
 * The top level is the boot sequence and nothing else: {@code Karatsuba} and this
 * file. Everything else lives in a folder with the content it belongs to, and each of
 * those folders holds its own catalogue &mdash; a flat list with no logic in it,
 * exposing {@code registerAll(Core)} like every other. Adding content is always
 * the same two steps, and both of them happen in one folder.
 *
 * <table>
 *   <caption>Where things go</caption>
 *   <tr><th>Adding</th><th>Write it in</th><th>Then list it in</th></tr>
 *   <tr><td>a module</td><td>{@code module/<category>/}</td>
 *       <td>{@link dev.px.karatsuba.client.module.Modules}</td></tr>
 *   <tr><td>a category</td><td>&mdash;</td>
 *       <td>{@link dev.px.karatsuba.client.module.Categories}</td></tr>
 *   <tr><td>a command</td><td>{@code command/}</td>
 *       <td>{@link dev.px.karatsuba.client.command.Commands}</td></tr>
 *   <tr><td>a HUD element</td><td>{@code hud/element/}</td>
 *       <td>{@link dev.px.karatsuba.client.hud.HudElements}</td></tr>
 *   <tr><td>a keyboard action</td><td>&mdash;</td>
 *       <td>{@link dev.px.karatsuba.client.input.Binds}</td></tr>
 *   <tr><td>a colour</td><td>&mdash;</td>
 *       <td>{@link dev.px.karatsuba.client.theme.Themes}</td></tr>
 *   <tr><td>a screen or widget</td><td>{@code gui/}, {@code gui/component/}</td>
 *       <td>nothing &mdash; screens are opened, not registered</td></tr>
 *   <tr><td>something drawn over the game</td><td>{@code screen/}</td>
 *       <td>{@link dev.px.karatsuba.client.screen.Overlays}</td></tr>
 *   <tr><td>an arbiter for something two modules want</td><td>{@code service/}</td>
 *       <td>{@link dev.px.karatsuba.client.service.Services}</td></tr>
 * </table>
 *
 * <h2>Modules never write contended state directly</h2>
 * The hotbar slot, the player's velocity and the swing are all things more than
 * one module may want in the same tick. Assigning them directly is how a client
 * becomes a mesh of modules checking whether other modules are enabled, so each
 * has an owner in {@code service/} and modules <em>ask</em>:
 *
 * <pre>{@code
 * Services.slots().request(this, sword, ClaimPriority.HIGH);
 * Services.motion().scaleHorizontal(this, 1.3d, ClaimPriority.NORMAL);
 * Core.rotations().request(this, aim, RotationPriority.HIGH, 30f);
 * }</pre>
 *
 * <p>A claim lasts one tick unless renewed, so nothing has to remember to let
 * go. See {@code service/package-info} for the whole model.
 *
 * <h2>What the client draws, and when</h2>
 * The adapter owns the frame and the keystroke; it owns nothing about what is in
 * one. {@code screen/} is where that meets: an
 * {@link dev.px.karatsuba.adapter.screen.Overlay} is handed the frame and the
 * input, and decides what to draw and what a key means. Registration order there
 * is draw order, and input is offered in the reverse, so the thing on top is the
 * thing asked first.
 *
 * <p>That is why the HUD editor's appearance and key map are in
 * {@link dev.px.karatsuba.client.screen.HudOverlay} and
 * {@code HudEditorChrome} rather than in the adapter: Core supplies the editing
 * arithmetic, the adapter supplies the cursor and the keystroke, and what it
 * <em>looks like</em> is content, the same as a module or a HUD element.
 *
 * <p>{@link dev.px.karatsuba.client.Karatsuba} is the only file that knows the
 * order any of it happens in, and it never needs editing to add content.
 * {@code config/} holds how state reaches disk.
 *
 * <h2>Reaching the game from here</h2>
 * <strong>Nothing in this package imports {@code net.minecraft}, and that is
 * checked rather than hoped for</strong> &mdash; grep it and the answer is zero.
 * The game is reached through the seams in {@code adapter.game}:
 *
 * <table>
 *   <caption>Where game state comes from</caption>
 *   <tr><th>You want</th><th>Ask</th></tr>
 *   <tr><td>the local player</td>
 *       <td>{@link dev.px.karatsuba.adapter.game.player.Players}</td></tr>
 *   <tr><td>everything else alive</td>
 *       <td>{@link dev.px.karatsuba.adapter.game.entity.Entities} &rarr;
 *           {@link dev.px.karatsuba.adapter.game.entity.GameEntity}</td></tr>
 *   <tr><td>a setting the player owns</td>
 *       <td>{@link dev.px.karatsuba.adapter.game.Options}</td></tr>
 *   <tr><td>blocks and the grid</td>
 *       <td>{@link dev.px.karatsuba.adapter.world.Voxels}</td></tr>
 *   <tr><td>a game-specific event</td>
 *       <td>{@code adapter.event.game}, e.g.
 *           {@link dev.px.karatsuba.adapter.event.game.player.PlayerUpdateEvent}</td></tr>
 * </table>
 *
 * <p>The rule is not purity for its own sake. A module that takes a
 * {@code net.minecraft.entity.Entity} has to be rewritten the next time a mapping
 * changes, and the same three-line null dance gets copied into every file that
 * wants a position. Both problems live in one package instead, and a module reads
 * as what it does rather than as how it reaches the game.
 *
 * <p>Missing a seam is a reason to add one, not a reason to import the game here.
 * The dependency runs one way and only one way: {@code client} uses
 * {@code adapter}, {@code adapter} never uses {@code client}, and
 * {@link dev.px.karatsuba.KaratsubaEntrypoint} is the single composition root
 * above both.
 *
 * <h2>What does not belong here</h2>
 * Re-implementing something Core owns. Positioning, scaling, persistence, hit
 * testing and z-order are the HUD engine's job; clamping and stepping a value is
 * the setting's job; drawing a slider is a {@code SettingRenderer}'s job; writing
 * JSON is {@code ConfigService}'s job.
 */
package dev.px.karatsuba.client;
