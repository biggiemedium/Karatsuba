package dev.px.karatsuba.client.hud.element;

import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Texture;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.game.player.Effects;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.AnimatedRows;
import dev.px.karatsuba.client.hud.style.StyledElement;

import java.util.Comparator;
import java.util.List;

/**
 * The status effects on the player: each one's icon, its name and level, and how
 * long it has left, the one running out soonest on top.
 *
 * <p>An effect that starts grows its row in, fading in, and pushes the rows below
 * down; one that ends shrinks away the same way ({@link AnimatedRows}, as ArrayList's
 * lines do, without the slide). Times sit in a column at the row's end, each in a
 * slot as wide as {@code 00:00}, so the plate does not twitch as they count down.
 *
 * <p>The icons are the game's own, a resource pack's if one replaces them, drawn
 * sharp as the pixel art they are. With nothing on the player it takes no space;
 * while positioning, a sample keeps it grabbable.
 */
public final class PotionsElement extends StyledElement {

    /** How tall an icon is against a line of text: a little taller, as in the inventory. */
    private static final float ICON_LINES = 1.25f;

    /** Between the icon and the name, and between the name and the time. */
    private static final float ICON_GAP = 4f;
    private static final float TIME_GAP = 8f;

    /** As wide as a time usually gets, and once an effect runs past an hour. */
    private static final String WIDEST_TIME = "00:00";
    private static final String WIDEST_LONG_TIME = "0:00:00";

    /** What an effect that never runs out shows instead of a time. */
    private static final String FOREVER = "∞";

    /** While positioning with nothing on the player: three effects, so the box has its real shape. */
    private static final List<Effects.Active> SAMPLE = List.of(
            new Effects.Active("minecraft:speed", "Speed", 1, 145 * 20, false),
            new Effects.Active("minecraft:regeneration", "Regeneration", 0, 89 * 20, false),
            new Effects.Active("minecraft:strength", "Strength", 0, 234 * 20, false));

    private final BooleanSetting showIcon = bool("Show Icon", true)
            .describe("Each effect's picture, as the inventory shows it");
    private final BooleanSetting showAmplifier = bool("Show Amplifier", true)
            .describe("The effect's level after its name, as Speed II");
    private final BooleanSetting showDuration = bool("Show Duration", true)
            .describe("How long the effect has left");

    /** Every row drawn, by effect: on the player, or still leaving. */
    private final AnimatedRows<Effects.Active> rows = new AnimatedRows<>(Effects.Active::id);

    public PotionsElement() {
        super("potions", "Potions", HudLayout.at(Anchor.BOTTOM_LEFT, 4f, -48f));
    }

    @Override
    protected void describe(Content body) {
        List<Effects.Active> onPlayer = Effects.active();
        List<Effects.Active> active = isEditing() && onPlayer.isEmpty() ? SAMPLE : onPlayer;
        watch(active.stream().map(Effects.Active::id).toList());
        List<AnimatedRows.Row<Effects.Active>> shown = rows.update(active, SOONEST_FIRST);
        if (shown.isEmpty()) {
            return;
        }
        Font name = tokens().font(TextRole.PRIMARY);
        Font time = tokens().font(TextRole.SECONDARY);
        float lineHeight = name.getHeight();
        float iconSize = showIcon.isOn() ? lineHeight * ICON_LINES : 0f;
        float rowHeight = Math.max(lineHeight, iconSize);
        float timeSlot = showDuration.isOn() ? time.widthOf(longest(shown)) : 0f;

        // Every row as wide as the widest, so the times line up in a column; the width eases
        // with the rows as they grow, so the plate never jumps when the widest one goes.
        float listWidth = 0f;
        for (AnimatedRows.Row<Effects.Active> row : shown) {
            listWidth = Math.max(listWidth, width(row.value(), name, iconSize, timeSlot) * row.grown());
        }

        float gap = tokens().gap();
        float width = listWidth;
        Align side = alignment();
        body.column(column -> {
            column.align(side);
            for (AnimatedRows.Row<Effects.Active> row : shown) {
                row(column, row, width, rowHeight, iconSize, gap * row.gapAbove(), name, time);
            }
        });
    }

