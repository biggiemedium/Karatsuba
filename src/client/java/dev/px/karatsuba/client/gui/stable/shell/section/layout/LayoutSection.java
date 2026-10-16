package dev.px.karatsuba.client.gui.stable.shell.section.layout;

import dev.px.core.Core;
import dev.px.core.hud.HudEditor;
import dev.px.core.hud.HudEditorView;
import dev.px.core.hud.Placement;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Render;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.stable.shell.Section;
import dev.px.karatsuba.client.gui.stable.shell.Shell;
import dev.px.karatsuba.client.gui.stable.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.stable.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;
import dev.px.karatsuba.client.gui.stable.shell.widget.Caption;
import dev.px.karatsuba.client.gui.stable.shell.widget.PillButton;
import dev.px.karatsuba.client.hud.stack.HudStacks;
import dev.px.karatsuba.client.hud.style.StyledElement;

import java.util.List;
import java.util.Set;

/**
 * The HUD layout editor: the HUD drawn over the game, to be dragged into place.
 *
 * <p>A mode, so it says so twice over: the game is dimmed, every element that
 * can be moved has a dashed outline, and the toolbar reads "Editing HUD". The way
 * out is always in view (Done), and Escape first lets go of the selection and
 * then leaves. Everything done here can be undone.
 *
 * <p>Click an element to select it; drag to move it, or its corner to scale it;
 * the wheel scales too. Arrows nudge by a pixel, Shift by ten. Snapping to the
 * screen's edges and centre and to other elements is on unless switched off in
 * the toolbar or Alt is held. L locks, H or Delete hides, R resets the
 * selection; Page Up and Page Down bring it forward or back; Ctrl+Z and Ctrl+Y
 * undo and redo.
 */
public final class LayoutSection implements Section {

    public static final String ID = "layout";

    /** How much one wheel notch scales. */
    private static final float SCALE_STEP = 0.05f;

    /** Fine and coarse nudges. */
    private static final float NUDGE = 1f;
    private static final float NUDGE_COARSE = 10f;

    /** A run of wheel notches closer together than this is one undo step. */
    private static final long SCROLL_GROUP_MILLIS = 500L;

    /** How long "Reset all" waits for its confirming second press. */
    private static final long CONFIRM_MILLIS = 3000L;

    /** Snapping, kept on or off across openings. */
    private static boolean snapping = true;

    private final Shell shell;
    private final Caption placeholder = new Caption("");
    private final EditorToolbar toolbar = new EditorToolbar();
    private final LayoutHistory history = new LayoutHistory();

    private boolean altHeld;
    private boolean shiftHeld;
    private long lastScaleAt;
    private long lastNudgeAt;
    private long resetArmedAt;

    public LayoutSection(Shell shell) {
        this.shell = shell;
        buildToolbar();
    }

    private void buildToolbar() {
        toolbar.add(new EditorToolbar.ModeLabel("Editing HUD"));
        toolbar.add(new EditorToolbar.Divider());
        toolbar.add(new PillButton(Glyph.UNDO, "Undo    Ctrl+Z", true, PillButton.Style.PLAIN,
                null, history::canUndo, this::undo));
        toolbar.add(new PillButton(Glyph.REDO, "Redo    Ctrl+Y", true, PillButton.Style.PLAIN,
                null, history::canRedo, this::redo));
        toolbar.add(new EditorToolbar.Divider());
        toolbar.add(new PillButton(Glyph.SNAP, "Snap to edges and other elements (hold Alt to skip)", true,
                PillButton.Style.TOGGLE, () -> snapping, null, () -> snapping = !snapping));
        toolbar.add(new PillButton(Glyph.RESET, "Reset the selected element    R", true, PillButton.Style.PLAIN,
                null, () -> editor().getSelected() != null, () -> change(() -> editor().resetSelected())));
        toolbar.add(new PillButton(Glyph.RESET, "Reset all", false, PillButton.Style.PLAIN, null, null,
                this::resetAll).labelledBy(() -> armed() ? "Click to reset all" : "Reset all"));
        toolbar.add(new EditorToolbar.Divider());
        toolbar.add(new EditorToolbar.Status(this::status));
        toolbar.add(new PillButton(Glyph.CHECK, "Done", false, PillButton.Style.PRIMARY, null, null,
                shell::returnFromFullscreen).tooltip("Back to the HUD    Esc"));
        // The bar is fully rounded, so its buttons are too: corners would reach past its curved ends.
        for (var child : toolbar.getChildren()) {
            if (child instanceof PillButton button) {
                button.rounded();
            }
        }
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Layout";
    }

