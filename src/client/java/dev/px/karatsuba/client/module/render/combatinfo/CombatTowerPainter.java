package dev.px.karatsuba.client.module.render.combatinfo;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.util.render.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Combat Info as a narrow column: everything stacked, one thing a line, so it
 * sits beside a player like a tall tag rather than a wide card.
 *
 * <pre>
 *  ╭──────────────╮
 *  │ Steve        │   name
 *  │ 18.5  +4     │   health, large
 *  │ ━━━━━━━━──── │   health bar
 *  │ ──────────── │
 *  │ Pops       3 │
 *  │ Armour   62% │   one stat a line,
 *  │ Ping    12ms │   label left, value right
 *  │ Dist    3.1m │
 *  │ ──────────── │
 *  │ ● Totem      │   status, one a line
 *  │ ● Hole       │
 *  │ ● Crystal 64 │
 *  │ ──────────── │
 *  │ Str II  1:20 │   effects, one a line
 *  ╰──────────────╯
 * </pre>
 *
 * <p>Built to be read at a glance: nothing is drawn smaller than nine-tenths of
 * the HUD text, values carry a soft shadow so they stand off the panel, and the
 * health figure is drawn large. One walk over the rows both measures and draws,
 * as in {@link CombatCardPainter}.
 */
public final class CombatTowerPainter implements CombatPainter {

    public static final float WIDTH = 86f;

    private static final float PAD = 6f;
    private static final float RADIUS = 6f;
    private static final float LINE_GAP = 2.5f;
    private static final float SECTION_GAP = 4f;
    private static final float BAR = 2.5f;

    /** Labels at this share of the HUD text: smaller than the values, never tiny. */
    private static final float LABEL = 0.9f;

    /** The health figure, larger than everything else. */
    private static final float HEALTH = 1.35f;

    private static final float DOT = 3f;

    private static final Color TEXT = Color.of(245, 245, 250);
    private static final Color MUTED = Color.of(165, 165, 175);
    private static final Color TRACK = Color.of(255, 255, 255, 24);
    private static final Color HAIRLINE = Color.of(255, 255, 255, 20);
    private static final Color SHADOW = Color.of(0, 0, 0, 90);
    private static final Color ABSORPTION = Color.rgb(0xF4C542);
    private static final Color POPS = Color.rgb(0xF4C542);
    private static final Color MISSING = Color.rgb(0xFF5A5A);

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

