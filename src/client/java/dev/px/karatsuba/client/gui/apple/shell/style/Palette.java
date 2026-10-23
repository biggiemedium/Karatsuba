package dev.px.karatsuba.client.gui.apple.shell.style;

import dev.px.core.Core;
import dev.px.core.render.Color;
import dev.px.core.render.font.Font;
import dev.px.core.render.theme.Theme;
import dev.px.karatsuba.adapter.asset.Assets;

/**
 * The Apple UI's design tokens: every colour, size, radius and font it uses
 * ({@code gui/apple/DESIGN.md} §3, §4, §11). Motion lives in {@link Animations}.
 *
 * <p>Every colour has a value for Light and for Dark ({@link Tone}); ask for it
 * with {@code get()} each frame and the {@link Appearance} setting decides. Grays
 * for everything but the accent and the sidebar icons. The accent is the theme's
 * primary colour lifted to 3:1 against a card: lighter in Dark, darker in Light.
 *
 * <p>Radii are derived, never written: an inner corner is its outer corner less
 * the padding between them, and {@link #derive} refuses to go under
 * {@link #R_MIN}, failing when this class loads rather than drawing a pinched
 * corner.
 *
 * <p><b>Classic aliases.</b> The tokens further down, marked as such, are the
 * Classic look's, kept so the Classic looks in this package still draw while
 * they are rewritten. Each goes when the last look using it does (PLAN.md).
 */
public final class Palette {

    // ======================================================== Apple: colour

    /** A colour with a value for each appearance. */
    public record Tone(Color dark, Color light) {

        /** @return the value for the appearance in use now */
        public Color get() {
            Appearance appearance = Appearance.get();
            return appearance != null && appearance.isLight() ? light : dark;
        }

        static Tone of(Color dark, Color light) {
            return new Tone(dark, light);
        }
    }

    /** The game behind the open GUI: dimmed by this, and blurred, like a macOS sheet's background (§5.1). */
    public static final Color BACKDROP_DIM = Color.of(0, 0, 0, 89);

    /** The window: a solid elevated surface. No translucency; the game never shows through it (§5.1). */
    public static final Tone WINDOW = Tone.of(Color.of(30, 30, 32, 255), Color.of(236, 236, 236, 255));

    /** A grouped-list card: solid, one step lighter than the window in Dark, white in Light. No border. */
    public static final Tone CARD = Tone.of(Color.of(42, 42, 45, 255), Color.of(255, 255, 255, 255));

    /** Popovers and menus: solid and elevated, with a soft shadow under them. */
    public static final Tone POPOVER = Tone.of(Color.of(50, 50, 54, 255), Color.of(255, 255, 255, 255));

    /** The row that is open in the detail pane: a neutral highlight, never the accent. */
    public static final Tone SELECTED = Tone.of(Color.of(255, 255, 255, 26), Color.of(0, 0, 0, 18));

    /** Hairlines between rows, inset to the label: low contrast. */
    public static final Tone SEPARATOR = Tone.of(Color.of(255, 255, 255, 22), Color.of(0, 0, 0, 20));

    /** A hovered row or sidebar entry. Never the accent. */
    public static final Tone HOVER_TINT = Tone.of(Color.of(255, 255, 255, 15), Color.of(0, 0, 0, 13));

    /** What you read. */
    public static final Tone LABEL = Tone.of(Color.of(255, 255, 255, 224), Color.of(0, 0, 0, 224));

    /** Values, descriptions, trailing actions: at least 60% emphasis, so it reads at the smallest window. */
    public static final Tone LABEL_SECONDARY = Tone.of(Color.of(255, 255, 255, 163), Color.of(0, 0, 0, 153));

    /** Disabled, placeholders, the › on a row. */
    public static final Tone LABEL_TERTIARY = Tone.of(Color.of(255, 255, 255, 71), Color.of(0, 0, 0, 66));

    /** A switch's track while off, a slider's empty track, a segmented control's track: visible on a card. */
    public static final Tone CONTROL_OFF = Tone.of(Color.of(255, 255, 255, 46), Color.of(0, 0, 0, 28));

    /** The selected segment's raised pill: lighter than its track. Neutral, never the accent. */
    public static final Tone CONTROL_THUMB = Tone.of(Color.of(112, 112, 117, 255), Color.of(255, 255, 255, 255));

