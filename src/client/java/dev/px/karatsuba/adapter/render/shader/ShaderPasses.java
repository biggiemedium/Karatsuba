package dev.px.karatsuba.adapter.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.px.core.Core;
import dev.px.core.math.Vec3;
import dev.px.core.render.Texture;
import dev.px.core.shader.ShaderService;
import dev.px.core.shader.UniformProvider;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws with one of the client's shaders: the geometry Core's {@code use(...)} body
 * cannot supply on this game.
 *
 * <p>Core's {@code ShaderService.use(name, uniforms, body)} binds a program and runs
 * a body that draws. Neither of our backends can be that body: NanoVG binds its own
 * program for every shape, and the 3D backend draws through the game's. So a pass
 * here draws its own quad, with the shader bound, and names nothing of the game:
 *
 * <pre>{@code
 * ShaderPasses.rect("frosted", x, y, width, height,
 *         u -> u.sampler("uScene", ShaderPasses.scene(), 0).set("uRadius", 8f));
 * }</pre>
 *
 * <h2>The passes</h2>
 * <table>
 *   <caption>Where each pass draws, and what it adds to the uniforms</caption>
 *   <tr><th>Pass</th><th>Call from</th><th>Uniforms set</th></tr>
 *   <tr><td>{@link #rect}, {@link #screen}</td><td>{@code Render2DEvent},
 *       {@code WorldOverlayEvent}, an overlay's {@code render}</td>
 *       <td>{@code uProjection} (scaled units to clip space), {@code uRect}
 *       (x, y, width, height), {@code uScale} (the GUI scale)</td></tr>
 *   <tr><td>{@link #sky}</td><td>{@code SkyRenderEvent}, {@code Render3DEvent}</td>
 *       <td>{@code uProjection} (identity), {@code uInverseProjection},
 *       {@code uInverseView}, {@code uCameraPosition}</td></tr>
 *   <tr><td>{@link #overlay}</td><td>{@code Render3DEvent}, {@code HandRenderEvent}</td>
 *       <td>{@code uProjection} (identity)</td></tr>
 * </table>
 *
 * <p>Every pass also gets Core's builtins ({@code uTime}, {@code uResolution},
 * {@code uMouse}) and the adapter's globals ({@code uViewport}, the framebuffer in
 * pixels, and {@code uPartialTicks}); see {@link Shaders}. The vertex shader
 * {@code core/quad.vsh} fits every pass.
 *
 * <h2>When a pass draws nothing</h2>
 * Unlike Core's {@code use}, which runs its body unshaded, a pass with no working
 * program skips the draw: its quad unshaded would be a white rectangle. Each pass
 * returns whether it drew, so a caller can draw a plain fallback instead.
 *
 * <p><b>Render thread only.</b>
 */
public final class ShaderPasses {

    private static final Matrix4f ORTHO = new Matrix4f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Matrix4f INVERSE_PROJECTION = new Matrix4f();
    private static final Matrix4f INVERSE_VIEW = new Matrix4f();

    // Core reads matrices as column-major float arrays, which is JOML's get(float[]).
    private static final float[] ORTHO_VALUES = new float[16];
    private static final float[] IDENTITY_VALUES = IDENTITY.get(new float[16]);
    private static final float[] INVERSE_PROJECTION_VALUES = new float[16];
    private static final float[] INVERSE_VIEW_VALUES = new float[16];

    private static final PassState STATE = new PassState();

    /** Shaders already reported as refused mid-clip, so the warning is once, not per frame. */
    private static final Set<String> REFUSED = new HashSet<>();

    private ShaderPasses() {
    }

    // --------------------------------------------------------------- 2D

    /**
     * Draws a screen-space rectangle with the shader, in the scaled units Core's
     * {@code Render} draws in.
     *
     * <p>The quad's UV runs from (0, 0) at the top left to (1, 1) at the bottom
     * right, the way a NanoVG image is stored, so a HUD texture samples upright.
     *
     * <p>Inside the 2D frame this paints what NanoVG has drawn so far first, then
     * draws, then starts the frame again, so the pass lands in order: over what came
     * before it, under what comes after. It cannot do that inside a clip, transform
     * or alpha push, and refuses (with one warning) rather than draw out of order.
     *
     * @param uniforms this draw's own values, applied after the pass's; may be null
     * @return whether it drew
     */
    public static boolean rect(String shader, float x, float y, float width, float height,
                               UniformProvider uniforms) {
        if (!RenderSystem.isOnRenderThread() || width <= 0f || height <= 0f
                || !Core.shaders().isReady(shader)) {
            return false;
        }
        NanoVGRender2D nanoVG = MinecraftAdapter.render2D();
        boolean stepped = false;
        if (nanoVG != null && nanoVG.isInFrame()) {
            if (!nanoVG.suspend()) {
                if (REFUSED.add(shader)) {
                    Core.log().warn("Shader pass '" + shader + "' was asked to draw inside a clip,"
                            + " transform or alpha push, which the 2D frame cannot step out of; skipped");
                }
                return false;
            }
            stepped = true;
        }
        try {
            float screenWidth = Core.platform().getScreenWidth();
            float screenHeight = Core.platform().getScreenHeight();
            ORTHO.setOrtho(0f, screenWidth, screenHeight, 0f, -1f, 1f).get(ORTHO_VALUES);
            float scale = Core.platform().getScreenScale();

            BufferBuilder buffer = Tessellator.getInstance()
                    .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buffer.vertex(x, y, 0f).texture(0f, 0f).color(-1);
            buffer.vertex(x, y + height, 0f).texture(0f, 1f).color(-1);
            buffer.vertex(x + width, y + height, 0f).texture(1f, 1f).color(-1);
            buffer.vertex(x + width, y, 0f).texture(1f, 0f).color(-1);

            draw(shader, buffer, false, u -> {
                u.matrix("uProjection", ORTHO_VALUES)
                        .set("uRect", x, y, width, height)
                        .set("uScale", scale);
                if (uniforms != null) {
                    uniforms.contribute(u);
                }
            });
        } finally {
            if (stepped) {
                nanoVG.resume();
            }
        }
        return true;
    }

    /** {@link #rect} over the whole screen. */
    public static boolean screen(String shader, UniformProvider uniforms) {
        return rect(shader, 0f, 0f, Core.platform().getScreenWidth(), Core.platform().getScreenHeight(),
                uniforms);
    }

    /**
     * What is on screen right now, as a texture to sample. Call it from a pass's
     * uniforms, which run after the frame so far has been painted; see
     * {@link SceneTexture}.
     */
    public static Texture scene() {
        return SceneTexture.capture();
    }

    /** {@link #scene()} with its mip levels built, for blurring by {@code textureLod}. */
    public static Texture sceneMipmapped() {
        return SceneTexture.captureMipmapped();
    }

    // --------------------------------------------------------------- 3D

    /**
     * Fills the background of the world with the shader: every pixel nothing has
     * been drawn on yet, at the far plane.
     *
     * <p>The quad covers the screen at depth 1, depth tested, so terrain, entities
     * and everything else that wrote depth stay in front. The UV is the screen
     * position, (0, 0) at the bottom left, so {@code uv * 2 - 1} is the clip-space
     * point; {@code core/sky.glsl} turns that into a view direction from the
     * inverse matrices this pass sets.
     *
     * <p>The matrices are the ones the game has set up right now, which the vanilla
     * sky dome draws with too: the camera's rotation and the world projection. So
     * this belongs in {@code SkyRenderEvent} (in place of the vanilla sky, under
     * clouds, rain and everything else) or {@code Render3DEvent} (over clouds and
     * rain, which write no depth).
     *
     * @return whether it drew
     */
    public static boolean sky(String shader, UniformProvider uniforms) {
        if (!RenderSystem.isOnRenderThread() || !Core.shaders().isReady(shader)) {
            return false;
        }
        INVERSE_PROJECTION.set(RenderSystem.getProjectionMatrix()).invert().get(INVERSE_PROJECTION_VALUES);
        INVERSE_VIEW.set(RenderSystem.getModelViewMatrix()).invert().get(INVERSE_VIEW_VALUES);
        net.minecraft.util.math.Vec3d eye = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vec3 camera = Vec3.of(eye.x, eye.y, eye.z);

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(-1f, -1f, 1f).texture(0f, 0f).color(-1);
        buffer.vertex(1f, -1f, 1f).texture(1f, 0f).color(-1);
        buffer.vertex(1f, 1f, 1f).texture(1f, 1f).color(-1);
        buffer.vertex(-1f, 1f, 1f).texture(0f, 1f).color(-1);

        draw(shader, buffer, true, u -> {
            u.matrix("uProjection", IDENTITY_VALUES)
                    .matrix("uInverseProjection", INVERSE_PROJECTION_VALUES)
                    .matrix("uInverseView", INVERSE_VIEW_VALUES)
                    .set("uCameraPosition", camera);
            if (uniforms != null) {
                uniforms.contribute(u);
            }
        });
        return true;
    }

    /**
     * Covers the whole framebuffer with the shader, over everything already drawn,
     * with no depth test: for compositing an offscreen mask back onto the picture
     * ({@link Silhouettes}).
     *
     * <p>The UV is the screen position with GL's origin, (0, 0) at the bottom left,
     * which is also how a framebuffer texture is laid out: {@code texture(uMask, vUv)}
     * reads the mask pixel under this one. Works from any world or hand pass, since
     * it uses no matrices.
     *
     * @return whether it drew
     */
    public static boolean overlay(String shader, UniformProvider uniforms) {
        if (!RenderSystem.isOnRenderThread() || !Core.shaders().isReady(shader)) {
            return false;
        }
        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(-1f, -1f, 0f).texture(0f, 0f).color(-1);
        buffer.vertex(1f, -1f, 0f).texture(1f, 0f).color(-1);
        buffer.vertex(1f, 1f, 0f).texture(1f, 1f).color(-1);
        buffer.vertex(-1f, 1f, 0f).texture(0f, 1f).color(-1);

        draw(shader, buffer, false, u -> {
            u.matrix("uProjection", IDENTITY_VALUES);
            if (uniforms != null) {
                uniforms.contribute(u);
            }
        });
        return true;
    }

    // --------------------------------------------------------------- shared

    /**
     * Draws the buffer with the shader bound, inside the pass's own state.
     *
     * <p>{@code BufferRenderer.draw} draws with whatever program is bound, which is
     * ours inside {@code use}. Afterwards the game's record of the bound vertex
     * buffer is cleared along with the binding, or its next draw in the same format
     * would trust a vertex array that is no longer bound.
     *
     * <p>The buffer is finished before anything can throw, and closed here if the
     * draw never took it (a uniform provider that threw), so the tessellator is
     * never left holding an unfinished one.
     */
    private static void draw(String shader, BufferBuilder buffer, boolean depthTested, UniformProvider uniforms) {
        ShaderService shaders = Core.shaders();
        BuiltBuffer built = buffer.end();
        boolean[] drawn = new boolean[1];
        STATE.save();
        try {
            PassState.blended(depthTested);
            shaders.use(shader, uniforms, () -> {
                drawn[0] = true;
                BufferRenderer.draw(built);
            });
        } finally {
            if (!drawn[0]) {
                built.close();
            }
            VertexBuffer.unbind();
            STATE.restore();
        }
    }
}
