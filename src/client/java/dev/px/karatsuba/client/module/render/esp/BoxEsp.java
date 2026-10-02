package dev.px.karatsuba.client.module.render.esp;

import dev.px.core.render.Render;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.render.EspModule;

import java.util.List;

/** An outline around each hitbox. */
public final class BoxEsp extends EspMode {

    private final NumberSetting<Float> lineWidth = number("Box Line Width", 2f, 0.5f, 3.5f).step(0.1f);

    public BoxEsp() {
        super(EspModule.Mode.BOX, "3D Box");
    }

    @Override
    public void render(EspModule esp, List<GameEntity> targets, float partialTicks) {
        for (GameEntity entity : targets) {
            Render.boxOutline(entity.getHitbox(), lineWidth.getFloat(), esp.colorFor(entity));
        }
    }
}
