package dev.px.karatsuba.client.module.render.esp;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.render.EspModule;

import java.util.List;

/** A translucent box with an outline. Karatsuba's own, kept alongside Leapfrog's modes. */
public final class FilledEsp extends EspMode {

    private final NumberSetting<Float> lineWidth = number("Filled Line Width", 1.5f, 0.5f, 4f).step(0.1f);

    private final NumberSetting<Integer> fillOpacity = integer("Fill Opacity", 40, 0, 255)
            .describe("How solid the inside of the box is, 0 to 255");

    public FilledEsp() {
        super(EspModule.Mode.FILLED);
    }

    @Override
    public void render(EspModule esp, List<GameEntity> targets, float partialTicks) {
        for (GameEntity entity : targets) {
            Color color = esp.colorFor(entity);
            Render.boxFilledOutline(entity.getHitbox(), color.withAlpha(fillOpacity.getInt()), color,
                    lineWidth.getFloat());
        }
    }
}
