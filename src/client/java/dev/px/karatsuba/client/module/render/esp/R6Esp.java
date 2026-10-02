package dev.px.karatsuba.client.module.render.esp;

import dev.px.core.math.Vec3;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.render.EspModule;

import java.util.List;

/**
 * A flat frame standing up around each entity, turned to face the camera, with a
 * health bar beside it &mdash; the Rainbow Six look.
 *
 * <p>Built from the world-space quad and line primitives rather than a 2D overlay,
 * so the frame sits in the world at the entity's real size and shrinks with
 * distance like everything else.
 */
public final class R6Esp extends EspMode {

    private static final float FRAME_WIDTH = 2f;
    private static final float OUTLINE_WIDTH = 1f;

    /** Gap between the frame and the bar, and the bar's width, in blocks. */
    private static final double BAR_GAP = 0.15d;
    private static final double BAR_WIDTH = 0.04d;

    private static final Color BAR_BACKGROUND = Color.ofFloats(0.2f, 0.2f, 0.2f, 0.7f);
    private static final Color BAR_OUTLINE = Color.ofFloats(0f, 0f, 0f, 0.9f);

    private final NumberSetting<Float> espScale = number("ESP Scale", 1.15f, 1f, 2f).step(0.05f)
            .describe("How much larger than the hitbox the frame is drawn");

    public R6Esp() {
        super(EspModule.Mode.R6, "R6");
    }

    @Override
    public void render(EspModule esp, List<GameEntity> targets, float partialTicks) {
        Vec3 camera = Render.cameraPosition();

        for (GameEntity entity : targets) {
            Vec3 base = entity.getPosition();
            Vec3 right = rightOf(camera, base);
            if (right == null) {
                continue;
            }
            double halfWidth = entity.getWidth() * espScale.getFloat() / 2d;
            double height = entity.getHeight() * espScale.getFloat();

            frame(base, right, -halfWidth, halfWidth, height, FRAME_WIDTH, esp.colorFor(entity));
            healthBar(base, right, halfWidth + BAR_GAP, height, entity.getHealthFraction());
        }
    }

    private static void healthBar(Vec3 base, Vec3 right, double left, double height, float health) {
        double outer = left + BAR_WIDTH;

        panel(base, right, left, outer, 0d, height, BAR_BACKGROUND);
        if (health > 0f) {
            panel(base, right, left, outer, 0d, height * health, healthColor(health));
        }
        frame(base, right, left, outer, height, OUTLINE_WIDTH, BAR_OUTLINE);
    }

    /** A filled vertical rectangle in the camera-facing plane through {@code base}. */
    private static void panel(Vec3 base, Vec3 right, double from, double to,
                              double bottom, double top, Color color) {
        Render.quad3D(
                point(base, right, from, bottom),
                point(base, right, to, bottom),
                point(base, right, to, top),
                point(base, right, from, top),
                color);
    }

    /** A vertical rectangle's outline, from the ground up, in the same plane. */
    private static void frame(Vec3 base, Vec3 right, double from, double to,
                              double height, float thickness, Color color) {
        Vec3 bottomLeft = point(base, right, from, 0d);
        Vec3 bottomRight = point(base, right, to, 0d);
        Vec3 topRight = point(base, right, to, height);
        Vec3 topLeft = point(base, right, from, height);

        Render.line3D(bottomLeft, bottomRight, thickness, color);
        Render.line3D(bottomRight, topRight, thickness, color);
        Render.line3D(topRight, topLeft, thickness, color);
        Render.line3D(topLeft, bottomLeft, thickness, color);
    }

    private static Vec3 point(Vec3 base, Vec3 right, double across, double up) {
        return base.add(right.scale(across)).add(0d, up, 0d);
    }

    /**
     * The horizontal direction that is "right" as seen from the camera, so the
     * frame faces the viewer and the bar always sits on their right.
     *
     * @return the unit vector, or null when the camera is directly above or below
     *         the entity and there is no horizontal direction to face
     */
    private static Vec3 rightOf(Vec3 camera, Vec3 target) {
        double dx = target.getX() - camera.getX();
        double dz = target.getZ() - camera.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4) {
            return null;
        }
        // Looking along +Z (south), the viewer's right is -X (west).
        return Vec3.of(-dz / length, 0d, dx / length);
    }

    /** Green at full health through yellow and orange to red, as Leapfrog shades it. */
    private static Color healthColor(float health) {
        if (health > 0.5f) {
            return Color.ofFloats(2f * (1f - health), 1f, 0f, 1f);
        }
        if (health > 0.25f) {
            return Color.ofFloats(1f, 0.5f + 2f * (health - 0.25f), 0f, 1f);
        }
        return Color.ofFloats(1f, 2f * health, 0f, 1f);
    }
}
