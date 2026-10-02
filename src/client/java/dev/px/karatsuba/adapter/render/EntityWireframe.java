package dev.px.karatsuba.adapter.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.px.core.math.Vec3;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.mixin.render.RenderPhaseAccessor;
import dev.px.karatsuba.adapter.world.Voxels;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * An entity's own model, drawn as lines.
 *
 * <p>Core's 3D facade draws shapes, and a model is not a shape: it is whatever
 * the entity's renderer emits, which only the game knows how to produce. So this
 * runs the real renderer against a vertex sink that keeps positions and throws
 * everything else away, turns each quad it receives into its four edges, and
 * draws those as one batch of lines. Whatever the module's depth setting is at
 * the time applies, which is how a wireframe shows through walls.
 *
 * <p>Only model geometry is kept. A renderer also emits its name tag, the glowing
 * eyes of spiders and endermen, and whatever it is holding, through layers of
 * their own; those are recognised by layer name and dropped, because a wireframe
 * of a font is noise.
 *
 * <p>Call from inside a {@code Render3DEvent} handler, on the render thread.
 */
public final class EntityWireframe {

    private EntityWireframe() {
    }

    /**
     * Draws one entity's model as lines.
     *
     * @param entityId     the entity, by id, as a {@code GameEntity} carries it
     * @param partialTicks the frame's interpolation
     * @param scale        size relative to the real model, about its feet
     * @param thickness    line width, in pixels
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void draw(int entityId, float partialTicks, float scale, float thickness, Color color) {
        ClientWorld world = Voxels.world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        if (entity == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(entity);
        Lines lines = new Lines();

        try {
            EntityRenderState state = renderer.getAndUpdateRenderState(entity, partialTicks);
            Vec3d offset = renderer.getPositionOffset(state);
            Vec3 camera = Render.cameraPosition();

            // Camera-relative, like everything WorldRender3D draws: the renderer
            // writes positions through this stack, so they come out ready to use.
            MatrixStack matrices = new MatrixStack();
            matrices.translate(
                    lerp(entity.lastRenderX, entity.getX(), partialTicks) - camera.getX() + offset.x,
                    lerp(entity.lastRenderY, entity.getY(), partialTicks) - camera.getY() + offset.y,
                    lerp(entity.lastRenderZ, entity.getZ(), partialTicks) - camera.getZ() + offset.z);
            matrices.scale(scale, scale, scale);

            renderer.render(state, matrices, lines, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        } catch (RuntimeException ignored) {
            // A renderer that cannot cope with being run outside the entity pass
            // (some modded ones assume state set up there) loses its wireframe for
            // this frame rather than taking the whole frame down with it.
            return;
        }
        lines.draw(thickness, color);
    }

    private static double lerp(double from, double to, float progress) {
        return from + (to - from) * progress;
    }

    /** @return whether a layer carries model geometry rather than a label, eyes or a held item */
    private static boolean isModel(RenderLayer layer) {
        if (layer.getDrawMode() != VertexFormat.DrawMode.QUADS) {
            return false;
        }
        String name = ((RenderPhaseAccessor) layer).karatsuba$getName();
        return !(name.contains("text") || name.contains("eyes") || name.contains("item") || name.contains("shadow"));
    }

    /** Collects the edges of every model quad, then draws them as one batch. */
    private static final class Lines implements VertexConsumerProvider {

        /** Camera-relative endpoints, six floats per edge. */
        private final FloatArrayList edges = new FloatArrayList(4096);

        /** One collector per layer: each layer's quads arrive four vertices at a time, independently. */
        private final Map<RenderLayer, VertexConsumer> collectors = new IdentityHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (!isModel(layer)) {
                return Discard.INSTANCE;
            }
            return collectors.computeIfAbsent(layer, ignored -> new QuadCollector(edges));
        }

        void draw(float thickness, Color color) {
            if (edges.isEmpty()) {
                return;
            }
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.lineWidth(thickness);
            BufferBuilder buffer = Tessellator.getInstance()
                    .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            int red = color.getRed();
            int green = color.getGreen();
            int blue = color.getBlue();
            int alpha = color.getAlpha();
            float[] points = edges.elements();
            for (int i = 0; i < edges.size(); i += 3) {
                buffer.vertex(points[i], points[i + 1], points[i + 2]).color(red, green, blue, alpha);
            }

            BuiltBuffer built = buffer.endNullable();
            if (built != null) {
                BufferRenderer.drawWithGlobalProgram(built);
            }
        }
    }

    /**
     * Turns a stream of quad vertices into edges.
     *
     * <p>Every entity layer draws quads, and every vertex funnels through
     * {@link #vertex(float, float, float)} already transformed by the matrix
     * stack, whichever overload the model called. So counting to four is enough
     * to know a quad is complete.
     */
    private static final class QuadCollector implements VertexConsumer {

        private final FloatArrayList edges;
        private final float[] quad = new float[12];
        private int count;

        QuadCollector(FloatArrayList edges) {
            this.edges = edges;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            quad[count * 3] = x;
            quad[count * 3 + 1] = y;
            quad[count * 3 + 2] = z;
            if (++count == 4) {
                for (int corner = 0; corner < 4; corner++) {
                    int next = (corner + 1) % 4;
                    edges.add(quad[corner * 3]);
                    edges.add(quad[corner * 3 + 1]);
                    edges.add(quad[corner * 3 + 2]);
                    edges.add(quad[next * 3]);
                    edges.add(quad[next * 3 + 1]);
                    edges.add(quad[next * 3 + 2]);
                }
                count = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }

    /** Swallows a layer that is not model geometry. */
    private static final class Discard implements VertexConsumer {

        static final Discard INSTANCE = new Discard();

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }
}