    /** A push button's face: bright, white in Light and a lighter grey in Dark, with a hairline edge. */
    public static final Tone BUTTON = Tone.of(Color.of(84, 84, 88, 255), Color.of(255, 255, 255, 255));
    public static final Tone BUTTON_PRESSED = Tone.of(Color.of(104, 104, 108, 255), Color.of(236, 236, 236, 255));
    public static final Tone BUTTON_EDGE = Tone.of(Color.of(255, 255, 255, 24), Color.of(0, 0, 0, 30));

    /** The soft shadow under anything raised: a button, a segment pill, a knob. */
    public static final Color SHADOW = Color.of(0, 0, 0, 45);

    /** A switch's or slider's knob: white in both appearances, as macOS draws it. */
    public static final Color KNOB = Color.of(255, 255, 255, 255);

    /** A scroll bar's thumb while it shows. */
    public static final Tone SCROLL_THUMB = Tone.of(Color.of(255, 255, 255, 90), Color.of(0, 0, 0, 80));

    /** Text on glass: more contrast, since what is behind glass is anyone's guess. */
    public static final Tone VIBRANT_LABEL = Tone.of(Color.of(255, 255, 255, 255), Color.of(0, 0, 0, 235));
    public static final Tone VIBRANT_SECONDARY = Tone.of(Color.of(255, 255, 255, 184), Color.of(0, 0, 0, 168));

    /** A player's monogram circle (Friends): neutral, the same for everyone, with a white initial. */
    public static final Tone AVATAR = Tone.of(Color.of(99, 99, 104, 255), Color.of(160, 160, 166, 255));

    /** The dot on a monogram while that player is online; always beside words saying where they are (§4.2). */
    public static final Color PRESENCE = rgb(0x30D158);

    /** Glass's tint colour; its strength comes from the Glass setting. */
    public static final Tone GLASS_TONE = Tone.of(Color.of(42, 42, 44, 255), Color.of(255, 255, 255, 255));

    /**
     * Solid stand-ins, for Reduce Transparency and for measuring contrast: each is
     * what its material looks like over a neutral backdrop.
     */
    public static final Tone PANE_SOLID = WINDOW;
    public static final Tone CARD_SOLID = CARD;
    public static final Tone GLASS_SOLID = Tone.of(Color.of(42, 42, 44, 255), Color.of(246, 246, 248, 255));

    /**
     * The HUD editor's hide and show badges (§10.2b): Control Center's grey circle with
     * a white − or +, the same in both appearances since they sit over the game, and a
     * little lighter under the cursor. Used nowhere else.
     */
    public static final Color BADGE = Color.of(86, 86, 92, 255);
    public static final Color BADGE_HOVER = Color.of(116, 116, 122, 255);
    public static final Color BADGE_INK = Color.of(255, 255, 255, 255);
    public static final Color BADGE_SHADOW = Color.of(0, 0, 0, 90);

    /**
     * The HUD editor's outlines (§10.2b): every element ringed in white, brighter under
     * the cursor, in its plate's own shape; the selection in the accent instead. White
     * in both appearances, since they sit over the game.
     */
    public static final Color EDIT_OUTLINE = Color.of(255, 255, 255, 150);
    public static final Color EDIT_OUTLINE_HOVER = Color.of(255, 255, 255, 235);
    /** The scale grip: a white dot ringed in the accent. */
    public static final Color GRIP_FILL = Color.of(255, 255, 255, 255);
    public static final Color GRIP_SHADOW = Color.of(0, 0, 0, 110);

    // The sidebar's icon tiles (§4.7): Apple's Light-mode system colours, kept in
    // both appearances as Tahoe's System Settings does, and used nowhere else.
    public static final Color ICON_RED = rgb(0xFF3B30);
    public static final Color ICON_BLUE = rgb(0x007AFF);
    public static final Color ICON_ORANGE = rgb(0xFF9500);
    public static final Color ICON_BROWN = rgb(0xA2845E);
    public static final Color ICON_TEAL = rgb(0x30B0C7);
    public static final Color ICON_PURPLE = rgb(0xAF52DE);
    public static final Color ICON_GRAY = rgb(0x8E8E93);
    public static final Color ICON_INDIGO = rgb(0x5856D6);
    public static final Color ICON_PINK = rgb(0xFF2D55);
    public static final Color ICON_GREEN = rgb(0x34C759);
    /** Appearance's tile: near-black, so it needs {@link #ICON_EDGE} to show in Dark. */
    public static final Color ICON_GRAPHITE = rgb(0x1C1C1E);