    /**
     * One effect's row, drawn by hand so it can animate: its room opens from nothing,
     * pushing the rows below down, and what is in it fades in, revealed from its middle
     * as the row opens; leaving, the same backwards.
     */
    private void row(Content column, AnimatedRows.Row<Effects.Active> row, float width, float rowHeight,
                     float iconSize, float gapAbove, Font nameFont, Font timeFont) {
        Effects.Active effect = row.value();
        float open = rowHeight * row.grown();
        Color nameColour = cued(tokens().text(TextRole.PRIMARY)).fade(row.shown());
        Color timeColour = cued(tokens().text(TextRole.SECONDARY)).fade(row.shown());
        String name = label(effect);
        String time = showDuration.isOn() ? time(effect) : "";
        float alpha = row.shown();
        column.custom(width, gapAbove + open, (x, y, w, h) -> {
            float top = y + gapAbove;
            // The row's content at its full size, centred in the room it has so far, and cut to it.
            float contentTop = top + (open - rowHeight) / 2f;
            Render.pushClip(x, top, w, open);
            try {
                float textY = contentTop + (rowHeight - nameFont.getHeight()) / 2f;
                float textX = x;
                if (showIcon.isOn()) {
                    Texture icon = Effects.icon(effect.id());
                    if (icon != null) {
                        Render.texture(icon, x, contentTop + (rowHeight - iconSize) / 2f, iconSize, iconSize,
                                Color.WHITE.fade(alpha));
                    }
                    textX += iconSize + ICON_GAP;
                }
                Render.text(nameFont, name, textX, textY, nameColour);
                if (!time.isEmpty()) {
                    Render.text(timeFont, time, x + w - timeFont.widthOf(time), textY, timeColour);
                }
            } finally {
                Render.popClip();
            }
        });
    }

    /** Rows grow past the plate while they come and go, so they are clipped to it then. */
    @Override
    protected boolean overflowing() {
        return rows.moving();
    }

    /** Its rows grow and shrink themselves, so the plate follows them exactly. */
    @Override
    protected boolean sizesItself() {
        return true;
    }

    // ------------------------------------------------------------------ text

    /** @return the effect's name, with its level as a numeral when it is above I */
    private String label(Effects.Active effect) {
        if (!showAmplifier.isOn() || effect.amplifier() <= 0) {
            return effect.name();
        }
        return effect.name() + " " + numeral(effect.amplifier() + 1);
    }

    /** @return how long is left: {@code 2:25}, {@code 1:04:09} past an hour, or ∞ for an effect that never ends */
    private static String time(Effects.Active effect) {
        if (effect.infinite()) {
            return FOREVER;
        }
        int seconds = effect.secondsLeft();
        int hours = seconds / 3600;
        int minutes = (seconds / 60) % 60;
        int rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest) : String.format("%d:%02d", minutes, rest);
    }

    /** @return the widest time any row may show: what the time column is sized to */
    private static String longest(List<AnimatedRows.Row<Effects.Active>> shown) {
        for (AnimatedRows.Row<Effects.Active> row : shown) {
            if (!row.value().infinite() && row.value().secondsLeft() >= 3600) {
                return WIDEST_LONG_TIME;
            }
        }
        return WIDEST_TIME;
    }

    /** @return how wide a row is drawn: icon, name, and the time column */
    private float width(Effects.Active effect, Font name, float iconSize, float timeSlot) {
        float width = name.widthOf(label(effect));
        if (iconSize > 0f) {
            width += iconSize + ICON_GAP;
        }
        if (timeSlot > 0f) {
            width += TIME_GAP + timeSlot;
        }
        return width;
    }

    /** I to X, then plain numbers: an effect past level ten is rare enough to read as a number. */
    private static String numeral(int level) {
        String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level <= numerals.length ? numerals[level - 1] : String.valueOf(level);
    }

    /** The one running out soonest on top; effects that never end at the bottom, by name. */
    private static final Comparator<Effects.Active> SOONEST_FIRST = Comparator
            .comparing(Effects.Active::infinite)
            .thenComparingInt(Effects.Active::ticksLeft)
            .thenComparing(Effects.Active::name);

}
