package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.integration.MediaTrack;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Texture;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.asset.RemoteImages;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What is playing: cover art, title, artist, album, and how far through.
 *
 * <p>Reads {@code Core.integrations().getCurrentTrack()}, which the media provider
 * (Spotify, in the adapter) fills on its poll interval. The position comes from
 * {@link MediaTrack#getProgress()}, extrapolated from the last poll, so the bar
 * moves every frame though Spotify is asked every few seconds.
 *
 * <h2>Sized by what it shows</h2>
 * The text column is as wide as its longest line, up to Width, and as tall as the
 * rows switched on; the cover follows the text's height unless given a size of its
 * own. The box eases between sizes as every {@link StyledElement} does. A card by
 * default, in the HUD Style's plate and colours: the title in the value colour,
 * the rest muted, the bar in the label colour.
 *
 * <p>Fades in when something starts playing and out when it stops, rather than
 * popping. The cover comes from {@link RemoteImages}; until it arrives, a plain
 * square holds its place. With Cover on Auto it sits on the side the element is
 * anchored to, so the text always faces the middle of the screen.
 */
public final class NowPlayingElement extends StyledElement {

    private static final float COVER_GAP = 6f;
    private static final float TIME_GAP = 8f;

    /** The narrowest the text column gets with Auto Width, so a short title does not make a sliver. */
    private static final float MIN_TEXT_WIDTH = 50f;

    private static final long FADE_MILLIS = 180L;

    /** Scrolling titles: pixels a second, the rest at the start of each pass, and the space before it repeats. */
    private static final float SCROLL_SPEED = 22f;
    private static final long SCROLL_REST_MILLIS = 2000L;
    private static final float SCROLL_GAP = 24f;

    private static final MediaTrack SAMPLE = new MediaTrack("Song Title", "Artist", "Album",
            200_000L, 80_000L, 0L, false, "");

    public enum Cover { AUTO, LEFT, RIGHT, OFF }

    public enum LongTitles { SCROLL, CUT }

    // ----------------------------------------------------------------- layout

    private final EnumSetting<Cover> cover = enumOf("Cover", Cover.AUTO)
            .describe("Which side the cover art sits on, or none. Auto is the side it is anchored to");
    private final BooleanSetting fitCover = bool("Fit Cover To Text", true)
            .describe("The cover is as tall as the text beside it")
            .visibleWhen(() -> cover.get() != Cover.OFF);
    private final NumberSetting<Integer> coverSize = integer("Cover Size", 36, 12, 96)
            .visibleWhen(() -> cover.get() != Cover.OFF && !fitCover.isOn());
    private final BooleanSetting autoWidth = bool("Auto Width", true)
            .describe("As wide as the longest line, up to Width");
    private final NumberSetting<Integer> width = integer("Width", 140, 60, 320)
            .describe("The most the text may take with Auto Width on; exactly this with it off");
    private final EnumSetting<LongTitles> longTitles = enumOf("Long Titles", LongTitles.SCROLL)
            .describe("Scroll a line too long for the width, or cut it short");

    private final GroupSetting layoutGroup = group("Layout", true)
            .containing(cover, fitCover, coverSize, autoWidth, width, longTitles);

    // ------------------------------------------------------------------- show

    private final BooleanSetting showArtist = bool("Artist", true);
    private final BooleanSetting showAlbum = bool("Album", false);
    private final BooleanSetting showBar = bool("Progress Bar", true);
    private final BooleanSetting showTime = bool("Time", true)
            .describe("The time through the track, and its length");
    private final BooleanSetting showPaused = bool("While Paused", true)
            .describe("Keep showing the track when playback is paused, dimmed");

    private final GroupSetting showGroup = group("Show", true)
            .containing(showArtist, showAlbum, showBar, showTime, showPaused);

    // ------------------------------------------------------------------ state

    private final Animation presence = Animation.fade(FADE_MILLIS, Easing.QUAD_OUT);

    /** Kept while fading out, when the provider has already stopped answering. */
    private MediaTrack shown;
    private String scrollingTitle = "";
    private long scrollStartedAt;

    /** This frame's fade, for the plate the base class draws. */
    private float opacity = 1f;

    public NowPlayingElement() {
        super("now_playing", "Now Playing", HudLayout.at(Anchor.MIDDLE_LEFT, 4f, 0f));
    }

    @Override
    protected void describe(Content body) {
        Font font = tokens().font(TextRole.PRIMARY);
        MediaTrack track = isEditing() ? SAMPLE : Core.integrations().getCurrentTrack();
        boolean wanted = track != null && (isEditing() || track.isPlaying() || showPaused.isOn());

        presence.duration(HudStyle.get().motion() == HudStyle.Motion.OFF ? 0L : FADE_MILLIS).target(wanted);
        if (wanted) {
            shown = track;
        } else if (presence.isIdleAtZero()) {
            shown = null;
        }
        if (shown == null) {
            return;
        }
        float alpha = presence.get();
        opacity = alpha;
        MediaTrack drawn = shown;
        watch(drawn.getTitle());
        if (!Objects.equals(drawn.getTitle(), scrollingTitle)) {
            scrollingTitle = drawn.getTitle();
            scrollStartedAt = System.currentTimeMillis();
        }

        List<Line> lines = lines(drawn);
        float textWidth = autoWidth.isOn()
                ? Math.max(MIN_TEXT_WIDTH, Math.min(width.getInt(), natural(font, lines, drawn)))
                : width.getInt();
        boolean hasCover = cover.get() != Cover.OFF;
        float coverSide = 0f;
        if (hasCover) {
            // The text's height, measured on its own, is what Fit Cover matches.
            Content probe = Content.column();
            text(probe, font, lines, drawn, textWidth, 1f);
            probe.measure();
            coverSide = fitCover.isOn() ? probe.size().getHeight() : coverSize.getInt();
        }
        boolean coverRight = cover.get() == Cover.RIGHT
                || (cover.get() == Cover.AUTO && alignment() == Align.END);

        float side = coverSide;
        body.row(row -> {
            row.align(Align.CENTER);
            if (hasCover && !coverRight) {
                cover(row, drawn, side, alpha);
                row.space(COVER_GAP, 0f);
            }
            row.column(column -> {
                column.width(textWidth);
                text(column, font, lines, drawn, textWidth, alpha);
            });
            if (hasCover && coverRight) {
                row.space(COVER_GAP, 0f);
                cover(row, drawn, side, alpha);
            }
        });
    }

    @Override
    protected float opacity() {
        return opacity;
    }

    // ------------------------------------------------------------------ parts

    /** One line of text: what it says, and whether it is the title. */
    private record Line(String text, boolean title) {
    }

    private List<Line> lines(MediaTrack track) {
        List<Line> lines = new ArrayList<>(3);
        lines.add(new Line(track.getTitle(), true));
        if (showArtist.isOn() && !track.getArtist().isEmpty()) {
            lines.add(new Line(track.getArtist(), false));
        }
        // A podcast's album is its show, which is already in the artist's place.
        if (showAlbum.isOn() && !track.getAlbum().isEmpty() && !track.getAlbum().equals(track.getArtist())) {
            lines.add(new Line(track.getAlbum(), false));
        }
        return lines;
    }

    /** @return the width the text would take with nothing cut: its longest line, or the time row */
    private float natural(Font font, List<Line> lines, MediaTrack track) {
        float widest = 0f;
        for (Line line : lines) {
            widest = Math.max(widest, font.widthOf(line.text()));
        }
        if (showTime.isOn() && track.getDurationMillis() > 0L) {
            // The length twice, not the position: the position's width changes every
            // second, and the box would twitch with it.
            String length = time(track.getDurationMillis());
            widest = Math.max(widest, font.widthOf(length) * 2f + TIME_GAP);
        }
        return widest;
    }

    private void text(Content column, Font font, List<Line> lines, MediaTrack track, float w, float alpha) {
        column.align(alignment()).gap(tokens().gap());
        boolean dim = !track.isPlaying() && !isEditing();
        Color title = tokens().text(TextRole.TITLE).fade(dim ? 0.6f : 1f).fade(alpha);
        Color detail = tokens().text(TextRole.SECONDARY).fade(alpha);
        for (Line line : lines) {
            line(column, font, line.text(), w, line.title() ? title : detail);
        }

        boolean timed = track.getDurationMillis() > 0L;
        if (showBar.isOn() && timed) {
            column.space(0f, 1f);
            track(column, w, track.getProgress(), alpha);
        }
        if (showTime.isOn() && timed) {
            column.row(times -> {
                times.width(w);
                text(times, time(track.getCurrentPositionMillis()), detail);
                times.fill();
                text(times, time(track.getDurationMillis()), detail);
            });
        }
    }

    /** A line that fits as it is, or scrolls or is cut short to fit {@code w}. */
    private void line(Content column, Font font, String text, float w, Color color) {
        float textWidth = font.widthOf(text);
        if (textWidth <= w) {
            text(column, text, color);
            return;
        }
        if (longTitles.get() == LongTitles.CUT) {
            text(column, font.truncate(text, w), color);
            return;
        }
        long now = System.currentTimeMillis();
        TextPainter painter = painter();
        // The clip is the width only; vertically it has room for descenders and halos.
        column.custom(w, font.getHeight(), (x, y, cw, ch) -> Render.clipped(x, y - ch / 2f, cw, ch * 2f, () -> {
            float offset = scrollOffset(textWidth, now);
            painter.draw(text, x - offset, y, color);
            painter.draw(text, x - offset + textWidth + SCROLL_GAP, y, color);
        }));
    }

    /**
     * @return how far a scrolling line has moved: a rest at the start of each pass,
     *         then one full loop, so the line is readable from its first word every time
     */
    private float scrollOffset(float textWidth, long now) {
        float distance = textWidth + SCROLL_GAP;
        long travel = (long) (distance / SCROLL_SPEED * 1000f);
        long into = (now - scrollStartedAt) % (SCROLL_REST_MILLIS + travel);
        return into < SCROLL_REST_MILLIS ? 0f : (into - SCROLL_REST_MILLIS) / 1000f * SCROLL_SPEED;
    }

    private void cover(Content row, MediaTrack track, float size, float alpha) {
        Texture art = RemoteImages.texture(track.getArtworkUrl());
        float radius = Math.min(tokens().radius(), size / 6f);
        Color placeholder = tokens().text(TextRole.SECONDARY).withAlpha(40).fade(alpha);
        row.custom(size, size, (x, y, w, h) -> {
            if (art != null) {
                Render.roundTexture(art, x, y, w, h, radius, Color.WHITE.fade(alpha));
            } else {
                Render.roundRect(x, y, w, h, radius, placeholder);
            }
        });
    }

    /** m:ss, or h:mm:ss for a podcast that runs past the hour. */
    private static String time(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        return hours > 0L
                ? String.format("%d:%02d:%02d", hours, (seconds / 60L) % 60L, seconds % 60L)
                : String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }
}
