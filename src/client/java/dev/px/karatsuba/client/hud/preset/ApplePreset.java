package dev.px.karatsuba.client.hud.preset;

import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.client.gui.apple.shell.style.Appearance;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.render.Glass;

/**
 * The HUD as the Apple UI draws it ({@code hud/DESIGN.md} §4): Regular Liquid Glass
 * plates over the game, drawn by the host behind each element at its scaled bounds
 * from one shared capture; SF Pro; vibrant text, labels secondary and values
 * primary; capsules for single rows, tiles for more; solid plates with Reduce
 * Transparency or Increase Contrast.
 *
 * <p>Its tokens are the Apple UI's (Palette's vibrant text, glass tone and solid
 * stand-in, {@code R_HUD_TILE}), so HUD glass and the GUI's glass read as one
 * material, and follow the Apple UI's appearance.
 */
final class ApplePreset implements HudPreset {

    /** Between a plate's edge and its content: wider than tall, so a capsule's ends have room. */
    private static final float PAD_X = 8f;
    private static final float PAD_Y = 4f;

    /**
     * A tile's corner as a share of its height, when that is under R_HUD_TILE: a short
     * tile keeps a corner that reads as a tile, never as a capsule (§4.9).
     */
    private static final float TILE_SHARE = 0.3f;

    /** A category well's tint, the room after it, and a list item's accent dot (radius). */
    private static final float CATEGORY_WELL_FADE = 0.22f;
    private static final float MARKER_GAP = 6f;
    private static final float LIST_DOT = 2f;

    /** Between rows. */
    private static final float GAP = 2f;

    /** A title, one step up from body text. */
    private static final float TITLE_STEP = 1f;

    /** The rim's lensing band on a HUD plate: thinner than the GUI's, since the plates are small. */
    private static final float RIM = 4f;

    /** Glass at Clear and at Tinted (§4.1): blur as a mip level, and how much of the glass tone is mixed in. */
    private static final float BLUR_CLEAR = 2.2f;
    private static final float BLUR_TINTED = 3.4f;
    private static final float TINT_CLEAR = 0.10f;
    private static final float TINT_TINTED = 0.45f;

    /**
     * Adaptive glass (§4.6): a backdrop brighter than ADAPT_FROM, or varying by as much
     * as ADAPT_BUSY, adds up to ADAPT_TINT to the tint and ADAPT_SHADOW to the shadow.
     * Brightness counts for ADAPT_BRIGHT_SHARE of it, busyness for the rest.
     */
    private static final float ADAPT_FROM = 0.4f;
    private static final float ADAPT_BUSY = 0.25f;
    private static final float ADAPT_BRIGHT_SHARE = 0.6f;
    private static final float ADAPT_TINT = 0.25f;
    private static final float ADAPT_SHADOW = 40f;

    /** The spring's time, and how far a toast travels in from its edge (§4.12). */
    private static final long SPRING_MILLIS = 360L;
    private static final float TOAST_TRAVEL = 24f;

    /** Tinted mutes the rim: how much of its bend and light it keeps. */
    private static final float TINTED_RIM = 0.5f;

    /**
     * Thickness follows size (§4.4): a plate this big, in natural units squared,
     * is as thick as glass gets. Thicker means more blur and a stronger shadow.
     */
    private static final float THICK_AREA = 9000f;
    private static final float THICK_BLUR = 0.8f;
    private static final int SHADOW_THIN = 30;
    private static final int SHADOW_THICK = 70;
    private static final float SHADOW_DROP = 1.5f;

    /** Fainter text and fills, against the vibrant label colour. */
    private static final float TERTIARY_FADE = 0.55f;
    private static final float TRACK_FADE = 0.22f;
    private static final float SEPARATOR_FADE = 0.15f;
    private static final float WELL_FADE = 0.12f;

    /** Meter steps, a track's thickness, and small controls' corners (rounded rectangles, not capsules, §4.9). */
    private static final float METER_STEP = 2f;
    private static final float METER_SPACING = 1f;
    private static final float METER_HEIGHT = 0.7f;
    private static final float TRACK_HEIGHT = 4f;
    private static final float CONTROL_RADIUS = 3f;
    private static final float WELL_RADIUS = 4f;
    private static final float WELL_PAD = 2f;
    private static final float BORDER = 1f;

