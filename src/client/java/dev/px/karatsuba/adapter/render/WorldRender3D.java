package dev.px.karatsuba.adapter.render;

import dev.px.core.math.Box;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.render.Color;
import dev.px.core.render.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.ShaderProgramKeys;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Core's world-space backend.
 *
 * <p>Split from {@link dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D NanoVGRender2D} for the reason Core gives: a 2D vector
 * library has no camera and no world space, so the two cannot be one backend.
 *
 * <p>Everything here draws in <em>camera-relative</em> coordinates. Minecraft
 * never uploads absolute world positions &mdash; at render distance the floats
 * would have lost their fractional bits &mdash; so a world position becomes a
 * vertex by subtracting the camera first. {@link #updateFrame(WorldRenderContext)}
 * captures the camera and matrices Fabric hands us each frame; nothing in this
 * class is meaningful outside a world render pass.
 */
public final class WorldRender3D implements Render3D {

    private static final int SPHERE_RINGS = 12;

    private final MinecraftClient client = MinecraftClient.getInstance();

    private Matrix4f positionMatrix = new Matrix4f();
    private Matrix4f projectionMatrix = new Matrix4f();
    private Vec3 cameraPosition = Vec3.ZERO;
    private Frustum frustum;

    private boolean depthTest = true;

    /**
     * Takes the camera and matrices for this frame.
     *
     * <p>Called by the event bridge immediately before Core's {@code Render3DEvent}
     * is posted, so every module handler draws against the frame it is being told about.
     */
    public void updateFrame(WorldRenderContext context) {
        Camera camera = context.camera();
        this.cameraPosition = Vec3.of(camera.getPos().x, camera.getPos().y, camera.getPos().z);
        this.positionMatrix = new Matrix4f(context.positionMatrix());
        this.projectionMatrix = new Matrix4f(context.projectionMatrix());
        this.frustum = context.frustum();
    }

    /**
     * This frame's view frustum, for culling what is drawn again offscreen
     * ({@code Silhouettes}). Null before the first world frame.
     */
    public Frustum frustum() {
        return frustum;
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void beginFrame(float partialTicks) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Outlines and tracers are meant to read as overlays, so back faces stay in.
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        // Every frame starts depth-tested, whatever the last one ended on. Without
        // this, one module drawing through walls would leave every module after it
        // drawing through walls too, from the next frame on.
        depthTest = true;
        applyDepthTest();
    }

    @Override
    public void endFrame() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1f);
    }

    @Override
    public void setDepthTest(boolean enabled) {
        this.depthTest = enabled;
        applyDepthTest();
    }

    private void applyDepthTest() {
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    // ---------------------------------------------------------------- shapes

    @Override
    public void box(Box box, Color color) {
        BufferBuilder buffer = begin(VertexFormat.DrawMode.QUADS);

        float x1 = rx(box.getMin().getX());
        float y1 = ry(box.getMin().getY());
        float z1 = rz(box.getMin().getZ());
        float x2 = rx(box.getMax().getX());
        float y2 = ry(box.getMax().getY());
        float z2 = rz(box.getMax().getZ());

        quad(buffer, color, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1); // north
        quad(buffer, color, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2); // south
        quad(buffer, color, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1); // west
        quad(buffer, color, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2); // east
        quad(buffer, color, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2); // bottom
        quad(buffer, color, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1); // top

        draw(buffer);
    }

    @Override
    public void boxOutline(Box box, float thickness, Color color) {
        RenderSystem.lineWidth(thickness);
        BufferBuilder buffer = begin(VertexFormat.DrawMode.DEBUG_LINES);

        float x1 = rx(box.getMin().getX());
        float y1 = ry(box.getMin().getY());
        float z1 = rz(box.getMin().getZ());
        float x2 = rx(box.getMax().getX());
        float y2 = ry(box.getMax().getY());
        float z2 = rz(box.getMax().getZ());

        // Four uprights, then the top and bottom rings.
        edge(buffer, color, x1, y1, z1, x1, y2, z1);
        edge(buffer, color, x2, y1, z1, x2, y2, z1);
        edge(buffer, color, x2, y1, z2, x2, y2, z2);
        edge(buffer, color, x1, y1, z2, x1, y2, z2);

        ring(buffer, color, x1, x2, y1, z1, z2);
        ring(buffer, color, x1, x2, y2, z1, z2);

        draw(buffer);
    }

    @Override
    public void boxFilledOutline(Box box, Color fill, Color outline, float thickness) {
        box(box, fill);
        boxOutline(box, thickness, outline);
    }

    @Override
    public void line(Vec3 from, Vec3 to, float thickness, Color color) {
        RenderSystem.lineWidth(thickness);
        BufferBuilder buffer = begin(VertexFormat.DrawMode.DEBUG_LINES);
        edge(buffer, color,
                rx(from.getX()), ry(from.getY()), rz(from.getZ()),
                rx(to.getX()), ry(to.getY()), rz(to.getZ()));
        draw(buffer);
    }

    @Override
    public void triangle(Vec3 a, Vec3 b, Vec3 c, Color color) {
        BufferBuilder buffer = begin(VertexFormat.DrawMode.TRIANGLES);
        vertex(buffer, color, a);
        vertex(buffer, color, b);
        vertex(buffer, color, c);
        draw(buffer);
    }

    @Override
    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Color color) {
        BufferBuilder buffer = begin(VertexFormat.DrawMode.QUADS);
        vertex(buffer, color, a);
        vertex(buffer, color, b);
        vertex(buffer, color, c);
        vertex(buffer, color, d);
        draw(buffer);
    }

    @Override
    public void sphere(Vec3 center, float radius, int segments, Color color) {
        BufferBuilder buffer = begin(VertexFormat.DrawMode.QUADS);

        for (int ring = 0; ring < SPHERE_RINGS; ring++) {
            double phi0 = Math.PI * ring / SPHERE_RINGS;
            double phi1 = Math.PI * (ring + 1) / SPHERE_RINGS;

            for (int segment = 0; segment < segments; segment++) {
                double theta0 = 2 * Math.PI * segment / segments;
                double theta1 = 2 * Math.PI * (segment + 1) / segments;

                vertex(buffer, color, spherePoint(center, radius, phi0, theta0));
                vertex(buffer, color, spherePoint(center, radius, phi1, theta0));
                vertex(buffer, color, spherePoint(center, radius, phi1, theta1));
                vertex(buffer, color, spherePoint(center, radius, phi0, theta1));
            }
        }
        draw(buffer);
    }

    @Override
    public void sphereOutline(Vec3 center, float radius, int segments, float thickness, Color color) {
        RenderSystem.lineWidth(thickness);
        BufferBuilder buffer = begin(VertexFormat.DrawMode.DEBUG_LINES);

        // Latitude rings and longitude arcs: a wireframe globe.
        for (int ring = 1; ring < SPHERE_RINGS; ring++) {
            double phi = Math.PI * ring / SPHERE_RINGS;
            for (int segment = 0; segment < segments; segment++) {
                double theta0 = 2 * Math.PI * segment / segments;
                double theta1 = 2 * Math.PI * (segment + 1) / segments;
                edge(buffer, color, spherePoint(center, radius, phi, theta0),
                        spherePoint(center, radius, phi, theta1));
            }
        }
        for (int segment = 0; segment < segments; segment++) {
            double theta = 2 * Math.PI * segment / segments;
            for (int ring = 0; ring < SPHERE_RINGS; ring++) {
                double phi0 = Math.PI * ring / SPHERE_RINGS;
                double phi1 = Math.PI * (ring + 1) / SPHERE_RINGS;
                edge(buffer, color, spherePoint(center, radius, phi0, theta),
                        spherePoint(center, radius, phi1, theta));
            }
        }
        draw(buffer);
    }

    @Override
    public void circle(Vec3 center, float radius, int segments, Color color) {
        BufferBuilder buffer = begin(VertexFormat.DrawMode.TRIANGLE_FAN);
        vertex(buffer, color, center);
        for (int segment = 0; segment <= segments; segment++) {
            vertex(buffer, color, discPoint(center, radius, segment, segments));
        }
        draw(buffer);
    }

    @Override
    public void circleOutline(Vec3 center, float radius, int segments, float thickness, Color color) {
        RenderSystem.lineWidth(thickness);
        BufferBuilder buffer = begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP);
        for (int segment = 0; segment <= segments; segment++) {
            vertex(buffer, color, discPoint(center, radius, segment, segments));
        }
        draw(buffer);
    }

    @Override
    public void cylinder(Vec3 base, float radius, float height, int segments, Color color) {
        BufferBuilder buffer = begin(VertexFormat.DrawMode.QUADS);
        Vec3 top = base.add(0d, height, 0d);

        for (int segment = 0; segment < segments; segment++) {
            Vec3 lowerNear = discPoint(base, radius, segment, segments);
            Vec3 lowerFar = discPoint(base, radius, segment + 1, segments);
            Vec3 upperNear = discPoint(top, radius, segment, segments);
            Vec3 upperFar = discPoint(top, radius, segment + 1, segments);

            vertex(buffer, color, lowerNear);
            vertex(buffer, color, upperNear);
            vertex(buffer, color, upperFar);
            vertex(buffer, color, lowerFar);
        }
        draw(buffer);
    }

    @Override
    public void tracer(Vec3 target, float thickness, Color color) {
        Camera camera = client.gameRenderer.getCamera();
        // Starting exactly at the camera puts the line inside the near plane, where
        // it clips away. A metre down the view vector is the usual compromise.
        org.joml.Vector3f look = camera.getHorizontalPlane();
        Vec3 origin = cameraPosition.add(look.x(), look.y(), look.z());
        line(origin, target, thickness, color);
    }

    // ------------------------------------------------------------ projection

    @Override
    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    @Override
    public Vec2 worldToScreen(Vec3 position) {
        Vector4f clip = new Vector4f(rx(position.getX()), ry(position.getY()), rz(position.getZ()), 1f);
        clip.mul(positionMatrix);
        clip.mul(projectionMatrix);

        // w is the view-space depth: at or behind the camera there is no screen
        // position, and Core's contract is to say so with null rather than a number.
        if (clip.w <= 0f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        float width = client.getWindow().getScaledWidth();
        float height = client.getWindow().getScaledHeight();

        return Vec2.of((ndcX * 0.5f + 0.5f) * width, (1f - (ndcY * 0.5f + 0.5f)) * height);
    }

    @Override
    public ScreenBounds project(Box box) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        boolean any = false;

        for (Vec3 corner : box.corners()) {
            Vec2 screen = worldToScreen(corner);
            if (screen == null) {
                continue;
            }
            any = true;
            minX = Math.min(minX, screen.getX());
            minY = Math.min(minY, screen.getY());
            maxX = Math.max(maxX, screen.getX());
            maxY = Math.max(maxY, screen.getY());
        }

        // Every corner behind the camera: an empty box, not a box spanning the screen.
        return any
                ? new ScreenBounds((float) minX, (float) minY, (float) maxX, (float) maxY)
                : new ScreenBounds(0f, 0f, 0f, 0f);
    }

    // ---------------------------------------------------------------- shared

    private static BufferBuilder begin(VertexFormat.DrawMode mode) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    private static void draw(BufferBuilder buffer) {
        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }

    private static void quad(BufferBuilder buffer, Color color,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(buffer, color, x1, y1, z1);
        vertex(buffer, color, x2, y2, z2);
        vertex(buffer, color, x3, y3, z3);
        vertex(buffer, color, x4, y4, z4);
    }

    private static void ring(BufferBuilder buffer, Color color,
                             float x1, float x2, float y, float z1, float z2) {
        edge(buffer, color, x1, y, z1, x2, y, z1);
        edge(buffer, color, x2, y, z1, x2, y, z2);
        edge(buffer, color, x2, y, z2, x1, y, z2);
        edge(buffer, color, x1, y, z2, x1, y, z1);
    }

    private static void edge(BufferBuilder buffer, Color color,
                             float x1, float y1, float z1, float x2, float y2, float z2) {
        vertex(buffer, color, x1, y1, z1);
        vertex(buffer, color, x2, y2, z2);
    }

    private void edge(BufferBuilder buffer, Color color, Vec3 from, Vec3 to) {
        edge(buffer, color,
                rx(from.getX()), ry(from.getY()), rz(from.getZ()),
                rx(to.getX()), ry(to.getY()), rz(to.getZ()));
    }

    private void vertex(BufferBuilder buffer, Color color, Vec3 position) {
        vertex(buffer, color, rx(position.getX()), ry(position.getY()), rz(position.getZ()));
    }

    private static void vertex(BufferBuilder buffer, Color color, float x, float y, float z) {
        buffer.vertex(x, y, z).color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static Vec3 spherePoint(Vec3 center, float radius, double phi, double theta) {
        return center.add(
                radius * Math.sin(phi) * Math.cos(theta),
                radius * Math.cos(phi),
                radius * Math.sin(phi) * Math.sin(theta));
    }

    private static Vec3 discPoint(Vec3 center, float radius, int segment, int segments) {
        double theta = 2 * Math.PI * segment / segments;
        return center.add(radius * Math.cos(theta), 0d, radius * Math.sin(theta));
    }

    // Camera-relative coordinates: see the class comment.
    private float rx(double x) {
        return (float) (x - cameraPosition.getX());
    }

    private float ry(double y) {
        return (float) (y - cameraPosition.getY());
    }

    private float rz(double z) {
        return (float) (z - cameraPosition.getZ());
    }
}