    /** @return the column's height; with {@code draw}, also draws it */
    private float layout(CombatCard card, boolean draw) {
        float text = Render.textHeight();
        float left = PAD;
        float right = WIDTH - PAD;
        float y = PAD;

        // Name, cut to fit the column rather than overflowing it.
        if (draw) {
            value(fit(card.name(), right - left), left, y, card.nameColor());
        }
        y += text + LINE_GAP;

        // Health, large, with absorption beside it.
        Color healthColor = ColorUtil.health(card.health(), card.maxHealth());
        if (draw) {
            String health = String.format(Locale.ROOT, "%.1f", card.health());
            scaled(health, left, y, HEALTH, healthColor, true);
            if (card.absorption() > 0f) {
                String absorption = String.format(Locale.ROOT, "+%.0f", card.absorption());
                float after = left + Render.textWidth(health) * HEALTH + 3f;
                scaled(absorption, after, y + text * (HEALTH - LABEL), LABEL, ABSORPTION, true);
            }
        }
        y += text * HEALTH + LINE_GAP;

        // The bar, absorption continuing it in gold.
        if (draw) {
            float span = right - left;
            float max = Math.max(1f, card.maxHealth());
            float health = clamp(card.health() / max);
            float absorption = Math.min(1f - health, clamp(card.absorption() / max));
            Render.roundRect(left, y, span, BAR, BAR / 2f, TRACK);
            Render.roundRect(left, y, Math.max(BAR, span * health), BAR, BAR / 2f, healthColor);
            if (absorption > 0f) {
                Render.roundRect(left + span * health, y, span * absorption, BAR, BAR / 2f, ABSORPTION);
            }
        }
        y += BAR + SECTION_GAP;
        y = divider(left, right, y, draw);

        // The stats, one a line.
        List<String[]> stats = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        stats.add(new String[]{"Pops", String.valueOf(card.pops())});
        colors.add(card.pops() == 0 ? TEXT : POPS.lerp(Color.WHITE, card.popFlash()));
        if (card.armour() != null) {
            Object[] armour = armour(card.armour());
            stats.add(new String[]{"Armour", (String) armour[0]});
            colors.add((Color) armour[1]);
        }
        stats.add(new String[]{"Ping", card.ping() >= 0 ? card.ping() + "ms" : "-"});
        colors.add(TEXT);
        stats.add(new String[]{"Dist", String.format(Locale.ROOT, "%.1fm", card.distance())});
        colors.add(TEXT);
        for (int i = 0; i < stats.size(); i++) {
            if (draw) {
                scaled(stats.get(i)[0], left, y + text * (1f - LABEL) / 2f, LABEL, MUTED, false);
                String value = stats.get(i)[1];
                value(value, right - Render.textWidth(value), y, colors.get(i));
            }
            y += text + LINE_GAP;
        }

        // Status, one a line, each with a dot in its colour.
        List<CombatCard.Chip> status = new ArrayList<>();
        if (card.hands() != null) {
            status.addAll(card.hands());
        }
        if (card.position() != null) {
            status.add(card.position());
        }
        if (!status.isEmpty()) {
            y += SECTION_GAP - LINE_GAP;
            y = divider(left, right, y, draw);
            for (CombatCard.Chip chip : status) {
                if (draw) {
                    Render.circle(left + DOT / 2f, y + text / 2f, DOT / 2f, chip.color());
                    value(fit(chip.text(), right - left - DOT - 4f), left + DOT + 4f, y, chip.color());
                }
                y += text + LINE_GAP;
            }
        }

        // Effects, one a line: name left, time right.
        if (card.effects() != null && !card.effects().isEmpty()) {
            y += SECTION_GAP - LINE_GAP;
            y = divider(left, right, y, draw);
            for (CombatCard.Chip effect : card.effects()) {
                if (draw) {
                    String[] parts = splitTime(effect.text());
                    value(parts[0], left, y, effect.color());
                    if (!parts[1].isEmpty()) {
                        scaled(parts[1], right - Render.textWidth(parts[1]) * LABEL,
                                y + text * (1f - LABEL) / 2f, LABEL, MUTED, false);
                    }
                }
                y += text + LINE_GAP;
            }
        }
        return y - LINE_GAP + PAD;
    }

    // --------------------------------------------------------------- pieces

    private static float divider(float left, float right, float y, boolean draw) {
        if (draw) {
            Render.rect(left, y, right - left, 1f, HAIRLINE);
        }
        return y + 1f + SECTION_GAP;
    }

    /** The worst armour piece: a bare slot by name, or the lowest durability. */
    private static Object[] armour(List<CombatCard.Piece> pieces) {
        String[] names = {"Head", "Chest", "Legs", "Feet"};
        int bare = 0;
        String bareName = "";
        float lowest = 1f;
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i).missing()) {
                bare++;
                bareName = names[i];
            } else {
                lowest = Math.min(lowest, pieces.get(i).durability());
            }
        }
        if (bare == pieces.size()) {
            return new Object[]{"None", MISSING};
        }
        if (bare > 0) {
            return new Object[]{bare == 1 ? "No " + bareName : bare + " bare", MISSING};
        }
        return new Object[]{Math.round(lowest * 100f) + "%", Color.hsb(clamp(lowest) / 3f, 0.75f, 1f)};
    }

    /** "Str II 1:20" into {"Str II", "1:20"}: the time is drawn apart, on the right. */
    private static String[] splitTime(String text) {
        int space = text.lastIndexOf(' ');
        if (space > 0 && text.indexOf(':', space) > space) {
            return new String[]{text.substring(0, space), text.substring(space + 1)};
        }
        return new String[]{text, ""};
    }

    /** A value: full size, with a soft shadow so it stands off the panel. */
    private static void value(String text, float x, float y, Color color) {
        Render.textShadowed(text, x, y, color);
    }

    private static void scaled(String text, float x, float y, float size, Color color, boolean shadow) {
        Render.pushTransform();
        try {
            Render.translate(x, y);
            Render.scale(size, size);
            if (shadow) {
                Render.textShadowed(text, 0f, 0f, color);
            } else {
                Render.text(text, 0f, 0f, color);
            }
        } finally {
            Render.popTransform();
        }
    }

    /** The text cut short with an ellipsis if it is wider than {@code width}. */
    private static String fit(String text, float width) {
        if (Render.textWidth(text) <= width) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && Render.textWidth(cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