    /** Good, fair and bad: Apple's system green, yellow and red, the Light ones darkened to read on white. */
    private static final Palette.Tone GOOD = new Palette.Tone(Color.of(48, 209, 88), Color.of(36, 138, 61));
    private static final Palette.Tone FAIR = new Palette.Tone(Color.of(255, 214, 10), Color.of(160, 90, 0));
    private static final Palette.Tone BAD = new Palette.Tone(Color.of(255, 69, 58), Color.of(215, 0, 21));

    @Override
    public String id() {
        return "apple";
    }

    @Override
    public String name() {
        return "Apple";
    }

    @Override
    public HudTokens tokens(StyledElement element) {
        HudStyle hud = HudStyle.get();
        boolean contrast = hud.increaseContrast();
        boolean solid = contrast || hud.reduceTransparency();
        return new Tokens(hud.textSize().scale(), solid, contrast, light(element.getId(), solid));
    }

    /**
     * @return whether {@code owner}'s plates are light (§4.14): fixed by Light or Dark;
     *         at Auto, a capsule over glass turns light or dark for what is behind it
     *         (with hysteresis, {@link Backdrop}) and anything else follows the Apple UI
     */
    private static boolean light(String owner, boolean solid) {
        return switch (HudStyle.get().hudAppearance()) {
            case LIGHT -> true;
            case DARK -> false;
            case AUTO -> {
                Backdrop.Reading behind = Backdrop.of(owner);
                if (!solid && behind != null && behind.capsule()) {
                    yield behind.light();
                }
                Appearance appearance = Appearance.get();
                yield appearance != null && appearance.isLight();
            }
        };
    }

    /**
     * @return how much a plate's tint and shadow strengthen for what is behind it, 0
     *         to 1 (§4.6): more over a bright backdrop, and more over a busy one
     */
    private static float adapt(String owner) {
        Backdrop.Reading behind = Backdrop.of(owner);
        if (behind == null) {
            return 0f;
        }
        float bright = Math.max(0f, Math.min(1f, (behind.brightness() - ADAPT_FROM) / (1f - ADAPT_FROM)));
        float busy = Math.min(1f, behind.busy() / ADAPT_BUSY);
        return Math.min(1f, bright * ADAPT_BRIGHT_SHARE + busy * (1f - ADAPT_BRIGHT_SHARE));
    }

    /** A plate springs to a new size (§4.12). */
    @Override
    public Curve resizeCurve() {
        return Curve.SPRING;
    }

    @Override
    public long resizeMillis() {
        return SPRING_MILLIS;
    }

    /** A toast springs in from its anchor edge: from further out than Legacy's, settling with the spring. */
    @Override
    public float toastSlide(float presence) {
        return (1f - Curve.SPRING.apply(presence)) * TOAST_TRAVEL;
    }

    /** Glass cues on its rim; a solid plate has none, so its text lights instead (§4.12). */
    @Override
    public boolean cuesText(HudTokens tokens) {
        return ((Tokens) tokens).solid();
    }

    @Override
    public boolean alignsByAnchor() {
        return false;
    }

    @Override
    public boolean drawsBehind() {
        return true;
    }

    /** One row or less, and the plate is a capsule (§4.9). */
    @Override
    public boolean capsule(HudTokens tokens, float height) {
        return height < tokens.font(TextRole.PRIMARY).getHeight() * 2f + PAD_Y * 2f;
    }

    /**
     * Glass is drawn behind by the host, so the content only takes its padding. A
     * solid plate (Reduce Transparency, Increase Contrast) is drawn here instead.
     */
    @Override
    public void plate(Content content, HudTokens tokens, float opacity) {
        content.padding(PAD_X, PAD_Y);
        Tokens apple = (Tokens) tokens;
        if (!apple.solid()) {
            return;
        }
        Color fill = tokens.plate().fade(opacity);
        Color border = tokens.border() == null ? null : tokens.border().fade(opacity);
        content.backdrop((x, y, w, h) -> solid(x, y, w, h, radiusFor(tokens, h), fill, border));
    }

