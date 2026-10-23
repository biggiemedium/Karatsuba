package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import dev.px.core.Core;
import dev.px.core.hud.HudEditor;
import dev.px.core.hud.HudEditorView;
import dev.px.core.hud.Placement;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.hud.group.GroupEditing;
import dev.px.karatsuba.client.hud.group.HudGroups;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.gui.apple.shell.widget.Caption;
import dev.px.karatsuba.client.hud.preset.glass.HudGlass;
import dev.px.karatsuba.client.hud.preset.HudPlate;

import java.util.EnumSet;
import java.util.Set;

/**
 * The HUD layout editor: the HUD drawn over the game, to be dragged into place.
 *
 * <p>A mode, so it says so: every element that can be moved has a dashed outline
 * and a badge at its corner, and the glass toolbar sits at the bottom centre. The
 * way out is always in view (Done), and Escape first lets go of the selection and
 * then leaves. Everything done here can be undone.
 *
 * <p>Each element's badge (Control Center's edit mode, {@code hud/DESIGN.md}
 * §7.1c): a circular − hides a shown element, which then leaves the editor; with
 * the HUD's Dim Hidden in Editor on it stays faint instead, with a + that shows it
 * again. One press, one undo step.
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
    private final GroupEditing grouping = new GroupEditing();

    private boolean altHeld;
    private long lastScaleAt;
    private long lastNudgeAt;
    private long resetArmedAt;

    /** Core's handles as they were before this editor opened, put back when it leaves; null while closed. */
    private Set<HudEditor.Handle> coreHandles;
    private float coreHandleSize;
    private float coreHandleGap;

    public LayoutSection(Shell shell) {
        this.shell = shell;
        buildToolbar();
    }

    /** The toolbar's tools (hud/DESIGN.md §7.1c): Undo, Redo, Snap, Reset All, Done. The selection's own reset is R. */
    private void buildToolbar() {
        toolbar.tool(Glyph.UNDO, "Undo    Ctrl+Z", true, EditorToolbar.Tool.Kind.PLAIN,
                null, history::canUndo, this::undo);
        toolbar.tool(Glyph.REDO, "Redo    Ctrl+Y", true, EditorToolbar.Tool.Kind.PLAIN,
                null, history::canRedo, this::redo);
        toolbar.divider();
        toolbar.tool(Glyph.SNAP, "Snap to edges and other elements (hold Alt to skip)", true,
                EditorToolbar.Tool.Kind.TOGGLE, () -> snapping, null, () -> snapping = !snapping);
        toolbar.tool(Glyph.RESET, "Reset All", false, EditorToolbar.Tool.Kind.PLAIN, null, null, this::resetAll)
                .labelledBy(() -> armed() ? "Click to Reset All" : "Reset All");
        toolbar.divider();
        toolbar.tool(Glyph.CHECK, "Done", false, EditorToolbar.Tool.Kind.PRIMARY, null, null,
                shell::returnFromFullscreen).tooltip("Back to the HUD    Esc");
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
        // Elements snap against one another as they are dragged, not only once dropped.
        editor.setDragConstraint(grouping);
        editor.setShowsHidden(HudStyle.get().dimHiddenInEditor());
        // One handle, the grip at the bottom-right corner, on the corner itself: the dot drawn is
        // the whole of what grabs. Core's editor is shared with the Classic UI, so its own
        // handles come back when this one closes.
        coreHandles = editor.getEnabledHandles();
        coreHandleSize = editor.getHandleSize();
        coreHandleGap = editor.getHandleGap();
        editor.setEnabledHandles(EnumSet.of(HudEditor.Handle.BOTTOM_RIGHT));
        editor.setHandleSize(Palette.GRIP_TARGET);
        editor.setHandleGap(-Palette.GRIP_TARGET / 2f);
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
        editor.setDragConstraint(null);
        HudSection.remember(editor.getSelectedId());
        if (editor.isActive()) {
            editor.close();
        }
        if (coreHandles != null) {
            editor.setEnabledHandles(coreHandles);
            editor.setHandleSize(coreHandleSize);
            editor.setHandleGap(coreHandleGap);
            coreHandles = null;
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
    public void drawScreen(float width, float height, float alpha) {
        HudEditor editor = editor();
        editor.setSnappingSuspended(!snapping || altHeld);
        // Off by default: a hidden element is gone from the editor, not dimmed (HUD Settings, Behaviour).
        editor.setShowsHidden(HudStyle.get().dimHiddenInEditor());
        // An attached element pulled far enough leaves its group before Core moves it on.
        grouping.moving(editor, Core.platform().getMouseX(), Core.platform().getMouseY());
        editor.update(Core.platform().getMouseX(), Core.platform().getMouseY());
        HudEditorView view = editor.view();
        toolbar.inTheWay(view.isInteracting() && nearToolbar(view));
        Placement badgeHovered = view.isInteracting() ? null
                : Badges.under(view, Core.platform().getMouseX(), Core.platform().getMouseY());
        EditorLook.draw(view, width, height, readoutOpacity(view), alpha, badgeHovered);
    }

    /** The toolbar's capsule of HUD glass, where its renderer named it, outside every fade (§10.2b). */
    @Override
    public void drawToolbarGlass(float alpha, float shiftY) {
        Bounds b = toolbar.glassBounds();
        if (b != null) {
            HudGlass.chrome(new HudPlate(EditorToolbar.OWNER, b.getX(), b.getY() + shiftY, b.getWidth(), b.getHeight(),
                    true, alpha, 0f));
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
        // The toolbar is at the bottom: an element coming down to it is in the way.
        return element.getBottom() > bar.getY() - 24f
                && element.getRight() > bar.getX() - 24f && element.getX() < bar.getRight() + 24f;
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean press(float x, float y, MouseButton button) {
        HudEditor editor = editor();
        // A badge first: its circle overhangs the element's corner, and pressing it hides
        // or shows that element without selecting or dragging anything (§10.2b).
        Placement badged = button == MouseButton.LEFT ? Badges.under(editor.view(), x, y) : null;
        if (badged != null) {
            change(() -> badged.getLayout().toggleHidden());
            return true;
        }
        if (button == MouseButton.LEFT) {
            history.begin();
            editor.press(x, y);
            grouping.pressed(editor, x, y);
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
        editor.release();
        // Dropped on or against another element: snapped to its side, in the same undo step.
        grouping.released(editor);
        history.commit();
        AutoSave.markDirty();
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
            case R -> change(() -> HudGroups.resetLayout(editor.getSelectedId()));
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
            change(HudGroups::resetAll);
        } else {
            resetArmedAt = System.currentTimeMillis();
        }
    }

    private boolean armed() {
        return resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt < CONFIRM_MILLIS;
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
