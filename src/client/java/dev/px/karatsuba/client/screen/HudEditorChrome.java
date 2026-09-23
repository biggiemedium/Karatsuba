package dev.px.karatsuba.client.screen;

import dev.px.core.Core;
import dev.px.core.gui.GuiStyle;
import dev.px.core.hud.HudEditor;
import dev.px.core.hud.HudEditorView;
import dev.px.core.hud.Placement;
import dev.px.core.hud.SnapGuide;
import dev.px.core.layout.Bounds;
import dev.px.core.math.MathUtil;
import dev.px.core.render.Color;
import dev.px.core.render.Render;

/**
 * What the HUD editor looks like.
 *
 * <p>Core computes the editing arithmetic &mdash; hit testing, drag offsets,
 * uniform resize about a fixed corner, snapping, handle placement &mdash; and
 * stops there. {@link HudEditor} draws nothing and listens to nothing: it hands
 * out a {@link HudEditorView} each frame and takes its input through plain method
 * calls. Everything above the arithmetic is a client's own, which is why this is
 * here and not in {@code core}, and not in {@code adapter} either.
 *
 * <p>Drawing only. It reads a view and puts pixels on the screen, and it changes
 * nothing: no selection, no layout, no editor state. {@link HudOverlay} decides
 * when it is called and what the keys do.
 *
 * <h2>The rules, and why</h2>
 * The thing being edited is the HUD, so the editor has to stay out of its way.
 * Every choice below follows from that:
 *
 * <ul>
 *   <li><b>One accent.</b> The theme's colour marks the selection, the grab
 *       handle and the snap guides. Locked is that colour dimmed, hover is a
 *       neutral whisper. Nothing introduces a second hue to decode.</li>
 *   <li><b>One weight.</b> Every outline is a single hairline. Difference is
 *       carried by colour, which reads instantly, rather than by thickness,
 *       which does not.</li>
 *   <li><b>One handle.</b> Scaling is uniform, so four corner grips were four
 *       ways to do one thing, on elements often smaller than the grips.</li>
 *   <li><b>No standing text.</b> The only label is the scale percentage, and
 *       only while the scale is moving. Everything else a label used to say is
 *       already visible in the element itself.</li>
 *   <li><b>Room to breathe.</b> The handle and the readout sit clear of the
 *       element rather than against it, so the selection reads as a frame around
 *       the content instead of a box welded to it.</li>
 * </ul>
 */
final class HudEditorChrome {

    private static final float OUTLINE = 1f;

    /**
     * The one grab point, out of the four the view publishes.
     *
     * <p>Four corner handles on an element the size of a watermark is most of the
     * element covered in chrome. Scaling is uniform here &mdash; an element sizes
     * itself from its content, so only the scale factor is free &mdash; which
     * means the other three corners were three more ways to do the one thing.
     */
    private static final HudEditor.Handle GRAB_HANDLE = HudEditor.Handle.BOTTOM_RIGHT;

    private static final float READOUT_GAP = 10f;
    private static final float READOUT_PADDING_X = 7f;
    private static final float READOUT_PADDING_Y = 4f;
    private static final float READOUT_RADIUS = 3f;

    /** How long the scale readout stays after the last scroll, and its fade tail. */
    private static final long READOUT_LINGER_MILLIS = 700L;
    private static final long READOUT_FADE_MILLIS = 250L;

    private static final Color BACKDROP = Color.of(8, 9, 12, 140);
    private static final Color SURFACE = Color.of(18, 20, 24, 230);
    private static final Color BORDER = Color.of(255, 255, 255, 30);
    private static final Color TEXT = Color.of(230, 232, 238, 240);
    private static final Color HOVER = Color.of(255, 255, 255, 55);

    /** Keeps a small accent square legible over bright HUD content. */
    private static final Color HANDLE_EDGE = Color.of(0, 0, 0, 130);

    /** A hidden element still has to be visible enough to be selected and switched back on. */
    private static final float HIDDEN_ALPHA = 0.3f;

    private HudEditorChrome() {
    }

    /**
     * Draws one frame of edit mode: the backdrop, the elements, then the chrome.
     *
     * @param lastScaleAt when the scale last changed by scroll, so the readout can
     *                    linger and fade; zero if it never has
     */
    static void render(HudEditorView view, float screenWidth, float screenHeight, long lastScaleAt) {
        // Dims the game behind the HUD so the editor reads as a separate mode.
        Render.rect(0f, 0f, screenWidth, screenHeight, BACKDROP);

        renderElements(view);
        renderOutlines(view);
        renderHandle(view);
        renderReadout(view, screenWidth, screenHeight, lastScaleAt);
        renderGuides(view, screenWidth, screenHeight);
    }

    /**
     * The elements themselves, with hidden ones faint rather than absent.
     *
     * <p>Core includes hidden elements in the view while editing precisely so they
     * can be shown somehow and selected to be brought back. How faint is this
     * client's decision.
     */
    private static void renderElements(HudEditorView view) {
        for (Placement placement : view.getPlacements()) {
            boolean hidden = placement.getLayout().isHidden();
            if (hidden) {
                Render.pushAlpha(HIDDEN_ALPHA);
            }
            Core.hud().draw(placement);
            if (hidden) {
                Render.popAlpha();
            }
        }
    }

