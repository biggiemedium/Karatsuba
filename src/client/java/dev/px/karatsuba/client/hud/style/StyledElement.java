package dev.px.karatsuba.client.hud.style;

import dev.px.core.Core;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.layout.Size;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.font.Font;
import dev.px.core.setting.Setting;
import dev.px.core.setting.Settings;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.adapter.game.GameHud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The parent of every HUD element: what makes them look and behave like one HUD.
 *
 * <p>An element describes only what it shows, in {@link #describe}. This class
 * does the rest, the same way for all of them (see {@code hud/PLAN.md}):
 *
 * <ul>
 *   <li><b>A panel behind everything.</b> Every element sits on the style's plate,
 *       with its padding. Not a choice: text on a plate reads over any scene, and
 *       one look for every element is what makes the HUD read as one HUD.</li>
 *   <li><b>Style.</b> The global {@link HudStyle}, or the element's own with
 *       Custom Style on. Resolved every frame.</li>
 *   <li><b>Alignment by anchor.</b> Anchored right, rows right-align and the box
 *       grows leftwards; anchored centre, they centre. Align overrides it.</li>
 *   <li><b>Size eased.</b> The box eases to its new size when what it shows
 *       changes, clipped only while moving. Values that change every tick go
 *       through {@link #fixed}, whose width never moves.</li>
 *   <li><b>Context.</b> Dims or hides while a game screen or F3 is open, fades
 *       when idle (with Fade When Idle on), and briefly highlights a change that
 *       matters. An element says what counts as either through {@link #watch}.</li>
 *   <li><b>Nothing to show, nothing drawn.</b> An element that describes nothing
 *       takes no space.</li>
 * </ul>
 *
 * <p>Every element gets one Style group in its settings: Align, Fade When Idle and
 * Custom Style with its overrides. The HUD section shows it after
 * the element's own.
 */
public abstract class StyledElement extends AbstractHudElement {

    public enum TextAlign { AUTO, LEFT, CENTER, RIGHT }

    private static final long RESIZE_MILLIS = 220L;
    private static final long FADE_MILLIS = 300L;
    private static final long CUE_MILLIS = 700L;

    /** How visible the HUD stays while a game screen is open, with In Screens on Dim. */
    private static final float DIMMED = 0.35f;

    /** How far a cue pulls text towards its highlight, at its peak. */
    private static final float CUE_STRENGTH = 0.75f;

    /** Between a label and its value. */
    protected static final float LABEL_GAP = 4f;

    private final EnumSetting<TextAlign> align = Settings.enumOf("Align", TextAlign.AUTO)
            .describe("Auto follows the side of the screen it is anchored to");
    private final BooleanSetting fadeWhenIdle = Settings.bool("Fade When Idle", fadesWhenIdleByDefault())
            .describe("Fade after nothing has changed for a while, and come back when something does. "
                    + "How long and how far is in the HUD Style");
    private final BooleanSetting customStyle = Settings.bool("Custom Style", false)
            .describe("Its own panel, colours and font instead of the HUD Style");
    private final StyleSettings own = new StyleSettings(customStyle::isOn);

    private final GroupSetting styleGroup = Settings.group("Style", false)
            .containing(join(new Setting<?>[]{align, fadeWhenIdle, customStyle}, own.plate(), own.text()));

    private final Animation width = Animation.of(0f, RESIZE_MILLIS, Easing.CUBIC_OUT);
    private final Animation height = Animation.of(0f, RESIZE_MILLIS, Easing.CUBIC_OUT);
    private final Animation visibility = Animation.of(1f, FADE_MILLIS, Easing.QUAD_OUT);
    private final Animation cue = Animation.of(0f, CUE_MILLIS, Easing.CUBIC_OUT);

    /** Whether there was something on screen last frame, so this one eases from it rather than snapping. */
    private boolean sized;

    // What watch() was last told, and when it last changed.
    private Object idleKey;
    private Object cueKey;
    private boolean watchedOnce;
    private long changedAt = System.currentTimeMillis();

    // This frame's, set before describe() for the helpers below.
    private Style style;
    private Align alignment = Align.START;

    protected StyledElement(String id, String displayName, HudLayout defaultLayout) {
        super(id, displayName, defaultLayout);
    }

    /**
     * Describes what the element shows, as rows in a column, using the helpers
     * below. Adding nothing draws nothing. Called every frame.
     */
    protected abstract void describe(Content body);

    @Override
    public final void content(Content content) {
        prepare(resolveAlignment());
        if (style.font() == null) {
            return;
        }

        Content body = Content.column();
        body.align(alignment).gap(style.gap());
        describe(body);
        float fade = opacity() * contextOpacity();
        body.opacity(fade);
        if (body.isEmpty()) {
            sized = false;
            return;
        }

        Size natural = body.measure();
        // Not while the layout editor is open: an element being scaled or dragged there
        // must sit exactly under the cursor, not ease after it.
        boolean ease = sized && HudStyle.get().animates() && !Core.hud().getEditor().isActive();
        float w = ease(width, natural.getWidth(), ease);
        float h = ease(height, natural.getHeight(), ease);
        sized = true;
        boolean moving = Math.abs(w - natural.getWidth()) > 0.5f || Math.abs(h - natural.getHeight()) > 0.5f;

        if (!platesItself()) {
            content.background(style.plate().fade(opacity() * contextOpacity()), style.radius())
                    .padding(style.padding());
        }
        content.align(alignment);
        Align inner = alignment;
        Content shown = body;
        content.column(frame -> {
            frame.width(w).height(h).align(inner);
            // Only while moving: settled, a clip at the edge shaves descenders.
            if (moving) {
                frame.clip();
            }
            frame.include(shown);
        });
    }

    /**
     * @return whether the element draws a plate per item itself (Notifications, a
     *         card per toast) rather than having one drawn behind everything
     */
    protected boolean platesItself() {
        return false;
    }

    /** @return Fade When Idle's default: off, unless the element is mostly worth glancing at once */
    protected boolean fadesWhenIdleByDefault() {
        return false;
    }

    /**
     * Tells the HUD what this element is showing, once a frame from {@link #describe}.
     *
     * @param idle what counts as a change for Fade When Idle: when it has not
     *             changed for the HUD Style's Idle After, the element fades. Null
     *             never idles
     * @param cue  what counts as a change worth pointing out: when it changes, the
     *             element's text briefly lights up. Null never cues. Keep it coarse
     *             (a ping's health, not its value), or it cues every second
     */
    protected final void watch(Object idle, Object cue) {
        long now = System.currentTimeMillis();
        if (!watchedOnce || !Objects.equals(idle, idleKey)) {
            changedAt = now;
        }
        if (watchedOnce && !Objects.equals(cue, cueKey) && cue != null && HudStyle.get().changeCues()
                && HudStyle.get().animates()) {
            this.cue.snapTo(1f).target(0f);
        }
        idleKey = idle;
        cueKey = cue;
        watchedOnce = true;
    }

    /** Draws a string the way {@link #text} adds one, for elements that draw inside a custom part. */
    @FunctionalInterface
    protected interface TextPainter {
        void draw(String value, float x, float y, Color color);
    }

    /**
     * @return a painter fixed to this frame's font. Taken during {@link #describe},
     *         used when the part draws.
     */
    protected final TextPainter painter() {
        Font font = style.font();
        return (value, x, y, color) -> Render.text(font, value, x, y, color);
    }

    /**
     * @return how opaque the element is this frame, 0 to 1, for one that fades itself
     *         in and out: the plate fades with it. Read after {@link #describe}.
     */
    protected float opacity() {
        return 1f;
    }

    /** @return the Style group, for the HUD section to show after the element's own settings */
    public final GroupSetting styleGroup() {
        return styleGroup;
    }

    // --------------------------------------------------------------- helpers

    /** @return this frame's style */
    protected final Style style() {
        return style;
    }

    /** @return how rows line up this frame: start, centre or end */
    protected final Align alignment() {
        return alignment;
    }

    /** Text in the style's font, lit by a cue. */
    protected final void text(Content into, String value, Color color) {
        into.text(style.font(), value, cued(color));
    }

    /** A label in the style's label colour: "Ping". */
    protected final void label(Content into, String value) {
        text(into, value, style.label());
    }

    /**
     * A value whose width never changes: it takes the width of {@code widest} (or of
     * itself, if longer) and sits in that space as the element is aligned. For
     * anything that ticks over every second, so the box does not twitch with it.
     */
    protected final void fixed(Content into, String value, String widest, Color color) {
        Font font = style.font();
        float textWidth = font.widthOf(value);
        float slot = Math.max(textWidth, font.widthOf(widest));
        float offset = switch (alignment) {
            case END -> slot - textWidth;
            case CENTER -> (slot - textWidth) / 2f;
            default -> 0f;
        };
        Color shown = cued(color);
        into.custom(slot, font.getHeight(), (x, y, w, h) -> Render.text(font, value, x + offset, y, shown));
    }

    /** One row of label and fixed-width value: "Ping 42ms". */
    protected final void stat(Content into, String label, String value, String widest, Color valueColor) {
        into.row(row -> {
            row.align(Align.CENTER);
            label(row, label);
            row.space(LABEL_GAP, 0f);
            fixed(row, value, widest, valueColor);
        });
    }

    /**
     * A signal-strength meter: {@code max} rising bars, {@code level} of them lit.
     * Shape carries the state as well as colour, so it reads without colour vision
     * (never red against green alone).
     */
    protected final void bars(Content into, int level, int max, Color lit) {
        Font font = style.font();
        float barWidth = 2f;
        float spacing = 1f;
        float height = font.getHeight() * 0.7f;
        Color on = cued(lit);
        Color unlit = style.muted().fade(0.35f);
        into.custom(max * barWidth + (max - 1) * spacing, font.getHeight(), (x, y, w, h) -> {
            float bottom = y + (h + height) / 2f;
            for (int i = 0; i < max; i++) {
                float barHeight = height * (i + 1) / max;
                float bx = x + i * (barWidth + spacing);
                float by = bottom - barHeight;
                Render.rect(bx, by, barWidth, barHeight, i < level ? on : unlit);
            }
        });
    }

    // ------------------------------------------------------------- internals

    /** Sets this frame's style and alignment. */
    private void prepare(Align alignment) {
        this.style = customStyle.isOn() ? own.resolve() : HudStyle.get().resolve();
        this.alignment = alignment;
    }

    /** @return how visible the element should be for what the player is doing, eased: screens, F3, idling */
    private float contextOpacity() {
        float target = 1f;
        if (!isEditing()) {
            HudStyle global = HudStyle.get();
            GameHud.ScreenKind screen = GameHud.screen();
            if (screen == GameHud.ScreenKind.CHAT || screen == GameHud.ScreenKind.OTHER) {
                target = switch (global.inScreens()) {
                    case SHOW -> 1f;
                    case DIM -> DIMMED;
                    case HIDE -> 0f;
                };
            }
            if (global.withDebug() == HudStyle.WithDebug.HIDE && GameHud.debugShown()) {
                target = 0f;
            }
            if (fadeWhenIdle.isOn() && watchedOnce && System.currentTimeMillis() - changedAt > global.idleAfterMillis()) {
                target = Math.min(target, global.idleOpacity());
            }
        }
        visibility.duration(HudStyle.get().animates() ? FADE_MILLIS : 0L).target(target);
        return visibility.get();
    }

    /** @return {@code color} lit by the current cue: pulled towards white for labels, towards the label colour for the rest */
    private Color cued(Color color) {
        float amount = cue.get();
        if (amount <= 0.01f) {
            return color;
        }
        Color towards = color.equals(style.label()) ? Color.WHITE : style.label();
        return color.lerp(towards.withAlpha(color.getAlpha()), amount * CUE_STRENGTH);
    }

    private Align resolveAlignment() {
        return switch (align.get()) {
            case LEFT -> Align.START;
            case CENTER -> Align.CENTER;
            case RIGHT -> Align.END;
            case AUTO -> {
                HudLayout layout = Core.hud().layoutOf(this);
                Anchor anchor = layout == null ? defaultLayout().getAnchor() : layout.getAnchor();
                yield anchor.isRight() ? Align.END : anchor.isLeft() ? Align.START : Align.CENTER;
            }
        };
    }

    private static float ease(Animation animation, float target, boolean ease) {
        if (ease) {
            animation.target(target);
        } else {
            animation.snapTo(target);
        }
        return animation.get();
    }

    private static Setting<?>[] join(Setting<?>[]... parts) {
        List<Setting<?>> all = new ArrayList<>();
        for (Setting<?>[] part : parts) {
            all.addAll(List.of(part));
        }
        return all.toArray(new Setting<?>[0]);
    }
}