    /** The glyph on an icon tile: solid white, never translucent. */
    public static final Color ICON_GLYPH = Color.of(255, 255, 255, 255);

    /** How far a tile's colour is lightened at its top and darkened at its bottom: a faint shade, 0 to 1. */
    public static final float ICON_SHADE = 0.08f;

    /** The 1px inner highlight along a tile's top edge. */
    public static final Color ICON_HIGHLIGHT = Color.of(255, 255, 255, 64);

    /** A tile's 1px outline: white at 10% in Dark, so a dark tile stays visible; none in Light. */
    public static final Tone ICON_EDGE = Tone.of(Color.of(255, 255, 255, 26), Color.of(255, 255, 255, 0));

    /** @return the tile colour for a sidebar entry, by its id or category name (any case) */
    public static Color iconColor(String entry) {
        return switch (entry == null ? "" : entry.toLowerCase(java.util.Locale.ROOT)) {
            case "combat" -> ICON_RED;
            case "movement" -> ICON_BLUE;
            case "building" -> ICON_ORANGE;
            case "mining" -> ICON_BROWN;
            case "inventory" -> ICON_TEAL;
            case "render" -> ICON_PURPLE;
            case "automation" -> ICON_INDIGO;
            case "hud" -> ICON_PINK;
            case "friends" -> ICON_GREEN;
            case "appearance" -> ICON_GRAPHITE;
            default -> ICON_GRAY;
        };
    }

    // ======================================================== Apple: shape

    /** No derived corner may be tighter than this; a smaller one means the padding is wrong. */
    public static final float R_MIN = 2f;

    public static final float INSET_WINDOW = 8f;
    public static final float INSET_SIDEBAR = 4f;
    public static final float INSET_SEGMENT = 1f;
    public static final float INSET_POPOVER = 4f;
    public static final float PAD_HUD_TILE = 6f;

    /** Cards from the pane's edges: the pane's padding, which card corners are derived from. */
    public static final float INSET_CONTENT = 16f;

    /** A segmented control's height; a push button's and a pop-up's: shorter than a row. */
    public static final float SEGMENTED_HEIGHT = 18f;
    public static final float BUTTON_HEIGHT = 18f;

    public static final float R_WINDOW = 26f;
    public static final float R_SIDEBAR = derive(R_WINDOW, INSET_WINDOW);
    public static final float R_SIDEBAR_ROW = derive(R_SIDEBAR, INSET_SIDEBAR);
    /** Concentric with the pane: its radius less its padding. */
    public static final float R_CARD = derive(R_WINDOW, INSET_CONTENT);
    /** A segmented control is a capsule, and its pill a capsule inset 1 inside it. */
    public static final float R_SEGMENTED = SEGMENTED_HEIGHT / 2f;
    public static final float R_SEGMENT = derive(R_SEGMENTED, INSET_SEGMENT);
    /** Push buttons, pop-up buttons, text fields and key caps. */
    public static final float R_BUTTON = 6f;
    public static final float R_POPOVER = 12f;
    public static final float R_MENU_ITEM = derive(R_POPOVER, INSET_POPOVER);
    public static final float R_HUD_TILE = 14f;
    public static final float R_HUD_INNER = derive(R_HUD_TILE, PAD_HUD_TILE);

    /** A sidebar icon tile, and its corners: macOS's ratio, 22.5% of its side. */
    public static final float ICON_SIZE = 20f;
    public static final float R_ICON = ICON_SIZE * 0.225f;

    // ====================================================== Apple: spacing

    /** Every row in a card, whatever its control. A row with a second line grows to fit it. */
    public static final float CELL_HEIGHT = 26f;

    /** Either side of a row, inside its card; hairlines start at the left one. */
    public static final float CELL_INSET = 12f;

    /** Between cards, and between a header and its card. */
    public static final float GAP_CARD = 18f;
    public static final float GAP_CARD_HEADER = 5f;

