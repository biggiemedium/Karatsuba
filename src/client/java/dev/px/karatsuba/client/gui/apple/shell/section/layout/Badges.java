package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import dev.px.core.Core;
import dev.px.core.hud.HudEditorView;
import dev.px.core.hud.Placement;
import dev.px.core.layout.Bounds;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.hud.preset.PlateShape;

import java.util.List;

/**
 * Where each element's hide or show badge is ({@code hud/DESIGN.md} §7.1c): a
 * circle overhanging the top-left of the element's first plate, on its curve, as
 * Control Center's edit mode has them, kept on screen. The one place it is worked out, so what is drawn and
 * what is pressed are the same circle.
 */
final class Badges {

    private Badges() {
    }

    /**
     * @return the square the badge's circle fills, in screen units: centred on the
     *         first plate's top-left curve (where a 45° line meets it), so it sits on
     *         a capsule's end as it does on a tile's corner
     */
    static Bounds of(Placement placement) {
        PlateShape plate = Outlines.of(placement).get(0);
        float onCurve = plate.radius() * (1f - (float) Math.sqrt(0.5d));
        float size = Palette.BADGE_SIZE;
        float x = plate.x() + onCurve + Palette.BADGE_INSET - size / 2f;
        float y = plate.y() + onCurve + Palette.BADGE_INSET - size / 2f;
        // An element against the screen's edge still has a whole badge to press.
        x = clamp(x, 0f, Core.platform().getScreenWidth() - size);
        y = clamp(y, 0f, Core.platform().getScreenHeight() - size);
        return Bounds.of(x, y, size, size);
    }

    /** @return the element whose badge is under {@code x, y}, the topmost if two overlap; null for none */
    static Placement under(HudEditorView view, float x, float y) {
        List<Placement> placements = view.getPlacements();
        for (int i = placements.size() - 1; i >= 0; i--) {
            Bounds badge = of(placements.get(i));
            float dx = x - badge.getCenterX();
            float dy = y - badge.getCenterY();
            float r = badge.getWidth() / 2f;
            if (dx * dx + dy * dy <= r * r) {
                return placements.get(i);
            }
        }
        return null;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
