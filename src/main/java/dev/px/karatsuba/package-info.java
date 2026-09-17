/**
 * Karatsuba: a Minecraft utility client built on Core.
 *
 * <p>The code is split in two, and the split is the whole architecture:
 *
 * <dl>
 *   <dt>{@code dev.px.karatsuba.client}</dt>
 *   <dd>The client itself &mdash; modules, commands, HUD elements, screens.
 *       Written against Core's API and the adapter's seams, and containing no
 *       {@code net.minecraft} import anywhere in it.</dd>
 *
 *   <dt>{@code dev.px.karatsuba.adapter}</dt>
 *   <dd>The Minecraft half. Core is a library with no game on its classpath:
 *       {@code Render2D.drawRect} is a signature until something turns it into
 *       pixels, and {@code Platform} is questions nobody can answer without a
 *       running game. The adapter answers them, and is what gives Core its value.</dd>
 * </dl>
 *
 * <p><strong>The dependency runs one way.</strong> {@code client} uses
 * {@code adapter}; {@code adapter} never uses {@code client}. Two rules follow from
 * it and both are checkable by grep: no {@code net.minecraft} in {@code client}, no
 * {@code dev.px.karatsuba.client} in {@code adapter}.
 *
 * <p>{@link dev.px.karatsuba.KaratsubaEntrypoint} is the single exception, and it
 * lives here rather than in either package for exactly that reason: an entrypoint
 * implements a Fabric interface <em>and</em> starts the client, so it has to see
 * both. Putting it above them keeps the rule below it absolute.
 *
 * <p>Both live in {@code src/client}, because this is a client-only mod and Loom's
 * split source sets keep that code off a server where it could never run. This
 * source set holds only {@code fabric.mod.json} and this file.
 *
 * <p>Core itself is the {@code :core} subproject &mdash; a local copy of
 * <a href="https://github.com/biggiemedium/Minecraft-Client-Core">Minecraft-Client-Core</a>,
 * editable in place. Its {@code README.md} is the API reference; section 8 is the
 * package map and section 9 is the list of what an adapter must supply.
 */
package dev.px.karatsuba;
