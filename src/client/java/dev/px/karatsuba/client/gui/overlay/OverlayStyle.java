package dev.px.karatsuba.client.gui.overlay;

import dev.px.core.render.Color;

/**
 * The game overlays' tokens: what the shell's {@code Palette} and
 * {@code Animations} are to the GUI, for screens drawn in place of the game's own.
 *
 * <p>Kept apart from the shell's because these are full-screen pictures, not
 * panels: they are laid out against the window's height rather than the GUI
 * scale, so they look the same whatever scale the player picked (see
 * {@link #REFERENCE_HEIGHT}), and they sit on a photograph rather than a surface.
 * Text colours still come from the shell's {@code Palette}; the theme's accent is
 * not used, so the overlays look the same whatever theme is picked.
 */
public final class OverlayStyle {

    // --------------------------------------------------------------- images

    /** The loading screen's backdrop by day: cherry trees at sunrise, 3840x2160. */
    public static final String LOADING_DAY = "/assets/karatsuba/texture/background/cherry-trees.png";

    /** The loading screen's backdrop by night: a torchlit harbour, 1920x1080. */
    public static final String LOADING_NIGHT = "/assets/karatsuba/texture/background/background-night.png";

    /** The hour, on the player's own clock, the day backdrop takes over: 06:00. */
    public static final int DAY_STARTS = 6;

    /** The hour the night backdrop takes over: 20:00. */
    public static final int NIGHT_STARTS = 20;

    /**
     * The panorama turning behind the title screen and every menu out of a world:
     * an End city, six 1280x1280 faces, {@code panorama_0.png} to
     * {@code panorama_5.png}. A path under {@code assets/karatsuba/} without the
     * suffix, since the game loads these itself rather than through {@code Assets}.
     * Each panorama has a folder of its own under {@code panorama/}; {@code tbtt/}
     * holds another set.
     */
    public static final String MENU_PANORAMA = "texture/background/panorama/end-city/panorama";

    // ---------------------------------------------------------------- colour

    /**
     * Behind the day backdrop, and all there is until it has decoded: a dark rose
     * near the photo's shadows, so the photo fading in reads as light arriving.
     */
    public static final Color DAY_BASE = Color.of(30, 17, 23, 255);

    /**
     * Behind the night backdrop, until it has decoded: the near black of the
     * photo's own shadows, so its arrival barely shows.
     */
    public static final Color NIGHT_BASE = Color.of(8, 8, 9, 255);

    /** The shade rising from the bottom edge that text and the bar sit on. */
    public static final Color SCRIM = Color.of(14, 8, 11, 200);

    /** How far up the screen the scrim reaches, as a share of its height. */
    public static final float SCRIM_REACH = 0.5f;

    /** The progress bar's empty part: lighter than the shell's track, to read over a photo. */
    public static final Color TRACK = Color.of(255, 255, 255, 46);

    /**
     * The progress bar's filled part: plain white rather than the theme's accent,
     * so it sits with the photo whatever theme is picked.
     */
    public static final Color BAR_FILL = Color.of(255, 255, 255, 255);

    // ---------------------------------------------------------------- layout

    /**
     * The height, in design units, every overlay is laid out against. The screen
     * is scaled so that it is this tall, whatever the GUI scale: 270 units is a
     * 1080p window at GUI scale 4, so one unit is four pixels there.
     */
    public static final float REFERENCE_HEIGHT = 270f;

    /** Content's distance from the screen's edges. */
    public static final float INSET = 16f;

    /** Between the wordmark and the caption row under it. */
    public static final float GAP = 6f;

    /** Between the caption row and the bar. */
    public static final float GAP_BAR = 4f;

    /** The progress bar's thickness; fully round at that size. */
    public static final float BAR_HEIGHT = 3f;

    /** The wordmark's size, in design units. */
    public static final float WORDMARK_SIZE = 22f;

    /** Captions: what is happening, and how far along. */
    public static final float CAPTION_SIZE = 8f;

    // ---------------------------------------------------------------- motion

    /** The backdrop fading in once it has decoded. An arrival, but a large one. */
    public static final float BACKDROP_FADE = 450f;

    /** How long the backdrop's slow push in takes, start to rest. */
    public static final float DRIFT = 20000f;

    /** How far the backdrop has pushed in when it rests: 4% larger. */
    public static final float DRIFT_SCALE = 1.04f;

    private OverlayStyle() {
    }
}