    /** Between standalone action buttons under a card, and above them. */
    public static final float GAP_ACTIONS = 6f;

    /** Between a row's value and its control, and between controls in a row. */
    public static final float GAP_CONTROL = 8f;

    /** A switch: a capsule about twice as wide as tall, its knob inset {@link #SWITCH_INSET} all round. */
    public static final float SWITCH_WIDTH = 30f;
    public static final float SWITCH_HEIGHT = 16f;
    public static final float SWITCH_INSET = 2f;

    /** A stepper: a capsule split into − and +. */
    public static final float STEPPER_WIDTH = 38f;
    public static final float STEPPER_HEIGHT = 18f;

    /** A slider in a row, beside its value: a thin track and an elongated knob. */
    public static final float SLIDER_WIDTH = 110f;
    public static final float SLIDER_TRACK = 4f;
    public static final float SLIDER_KNOB_WIDTH = 14f;
    public static final float SLIDER_KNOB_HEIGHT = 10f;

    /** An overlay scroll bar's thumb, and how close to the edge it runs. */
    public static final float SCROLLBAR_WIDTH = 3f;

    /** A text field in a row. */
    public static final float FIELD_WIDTH = 130f;

    public static final float SIDEBAR_PANE_WIDTH = 168f;
    /** A sidebar entry: its icon tile and 2 either side, the most that lets every entry show at the default window height. */
    public static final float SIDEBAR_ROW_HEIGHT = 24f;

    /** Inside the sidebar pane: above the search field, and either side and below (INSET_SIDEBAR). */
    public static final float SIDEBAR_PAD_TOP = 8f;

    /** Between groups of sidebar entries: room, not a line. */
    public static final float SIDEBAR_GAP_GROUP = 10f;

    /** A row's inset either side, inside its selection fill. */
    public static final float SIDEBAR_ROW_INSET = 6f;

    /** The search field: a pill one row tall. */
    public static final float SEARCH_HEIGHT = 20f;

    /** The glyph on an icon tile (62% of it), and the gap from the tile to its label. */
    public static final float ICON_GLYPH_SIZE = ICON_SIZE * 0.62f;

    /** The one stroke weight of a tile's glyphs, as a share of the glyph's size. */
    public static final float ICON_STROKE = 0.16f;
    public static final float GAP_ICON_LABEL = 7f;

    /** The sidebar with icons alone: the icon, its row's inset, the pane's, and the scroll bar's room balanced either side. */
    public static final float SIDEBAR_COLLAPSED_WIDTH = ICON_SIZE + SIDEBAR_ROW_INSET * 2f + INSET_SIDEBAR * 2f
            + (SCROLLBAR_WIDTH + 2f) * 2f;

    /** Below this window width the sidebar shows icons alone. */
    public static final float SIDEBAR_COLLAPSE_WIDTH = 560f;

    /** How far one notch of the wheel scrolls a list, and a long list read row by row. */
    public static final float SCROLL_STEP = 28f;
    public static final float SCROLL_STEP_FINE = 18f;

    /** A theme's circle on the Appearance page, and the room between circles. */
    public static final float THEME_CIRCLE = 18f;
    public static final float GAP_THEME = 9f;

    /** Modules in its Split layout: the list's width, and the room between it and the module. */
    public static final float SPLIT_LIST_WIDTH = 210f;
    public static final float GAP_SPLIT = 14f;

    /** A player's monogram in a list row, and on the first card of their page. */
    public static final float AVATAR_SIZE = 18f;
    public static final float AVATAR_LARGE = 36f;

    /** Players on this server listed on the Add Player page before "Show All (n)". */
    public static final int SUGGESTIONS_SHOWN = 6;

    /** An empty state's text column, centred in the content. */
    public static final float EMPTY_WIDTH = 260f;

    /** The widest the content column gets; it centres in any room beyond (§2.4). Search fields included. */
    public static final float CONTENT_MAX_WIDTH = 600f;

    public static final float HEADER_HEIGHT = 32f;

    /** The header's corners: concentric with the window's top right, as the sidebar's are with its top left. */
    public static final float R_HEADER = derive(R_WINDOW, INSET_WINDOW);

    /** A back or forward button in the header. */
    public static final float NAV_BUTTON = 22f;
    /** Inside the header: before the buttons, and after the title. */
    public static final float HEADER_PAD_LEFT = 5f;
    public static final float HEADER_PAD_RIGHT = 12f;

