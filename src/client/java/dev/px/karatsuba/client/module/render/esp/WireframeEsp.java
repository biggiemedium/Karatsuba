package dev.px.karatsuba.client.module.render.esp;

import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.render.EntityWireframe;
import dev.px.karatsuba.client.module.render.EspModule;

import java.util.List;

/**
 * Each entity's real model, as lines: limbs, head and armour, posed as the game
 * is posing them this frame.
 *
 * <p>The heaviest mode by some distance, since it runs every target's renderer a
 * second time. Fine for a handful of players; a farm full of animals at a long
 * range will be felt.
 */
public final class WireframeEsp extends EspMode {

    private static final float LINE_WIDTH = 1.5f;

    private final NumberSetting<Float> scale = number("Scale", 1f, 0.5f, 2f).step(0.05f)
            .describe("Size of the wireframe relative to the real model");

    public WireframeEsp() {
        super(EspModule.Mode.WIREFRAME, "WireFrame");
    }

    @Override
    public void render(EspModule esp, List<GameEntity> targets, float partialTicks) {
        for (GameEntity entity : targets) {
            EntityWireframe.draw(entity.getId(), partialTicks, scale.getFloat(), LINE_WIDTH, esp.colorFor(entity));
        }
    }
}
