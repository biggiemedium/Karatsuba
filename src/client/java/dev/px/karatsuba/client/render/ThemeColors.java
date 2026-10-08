package dev.px.karatsuba.client.render;

import dev.px.core.Core;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.ColorSetting;

/**
 * A render module's colours, or the client theme's in their place.
 *
 * <p>A module with a {@code Theme Colours} switch draws every colour through
 * {@link #of}. Off, each colour is its own setting (which can still follow the
 * theme or cycle a rainbow on its own, from its picker). On, each takes a point
 * on the theme's accent gradient instead, so a module's colours stay apart from
 * each other while all matching the client: a module gives each colour role its
 * own point, 0 the primary accent and 1 the secondary.
 *
 * <p>The setting's own opacity is kept either way, so a fill set faint stays
 * faint in theme colours.
 */
public final class ThemeColors {

    private ThemeColors() {
    }

    /**
     * @param themed   whether the module's Theme Colours switch is on
     * @param position where on the theme's gradient this colour sits, 0 to 1
     */
    public static Color of(ColorSetting setting, boolean themed, float position) {
        if (!themed) {
            return setting.resolve();
        }
        return Core.themes().accent(position).withAlpha(setting.get().getAlpha());
    }

    /**
     * A colour between two settings' colours: {@code progress} 0 is {@code from},
     * 1 is {@code to}. Themed, it runs along the theme's gradient instead, which is
     * how a progress bar in theme colours reads.
     */
    public static Color between(ColorSetting from, ColorSetting to, boolean themed, float progress) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        if (themed) {
            int alpha = Math.round(from.get().getAlpha() + (to.get().getAlpha() - from.get().getAlpha()) * clamped);
            return Core.themes().accent(clamped).withAlpha(alpha);
        }
        return from.resolve().lerp(to.resolve(), clamped);
    }
}
