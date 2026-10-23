package dev.px.karatsuba.client.hud.preset;

import dev.px.core.render.Color;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.client.hud.preset.color.Contrast;
import dev.px.karatsuba.client.hud.preset.role.ColorRole;
import dev.px.karatsuba.client.hud.preset.role.HudCategory;
import dev.px.karatsuba.client.hud.preset.role.TextRole;

/**
 * One element's tokens for one frame, from the active preset: what each role looks
 * like, and the plate's measurements. Resolved fresh each frame, so a change to a
 * preset's settings shows at once.
 */
public interface HudTokens {

    /** @return the face {@code role} is set in; null while no font has loaded */
    Font font(TextRole role);

    /** @return the colour {@code role} is drawn in */
    Color text(TextRole role);

    /** @return the colour {@code role} means */
    Color color(ColorRole role);

    /**
     * @return the colour of the {@code index}-th of {@code total} entries in a ranked
     *         list (ArrayList's names): the listItem role. Primary text unless the
     *         preset says otherwise (Legacy follows its Tint)
     */
    default Color listItem(int index, int total) {
        return text(TextRole.PRIMARY);
    }

    /** @return {@code category}'s colour: its marker's, and what tells it apart (never alone, §2.3) */
    Color category(HudCategory category);

    /** @return the colour of an item's title in {@code category}: primary text unless the preset says otherwise */
    default Color categoryTitle(HudCategory category) {
        return text(TextRole.PRIMARY);
    }

    /** @return the plate's edge, with Increase Contrast on (§2.11); null for none */
    default Color border() {
        return null;
    }

    /** @return the plate's fill, its opacity applied */
    Color plate();

    /** @return the plate's corner radius */
    float radius();

    /** @return between the plate's edge and what is on it */
    float padding();

    /** @return between rows */
    float gap();
}
