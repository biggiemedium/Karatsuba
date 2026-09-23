/**
 * The NanoVG 2D backend, and everything that only exists because of it.
 *
 * <p>Core's {@code Render2D} is twenty-eight signatures that mean nothing until
 * something turns them into pixels. This is that something: a vector rasteriser
 * that draws rounded rectangles, arcs and stroked outlines as real curves rather
 * than as quads somebody approximated.
 *
 * <p>Everything here is downstream of that one choice. The font and the texture
 * are NanoVG handles rather than game resources, because NanoVG keeps its own
 * font atlas and its own GL textures; the state guard exists because NanoVG goes
 * straight to the driver while Minecraft caches what it believes the driver to
 * be. Swap the backend and this whole package goes with it.
 *
 * <p>{@code render.WorldRender3D} is deliberately <em>not</em> in here: it draws
 * in world space with the game's own tessellator and shares nothing with this
 * but the facade above both.
 */
package dev.px.karatsuba.adapter.render.nanovg;
