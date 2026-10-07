/**
 * The arbiters: one owner for each thing two modules might both want.
 *
 * <p>This package exists to kill one specific failure, which is the main reason
 * module classes in a utility client turn into a mesh. Two modules both assign
 * the held slot. Neither can see the other. The winner is whichever handler
 * happened to run last, which depends on registration order and changes when
 * someone adds a third module. The fix people reach for is a direct reference
 * &mdash; {@code if (Modules.get(KillAura.class).isEnabled()) return;} &mdash;
 * and now module A imports module B, the dependency graph is a mesh, and nothing
 * about it shows up as a problem in a diff.
 *
 * <p>So contended things get an owner, and modules <b>ask</b> instead of
 * writing:
 *
 * <pre>{@code
 * Core.rotations().request(this, aim, RotationPriority.HIGH, 30f);   // Core's
 * Services.inventory().request(this, sword, ClaimPriority.HIGH);
 * Services.motion().scaleHorizontal(this, 1.3d, ClaimPriority.NORMAL);
 * Services.actions().submit(this, Action.attack(target.getId()).priority(ClaimPriority.HIGH));
 * }</pre>
 *
 * <p>No module names another module anywhere in that. Priorities are how they
 * argue, and the argument is settled in one place that can be printed.
 *
 * <h2>The claim model</h2>
 * Every service here uses {@link dev.px.karatsuba.client.service.claim.Claims}, which
 * is Core's {@code RotationService} lease model lifted out so the four of them
 * behave identically:
 *
 * <ul>
 *   <li><b>A claim lasts one tick unless renewed.</b> A module keeps a thing by
 *       continuing to want it, and loses it by going quiet &mdash; which is what
 *       happens when it is switched off, returns early, or throws. There is a
 *       {@code release}, and nothing depends on it being called. <b>A module
 *       cannot leak a claim.</b></li>
 *   <li><b>Highest priority wins; ties go to whoever asked first</b> and stay
 *       there while they keep asking. Most-recent-wins would make two equal
 *       modules trade the thing back and forth once a tick, which looks exactly
 *       like a bug.</li>
 *   <li><b>Claims are dropped on a world change</b>, because a slot number or a
 *       target id from the last server means nothing on this one.</li>
 * </ul>
 *
 * <h2>What is arbitrated, and what is not</h2>
 * <table>
 *   <caption>Where each contended thing is owned</caption>
 *   <tr><th>Thing</th><th>Owner</th><th>Contest</th></tr>
 *   <tr><td>Rotation</td><td>Core {@code RotationService}</td><td>one winner</td></tr>
 *   <tr><td>Hotbar slot</td><td>{@link dev.px.karatsuba.client.service.InventoryService}</td>
 *       <td>one winner</td></tr>
 *   <tr><td>Velocity</td><td>{@link dev.px.karatsuba.client.service.MotionService}</td>
 *       <td>one winner per axis group</td></tr>
 *   <tr><td>Attack / right-click</td><td>{@link dev.px.karatsuba.client.service.ActionService}</td>
 *       <td>one winner per button, per tick</td></tr>
 * </table>
 *
 * <h2>The action service does more than arbitrate</h2>
 * Attacks, block clicks and item uses go through
 * {@link dev.px.karatsuba.client.service.ActionService} as a whole
 * {@link dev.px.karatsuba.client.service.action.Action}: the module says what it
 * wants, and the service turns to it, gets the item into a hand, checks reach and
 * look against what the server knows and sends it where vanilla would, for every
 * module the same way. How careful it is lives in one profile, the
 * {@code Interact} module. Actions are one-shot: each lasts until the end of the
 * tick it was submitted for, and every one hears its
 * {@link dev.px.karatsuba.client.service.action.Outcome}.
 *
 * <p><b>Reads are not arbitrated and never will be.</b> {@code Players},
 * {@code Entities}, {@code Targets}, {@code Armor} and the rest of
 * {@code adapter.game} are stateless and answer everyone the same. Only writes
 * contend.
 *
 * <p><b>One-shot actions are not arbitrated either.</b> Throwing a pearl with
 * {@code Inventories.useFromHotbar} selects, uses and restores inside a single
 * call; it has finished before anything else could disagree with it. This
 * package is for <em>sustained</em> ownership across ticks.
 *
 * <h2>Why these are services and not "managers"</h2>
 * A manager in most clients is a static singleton holding three unrelated jobs
 * &mdash; reading game state, deriving state over time, and arbitrating writes
 * &mdash; with no lifecycle to reset any of it. Those three have different
 * correctness requirements and live in three different places here: reads in
 * {@code adapter.game}, derived state and arbitration in a
 * {@link dev.px.core.service.Service}.
 *
 * <p>Registering into Core's own {@code ServiceContainer} rather than a
 * container of our own is what buys the lifecycle: dependency ordering, start
 * and stop, and rollback when one fails to come up. See
 * {@link dev.px.karatsuba.client.service.Services}.
 *
 * <h2>Writing a module against them</h2>
 * Ask every tick for as long as you want the thing, and do nothing to give it
 * back:
 *
 * <pre>{@code
 * @Subscribe
 * private void onTick(TickEvent event) {
 *     if (event.getStage() != Stage.PRE || !Players.present()) {
 *         return;
 *     }
 *     int sword = Inventories.findInHotbar("netherite_sword");
 *     if (sword != Inventories.NOT_FOUND) {
 *         Services.inventory().request(this, sword, ClaimPriority.HIGH);
 *     }
 * }
 * }</pre>
 *
 * <p>There is no {@code onDisable} to write. The module stops asking, the claim
 * expires, and the hotbar goes back to whatever the player had.
 */
package dev.px.karatsuba.client.service;
