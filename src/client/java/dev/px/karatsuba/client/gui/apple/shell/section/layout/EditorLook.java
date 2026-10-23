package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import dev.px.core.Core;
import dev.px.core.hud.HudEditor;
import dev.px.core.hud.HudEditorView;
import dev.px.core.hud.Placement;
import dev.px.karatsuba.client.hud.preset.glass.HudGlass;
import dev.px.karatsuba.client.hud.preset.PlateShape;
import dev.px.core.hud.SnapGuide;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * What the layout editor draws over the game: the elements, their outlines, the
 * selection's grip, the scale while it changes, and the snap guides.
 *
 * <p>Three states for an element, one accent ({@code gui/apple/DESIGN.md} §10.2b).
 * Every element is ringed in white in its plates' own shapes, which is what makes
 * edit mode look like a mode; the one under the cursor more brightly; the selected
 * one in the accent with a glow, dimmed when locked. Hidden elements are left out of
 * the view unless the HUD's Dim Hidden in Editor is on; then they are drawn faint,
 * so they can still be found and shown again.
 */
final class EditorLook {

    private static final float LINE_WEIGHT = 1f;

    /** The one grab point: scaling is uniform, so one corner is all it needs. */
    private static final HudEditor.Handle GRAB = HudEditor.Handle.BOTTOM_RIGHT;

    private static final float HIDDEN_ALPHA = 0.3f;

    private EditorLook() {
    }

    /**
     * @param alpha        0 to 1: the editor fading in as the window steps aside; glass forms with it instead
     * @param badgeHovered the element whose badge is under the cursor, or null
     */
    static void draw(HudEditorView view, float width, float height, float readout, float alpha,
                     Placement badgeHovered) {
        // A glass preset's plates, behind the elements, before the fade: a pass refuses inside
        // any alpha push, which would leave only the solid stand-in. The glass forms with it.
        HudGlass.behind(view.getPlacements(), alpha);
        if (alpha >= 1f) {
            drawOver(view, width, height, readout, badgeHovered);
            return;
        }
        Render.pushAlpha(alpha);
        try {
            drawOver(view, width, height, readout, badgeHovered);
        } finally {
            Render.popAlpha();
        }
    }

    /** The elements and the editor's marks over them, inside whatever fade there is. */
    private static void drawOver(HudEditorView view, float width, float height, float readout,
                                 Placement badgeHovered) {
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
        for (Placement placement : view.getPlacements()) {
            outline(view, placement);
        }
        // Over every outline, so an element's badge is never under its neighbour's.
        for (Placement placement : view.getPlacements()) {
            badge(placement, placement == badgeHovered);
        }
        grip(view);
        readout(view, width, height, readout);
        guides(view, width, height);
    }

    /**
     * Every element ringed in white just outside each of its plates, in the plate's own
     * shape, brighter under the cursor; the selection in the accent instead, with a
     * glow beyond it (fainter when locked).
     */
    private static void outline(HudEditorView view, Placement placement) {
        boolean selected = view.isSelected(placement);
        Color ring;
        float weight;
        if (selected) {
            ring = placement.getLayout().isLocked() ? faded(accent(), EDIT_LOCKED_FADE) : accent();
            weight = EDIT_SELECT_WEIGHT;
        } else {
            ring = view.isHovered(placement) ? EDIT_OUTLINE_HOVER : EDIT_OUTLINE;
            weight = EDIT_OUTLINE_WEIGHT;
        }
        for (PlateShape plate : Outlines.of(placement)) {
            ring(plate, EDIT_OUTLINE_GAP, weight, ring);
            if (selected) {
                // The glow: rings beyond the accent one, a unit apart, fading out.
                for (int i = 1; i <= EDIT_GLOW_RINGS; i++) {
                    float fade = EDIT_GLOW_ALPHA * (1f - (i - 1f) / EDIT_GLOW_RINGS);
                    ring(plate, EDIT_OUTLINE_GAP + weight + i - 1f, 1f, faded(ring, fade));
                }
            }
        }
    }

