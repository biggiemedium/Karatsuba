package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.render.ColorUtil;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;

import java.util.List;

/**
 * Outlines nearby entities in the world.
 *
 * <p>The worked example for <b>{@code Render3D}</b>, the half of the render facade
 * that needs a camera. {@code Render.boxOutline} and {@code Render.tracer} take
 * world coordinates and Core hands them to whatever backend the adapter installed
 * &mdash; this file never learns that a projection matrix exists, and it never
 * mentions a screen coordinate.
 *
 * <p>It is also where several other systems meet, which is why it is worth reading
 * as a whole rather than for the drawing:
 *
 * <ul>
 *   <li><b>Settings</b> register by being declared, and five of the nine types are
 *       here. {@code visibleWhen} hides the tracer colour when tracers are off,
 *       so the panel never shows a control that does nothing.</li>
 *   <li><b>The event bus</b> delivers {@link Render3DEvent} to an annotated method,
 *       and stops delivering it the moment the module is switched off. There is no
 *       {@code if (!isEnabled()) return} here because there does not need to be.</li>
 *   <li><b>The adapter seam</b> supplies the entities. {@link GameEntity} is a
 *       snapshot in Core's own types, so nothing below is tied to a game version.</li>
 * </ul>
 */
@ModuleInfo(name = "ESP", description = "Outlines entities through the world", category = "Render")
public final class EspModule extends Module {

    private final MultiEnumSetting<Target> targets =
            multi("Targets", Target.class, Target.PLAYERS, Target.HOSTILES)
                    .describe("Which entities are worth drawing");

    private final EnumSetting<Shape> shape = enumOf("Shape", Shape.BOX);

    private final NumberSetting<Double> range = decimal("Range", 64d, 8d, 128d)
            .describe("How far out to look, in blocks");

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f).step(0.1f);

    private final BooleanSetting healthTint = bool("Health Tint", true)
            .describe("Fade the outline from green to red as a target is hurt");

    private final ColorSetting neutral = color("Neutral", Color.of(120, 200, 255))
            .describe("Used when the health tint is off, and for anything with no health");

    private final ColorSetting friends = color("Friends", Color.of(120, 255, 150));

    private final BooleanSetting tracers = bool("Tracers", false)
            .describe("Draw a line from the crosshair to each target");

    private final ColorSetting tracerColor = color("Tracer Colour", Color.of(255, 255, 255, 120))
            .visibleWhen(tracers);

    /**
     * Draws one frame.
     *
     * <p>Synchronous, and it has to be: the camera and the projection are only
     * valid inside the game's world-render callback, which is what posts this
     * event. Queueing the work for later would draw it against the wrong matrices.
     */
    @Subscribe
    private void onRender3D(Render3DEvent event) {
        List<GameEntity> found = Entities.nearby(event.getPartialTicks(), range.getDouble());

        for (GameEntity entity : found) {
            if (!wanted(entity)) {
                continue;
            }
            Color color = colorFor(entity);

            switch (shape.get()) {
                case BOX -> Render.boxOutline(entity.getHitbox(), thickness.getFloat(), color);
                case FILLED -> Render.boxFilledOutline(entity.getHitbox(),
                        color.withAlpha(40), color, thickness.getFloat());
                case SPHERE -> Render.sphereOutline(entity.getPosition().add(0d, 1d, 0d),
                        1f, 16, thickness.getFloat(), color);
            }

            if (tracers.isOn()) {
                Render.tracer(entity.getEyePosition(), thickness.getFloat(), tracerColor.resolve());
            }
        }
    }

    /** @return whether this entity is one of the kinds the user asked for */
    private boolean wanted(GameEntity entity) {
        if (entity.isFriend()) {
            // Friends are drawn whenever players are, and in their own colour. A
            // separate toggle would be a fourth way to say the same thing.
            return targets.has(Target.PLAYERS);
        }
        if (entity.isPlayer()) {
            return targets.has(Target.PLAYERS);
        }
        return targets.has(entity.isHostile() ? Target.HOSTILES : Target.PASSIVES);
    }

    /**
     * @return the outline colour for one entity
     *
     * <p>Friend beats health: who someone is matters more than how hurt they are,
     * and a friend fading to red reads as a warning about the wrong thing.
     */
    private Color colorFor(GameEntity entity) {
        if (entity.isFriend()) {
            return friends.resolve();
        }
        if (!healthTint.isOn() || entity.getMaxHealth() <= 0f) {
            return neutral.resolve();
        }
        return ColorUtil.health(entity.getHealth(), entity.getMaxHealth());
    }

    /** Extra text after the name in the ArrayList, so the mode is visible without opening the GUI. */
    @Override
    public String getDisplayInfo() {
        return shape.displayValue();
    }

    public enum Target {
        PLAYERS,
        HOSTILES,
        PASSIVES
    }

    public enum Shape {
        BOX,
        FILLED,
        SPHERE
    }
}
