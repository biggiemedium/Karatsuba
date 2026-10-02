package dev.px.karatsuba.client.module.render.esp;

import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.render.EspModule;

import java.util.List;

/**
 * One way of drawing ESP.
 *
 * <p>Unlike most modes these do not subscribe to anything. {@link EspModule}
 * owns the frame: it picks the targets, turns depth testing off so everything
 * shows through walls, hands the list to whichever mode is selected, draws the
 * tracers, and turns depth testing back on. A mode is only the drawing, which is
 * what keeps the through-walls switch in one place instead of six.
 */
public abstract class EspMode extends SubMode<EspModule.Mode> {

    protected EspMode(EspModule.Mode key) {
        super(key);
    }

    protected EspMode(EspModule.Mode key, String label) {
        super(key, label);
    }

    /**
     * Draws one frame.
     *
     * @param esp          the module, for {@link EspModule#colorFor}
     * @param targets      the entities that passed the module's filters, already
     *                     within range; never empty
     * @param partialTicks the frame's interpolation
     */
    public abstract void render(EspModule esp, List<GameEntity> targets, float partialTicks);

    /**
     * Draws in screen space, for a mode that works there.
     *
     * <p>Called from the world overlay layer, after the world has been drawn and
     * before any screen, so anything here shows through walls with no depth
     * handling and is still covered by an open inventory or menu. Most modes are
     * world-space only and leave this empty.
     *
     * @param targets the same filtered list as {@link #render}, interpolated for
     *                this frame; never empty
     */
    public void render2D(EspModule esp, List<GameEntity> targets, float partialTicks) {
    }
}
