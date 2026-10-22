package dev.px.karatsuba.client.hud.preset;

import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.gui.legacy.GuiStyle;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.style.Style;
import dev.px.karatsuba.client.hud.style.StyleSettings;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * The HUD as it was before presets, kept exactly ({@code hud/DESIGN.md} §5): a dark
 * rounded plate per element, accent labels, white values, in the font and colours
 * of the HUD Style, or of an element's own Custom Style while that is on.
 *
 * <p>Its settings are the HUD Style's, unchanged for now; they move to the Legacy
 * Settings page in {@code hud/PLAN.md} step 3.
 */
final class LegacyPreset implements HudPreset {

    // Good, fair and bad: softer than the game's pure red and green, which vibrate
    // against each other and against a bright sky. Never the only cue: an element
    // showing them shows the same state by shape too (bars lit, words), since red
    // against green is exactly the pair common colourblindness cannot tell apart.
    private static final Color GOOD = Color.of(90, 210, 125);
    private static final Color FAIR = Color.of(240, 200, 85);
    private static final Color BAD = Color.of(240, 95, 95);

    /** How faint a meter's unlit steps and a track's empty part are, against muted text. */
    private static final float METER_TRACK_FADE = 0.35f;
    private static final float TRACK_FADE = 0.25f;

    /** A meter's steps: their width, the room between them, and their tallest against a line of text. */
    private static final float METER_STEP = 2f;
    private static final float METER_SPACING = 1f;
    private static final float METER_HEIGHT = 0.7f;

    /** A track's thickness. */
    private static final float TRACK_HEIGHT = 2f;

    /** How faint tertiary text and separators are, against muted text. */
    private static final float TERTIARY_FADE = 0.6f;
    private static final float SEPARATOR_FADE = 0.3f;

    /** Increase Contrast's border round every plate. */
    private static final float BORDER = 1f;

    /** A notification's category edge, and its countdown line: thickness and opacity. */
    private static final float CATEGORY_EDGE = 2f;
    private static final float COUNTDOWN = 1f;
    private static final int COUNTDOWN_ALPHA = 140;

    /** A toggle indicator's dot, against a line of text. */
    private static final float TOGGLE_SIZE = 0.5f;

    @Override
    public String id() {
        return "legacy";
    }

    @Override
    public String name() {
        return "Legacy";
    }

    @Override
    public HudTokens tokens(StyledElement element) {
        StyleSettings own = element.customStyle();
        HudStyle hud = HudStyle.get();
        // Reduce Transparency and Increase Contrast both make the plate solid: Legacy has no
        // glass, so solid is the whole of Reduce Transparency here (§1.6).
        boolean contrast = hud.increaseContrast();
        return new Tokens(own != null ? own.resolve() : hud.resolve(), contrast || hud.reduceTransparency(), contrast);
    }

    @Override
    public void plate(Content content, HudTokens tokens, float opacity) {
        Color fill = tokens.plate().fade(opacity);
        float radius = tokens.radius();
        Color border = tokens.border();
        if (border == null) {
            content.background(fill, radius).padding(tokens.padding());
            return;
        }
        // A background is drawn over a backdrop, which would hide the border: so both go here.
        Color edge = border.fade(opacity);
        content.padding(tokens.padding()).backdrop((x, y, w, h) -> {
            Render.roundRect(x, y, w, h, radius, fill);
            Render.roundRectOutline(x, y, w, h, radius, BORDER, edge);
        });
    }

    @Override
    public void drawPlate(float x, float y, float w, float h, HudTokens tokens) {
        float radius = Math.min(tokens.radius(), h / 2f);
        Render.roundRect(x, y, w, h, radius, tokens.plate());
        if (tokens.border() != null) {
            Render.roundRectOutline(x, y, w, h, radius, BORDER, tokens.border());
        }
    }

    @Override
    public void meter(Content into, HudTokens tokens, int level, int max, Color fill) {
        Font font = tokens.font(TextRole.VALUE);
        float height = font.getHeight() * METER_HEIGHT;
        Color unlit = tokens.color(ColorRole.METER_TRACK).fade(METER_TRACK_FADE);
        into.custom(max * METER_STEP + (max - 1) * METER_SPACING, font.getHeight(), (x, y, w, h) -> {
            float bottom = y + (h + height) / 2f;
            for (int i = 0; i < max; i++) {
                float step = height * (i + 1) / max;
                Render.rect(x + i * (METER_STEP + METER_SPACING), bottom - step, METER_STEP, step,
                        i < level ? fill : unlit);
            }
        });
    }

    @Override
    public void track(Content into, HudTokens tokens, float width, float progress, float alpha) {
        into.bar(width, TRACK_HEIGHT, TRACK_HEIGHT / 2f, progress,
                tokens.text(TextRole.SECONDARY).fade(alpha).fade(TRACK_FADE),
                tokens.color(ColorRole.METER_FILL).fade(alpha));
    }

    @Override
    public void icon(Content into, HudTokens tokens, HudIcon icon, Color colour) {
        float size = tokens.font(TextRole.PRIMARY).getHeight();
        into.custom(size, size, (x, y, w, h) -> icon.draw(x, y, w, colour));
    }

