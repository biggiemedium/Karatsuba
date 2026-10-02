package dev.px.karatsuba.client.module.render.esp;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Render3D;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.render.ColorUtil;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.render.EspModule;

import java.util.List;

/**
 * A flat rectangle on the screen around each entity: the classic 2D box.
 *
 * <p>Drawn in the HUD pass from the hitbox's eight corners projected onto the
 * screen, so the rectangle hugs the entity from any angle and stays the same
 * line weight at any distance. Being drawn over the finished frame is also what
 * puts it on top of walls.
 */
public final class Box2DEsp extends EspMode {

    private static final Color OUTLINE = Color.of(0, 0, 0, 160);
    private static final Color BAR_BACKGROUND = Color.of(0, 0, 0, 140);

    /** Gap between the box and the health bar, and the bar's width, in GUI pixels. */
    private static final float BAR_GAP = 2f;
    private static final float BAR_WIDTH = 2f;

    /** Boxes smaller than this are an entity too far away to be worth a rectangle. */
    private static final float MIN_SIZE = 2f;

    private final NumberSetting<Float> lineWidth = number("2D Line Width", 1f, 0.5f, 3f).step(0.1f);

    private final BooleanSetting outline = bool("Outline", false)
            .describe("A dark border either side of the line, so it reads against bright skies and snow");

    private final BooleanSetting healthBar = bool("Health Bar", true)
            .describe("A bar down the left side, green to red");

    public Box2DEsp() {
        super(EspModule.Mode.BOX_2D, "2D Box");
    }

    @Override
    public void render(EspModule esp, List<GameEntity> targets, float partialTicks) {
        // Screen space only: everything happens in render2D.
    }

    @Override
    public void render2D(EspModule esp, List<GameEntity> targets, float partialTicks) {
        float width = lineWidth.getFloat();

        for (GameEntity entity : targets) {
            Render3D.ScreenBounds bounds = Render.project(entity.getHitbox());
            if (bounds.getWidth() < MIN_SIZE || bounds.getHeight() < MIN_SIZE) {
                // Behind the camera comes back as an empty box; tiny means far off.
                continue;
            }
            float x = bounds.minX;
            float y = bounds.minY;
            float w = bounds.getWidth();
            float h = bounds.getHeight();

            if (outline.isOn()) {
                Render.rectOutline(x - width, y - width, w + width * 2f, h + width * 2f, width, OUTLINE);
                Render.rectOutline(x + width, y + width, w - width * 2f, h - width * 2f, width, OUTLINE);
            }
            Render.rectOutline(x, y, w, h, width, esp.colorFor(entity));

            if (healthBar.isOn() && entity.getMaxHealth() > 0f) {
                drawHealthBar(entity, x - BAR_GAP - BAR_WIDTH, y, h);
            }
        }
    }

    /** A vertical bar that empties from the top, so what is left sits at the entity's feet. */
    private static void drawHealthBar(GameEntity entity, float x, float y, float height) {
        float fill = height * entity.getHealthFraction();
        Render.rect(x - 1f, y - 1f, BAR_WIDTH + 2f, height + 2f, BAR_BACKGROUND);
        Render.rect(x, y + height - fill, BAR_WIDTH, fill,
                ColorUtil.health(entity.getHealth(), entity.getMaxHealth()));
    }
}