    /** Where content starts below the window's top: under the header, and a little room. */
    public static final float CONTENT_TOP = INSET_WINDOW + HEADER_HEIGHT + 4f;

    /** The band at a glass shape's rim where it bends what is behind it. */
    public static final float GLASS_RIM = 10f;

    /** The HUD editor's toolbar (§10.2b): a glass capsule; a tool, square for a glyph; inside the capsule; between tools. */
    public static final float TOOL_SIZE = 24f;
    public static final float TOOLBAR_PAD = 4f;
    public static final float TOOLBAR_GAP = 2f;
    /** A tool's glyph, and a labelled tool's ends. */
    public static final float TOOL_GLYPH = 11f;
    public static final float TOOL_PAD = 10f;
    /** A hovered tool's tint, and a disabled one's ink, as shares of the toolbar's text colour. */
    public static final float TOOL_HOVER = 0.14f;
    public static final float TOOL_DISABLED = 0.35f;
    /** How far above the screen's bottom edge the toolbar floats. */
    public static final float TOOLBAR_MARGIN = 16f;

    /**
     * A hide or show badge (§10.2b): its diameter, its − or + stroke, and how far its
     * centre sits in from the plate's corner curve along each axis; negative is outward,
     * so it overhangs the element rather than covering it.
     */
    public static final float BADGE_SIZE = 14f;
    public static final float BADGE_STROKE = 1.5f;
    public static final float BADGE_GLYPH = 6f;
    public static final float BADGE_INSET = -3f;

    /** An element's outline in the editor: its weight, and how far outside the plate it runs. */
    public static final float EDIT_OUTLINE_WEIGHT = 1f;
    public static final float EDIT_OUTLINE_GAP = 0.25f;
    /** The selection's accent ring, and the glow beyond it: rings fading out, one unit apart. */
    public static final float EDIT_SELECT_WEIGHT = 1.5f;
    public static final int EDIT_GLOW_RINGS = 3;
    public static final float EDIT_GLOW_ALPHA = 0.35f;
    /** A locked selection's ring, as a share of the accent. */
    public static final float EDIT_LOCKED_FADE = 0.45f;
    /** The scale grip: its diameter and its accent ring; and the square it is pressed in, a little larger than the dot. */
    public static final float GRIP_SIZE = 7f;
    public static final float GRIP_RING = 1.5f;
    public static final float GRIP_TARGET = 10f;

    // ======================================================== Apple: fonts

    /** A font family's files, by path under {@code assets/karatsuba/font} (one folder per family), one per style the UI uses. */
    public record Typeface(String regular, String semibold, String italic) {
    }

    /** The one font token. Swapping fonts is changing this line. */
    public static final Typeface FONT = new Typeface("sf-pro/sf-pro.ttf", "sf-pro/sf-pro-semibold.otf", "sf-pro/sf-pro-italic.ttf");

    /** Rows, values, the sidebar. */
    public static Font text() {
        return Assets.fontFile(FONT.regular(), baseSize());
    }

    /** A row's label that leads, a selected tab: the body size, semibold. */
    public static Font textStrong() {
        return Assets.fontFile(FONT.semibold(), baseSize());
    }

    /** Section headers above cards, captions, descriptions: one step smaller. */
    public static Font caption() {
        return Assets.fontFile(FONT.regular(), baseSize() - 1f);
    }

    /** The page title in the header: semibold. */
    public static Font heading() {
        return Assets.fontFile(FONT.semibold(), baseSize() + 4f);
    }

    /** Asides and examples: the body size, italic. */
    public static Font emphasis() {
        return Assets.fontFile(FONT.italic(), baseSize());
    }

    /** Every size is a step from the client's default, so the UI scales with it. */
    private static float baseSize() {
        return Core.fonts().getDefault().getSize();
    }

    // ===================================================== Apple: helpers

    /**
     * An inner corner from its outer one: {@code outer - inset}.
     *
     * @throws IllegalStateException if that is under {@link #R_MIN}: fix the padding
     */
    static float derive(float outer, float inset) {
        float inner = outer - inset;
        if (inner < R_MIN) {
            throw new IllegalStateException("Corner " + outer + " less " + inset + " is " + inner
                    + ", under the minimum " + R_MIN + "; the padding is too big for its parent");
        }
        return inner;
    }