    /** No wells in Legacy: the bare icon (§3.3). */
    @Override
    public void iconWell(Content into, HudTokens tokens, HudIcon icon, Color colour) {
        icon(into, tokens, icon, colour);
    }

    /** A dot: the accent while on, a faint one while off. */
    @Override
    public void toggleOn(Content into, HudTokens tokens, boolean on) {
        float line = tokens.font(TextRole.PRIMARY).getHeight();
        float size = line * TOGGLE_SIZE;
        Color colour = on ? tokens.color(ColorRole.ACCENT) : tokens.color(ColorRole.METER_TRACK).fade(METER_TRACK_FADE);
        into.custom(size, line, (x, y, w, h) -> Render.circle(x + w / 2f, y + h / 2f, size / 2f, colour));
    }

    /** A coloured edge down the item's left side: colour plus shape, not a coloured background (§2.13). */
    @Override
    public float categoryMarker(float x, float y, float height, HudTokens tokens, HudCategory category) {
        Render.roundRect(x, y, CATEGORY_EDGE, height, 0f, tokens.category(category));
        return CATEGORY_EDGE + tokens.padding();
    }

    /** A thin line along the bottom, shrinking as the item's time runs out, so one about to go says so. */
    @Override
    public void countdown(float x, float y, float width, float height, float remaining, HudTokens tokens,
                          HudCategory category) {
        Render.rect(x, y + height - COUNTDOWN, width * remaining, COUNTDOWN,
                tokens.category(category).withAlpha(COUNTDOWN_ALPHA));
    }

    /** Two lines of text, the title in its own colour; no block of its own in Legacy. */
    @Override
    public void titleBlock(Content into, HudTokens tokens, String title, Color titleColour,
                           String subtitle, Color subtitleColour) {
        into.text(tokens.font(TextRole.TITLE), title, titleColour);
        if (!subtitle.isEmpty()) {
            into.text(tokens.font(TextRole.SUBTITLE), subtitle, subtitleColour);
        }
    }

    /** Legacy's tokens: the resolved HUD Style, mapped role by role. */
    /**
     * @param solid    plates fully opaque: Reduce Transparency or Increase Contrast
     * @param contrast Increase Contrast: a border, and every colour lifted to 7:1 against the plate
     */
    private record Tokens(Style style, boolean solid, boolean contrast) implements HudTokens {

        /** One face for everything, as before. */
        @Override
        public Font font(TextRole role) {
            return style.font();
        }

        @Override
        public Color text(TextRole role) {
            return switch (role) {
                case LABEL -> strong(label(Accents.TEXT));
                case PRIMARY, VALUE, TITLE -> strong(style.value());
                case SECONDARY, SUBTITLE -> strong(style.muted());
                // Fainter, but never below 7:1 with Increase Contrast on.
                case TERTIARY -> contrast ? strong(style.muted()) : style.muted().fade(TERTIARY_FADE);
            };
        }

        @Override
        public Color color(ColorRole role) {
            return switch (role) {
                case ACCENT, METER_FILL -> strong(label(Accents.TARGET));
                case METER_TRACK -> style.muted();
                case GOOD -> strong(GOOD);
                case FAIR -> strong(FAIR);
                case BAD -> strong(BAD);
                case SEPARATOR -> contrast ? strong(style.muted()) : style.muted().fade(SEPARATOR_FADE);
            };
        }

        /** By the HUD Style's Tint: a gradient through the theme's accents down the list, or a text colour. */
        @Override
        public Color listItem(int index, int total) {
            return strong(switch (HudStyle.get().listTint()) {
                case THEME_GRADIENT -> Accents.onPlate(GuiStyle.accent(total <= 1 ? 0f : index / (float) (total - 1)), style.plate(), Accents.TEXT);
                case LABEL -> label(Accents.TEXT);
                case VALUE -> style.value();
            });
        }

        @Override
        public Color plate() {
            return solid ? style.plate().withAlpha(255) : style.plate();
        }

        /**
         * @return Label Colour: lifted to {@code target} against the plate while it
         *         follows the theme (§5b.2: 4.5:1 as text, 3:1 as a fill), so a dark
         *         theme's accent still reads; as picked otherwise
         */
        private Color label(double target) {
            return style.labelFromTheme() ? Accents.onPlate(style.label(), style.plate(), target) : style.label();
        }

        /** Core's notification colours, as Legacy has always drawn them (§5). */
        @Override
        public Color category(HudCategory category) {
            return strong(category.coreColour());
        }

        /** The title in its category's colour, as before. */
        @Override
        public Color categoryTitle(HudCategory category) {
            return category(category);
        }

        /** Muted text, lifted to 7:1: visible on any plate, never louder than the text. */
        @Override
        public Color border() {
            return contrast ? strong(style.muted()) : null;
        }

        /** @return {@code colour} lifted to 7:1 against the solid plate with Increase Contrast on; as it is otherwise */
        private Color strong(Color colour) {
            return contrast ? Contrast.lift(colour, style.plate().withAlpha(255), Contrast.HIGH) : colour;
        }

        @Override
        public float radius() {
            return style.radius();
        }

        @Override
        public float padding() {
            return style.padding();
        }

        @Override
        public float gap() {
            return style.gap();
        }
    }
}
