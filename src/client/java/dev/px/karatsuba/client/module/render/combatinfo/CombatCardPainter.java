package dev.px.karatsuba.client.module.render.combatinfo;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.util.render.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lays out and draws one Combat Info card.
 *
 * <p>Knows nothing about players or settings, only the {@link CombatCard} it is
 * handed and where on the screen its left-middle point goes. Laid out in local
 * units and scaled once:
 *
 * <pre>
 *  ╭──────────────────────────────╮
 *  │ Steve                18.5 +4 │   name, health
 *  │ ━━━━━━━━━━━━━━━━━━──────────  │   health bar
 *  │ POPS 3          PING 12ms    │
 *  │ ARMOUR 62%      DIST 3.1m    │   the stats
 *  │ ──────────────────────────── │
 *  │ (Totem) (Hole) (Crystal 64)  │   status pills
 *  │ Str II 1:20 · Res I 0:40     │   effects
 *  ╰──────────────────────────────╯
 * </pre>
 *
 * <p>Quiet by design: one soft rounded panel, labels small and muted, colour kept
 * for what needs it (health, a missing totem, worn armour, effects). One walk over
 * the rows both measures and draws ({@link #layout}), so the height the card is
 * centred on is exactly the height drawn.
 */
public final class CombatCardPainter implements CombatPainter {

    public static final float WIDTH = 124f;

    private static final float PAD = 6f;
    private static final float RADIUS = 6f;
    private static final float ROW_GAP = 3f;
    private static final float BAR = 2f;

    /** Labels and pill text are drawn at this share of the main text size. */
    private static final float SMALL = 0.72f;

    private static final float PILL_PAD_X = 3.5f;
    private static final float PILL_PAD_Y = 1.5f;

    private static final Color TEXT = Color.of(240, 240, 245);
    private static final Color LABEL = Color.of(140, 140, 150);
    private static final Color TRACK = Color.of(255, 255, 255, 22);
    private static final Color HAIRLINE = Color.of(255, 255, 255, 18);
    private static final Color PILL = Color.of(255, 255, 255, 14);
    private static final Color SHADOW = Color.of(0, 0, 0, 90);
    private static final Color ABSORPTION = Color.rgb(0xF4C542);
    private static final Color POPS = Color.rgb(0xF4C542);
    private static final Color MISSING = Color.rgb(0xFF5A5A);

    /**
     * @param x          screen X of the card's left edge, at the middle of its height
     * @param y          screen Y of that point
     * @param scale      1 draws at HUD text size
     * @param alpha      0 to 1, the whole card
     * @param accent     the hairline outline
     * @param background the panel
     */
    @Override
    public void paint(float x, float y, float scale, float alpha, CombatCard card, Color accent, Color background) {
        if (alpha <= 0f || scale <= 0f) {
            return;
        }
        float height = layout(card, false);
        Render.pushAlpha(alpha);
        try {
            Render.pushTransform();
            try {
                Render.translate(x, y - height / 2f * scale);
                Render.scale(scale, scale);
                Render.shadow(0f, 0f, WIDTH, height, RADIUS, SHADOW);
                Render.roundRect(0f, 0f, WIDTH, height, RADIUS, background);
                Render.roundRectOutline(0f, 0f, WIDTH, height, RADIUS, 1f, accent.withAlpha(70));
                layout(card, true);
            } finally {
                Render.popTransform();
            }
        } finally {
            Render.popAlpha();
        }
    }

    /**
     * Walks the rows from the top: measures them, and with {@code draw} also
     * draws them.
     *
     * @return the card's height
     */
    private float layout(CombatCard card, boolean draw) {
        float text = Render.textHeight();
        float small = text * SMALL;
        float left = PAD;
        float right = WIDTH - PAD;
        float y = PAD;

        // Name, and health with absorption.
        if (draw) {
            Render.text(card.name(), left, y, card.nameColor());
            Color healthColor = ColorUtil.health(card.health(), card.maxHealth());
            String health = String.format(Locale.ROOT, "%.1f", card.health());
            float x = right;
            if (card.absorption() > 0f) {
                String absorption = String.format(Locale.ROOT, "+%.0f", card.absorption());
                x -= smallWidth(absorption);
                small(absorption, x, y + (text - small), ABSORPTION);
                x -= 2f;
            }
            Render.text(health, x - Render.textWidth(health), y, healthColor);
        }
        y += text + ROW_GAP;

        // A thin health bar, absorption continuing it in gold.
        if (draw) {
            float span = right - left;
            float max = Math.max(1f, card.maxHealth());
            float health = clamp(card.health() / max);
            float absorption = Math.min(1f - health, clamp(card.absorption() / max));
            Render.roundRect(left, y, span, BAR, BAR / 2f, TRACK);
            Render.roundRect(left, y, Math.max(BAR, span * health), BAR, BAR / 2f,
                    ColorUtil.health(card.health(), card.maxHealth()));
            if (absorption > 0f) {
                Render.roundRect(left + span * health, y, span * absorption, BAR, BAR / 2f, ABSORPTION);
            }
        }
        y += BAR + ROW_GAP + 2f;

        // The stats, two to a row.
        float column = (right - left) / 2f;
        String ping = card.ping() >= 0 ? card.ping() + "ms" : "-";
        Stat popStat = new Stat("POPS", String.valueOf(card.pops()),
                card.pops() == 0 ? TEXT : POPS.lerp(Color.WHITE, card.popFlash()));
        Stat pingStat = new Stat("PING", ping, TEXT);
        Stat armourStat = card.armour() == null ? null : armourStat(card.armour());
        Stat distanceStat = new Stat("DIST", String.format(Locale.ROOT, "%.1fm", card.distance()), TEXT);
        y = statRow(popStat, pingStat, left, column, y, draw) + ROW_GAP;
        y = statRow(armourStat != null ? armourStat : distanceStat, armourStat != null ? distanceStat : null,
                left, column, y, draw) + ROW_GAP;

        // Everything after the divider is optional; with none of it, no divider.
        List<CombatCard.Chip> pills = new ArrayList<>();
        if (card.hands() != null) {
            pills.addAll(card.hands());
        }
        if (card.position() != null) {
            pills.add(card.position());
        }
        boolean hasEffects = card.effects() != null && !card.effects().isEmpty();
        if (!pills.isEmpty() || hasEffects) {
            if (draw) {
                Render.rect(left, y + 1f, right - left, 1f, HAIRLINE);
            }
            y += 1f + ROW_GAP + 1f;
        }
        if (!pills.isEmpty()) {
            y = pills(pills, left, right, y, draw) + ROW_GAP;
        }
        if (hasEffects) {
            y = effects(card.effects(), left, right, y, draw) + ROW_GAP;
        }
        return y - ROW_GAP + PAD;
    }

    private record Stat(String label, String value, Color color) {
    }

    /** @return the bottom of the row */
    private static float statRow(Stat first, Stat second, float left, float column, float top, boolean draw) {
        float text = Render.textHeight();
        if (draw) {
            stat(first, left, top, text);
            if (second != null) {
                stat(second, left + column, top, text);
            }
        }
        return top + text;
    }

    /** A small muted label, and its value beside it on the same baseline. */
    private static void stat(Stat stat, float x, float top, float text) {
        float small = text * SMALL;
        small(stat.label(), x, top + (text - small) - 0.5f, LABEL);
        Render.text(stat.value(), x + smallWidth(stat.label()) + 3f, top, stat.color());
    }

    /** The worst piece, as one value: a bare slot by name, or the lowest durability. */
    private static Stat armourStat(List<CombatCard.Piece> pieces) {
        String[] names = {"Head", "Chest", "Legs", "Feet"};
        List<String> bare = new ArrayList<>();
        float lowest = 1f;
        for (int i = 0; i < pieces.size(); i++) {
            CombatCard.Piece piece = pieces.get(i);
            if (piece.missing()) {
                bare.add(names[i]);
            } else {
                lowest = Math.min(lowest, piece.durability());
            }
        }
        if (bare.size() == pieces.size()) {
            return new Stat("ARMOUR", "None", MISSING);
        }
        if (!bare.isEmpty()) {
            return new Stat("ARMOUR", bare.size() == 1 ? "No " + bare.get(0) : bare.size() + " bare", MISSING);
        }
        return new Stat("ARMOUR", Math.round(lowest * 100f) + "%", durabilityColor(lowest));
    }

    /** Pills left to right, wrapping. @return the bottom of the last line */
    private static float pills(List<CombatCard.Chip> chips, float left, float right, float top, boolean draw) {
        float small = Render.textHeight() * SMALL;
        float line = small + PILL_PAD_Y * 2f;
        float x = left;
        float y = top;
        for (CombatCard.Chip chip : chips) {
            float width = smallWidth(chip.text()) + PILL_PAD_X * 2f;
            if (x > left && x + width > right) {
                x = left;
                y += line + 2f;
            }
            if (draw) {
                Render.roundRect(x, y, width, line, line / 2f, PILL);
                small(chip.text(), x + PILL_PAD_X, y + PILL_PAD_Y, chip.color());
            }
            x += width + 3f;
        }
        return y + line;
    }

    /** Effects as one quiet line of coloured text, separated by dots, wrapping. @return the bottom */
    private static float effects(List<CombatCard.Chip> effects, float left, float right, float top, boolean draw) {
        float small = Render.textHeight() * SMALL;
        String separator = "  ·  ";
        float x = left;
        float y = top;
        for (int i = 0; i < effects.size(); i++) {
            CombatCard.Chip effect = effects.get(i);
            float width = smallWidth(effect.text());
            if (x > left && x + width > right) {
                x = left;
                y += small + 2f;
            } else if (i > 0) {
                if (draw) {
                    small(separator, x, y, LABEL);
                }
                x += smallWidth(separator);
                if (x + width > right) {
                    x = left;
                    y += small + 2f;
                }
            }
            if (draw) {
                small(effect.text(), x, y, effect.color());
            }
            x += width;
        }
        return y + small;
    }

    // --------------------------------------------------------------- helpers

    private static void small(String value, float x, float y, Color color) {
        Render.pushTransform();
        try {
            Render.translate(x, y);
            Render.scale(SMALL, SMALL);
            Render.text(value, 0f, 0f, color);
        } finally {
            Render.popTransform();
        }
    }

    private static float smallWidth(String value) {
        return Render.textWidth(value) * SMALL;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Green when sound, through amber, to red near breaking. */
    private static Color durabilityColor(float left) {
        return Color.hsb(clamp(left) / 3f, 0.75f, 1f);
    }
}
