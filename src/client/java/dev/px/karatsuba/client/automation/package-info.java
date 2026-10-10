/**
 * Automation: tasks the player starts and the client carries out &mdash; walking
 * somewhere, mining a block, climbing a ladder, eating, fighting &mdash; run on
 * Core's flow engine and the navigation library.
 *
 * <h2>Tasks are not modules</h2>
 * A module is switched on and stays on, reacting to the game every tick. A
 * {@link dev.px.karatsuba.client.automation.task.Task} is started, works toward
 * an end, and finishes, fails or is stopped. It is a request with its arguments
 * in it ("mine 100, 64, -20") that builds a Core {@code Step}: the unit the
 * flow engine runs, with fallbacks, retries, interrupts, timeouts and a live
 * view for free. Modules and tasks never touch each other: a task never
 * switches a module on, and both are built on the same services.
 *
 * <h2>The layers</h2>
 * <table>
 *   <caption>From the player's request down to the keys</caption>
 *   <tr><th>Layer</th><th>Where</th><th>Does</th></tr>
 *   <tr><td>front ends</td>
 *       <td>{@code command/TaskCommand} ({@code .task <kind> ...}), the Stop Task
 *           bind, the Task HUD element</td>
 *       <td>make a task from its kind and hand it over; read and control the one
 *           running</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.client.automation.Automation}</td>
 *       <td>here</td>
 *       <td>runs one task at a time as a flow, at the settings' priority; stops it
 *           when the player takes the keys; says how it ended</td></tr>
 *   <tr><td>{@code task/}</td>
 *       <td>here</td>
 *       <td>what each kind of task is: its {@code TaskType} (name, usage, how to
 *           read its arguments), listed in {@code Tasks}, and the steps it is made
 *           of</td></tr>
 *   <tr><td>{@code step/}</td>
 *       <td>here</td>
 *       <td>the building blocks, each doing one thing the way a player does it:
 *           {@code Eat}, {@code UseBlock}, {@code CloseContainer},
 *           {@code BreakBlock}, {@code Climb}, {@code Melee}, {@code Throw},
 *           {@code Equip}; and what they share: {@code Gaze} (how the head turns),
 *           {@code Sighting} (another player), {@code Ladder}</td></tr>
 *   <tr><td>{@code navigation/}</td>
 *       <td>here</td>
 *       <td>travel steps, wired to the client: the local planner under the
 *           settings' rules, the route rules (hazards, water), the player's state,
 *           and the slot a long-range pathfinder plugs into</td></tr>
 *   <tr><td>{@code view/}</td>
 *       <td>here</td>
 *       <td>the route drawn in the world</td></tr>
 *   <tr><td>flows, controls, rotations</td>
 *       <td>Core</td>
 *       <td>who holds the keys, the head and the buttons this tick</td></tr>
 *   <tr><td>services and sinks</td>
 *       <td>{@code service/} ({@code ActionService}, {@code InventoryService},
 *           {@code ControlClicks}); adapter {@code MovementKeys},
 *           {@code Rotations}</td>
 *       <td>every click, swap, key and turn written to the game the way Grim
 *           expects</td></tr>
 * </table>
 *
 * <h2>Looking like a player</h2>
 * Every step goes through the same few rules, so no task can break them:
 * <ul>
 *   <li><b>The head eases</b> onto what it wants ({@code Gaze}), and the camera
 *       itself turns where the game acts on the crosshair (breaking).</li>
 *   <li><b>Walking is forward.</b> A route's keys are meant for the yaw the head
 *       turns to, so the player faces where they go. Where something else has the
 *       head (a fight), the walk keeps toward what the head is on.</li>
 *   <li><b>Items are switched to in the open</b> through the inventory service:
 *       nothing is eaten or thrown from a slot the server does not see held.</li>
 *   <li><b>Clicks wait for the look.</b> Every click, attack and throw goes out
 *       through the action service, only once the rotation the server has looks
 *       at it, from within reach, at the start of a tick.</li>
 *   <li><b>The game does the doing</b> wherever it can: breaking, eating and
 *       climbing are vanilla's own, driven by held buttons and keys, so their
 *       speed and packets are a player's.</li>
 * </ul>
 *
 * <p>The rules every task runs under &mdash; priority, turning, fall height,
 * hazards, when to count as stuck, what to show &mdash; are one page of client
 * settings, {@code settings/automation/AutomationSettings}, read live.
 *
 * <h2>Adding a task</h2>
 * <ol>
 *   <li>If it needs something new done with the hands or feet, a step in
 *       {@code step/}: one thing, done the way a player does it.</li>
 *   <li>The task in {@code task/}: its steps put together with Core's
 *       {@code Flow} factories ({@code Task.of(...)} when it is one step), and a
 *       {@code TaskType} saying what it is called and how to read its
 *       arguments.</li>
 *   <li>A line in {@code Tasks}. {@code .task} picks it up; nothing else changes.</li>
 * </ol>
 *
 * <h2>Not yet</h2>
 * Long-range pathfinding. The local planner reaches a far goal a plan at a time,
 * walking only; it does not break, bridge or swim on the way. A pathfinder that
 * does &mdash; Baritone, as a {@code PathProvider} in the adapter &mdash; is
 * given to {@code Navigation.setLongRange}, and every task gets it without
 * changing.
 */
package dev.px.karatsuba.client.automation;