    @Override
    public Glyph glyph() {
        return Glyph.LAYOUT;
    }

    @Override
    public Widget content() {
        return placeholder;
    }

    @Override
    public boolean fullscreen() {
        return true;
    }

    @Override
    public Widget toolbar() {
        return toolbar;
    }

    @Override
    public void entered() {
        HudEditor editor = editor();
        if (!editor.isActive()) {
            editor.open();
        }
        String start = HudSection.selectedId();
        if (start != null) {
            editor.select(start);
        }
        resetArmedAt = 0L;
    }

    @Override
    public void left() {
        history.commit();
        HudEditor editor = editor();
        HudSection.remember(editor.getSelectedId());
        if (editor.isActive()) {
            editor.close();
        }
        AutoSave.markDirty();
    }

    @Override
    public boolean back() {
        // Escape lets go of the selection first; a second Escape is Done.
        if (editor().getSelectedId() != null) {
            editor().deselect();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- frames

    @Override
    public void drawScreen(float width, float height) {
        HudEditor editor = editor();
        editor.setSnappingSuspended(!snapping || altHeld);
        editor.update(Core.platform().getMouseX(), Core.platform().getMouseY());
        HudEditorView view = editor.view();
        toolbar.inTheWay(view.isInteracting() && nearToolbar(view));
        EditorLook.draw(view, width, height, readoutOpacity(view));
        if (shiftHeld && editor.isDragging()) {
            Placement target = dropTarget(Core.platform().getMouseX(), Core.platform().getMouseY());
            if (target != null) {
                // Where it would land: the target outlined in the accent, and a word saying so.
                target.shape().stroke(2f, Palette.accent());
                Bounds at = target.shape().bounds();
                Render.text(Palette.small(), "Stack here", at.getX(), at.getBottom() + 3f, Palette.accent());
            }
        }
    }

    /** @return whether the dragged element is up where the toolbar is */
    private boolean nearToolbar(HudEditorView view) {
        Placement selected = view.getSelected();
        Bounds bar = toolbar.getBounds();
        if (selected == null || bar == null) {
            return false;
        }
        Bounds element = selected.shape().bounds();
        return element.getY() < bar.getBottom() + 24f
                && element.getRight() > bar.getX() - 24f && element.getX() < bar.getRight() + 24f;
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean press(float x, float y, MouseButton button) {
        HudEditor editor = editor();
        if (button == MouseButton.LEFT) {
            history.begin();
            editor.press(x, y);
            return true;
        }
        if (button == MouseButton.RIGHT) {
            // Selecting first is what makes this act on what is under the cursor.
            editor.press(x, y);
            if (editor.getSelected() != null) {
                change(editor::toggleHiddenSelected);
            }
            return true;
        }
        return false;
    }

    @Override
    public void release(float x, float y) {
        HudEditor editor = editor();
        // Shift held as an element is dropped on another: they stack.
        Placement target = shiftHeld && editor.isDragging() ? dropTarget(x, y) : null;
        String dragged = editor.getSelectedId();
        editor.release();
        if (target != null && dragged != null && HudStacks.get() != null) {
            HudStacks.get().dock(dragged, target.getId());
        }
        history.commit();
        AutoSave.markDirty();
    }

    /**
     * @return the element under the cursor that the one being dragged would stack
     *         with if dropped now, or null: both must be able to stack, and a
     *         follower is never a target (it has no box of its own; its host is)
     */
    private Placement dropTarget(float x, float y) {
        HudEditor editor = editor();
        Placement dragged = editor.getSelected();
        if (dragged == null || !(dragged.getElement() instanceof StyledElement mover) || !mover.dockable()) {
            return null;
        }
        List<Placement> placements = editor.view().getPlacements();
        // Topmost first, as a click would find it.
        for (int i = placements.size() - 1; i >= 0; i--) {
            Placement candidate = placements.get(i);
            if (candidate != dragged && candidate.hits(x, y)
                    && candidate.getElement() instanceof StyledElement styled && styled.dockable()) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public boolean scroll(float amount) {
        HudEditor editor = editor();
        if (editor.getSelected() == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastScaleAt > SCROLL_GROUP_MILLIS) {
            history.commit();
        }
        history.begin();
        editor.scaleSelected(amount > 0f ? SCALE_STEP : -SCALE_STEP);
        lastScaleAt = now;
        AutoSave.markDirty();
        return true;
    }

    @Override
    public void modifiers(Set<Modifier> modifiers) {
        altHeld = modifiers.contains(Modifier.ALT);
        shiftHeld = modifiers.contains(Modifier.SHIFT);
    }

    @Override
    public boolean key(Key key, Set<Modifier> modifiers) {
        HudEditor editor = editor();
        boolean ctrl = modifiers.contains(Modifier.CTRL);
        if (ctrl && key == Key.Z) {
            if (modifiers.contains(Modifier.SHIFT)) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (ctrl && key == Key.Y) {
            redo();
            return true;
        }
        float step = modifiers.contains(Modifier.SHIFT) ? NUDGE_COARSE : NUDGE;
        switch (key) {
            case LEFT -> nudge(-step, 0f);
            case RIGHT -> nudge(step, 0f);
            case UP -> nudge(0f, -step);
            case DOWN -> nudge(0f, step);
            case L -> change(editor::toggleLockSelected);
            case H, DELETE -> change(editor::toggleHiddenSelected);
            case R -> change(editor::resetSelected);
            case PAGE_UP -> change(editor::bringSelectedToFront);
            case PAGE_DOWN -> change(editor::sendSelectedToBack);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- internals

    private void nudge(float dx, float dy) {
        long now = System.currentTimeMillis();
        // Held arrows repeat; a run of them is one step to undo.
        if (now - lastNudgeAt > SCROLL_GROUP_MILLIS) {
            history.commit();
        }
        history.begin();
        editor().nudgeSelected(dx, dy);
        lastNudgeAt = now;
        AutoSave.markDirty();
    }

    /** Does one discrete change as one undo step. */
    private void change(Runnable edit) {
        history.commit();
        history.begin();
        edit.run();
        history.commit();
        AutoSave.markDirty();
    }

    private void undo() {
        history.undo();
        AutoSave.markDirty();
    }

    private void redo() {
        history.redo();
        AutoSave.markDirty();
    }

    /** Resets every element, but only on a second press within three seconds, and undoably. */
    private void resetAll() {
        if (armed()) {
            resetArmedAt = 0L;
            change(Core.hud()::resetAll);
        } else {
            resetArmedAt = System.currentTimeMillis();
        }
    }

    private boolean armed() {
        return resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt < CONFIRM_MILLIS;
    }

    /** What the toolbar says about the selection: its name and scale, or how to start. */
    private String status() {
        Placement selected = editor().getSelected();
        if (selected == null) {
            return "Click an element to select it";
        }
        String state = selected.getLayout().isLocked() ? "  ·  locked"
                : selected.getLayout().isHidden() ? "  ·  hidden" : "";
        return selected.getElement().getDisplayName() + "  ·  "
                + Math.round(selected.getLayout().getScale() * 100f) + "%" + state;
    }

    /** The scale readout by the selection: while resizing, and lingering after the wheel or a nudge. */
    private float readoutOpacity(HudEditorView view) {
        if (view.isResizing() || view.isDragging()) {
            return 1f;
        }
        long since = System.currentTimeMillis() - Math.max(lastScaleAt, lastNudgeAt);
        if (since >= 700L) {
            return 0f;
        }
        return since <= 450L ? 1f : 1f - (since - 450L) / 250f;
    }

    private static HudEditor editor() {
        return Core.hud().getEditor();
    }
}