    @Override
    public void drawPlate(float x, float y, float w, float h, HudTokens tokens) {
        if (((Tokens) tokens).solid()) {
            solid(x, y, w, h, h / 2f, tokens.plate(), tokens.border());
        }
    }

    @Override
    public void shadow(HudPlate plate, float scale) {
        if (plate.presence() <= 0f || HudStyle.get().reduceTransparency() || HudStyle.get().increaseContrast()) {
            return;
        }
        float thickness = thickness(plate, scale);
        float base = SHADOW_THIN + (SHADOW_THICK - SHADOW_THIN) * thickness;
        int alpha = Math.round(Math.min(255f, (base + ADAPT_SHADOW * adapt(plate.owner())) * plate.presence()));
        Render.shadow(plate.x(), plate.y() + SHADOW_DROP * scale, plate.width(), plate.height(),
                radius(plate, scale), Color.of(0, 0, 0, alpha));
    }

    @Override
    public boolean glass(Glass.Layer layer, HudPlate plate, float scale) {
        HudStyle hud = HudStyle.get();
        if (hud.reduceTransparency() || hud.increaseContrast()) {
            // Solid plates are drawn in the content instead, and no pass runs (§4.5).
            return true;
        }
        boolean tinted = hud.glassTinted();
        float thickness = thickness(plate, scale);
        float blur = (tinted ? BLUR_TINTED : BLUR_CLEAR) + THICK_BLUR * thickness;
        // Stronger over a bright or busy backdrop, so the text on it holds (§4.6).
        float tint = Math.min(1f, (tinted ? TINT_TINTED : TINT_CLEAR) + ADAPT_TINT * adapt(plate.owner()));
        Palette.Tone tone = Palette.GLASS_TONE;
        Color glassTone = light(plate.owner(), false) ? tone.light() : tone.dark();
        float rimKept = tinted ? TINTED_RIM : 1f;
        Glass.Optics regular = Glass.Optics.glass(RIM * scale, blur);
        Glass.Optics optics = new Glass.Optics(regular.rim(), rimKept, rimKept, regular.blur(),
                regular.saturation(), 0f);
        // The change cue brightens the rim, as a press does in the Apple UI (§4.12).
        return layer.draw(plate.x(), plate.y(), plate.width(), plate.height(), radius(plate, scale), optics,
                glassTone.withAlpha(Math.round(tint * 255f)), plate.presence(), plate.cue());
    }

    /** The glass's solid stand-in, formed as far as the plate is. */
    @Override
    public void standIn(HudPlate plate, float scale) {
        Palette.Tone solid = Palette.GLASS_SOLID;
        Color fill = light(plate.owner(), true) ? solid.light() : solid.dark();
        Render.roundRect(plate.x(), plate.y(), plate.width(), plate.height(), radius(plate, scale),
                fill.fade(plate.presence()));
    }

    /** @return how thick a plate's glass is, 0 to 1, by its unscaled area (§4.4) */
    private static float thickness(HudPlate plate, float scale) {
        float area = plate.width() * plate.height() / (scale * scale);
        return Math.min(1f, area / THICK_AREA);
    }

    private static float radius(HudPlate plate, float scale) {
        float half = plate.height() / 2f;
        return plate.capsule() ? half : Math.min(Palette.R_HUD_TILE * scale, plate.height() * TILE_SHARE);
    }

    private float radiusFor(HudTokens tokens, float height) {
        return capsule(tokens, height) ? height / 2f : Math.min(Palette.R_HUD_TILE, height * TILE_SHARE);
    }

    private static void solid(float x, float y, float w, float h, float radius, Color fill, Color border) {
        Render.roundRect(x, y, w, h, radius, fill);
        if (border != null) {
            Render.roundRectOutline(x, y, w, h, radius, BORDER, border);
        }
    }

