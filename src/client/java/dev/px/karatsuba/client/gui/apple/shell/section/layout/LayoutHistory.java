package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import com.google.gson.JsonObject;
import dev.px.karatsuba.client.hud.group.HudGroups;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo for the layout editor: the whole HUD layout, and its groups, snapshotted before
 * each change, and kept only if the change changed anything.
 *
 * <p>Snapshots rather than recorded operations, because the editor's operations
 * live in Core and a snapshot of every element's layout is a few hundred bytes.
 * A change is opened with {@link #begin} (a press, a key, the first notch of a
 * scroll) and closed with {@link #commit}; an open change that ends where it
 * started (a click that selected without moving) leaves no step behind.
 */
final class LayoutHistory {

    private static final int LIMIT = 100;

    private final Deque<JsonObject> undo = new ArrayDeque<>();
    private final Deque<JsonObject> redo = new ArrayDeque<>();
    private JsonObject pending;

    /** Snapshots the layout before a change. A change already open stays open. */
    void begin() {
        if (pending == null) {
            pending = snapshot();
        }
    }

    /** Keeps the change as one step, if it changed anything. */
    void commit() {
        if (pending == null) {
            return;
        }
        if (!pending.equals(snapshot())) {
            undo.push(pending);
            while (undo.size() > LIMIT) {
                undo.removeLast();
            }
            redo.clear();
        }
        pending = null;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    void undo() {
        commit();
        if (!undo.isEmpty()) {
            redo.push(snapshot());
            HudGroups.restore(undo.pop());
        }
    }

    void redo() {
        commit();
        if (!redo.isEmpty()) {
            undo.push(snapshot());
            HudGroups.restore(redo.pop());
        }
    }

    private static JsonObject snapshot() {
        return HudGroups.snapshot();
    }
}
