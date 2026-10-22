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
import dev.px.karatsuba.client.hud.preset.ColorRole;
import dev.px.karatsuba.client.hud.preset.HudIcon;
import dev.px.karatsuba.client.hud.preset.HudPlate;
import dev.px.karatsuba.client.hud.preset.HudPreset;
import dev.px.karatsuba.client.hud.preset.HudPresets;
import dev.px.karatsuba.client.hud.preset.HudTokens;
import dev.px.karatsuba.client.hud.preset.TextRole;
import dev.px.karatsuba.client.setting.SettingLabels;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The parent of every HUD element: what makes them look and behave like one HUD.
 *
 * <p>An element describes only what it shows, in {@link #describe}, as roles
 * ({@code hud/DESIGN.md} §3): text in a {@link TextRole}, values, meters, tracks,
 * icons. The active {@link HudPreset} draws every role; this class does the rest,
 * the same way for all of them:
 *
 * <ul>
 *   <li><b>A plate behind everything,</b> as the preset draws it, with its
 *       padding.</li>
 *   <li><b>Tokens.</b> The preset's fonts and colours for each role, resolved
 *       every frame ({@link #tokens()}). In Legacy they come from the HUD Style,
 *       or the element's own with Custom Style on.</li>
 *   <li><b>Alignment by anchor.</b> Anchored right, rows right-align and the box
 *       grows leftwards; anchored centre, they centre. Align overrides it.</li>
 *   <li><b>Size eased.</b> The box eases to its new size when what it shows
 *       changes, clipped only while moving. Values that change every tick go
 *       through {@link #fixed}, whose width never moves.</li>
 *   <li><b>Context.</b> Dims or hides while a game screen or F3 is open, and
 *       briefly highlights a change that matters, which an element names through
 *       {@link #watch}. Nothing fades for being idle ({@code hud/DESIGN.md} §2.8b).</li>
 *   <li><b>Nothing to show, nothing drawn.</b> An element that describes nothing
 *       takes no space.</li>
 * </ul>
 *
 * <p>Every element gets one Style group in its settings (shown as Display): Align,
 * and Custom Style with its overrides. The HUD section shows it after
 * the element's own.
 */
public abstract class StyledElement extends AbstractHudElement {

    public enum TextAlign { AUTO, LEFT, CENTER, RIGHT }

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
    private final BooleanSetting customStyle = Settings.bool("Custom Style", false)
            .describe("Its own panel, colours and font instead of the HUD Style");
    // Legacy's per-element style: hidden while another preset draws (§1.8).
    private final StyleSettings own = new StyleSettings(() -> customStyle.isOn() && legacy());

    private final GroupSetting styleGroup = Settings.group("Style", false)
            .containing(join(new Setting<?>[]{align, customStyle}, own.plate(), own.text()));

    // The plate's size, along the preset's curve: Legacy's ease, Apple's spring (§4.12).
    private final Tween width = new Tween(0f);
    private final Tween height = new Tween(0f);
    private final Animation visibility = Animation.of(1f, FADE_MILLIS, Easing.QUAD_OUT);
    private final Animation cue = Animation.of(0f, CUE_MILLIS, Easing.CUBIC_OUT);

    /** Whether there was something on screen last frame, so this one eases from it rather than snapping. */
    private boolean sized;

    // What watch() was last told.
    private Object cueKey;
    private boolean watchedOnce;
    /** When the last cue began, for Motion: Off's steady highlight. */
    private long steadyCueAt;
    /** Last frame's fade of the whole element, for the plates behind it: glass fades with it. */
    private float lastFade = 1f;

    // This frame's, set before describe() for the helpers below.
    private HudPreset preset;
    private HudTokens tokens;
    private Align alignment = Align.START;

    protected StyledElement(String id, String displayName, HudLayout defaultLayout) {
        super(id, displayName, defaultLayout);
        customStyle.visibleWhen(StyledElement::legacy);
        align.visibleWhen(() -> HudPresets.active().alignsByAnchor() || keepsAnchorAlignment());
        // Saved as "Style", shown as "Display" (§1.8): renaming it would lose what players set.
        SettingLabels.relabel(styleGroup, "Display");
    }

    /**
     * Describes what the element shows, as rows in a column, using the helpers
     * below. Adding nothing draws nothing. Called every frame.
     */
    protected abstract void describe(Content body);

    @Override
    public final void content(Content content) {
        prepare();
        if (tokens.font(TextRole.PRIMARY) == null) {
            return;
        }

        Content body = Content.column();
        body.align(alignment).gap(tokens.gap());
        describe(body);
        float fade = opacity() * contextOpacity();
        lastFade = fade;
        body.opacity(fade);
        if (body.isEmpty()) {
            sized = false;
            return;
        }

        Size natural = body.measure();
        // Not while the layout editor is open: an element being scaled or dragged there
        // must sit exactly under the cursor, not ease after it.
        // Only at Motion: Full; Reduced keeps fades but no movement or scaling (§2.10).
        boolean ease = sized && HudStyle.get().motion() == HudStyle.Motion.FULL && !Core.hud().getEditor().isActive();
        float w = resize(width, natural.getWidth(), ease);
        float h = resize(height, natural.getHeight(), ease);
        sized = true;
        boolean moving = Math.abs(w - natural.getWidth()) > 0.5f || Math.abs(h - natural.getHeight()) > 0.5f;

        if (!platesItself()) {
            preset.plate(content, tokens, fade);
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

    /** @return whether the element draws as Legacy does whatever the preset: Debug, a dev readout (§3.6) */
    protected boolean exemptFromPresets() {
        return false;
    }

    /** @return whether the element keeps anchor alignment in a preset that left-aligns (ArrayList, §4.16) */
    protected boolean keepsAnchorAlignment() {
        return false;
    }

    /** @return the preset that drew this element last frame */
    public final HudPreset drawingPreset() {
        return preset == null ? HudPresets.active() : preset;
    }

    /**
     * @return the plates behind this element, in screen space, for a preset that draws
     *         them behind (glass): one across the whole element, at {@code x, y} and
     *         {@code naturalW} by {@code naturalH} scaled by {@code scale}. An element that
     *         plates each item itself (a toast) gives one per item
     */
    public List<HudPlate> plates(float x, float y, float scale, float naturalW, float naturalH) {
        if (platesItself() || tokens == null || lastFade <= 0f) {
            return List.of();
        }
        return List.of(new HudPlate(getId(), x, y, naturalW * scale, naturalH * scale,
                preset.capsule(tokens, naturalH), lastFade, cueAmount()));
    }

    /** @return how faded the element was last frame, 0 to 1: its plates form and melt with it */
    protected final float lastFade() {
        return lastFade;
    }

    /** @return how strongly a change cue lights the element now, 0 to 1: eased, or steady at Motion: Off */
    protected final float cueAmount() {
        return HudStyle.get().motion() == HudStyle.Motion.OFF
                ? (System.currentTimeMillis() - steadyCueAt < CUE_MILLIS ? 1f : 0f)
                : cue.get();
    }

    /**
     * Tells the HUD what change in this element is worth pointing out, once a frame
     * from {@link #describe}: when {@code cue} changes, the element's text briefly
     * lights up. Null never cues. Keep it coarse (a ping's health, not its value),
     * or it cues every second.
     */
    protected final void watch(Object cue) {
        if (watchedOnce && !Objects.equals(cue, cueKey) && cue != null && HudStyle.get().changeCues()) {
            // A fade is still allowed at Reduced; at Off the cue holds steady, then goes (§2.10).
            this.cue.snapTo(1f).target(0f);
            steadyCueAt = System.currentTimeMillis();
        }
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
        return painter(TextRole.PRIMARY);
    }

    /** A painter in {@code role}'s font this frame, for text in a custom part. */
    protected final TextPainter painter(TextRole role) {
        Font font = tokens.font(role);
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

    /** @return this frame's tokens from the active preset: each role's font and colour, the plate's measurements */
    protected final HudTokens tokens() {
        return tokens;
    }

    /** @return the preset drawing this frame, for an element that draws a plate in a custom part */
    protected final HudPreset preset() {
        return preset;
    }

    /**
     * @return the element's own style settings while Custom Style is on, else null:
     *         for the Legacy preset, which reads them in place of the HUD Style
     */
    public final StyleSettings customStyle() {
        return customStyle.isOn() ? own : null;
    }

    /** @return how rows line up this frame: start, centre or end */
    protected final Align alignment() {
        return alignment;
    }

    /** Text in {@code role}'s font and colour, lit by a cue. */
    protected final void text(Content into, String value, TextRole role) {
        into.text(tokens.font(role), value, cued(tokens.text(role)));
    }

    /**
     * Primary text in a colour the preset gave (a health colour, a list item), lit by
     * a cue. Never a colour the element picked itself ({@code hud/DESIGN.md} §3.4).
     */
    protected final void text(Content into, String value, Color color) {
        into.text(tokens.font(TextRole.PRIMARY), value, cued(color));
    }

    /** What a value is: "Ping". The label role; how it looks is the preset's (Legacy: the accent). */
    protected final void label(Content into, String value) {
        text(into, value, TextRole.LABEL);
    }

    /**
     * @return the good, fair or bad colour role for {@code level} (3 good, 2 fair, 1
     *         bad). Never the only cue: show the level by shape or words too (§2.3)
     */
    protected final Color health(int level) {
        return tokens.color(level >= 3 ? ColorRole.GOOD : level == 2 ? ColorRole.FAIR : ColorRole.BAD);
    }

    /** A {@link #fixed fixed-width} value in {@code role}'s colour. */
    protected final void fixed(Content into, String value, String widest, TextRole role) {
        fixed(into, value, widest, tokens.text(role));
    }

    /**
     * A value whose width never changes: it takes the width of {@code widest} (or of
     * itself, if longer) and sits in that space as the element is aligned. For
     * anything that ticks over every second, so the box does not twitch with it.
     */
    protected final void fixed(Content into, String value, String widest, Color color) {
        Font font = tokens.font(TextRole.VALUE);
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
        preset.meter(into, tokens, level, max, cued(lit));
    }

    /** A thick rounded progress bar, {@code progress} 0 to 1, {@code width} wide, faded by {@code alpha}: the track role. */
    protected final void track(Content into, float width, float progress, float alpha) {
        preset.track(into, tokens, width, progress, alpha);
    }

    /** An icon the size of a line of text, in {@code role}'s colour. */
    protected final void icon(Content into, HudIcon icon, TextRole role) {
        preset.icon(into, tokens, icon, cued(tokens.text(role)));
    }

    /** An icon in a well of its own, as the preset draws one (Legacy: the bare icon). */
    protected final void iconWell(Content into, HudIcon icon, TextRole role) {
        preset.iconWell(into, tokens, icon, cued(tokens.text(role)));
    }

    /** Whether something is switched on, as the preset's small indicator. */
    protected final void toggleOn(Content into, boolean on) {
        preset.toggleOn(into, tokens, on);
    }

    /** A title over a subtitle ({@code subtitle} empty for none), lined up as the element is. */
    protected final void titleBlock(Content into, String title, String subtitle) {
        Color titleColour = cued(tokens.text(TextRole.TITLE));
        Color subtitleColour = cued(tokens.text(TextRole.SUBTITLE));
        Align inner = alignment;
        into.column(block -> {
            block.align(inner).gap(tokens.gap());
            preset.titleBlock(block, tokens, title, titleColour, subtitle, subtitleColour);
        });
    }

    // ------------------------------------------------------------- internals

    /** Sets this frame's preset, tokens and alignment. */
    private void prepare() {
        this.preset = exemptFromPresets() ? HudPresets.LEGACY : HudPresets.active();
        this.tokens = preset.tokens(this);
        this.alignment = resolveAlignment();
    }

    /** @return how visible the element should be for what the player is doing, eased: screens and F3 */
    private float contextOpacity() {
        float target = 1f;
        if (!isEditing()) {
            HudStyle global = HudStyle.get();
            if (inScreen()) {
                target = switch (global.inScreens()) {
                    case SHOW -> 1f;
                    case DIM -> DIMMED;
                    case HIDE -> 0f;
                };
            }
            if (global.hideWithF3() && GameHud.debugShown()) {
                target = 0f;
            }
        }
        visibility.duration(HudStyle.get().motion() == HudStyle.Motion.OFF ? 0L : FADE_MILLIS).target(target);
        return visibility.get();
    }

    /** @return whether chat or another game screen is open; the client's own GUI does not count */
    private static boolean inScreen() {
        GameHud.ScreenKind screen = GameHud.screen();
        return screen == GameHud.ScreenKind.CHAT || screen == GameHud.ScreenKind.OTHER;
    }

    /** @return {@code color} lit by the current cue: pulled towards white for labels, towards the label colour for the rest */
    private Color cued(Color color) {
        // A preset with a rim to brighten cues there instead (Apple, §4.12).
        float amount = preset.cuesText(tokens) ? cueAmount() : 0f;
        if (amount <= 0.01f) {
            return color;
        }
        Color label = tokens.text(TextRole.LABEL);
        Color towards = color.equals(label) ? Color.WHITE : label;
        return color.lerp(towards.withAlpha(color.getAlpha()), amount * CUE_STRENGTH);
    }

    private Align resolveAlignment() {
        // Apple left-aligns inside a plate whatever the anchor; ArrayList keeps its own (§4.16).
        if (!preset.alignsByAnchor() && !keepsAnchorAlignment()) {
            return Align.START;
        }
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

    /** @return whether Legacy is drawing, the one preset with per-element styling */
    private static boolean legacy() {
        return HudPresets.active() == HudPresets.LEGACY;
    }

    /** Moves a plate dimension to {@code target}, along the preset's curve when easing, at once otherwise. */
    private float resize(Tween tween, float target, boolean ease) {
        if (ease) {
            tween.target(target, preset.resizeMillis(), preset.resizeCurve());
        } else {
            tween.snapTo(target);
        }
        return tween.get();
    }

    private static Setting<?>[] join(Setting<?>[]... parts) {
        List<Setting<?>> all = new ArrayList<>();
        for (Setting<?>[] part : parts) {
            all.addAll(List.of(part));
        }
        return all.toArray(new Setting<?>[0]);
    }
}
