/**
 * Core's shader service on the game's GL context.
 *
 * <table>
 *   <caption>What is here</caption>
 *   <tr><td>{@link dev.px.karatsuba.adapter.render.shader.Shaders}</td><td>the install: backend,
 *       loader (editable folder over the jar), F3+T reload, the adapter's global uniforms</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.render.shader.GlShaderBackend}</td><td>Core's
 *       {@code ShaderBackend}: compiles, binds, uploads uniforms and samplers</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.render.shader.ShaderPasses}</td><td>what the client draws
 *       with: a 2D rectangle or the whole screen, and the world's background (sky)</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.render.shader.SceneTexture}</td><td>a copy of the
 *       screen, for effects that read what is behind them</td></tr>
 *   <tr><td>{@link dev.px.karatsuba.adapter.render.shader.GlTexture}</td><td>how a Core
 *       {@code Texture} names its GL texture, so it can be sampled</td></tr>
 * </table>
 *
 * <p>The client registers shaders with {@code Core.shaders().register(...)} and draws
 * with {@code ShaderPasses}; nothing it calls names a game type. The GLSL
 * conventions (attribute names, UV origins, the uniforms each pass sets) are in
 * {@code assets/karatsuba/shaders/readme.md}.
 *
 * <p>The one rule this package keeps for the rest of the adapter: every piece of
 * GL state the game caches (texture units, framebuffer bindings, blend, depth,
 * cull, the bound vertex buffer) is changed through {@code GlStateManager} or
 * {@code RenderSystem}, or put back exactly, so the game's next draw trusts a
 * cache that is still true.
 */
package dev.px.karatsuba.adapter.render.shader;
