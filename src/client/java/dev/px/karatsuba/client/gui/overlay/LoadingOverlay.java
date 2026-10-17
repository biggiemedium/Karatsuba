package dev.px.karatsuba.client.gui.overlay;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Texture;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.adapter.screen.LoadingScreen;
import dev.px.karatsuba.client.Karatsuba;
import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.gui.stable.shell.style.Easing;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;

import java.time.LocalTime;

/**
 * The loading screen: a photo in place of the Mojang red, with the wordmark and
 * a progress bar at the bottom left. The cherry trees from 06:00 until 20:00 on
 * the player's clock, the harbour at night otherwise.
 *
 * <p>Shown at start-up and on every reload after (a resource pack change, F3+T).
 * The game still runs the loading: it says how far along it is, when it is done,
 * and fades this out over the title screen; see the adapter's
 * {@link LoadingScreen}.
 *
 * <h2>Layout</h2>
 * Everything sits on a scrim rising from the bottom edge, at the bottom left,
 * away from the sun in the middle of the photo where white text would vanish. The
 * screen is laid out {@link OverlayStyle#REFERENCE_HEIGHT} units tall whatever the
 * GUI scale, so the wordmark is the same share of the window at scale 1 or 4.
 * NanoVG rasterises text at the size it ends up, so the scaling stays sharp.
 *
 * <h2>The backdrop</h2>
 * Chosen when each showing begins and kept for it. Decoded on a worker
 * ({@link Assets#backdrop}): at 4K it would otherwise freeze the first frame.
 * Until it arrives the screen is that photo's base colour, and then it fades in. It pushes in slowly while loading goes on, unless the player
 * has turned animations off.
 */
public final class LoadingOverlay implements LoadingScreen {

    /** Which photo, chosen by the clock as each showing begins. */
    private enum Backdrop {
        DAY(OverlayStyle.LOADING_DAY, OverlayStyle.DAY_BASE),
        NIGHT(OverlayStyle.LOADING_NIGHT, OverlayStyle.NIGHT_BASE);

        private final String path;
        private final Color base;

        Backdrop(String path, Color base) {
            this.path = path;
            this.base = base;
        }

        /** @return the day photo from 06:00 until 20:00 on the player's clock, the night one otherwise */
        static Backdrop forNow() {
            int hour = LocalTime.now().getHour();
            return hour >= OverlayStyle.DAY_STARTS && hour < OverlayStyle.NIGHT_STARTS ? DAY : NIGHT;
        }
    }

    /** This showing's photo. Never switched mid-showing, so 20:00 striking during a load changes nothing. */
    private Backdrop backdrop = Backdrop.forNow();

    /** When this showing's photo was first drawn, for its fade in; 0 until then. */
    private long backdropShownAt;

    /** When this showing began, for the backdrop's push in. */
    private long shownAt;

    /** The last frame drawn, to tell a new showing (a reload) from the next frame. */
    private long lastDrawnAt;

    /** A gap between frames longer than this means the screen went away and came back. */
    private static final long NEW_SHOWING_NANOS = 1_000_000_000L;

    @Override
    public void render(float screenWidth, float screenHeight, float progress, float opacity, float finished) {
        long now = System.nanoTime();
        if (lastDrawnAt == 0L || now - lastDrawnAt > NEW_SHOWING_NANOS) {
            shownAt = now;
            Backdrop chosen = Backdrop.forNow();
            if (chosen != backdrop) {
                // The other photo fades in afresh; the one already seen does not.
                backdrop = chosen;
                backdropShownAt = 0L;
            }
        }
        lastDrawnAt = now;

        Render.pushAlpha(opacity);
        try {
            drawBackdrop(screenWidth, screenHeight);

            float scale = screenHeight / OverlayStyle.REFERENCE_HEIGHT;
            float width = screenWidth / scale;
            float height = OverlayStyle.REFERENCE_HEIGHT;
            Render.scaled(0f, 0f, scale, () -> drawContent(width, height, progress, 1f - finished));
        } finally {
            Render.popAlpha();
        }
    }

    /** The photo, filling the screen without stretching, cropped to centre. */
    private void drawBackdrop(float width, float height) {
        Render.rect(0f, 0f, width, height, this.backdrop.base);

        Texture backdrop = Assets.backdrop(this.backdrop.path);
        if (backdrop == null || !backdrop.isValid()) {
            return;
        }
        if (backdropShownAt == 0L) {
            backdropShownAt = System.nanoTime();
        }
        float fade = Animations.enabled()
                ? Easing.STANDARD.apply(Animations.progress(backdropShownAt, OverlayStyle.BACKDROP_FADE)) : 1f;
        float drift = Animations.enabled()
                ? Easing.DECELERATE.apply(Animations.progress(shownAt, OverlayStyle.DRIFT)) : 0f;

        float zoom = 1f + (OverlayStyle.DRIFT_SCALE - 1f) * drift;
        float cover = Math.max(width / backdrop.getWidth(), height / backdrop.getHeight()) * zoom;
        float drawnWidth = backdrop.getWidth() * cover;
        float drawnHeight = backdrop.getHeight() * cover;
        float x = (width - drawnWidth) / 2f;
        float y = (height - drawnHeight) / 2f;
        Render.clipped(0f, 0f, width, height, () ->
                Render.texture(backdrop, x, y, drawnWidth, drawnHeight, Color.WHITE.withAlpha(fade)));

        float scrimTop = height * (1f - OverlayStyle.SCRIM_REACH);
        Render.gradientV(0f, scrimTop, width, height - scrimTop,
                OverlayStyle.SCRIM.withAlpha(0), OverlayStyle.SCRIM);
    }

    /**
     * The wordmark, the caption row and the bar, from the bottom up, in design
     * units. {@code barOpacity} takes the caption and bar away once loading is
     * done, leaving the wordmark for the fade out.
     */
    private void drawContent(float width, float height, float progress, float barOpacity) {
        Font wordmark = Assets.fontFile("sf-pro/sf-pro-semibold.otf", OverlayStyle.WORDMARK_SIZE);
        Font caption = Assets.fontFile("sf-pro/sf-pro.ttf", OverlayStyle.CAPTION_SIZE);

        float left = OverlayStyle.INSET;
        float right = width - OverlayStyle.INSET;
        float barY = height - OverlayStyle.INSET - OverlayStyle.BAR_HEIGHT;
        float captionY = barY - OverlayStyle.GAP_BAR - caption.getHeight();
        float wordmarkY = captionY - OverlayStyle.GAP - wordmark.getHeight();

        Render.text(wordmark, Karatsuba.NAME, left, wordmarkY, Palette.TEXT);

        if (barOpacity <= 0f) {
            return;
        }
        Render.pushAlpha(barOpacity);
        try {
            Render.text(caption, "Loading resources", left, captionY, Palette.TEXT_MUTED);
            // Right-aligned to the bar's end, so its changing width never moves it.
            String percent = Math.round(progress * 100f) + "%";
            Render.text(caption, percent, right - caption.widthOf(percent), captionY, Palette.TEXT_MUTED);

            Render.progressBar(left, barY, right - left, OverlayStyle.BAR_HEIGHT, OverlayStyle.BAR_HEIGHT / 2f,
                    progress, OverlayStyle.TRACK, OverlayStyle.BAR_FILL);
        } finally {
            Render.popAlpha();
        }
    }
}
