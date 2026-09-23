package dev.px.karatsuba.client.screen;

import dev.px.core.Core;
import dev.px.core.hud.HudEditor;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.Overlay;
import dev.px.karatsuba.client.config.AutoSave;

import java.util.Set;

/**
 * The HUD on screen, in both of its modes.
 *
 * <p>Drawing the HUD and editing it are the same overlay because they are the
 * same pixels: edit mode does not appear over the HUD, it replaces how the HUD is
 * drawn &mdash; hidden elements become faint instead of absent, and the chrome
 * goes over the top of them. Splitting the two would mean two things deciding
 * whether an element is drawn this frame.
 *
 * <p>This class is the <b>behaviour</b>: when the editor opens, what each key
 * does, what a right-click means. {@link HudEditorChrome} is the <b>look</b>, and
 * Core's {@link HudEditor} is the <b>arithmetic</b>. Three layers, and the one in
 * the middle is the only one worth arguing about.
 *
 * <h2>The key map</h2>
 * <table>
 *   <caption>What each key does while editing</caption>
 *   <tr><td>Drag</td><td>move; hold ALT to suspend snapping</td></tr>
 *   <tr><td>Drag the handle, or scroll</td><td>scale</td></tr>
 *   <tr><td>Right-click</td><td>hide or show what is under the cursor</td></tr>
 *   <tr><td>Arrows</td><td>nudge one pixel, ten with SHIFT</td></tr>
 *   <tr><td>L / H / R</td><td>lock, hide, reset the selection</td></tr>
 *   <tr><td>PageUp / PageDown</td><td>raise or lower in the draw order</td></tr>
 *   <tr><td>Escape</td><td>clear the selection, then leave</td></tr>
 * </table>
 *
 * <p>Keys not in that table are not consumed, which is what leaves the bind that
 * opened the editor able to close it.
 */
public final class HudOverlay implements Overlay {

    /** How much one wheel notch changes the scale. */
    private static final float SCALE_STEP = 0.05f;

    /** Fine and coarse nudges. Single pixels to finish a position, ten to get near one. */
    private static final float NUDGE = 1f;
    private static final float NUDGE_COARSE = 10f;

    /**
     * When the scale last changed by scroll, so the readout can linger and fade.
     *
     * <p>Held here rather than in the chrome because scrolling is an input and this
     * is the half that sees input. The chrome is handed it and decides what to do
     * with it.
     */
    private long lastScaleAt;

    // ------------------------------------------------------------- lifecycle

    /** Opens the editor, or closes it if it is already showing. */
    public void toggleEditor() {
        // The game screen goes up first: the frame that draws overlays is only
        // reached once a screen is showing.
        CoreScreen.open();
        editor().toggle();
    }

    @Override
    public void dismiss() {
        editor().close();
    }

    @Override
    public boolean isCapturing() {
        return editor().isActive();
    }

    private static HudEditor editor() {
        return Core.hud().getEditor();
    }

    // --------------------------------------------------------------- drawing

    @Override
    public void render(float screenWidth, float screenHeight) {
        HudEditor editor = editor();
        if (!editor.isActive()) {
            Core.hud().drawAll();
            return;
        }
        // Applies whatever drag or resize is in flight before the geometry is read,
        // so an element is drawn where the cursor is this frame rather than one
        // frame behind it.
        editor.update(Core.platform().getMouseX(), Core.platform().getMouseY());

        HudEditorChrome.render(editor.view(), screenWidth, screenHeight, lastScaleAt);
    }

    // ----------------------------------------------------------------- input

    /** ALT suspends snapping. Core tracks no key itself, because which key it is is a UX decision. */
    @Override
    public void modifiersChanged(Set<Modifier> modifiers) {
        editor().setSnappingSuspended(modifiers.contains(Modifier.ALT));
    }

    @Override
    public boolean mousePressed(float x, float y, MouseButton button) {
        HudEditor editor = editor();

        if (button == MouseButton.LEFT) {
            editor.press(x, y);
            return true;
        }
        if (button == MouseButton.RIGHT) {
            // Selecting first is what makes this act on what is under the cursor
            // rather than on whatever happened to be selected already.
            editor.press(x, y);
            editor.toggleHiddenSelected();
            AutoSave.markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(float x, float y) {
        editor().release();
        // A drag or a resize has just finished, and neither is a Setting, so this
        // is the only thing that will ever say the profile changed.
        AutoSave.markDirty();
    }

    @Override
    public boolean scrolled(float amount, float x, float y) {
        editor().scaleSelected(amount > 0f ? SCALE_STEP : -SCALE_STEP);
        lastScaleAt = System.currentTimeMillis();
        AutoSave.markDirty();
        return true;
    }

    /**
     * The editor's key map.
     *
     * <p>Escape backs out one level rather than leaving outright: clearing the
     * selection is almost always what was meant, and a second press still leaves.
     *
     * @return whether the key is one of the editor's; anything else falls through
     *         to the client's action binds
     */
    @Override
    public boolean keyPressed(Key key, Set<Modifier> modifiers) {
        HudEditor editor = editor();
        float step = modifiers.contains(Modifier.SHIFT) ? NUDGE_COARSE : NUDGE;

        switch (key) {
            case ESCAPE -> {
                if (editor.getSelectedId() != null) {
                    editor.deselect();
                } else {
                    editor.close();
                }
            }
            case LEFT -> editor.nudgeSelected(-step, 0f);
            case RIGHT -> editor.nudgeSelected(step, 0f);
            case UP -> editor.nudgeSelected(0f, -step);
            case DOWN -> editor.nudgeSelected(0f, step);
            case L -> editor.toggleLockSelected();
            case H -> editor.toggleHiddenSelected();
            case R -> editor.resetSelected();
            case PAGE_UP -> editor.bringSelectedToFront();
            case PAGE_DOWN -> editor.sendSelectedToBack();
            default -> {
                return false;
            }
        }
        AutoSave.markDirty();
        return true;
    }
}
