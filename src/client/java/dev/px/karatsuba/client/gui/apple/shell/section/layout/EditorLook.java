package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import dev.px.core.Core;
import dev.px.core.hud.HudEditor;
import dev.px.core.hud.HudEditorView;
import dev.px.core.hud.Placement;
import dev.px.core.hud.SnapGuide;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * What the layout editor draws over the game: the elements, their outlines, the
 * selection's grip, the scale while it changes, and the snap guides.
 *
 * <p>Three states for an element, one accent, one line weight. Every element that
 * can be moved has a faint dashed outline, which is half of what makes edit mode
 * look like a mode; the one under the cursor is outlined solid and faint; the
 * selected one in the accent, dimmed when locked. Hidden elements are drawn faint
 * rather than left out, so they can still be found and shown again.
 */
final class EditorLook {

    private static final float LINE_WEIGHT = 1f;
    private static final float DASH = 3f;
    private static final float GAP = 3f;

    /** The one grab point: scaling is uniform, so one corner is all it needs. */
    private static final HudEditor.Handle GRAB = HudEditor.Handle.BOTTOM_RIGHT;

    private static final float HIDDEN_ALPHA = 0.3f;

    private EditorLook() {
    }

    static void draw(HudEditorView view, float width, float height, float readout) {
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
        grip(view);
        readout(view, width, height, readout);
        guides(view, width, height);
    }

    private static void outline(HudEditorView view, Placement placement) {
        boolean selected = view.isSelected(placement);
        boolean locked = placement.getLayout().isLocked();
        if (selected) {
            placement.shape().stroke(LINE_WEIGHT, locked ? faded(accent(), 0.45f) : accent());
        } else if (view.isHovered(placement)) {
            placement.shape().stroke(LINE_WEIGHT, Color.of(255, 255, 255, 70));
        } else if (!locked) {
            Bounds b = placement.shape().bounds();
            dashed(b.getX() - 1f, b.getY() - 1f, b.getWidth() + 2f, b.getHeight() + 2f, Color.of(255, 255, 255, 45));
        }
    }

    /** A rectangle drawn as dashes, the conventional sign of "this can be moved". */
    private static void dashed(float x, float y, float w, float h, Color colour) {
        for (float at = 0f; at < w; at += DASH + GAP) {
            float len = Math.min(DASH, w - at);
            Render.rect(x + at, y, len, LINE_WEIGHT, colour);
            Render.rect(x + at, y + h - LINE_WEIGHT, len, LINE_WEIGHT, colour);
        }
        for (float at = 0f; at < h; at += DASH + GAP) {
            float len = Math.min(DASH, h - at);
            Render.rect(x, y + at, LINE_WEIGHT, len, colour);
            Render.rect(x + w - LINE_WEIGHT, y + at, LINE_WEIGHT, len, colour);
        }
    }

    /** A small square in the accent, ringed so it survives a bright HUD behind it. */
    private static void grip(HudEditorView view) {
        Bounds box = view.getHandles().get(GRAB);
        if (box == null) {
            return;
        }
        Render.roundRect(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 1.5f, accent());
        Render.roundRectOutline(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 1.5f, LINE_WEIGHT,
                Color.of(0, 0, 0, 130));
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