    private static Color rgb(int hex) {
        return Color.of((hex >> 16) & 0xFF, (hex >> 8) & 0xFF, hex & 0xFF, 255);
    }

    // ===================================================================
    // Classic aliases: the Classic look's tokens, until its looks are gone.
    // ===================================================================

    // ------------------------------------------------------------- surfaces

    /** Dims the game behind the GUI. */
    public static final Color SCRIM = Color.of(0, 0, 0, 110);

    /** The panel: the lowest surface. Translucent here, so the glass under the window shows. */
    public static final Color BASE = Color.of(18, 18, 21, 140);

    /** The category sidebar, one step up. */
    public static final Color SIDEBAR = Color.of(24, 24, 28, 90);

    /** A hovered row. */
    public static final Color HOVER = Color.of(255, 255, 255, 10);

    /** A selected row, a chip, a text field. */
    public static final Color RAISED = Color.of(255, 255, 255, 18);

    /** A pressed or open control. */
    public static final Color RAISED_HIGH = Color.of(255, 255, 255, 28);

    /** Popups, menus and tooltips: the highest surface. */
    public static final Color POPUP = Color.of(36, 36, 42, 252);

    /** Hairlines: the panel's edge, the divider under a header. */
    public static final Color LINE = Color.of(255, 255, 255, 16);

    /** A switch's track while off, a slider's empty track. */
    public static final Color TRACK = Color.of(255, 255, 255, 34);

    // ------------------------------------------------------------------ text

    /** What you read: names, values. 87%. */
    public static final Color TEXT = Color.of(255, 255, 255, 222);

    /** Labels and descriptions. 60%. */
    public static final Color TEXT_MUTED = Color.of(255, 255, 255, 153);

    /** Disabled, placeholders and captions. 38%. */
    public static final Color TEXT_FAINT = Color.of(255, 255, 255, 97);

    /** A warning: a keybind that clashes with another. Amber, the one colour besides the accent. */
    public static final Color WARN = Color.of(242, 184, 88, 235);

    /** Someone online: the dot on a friend's initial. Always beside the word "Online" or a distance. */
    public static final Color ONLINE = Color.of(98, 204, 132, 255);

    /** The knob of a switch that is on, and text on the accent. */
    public static final Color ON_ACCENT = Color.of(255, 255, 255, 245);

    // --------------------------------------------------------------- geometry

    public static final float RADIUS_PANEL = 8f;
    public static final float RADIUS_CONTROL = 4f;

    public static final float ROW_HEIGHT = 22f;
    public static final float GAP_ROW = 2f;
    public static final float GAP_SECTION = 12f;
    public static final float INSET = 8f;

    public static final float SIDEBAR_WIDTH = 116f;
    public static final float LIST_WIDTH = 156f;

    /** The navigation rail, with its labels and with icons alone. */
    public static final float RAIL_WIDTH = 54f;
    public static final float RAIL_WIDTH_COMPACT = 38f;

    /** Where every section's title row sits, so switching sections never moves it. */
    public static final float TITLE_HEIGHT = 22f;

    /** The window's default size, and the least it can be resized to. */
    public static final float FRAME_WIDTH = 700f;
    public static final float FRAME_HEIGHT = 380f;
    public static final float FRAME_MIN_WIDTH = 420f;
    public static final float FRAME_MIN_HEIGHT = 220f;

    /** The accent never gets fainter than this against a card ({@link #CARD_SOLID}). */
    private static final double MIN_CONTRAST = 3.0d;

    // ------------------------------------------------- the theme crossfade

    private static Theme shown;
    private static Color fromPrimary;
    private static Color fromSecondary;
    private static long changedAt;

    private Palette() {
    }

    // ----------------------------------------------------------------- accent

    /** The theme's primary colour, lifted until it reads against the panel. */
    public static Color accent() {
        return accent(0f);
    }

    /** A point on the theme's gradient, 0 primary to 1 secondary, lifted the same way. */
    public static Color accent(float progress) {
        Theme active = Core.themes().getActive();
        if (active != shown) {
            // Start the crossfade from whatever is on screen now, even mid-fade.
            if (shown != null) {
                fromPrimary = blended(0f);
                fromSecondary = blended(1f);
                changedAt = System.nanoTime();
            }
            shown = active;
        }
        Color now = blended(progress);
        return legible(now);
    }