    @Override
    public void meter(Content into, HudTokens tokens, int level, int max, Color fill) {
        Font font = tokens.font(TextRole.VALUE);
        float height = font.getHeight() * METER_HEIGHT;
        Color unlit = tokens.color(ColorRole.METER_TRACK);
        into.custom(max * METER_STEP + (max - 1) * METER_SPACING, font.getHeight(), (x, y, w, h) -> {
            float bottom = y + (h + height) / 2f;
            for (int i = 0; i < max; i++) {
                float step = height * (i + 1) / max;
                Render.roundRect(x + i * (METER_STEP + METER_SPACING), bottom - step, METER_STEP, step,
                        METER_STEP / 2f, i < level ? fill : unlit);
            }
        });
    }

    /** Thick and rounded, the accent filling a faint track. */
    @Override
    public void track(Content into, HudTokens tokens, float width, float progress, float alpha) {
        into.bar(width, TRACK_HEIGHT, TRACK_HEIGHT / 2f, progress,
                tokens.color(ColorRole.METER_TRACK).fade(alpha), tokens.color(ColorRole.METER_FILL).fade(alpha));
    }

    @Override
    public void icon(Content into, HudTokens tokens, HudIcon icon, Color colour) {
        float size = tokens.font(TextRole.PRIMARY).getHeight();
        into.custom(size, size, (x, y, w, h) -> icon.draw(x, y, w, colour));
    }

    /** A rounded-rectangle well in a faint fill, the icon inside it. Never glass on glass (§4.10). */
    @Override
    public void iconWell(Content into, HudTokens tokens, HudIcon icon, Color colour) {
        float line = tokens.font(TextRole.PRIMARY).getHeight();
        float size = line + WELL_PAD * 2f;
        Color well = tokens.text(TextRole.PRIMARY).fade(WELL_FADE);
        into.custom(size, size, (x, y, w, h) -> {
            Render.roundRect(x, y, w, h, WELL_RADIUS, well);
            icon.draw(x + WELL_PAD, y + WELL_PAD, line, colour);
        });
    }

    /** A small switch: a rounded rectangle, the accent while on, and its knob at the end it is at. */
    @Override
    public void toggleOn(Content into, HudTokens tokens, boolean on) {
        float line = tokens.font(TextRole.PRIMARY).getHeight();
        float height = line * 0.8f;
        float width = height * 1.7f;
        // On, the accent variant that holds 3:1 against the white knob (§5b.2).
        Color fill = on ? Accents.onToggle() : tokens.color(ColorRole.METER_TRACK);
        Color knob = tokens.text(TextRole.PRIMARY);
        into.custom(width, line, (x, y, w, h) -> {
            float top = y + (h - height) / 2f;
            Render.roundRect(x, top, w, height, CONTROL_RADIUS, fill);
            float inset = 1.5f;
            float side = height - inset * 2f;
            float knobX = on ? x + w - inset - side : x + inset;
            Render.roundRect(knobX, top + inset, side, side, CONTROL_RADIUS - inset, knob);
        });
    }

    /** The category's glyph in a rounded well tinted by its colour, inside the plate's padding (§4.11). */
    @Override
    public float categoryMarker(float x, float y, float height, HudTokens tokens, HudCategory category) {
        float line = tokens.font(TextRole.PRIMARY).getHeight();
        float well = line + WELL_PAD * 2f;
        float left = x + PAD_X;
        float top = y + (height - well) / 2f;
        Color colour = tokens.category(category);
        Render.roundRect(left, top, well, well, WELL_RADIUS, colour.fade(CATEGORY_WELL_FADE));
        HudGlyphs.of(category).draw(left + WELL_PAD, top + WELL_PAD, line, colour);
        return PAD_X + well + MARKER_GAP;
    }

    @Override
    public boolean marksListItems() {
        return true;
    }

    /** A small dot in the accent: the module is on (§4.7). */
    @Override
    public void listMarker(Content into, HudTokens tokens) {
        float line = tokens.font(TextRole.PRIMARY).getHeight();
        Color accent = tokens.color(ColorRole.ACCENT);
        into.custom(LIST_DOT * 2f, line, (x, y, w, h) -> Render.circle(x + w / 2f, y + h / 2f, LIST_DOT, accent));
    }