    /**
     * Three states, one accent, one hairline weight.
     *
     * <p>Hover is a neutral whisper, selected is the accent, and locked is that
     * same accent dimmed &mdash; still obviously the selection, obviously not
     * editable. A second hue for locked would be a second accent to learn, and the
     * weight of an outline is not something anyone reads as meaning.
     */
    private static void renderOutlines(HudEditorView view) {
        for (Placement placement : view.getPlacements()) {
            boolean selected = view.isSelected(placement);
            if (!selected && !view.isHovered(placement)) {
                continue;
            }
            Color outline = !selected ? HOVER
                    : placement.getLayout().isLocked() ? GuiStyle.accent().fade(0.4f)
                    : GuiStyle.accent();

            // The shape strokes itself, so an element made of separate rows traces
            // those rows and a circular one traces its circle, without this file
            // knowing either exists.
            placement.shape().stroke(OUTLINE, outline);
        }
    }

    /**
     * A small accent square, ringed so it survives a white HUD behind it.
     *
     * <p>The rectangle comes from the view rather than being worked out here,
     * which is what keeps the handle drawn and the handle grabbed in agreement
     * &mdash; including when Core has flipped it inside an element anchored into a
     * screen corner, where an outside handle would be off-screen and permanently
     * unclickable.
     */
    private static void renderHandle(HudEditorView view) {
        // Empty whenever nothing is selected or the selection is locked, which is
        // exactly when no handle is grabbable.
        Bounds box = view.getHandles().get(GRAB_HANDLE);
        if (box == null) {
            return;
        }
        Render.rect(box.getX(), box.getY(), box.getWidth(), box.getHeight(), GuiStyle.accent());
        Render.rectOutline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), OUTLINE, HANDLE_EDGE);
    }

    /**
     * The only text in the editor, and only while the scale is moving.
     *
     * <p>A standing label would say the element's name, its anchor and its state,
     * none of which is news: the name is the thing you are looking at, the anchor
     * is where it is, and locked and hidden already show as a dimmed outline and a
     * faded element. Permanent labels on every selection are what make an editor
     * feel busy.
     *
     * <p>Scale is the exception, because it is the one property with no visual form
     * of its own &mdash; a thing gets bigger and you cannot tell whether that is
     * 1.2 or 1.25. So it appears while it is changing and leaves when it stops.
     */
    private static void renderReadout(HudEditorView view, float screenWidth, float screenHeight,
                                      long lastScaleAt) {
        Placement selected = view.getSelected();
        if (selected == null || selected.getLayout().isLocked()) {
            return;
        }
        float opacity = readoutOpacity(view, lastScaleAt);
        if (opacity <= 0f) {
            return;
        }

        String text = Math.round(selected.getLayout().getScale() * 100f) + "%";
        float width = Render.textWidth(text) + READOUT_PADDING_X * 2f;
        float height = Render.textHeight() + READOUT_PADDING_Y * 2f;

        Bounds bounds = selected.shape().bounds();
        float x = MathUtil.clamp(bounds.getCenterX() - width / 2f,
                0f, Math.max(0f, screenWidth - width));

        // Below the element, flipping above when there is no room. The gap is
        // deliberately wider than the handle needs: the readout is a separate
        // object from the selection and should not look welded to it.
        float y = bounds.getBottom() + READOUT_GAP;
        if (y + height > screenHeight) {
            y = bounds.getY() - READOUT_GAP - height;
        }

        Render.roundRect(x, y, width, height, READOUT_RADIUS, SURFACE.fade(opacity));
        Render.roundRectOutline(x, y, width, height, READOUT_RADIUS, OUTLINE, BORDER.fade(opacity));
        Render.text(text, x + READOUT_PADDING_X, y + READOUT_PADDING_Y, TEXT.fade(opacity));
    }

    /**
     * @return the readout's opacity, or zero when it should not be drawn
     *
     * <p>Full while a handle is held, then a linger and a short fade after the last
     * scroll tick. A drag has a state to key off; a scroll tick does not, and a
     * value that vanished the instant the wheel stopped would be unreadable.
     */
    private static float readoutOpacity(HudEditorView view, long lastScaleAt) {
        if (view.isResizing()) {
            return 1f;
        }
        if (lastScaleAt == 0L) {
            return 0f;
        }
        long elapsed = System.currentTimeMillis() - lastScaleAt;
        if (elapsed >= READOUT_LINGER_MILLIS) {
            return 0f;
        }
        long solidUntil = READOUT_LINGER_MILLIS - READOUT_FADE_MILLIS;
        return elapsed <= solidUntil
                ? 1f
                : 1f - (elapsed - solidUntil) / (float) READOUT_FADE_MILLIS;
    }

    /** Full-screen lines wherever Core reported a snap this frame. */
    private static void renderGuides(HudEditorView view, float screenWidth, float screenHeight) {
        Color color = GuiStyle.accent().withAlpha(150);

        for (SnapGuide guide : view.getGuides()) {
            if (guide.isVertical()) {
                Render.line(guide.getPosition(), 0f, guide.getPosition(), screenHeight, OUTLINE, color);
            } else {
                Render.line(0f, guide.getPosition(), screenWidth, guide.getPosition(), OUTLINE, color);
            }
        }
    }
}