    /** The accent at {@code alpha}, for a tint behind something selected. */
    public static Color accentTint(int alpha) {
        return accent().withAlpha(alpha);
    }

    /** {@code theme}'s own accent, lifted: for drawing a theme that is not the active one. */
    public static Color accentOf(Theme theme, float progress) {
        return legible(theme.accent(progress));
    }

    private static Color blended(float progress) {
        Color target = shown.accent(progress);
        if (fromPrimary == null) {
            return target;
        }
        float t = Easing.LINEAR.apply(Animations.progress(changedAt, Animations.THEME));
        if (t >= 1f) {
            fromPrimary = null;
            fromSecondary = null;
            return target;
        }
        return fromPrimary.lerp(fromSecondary, progress).lerp(target, t);
    }

    /**
     * Lifted until it reaches 3:1 against a card in the appearance in use: toward
     * white in Dark, toward black in Light, where white would only fade it further.
     */
    private static Color legible(Color color) {
        Color lifted = color.withAlpha(255);
        Color card = CARD_SOLID.get();
        Color toward = card.equals(CARD_SOLID.light()) ? Color.BLACK : Color.WHITE;
        for (int step = 0; step < 10 && contrast(lifted, card) < MIN_CONTRAST; step++) {
            lifted = lifted.lerp(toward, 0.15f);
        }
        return lifted;
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05d) / (Math.min(first, second) + 0.05d);
    }

    private static double luminance(Color color) {
        return 0.2126d * linear(color.redF()) + 0.7152d * linear(color.greenF()) + 0.0722d * linear(color.blueF());
    }

    private static double linear(float channel) {
        return channel <= 0.03928f ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    // ------------------------------------------------------------------ fonts

    // Classic font names, now set in the Apple font, so the looks not yet
    // rewritten already read like the Apple UI.

    /** Body text. Alias of {@link #text()}. */
    public static Font body() {
        return text();
    }

    /** Section headers and the selected tab. Alias of {@link #textStrong()}. */
    public static Font strong() {
        return textStrong();
    }

    /** The module's name over its settings, and the brand: semibold, three steps up. */
    public static Font title() {
        return Assets.fontFile(FONT.semibold(), baseSize() + 3f);
    }

    /** Captions. Alias of {@link #caption()}. */
    public static Font small() {
        return caption();
    }

    // ---------------------------------------------------------------- colour

    /**
     * What to draw on top of {@code accent}: white, unless the accent is so light
     * (the White theme, say) that white would vanish into it, then near-black.
     */
    public static Color onAccent(Color accent) {
        return luminance(accent) > 0.6d ? Color.of(24, 24, 28, 235) : ON_ACCENT;
    }

    /**
     * The control accent: the theme's primary, darkened and saturated until white
     * reaches 3:1 on it. A switch's "on" fill (its knob is white) and the sidebar's
     * selection (its text is white) both use it, so a pale theme such as a lavender
     * still shows them plainly. A dark accent comes back as it is.
     */
    public static Color accentControl() {
        Color fill = accent();
        for (int step = 0; step < 16 && contrast(Color.WHITE, fill) < 3.0d; step++) {
            fill = saturated(fill.lerp(Color.BLACK, 0.07f), 1.12f);
        }
        return fill;
    }

    /** {@code colour} pushed {@code amount} times further from its own grey. */
    private static Color saturated(Color colour, float amount) {
        float grey = colour.redF() * 0.299f + colour.greenF() * 0.587f + colour.blueF() * 0.114f;
        return Color.of(channel(grey + (colour.redF() - grey) * amount),
                channel(grey + (colour.greenF() - grey) * amount),
                channel(grey + (colour.blueF() - grey) * amount), colour.getAlpha());
    }

    private static int channel(float value) {
        return Math.round(Math.max(0f, Math.min(1f, value)) * 255f);
    }

    /** {@code colour} with its opacity scaled by {@code amount}, 0 to 1. */
    public static Color faded(Color colour, float amount) {
        return colour.withAlpha(Math.round(colour.getAlpha() * Math.max(0f, Math.min(1f, amount))));
    }
}