    @Override
    public void titleBlock(Content into, HudTokens tokens, String title, Color titleColour,
                           String subtitle, Color subtitleColour) {
        into.text(tokens.font(TextRole.TITLE), title, titleColour);
        if (!subtitle.isEmpty()) {
            into.text(tokens.font(TextRole.SUBTITLE), subtitle, subtitleColour);
        }
    }

    /**
     * @param textScale Text Size's factor
     * @param solid     plates solid rather than glass: Reduce Transparency or Increase Contrast
     * @param contrast  Increase Contrast: a border, and every colour lifted to 7:1 against the solid plate
     * @param light     the element's appearance: Light (dark text) or Dark (§4.14)
     */
    private record Tokens(float textScale, boolean solid, boolean contrast, boolean light) implements HudTokens {

        /** Primary text, values and titles semibold; the rest regular (§4.8). */
        @Override
        public Font font(TextRole role) {
            Font base = Render.getDefaultFont();
            if (base == null) {
                return null;
            }
            float size = base.getSize() * textScale;
            return switch (role) {
                case PRIMARY, VALUE -> Assets.fontFile(Palette.FONT.semibold(), size);
                case TITLE -> Assets.fontFile(Palette.FONT.semibold(), size + TITLE_STEP * textScale);
                case SECONDARY, TERTIARY, LABEL, SUBTITLE -> Assets.fontFile(Palette.FONT.regular(), size);
            };
        }

        /** Vibrant text: labels secondary, values primary; tertiary only for what is inactive (§4.8). */
        @Override
        public Color text(TextRole role) {
            return switch (role) {
                case PRIMARY, VALUE, TITLE -> strong(pick(Palette.VIBRANT_LABEL));
                case SECONDARY, LABEL, SUBTITLE -> strong(pick(Palette.VIBRANT_SECONDARY));
                case TERTIARY -> contrast ? strong(pick(Palette.VIBRANT_SECONDARY))
                        : pick(Palette.VIBRANT_SECONDARY).fade(TERTIARY_FADE);
            };
        }

        /** The accent only where §4.7 allows it: meter fills and toggles that are on. */
        @Override
        public Color color(ColorRole role) {
            return switch (role) {
                // The theme's first colour, lifted to 3:1 at the glass's brightest (§5b.2).
                case ACCENT, METER_FILL -> strong(Accents.onGlass(light));
                case METER_TRACK -> pick(Palette.VIBRANT_LABEL).fade(TRACK_FADE);
                case GOOD -> strong(pick(GOOD));
                case FAIR -> strong(pick(FAIR));
                case BAD -> strong(pick(BAD));
                case SEPARATOR -> contrast ? strong(pick(Palette.VIBRANT_SECONDARY))
                        : pick(Palette.VIBRANT_LABEL).fade(SEPARATOR_FADE);
            };
        }

        /** The glass's solid stand-in: used only while the plate is solid. */
        @Override
        public Color plate() {
            return pick(Palette.GLASS_SOLID);
        }

        @Override
        public Color border() {
            return contrast ? strong(pick(Palette.VIBRANT_SECONDARY)) : null;
        }

        @Override
        public float radius() {
            return Palette.R_HUD_TILE;
        }

        @Override
        public float padding() {
            return PAD_X;
        }

        @Override
        public float gap() {
            return GAP;
        }

        /** @return {@code tone}'s value for this element's own appearance, which Auto may set per capsule (§4.14) */
        /** Info in primary text; success, warning and error in good, fair and bad. */
        @Override
        public Color category(HudCategory category) {
            return switch (category) {
                case INFO -> text(TextRole.PRIMARY);
                case SUCCESS -> color(ColorRole.GOOD);
                case WARNING -> color(ColorRole.FAIR);
                case ERROR -> color(ColorRole.BAD);
            };
        }

        private Color pick(Palette.Tone tone) {
            return light ? tone.light() : tone.dark();
        }

        private Color strong(Color colour) {
            return contrast ? Contrast.lift(colour, pick(Palette.GLASS_SOLID), Contrast.HIGH) : colour;
        }
    }
}