    /** A ring {@code weight} wide, {@code gap} outside {@code plate}, its corners concentric with the plate's. */
    private static void ring(PlateShape plate, float gap, float weight, Color colour) {
        float out = gap + weight;
        Render.roundRectOutline(plate.x() - out, plate.y() - out, plate.width() + out * 2f, plate.height() + out * 2f,
                plate.radius() + out, weight, colour);
    }

    /**
     * The hide or show badge at an element's corner ({@code gui/apple/DESIGN.md}
     * §10.2b): a grey circle with a white − on a shown element, + on a hidden one.
     * Solid, never glass: it sits on an element's glass, and there is no glass on glass.
     */
    private static void badge(Placement placement, boolean hovered) {
        Bounds b = Badges.of(placement);
        float cx = b.getCenterX();
        float cy = b.getCenterY();
        float r = b.getWidth() / 2f;
        Render.shadow(b.getX(), b.getY() + 0.5f, b.getWidth(), b.getHeight(), r, BADGE_SHADOW);
        Render.circle(cx, cy, r, hovered ? BADGE_HOVER : BADGE);
        float half = BADGE_GLYPH / 2f;
        Render.line(cx - half, cy, cx + half, cy, BADGE_STROKE, BADGE_INK);
        if (placement.getLayout().isHidden()) {
            Render.line(cx, cy - half, cx, cy + half, BADGE_STROKE, BADGE_INK);
        }
    }

    /**
     * The scale grip: a small white dot ringed in the accent with a soft shadow, centred
     * on the handle Core grabs, so the dot is the target. The only handle there is
     * (LayoutSection enables just this corner).
     */
    private static void grip(HudEditorView view) {
        Bounds box = view.getHandles().get(GRAB);
        if (box == null) {
            return;
        }
        float r = GRIP_SIZE / 2f;
        float cx = box.getCenterX();
        float cy = box.getCenterY();
        Render.shadow(cx - r, cy - r + 0.5f, GRIP_SIZE, GRIP_SIZE, r, GRIP_SHADOW);
        Render.circle(cx, cy, r, GRIP_FILL);
        Render.circleOutline(cx, cy, r - GRIP_RING / 2f, GRIP_RING, accent());
    }

    /**
     * The scale, by the selection, only while it is changing or just after: the one
     * property with no visual form of its own.
     */
    private static void readout(HudEditorView view, float width, float height, float opacity) {
        Placement selected = view.getSelected();
        if (selected == null || opacity <= 0f) {
            return;
        }
        String text = Math.round(selected.getLayout().getScale() * 100f) + "%";
        float padX = 6f;
        float padY = 3f;
        float w = small().widthOf(text) + padX * 2f;
        float h = small().getHeight() + padY * 2f;
        Bounds bounds = selected.shape().bounds();
        float x = Math.max(2f, Math.min(width - w - 2f, bounds.getCenterX() - w / 2f));
        float y = bounds.getBottom() + 8f;
        if (y + h > height) {
            y = bounds.getY() - 8f - h;
        }
        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(POPUP, opacity));
        Render.roundRectOutline(x, y, w, h, RADIUS_CONTROL, LINE_WEIGHT, faded(LINE, opacity));
        Render.text(small(), text, x + padX, y + padY, faded(TEXT, opacity));
    }

    /** Full-length lines wherever the selection is snapped this frame. */
    private static void guides(HudEditorView view, float width, float height) {
        Color colour = accent().withAlpha(160);
        for (SnapGuide guide : view.getGuides()) {
            if (guide.isVertical()) {
                Render.line(guide.getPosition(), 0f, guide.getPosition(), height, LINE_WEIGHT, colour);
            } else {
                Render.line(0f, guide.getPosition(), width, guide.getPosition(), LINE_WEIGHT, colour);
            }
        }
    }
}
