package dev.px.karatsuba.client.hud.group;

import dev.px.core.Core;
import dev.px.core.hud.HudEditor;
import dev.px.core.hud.Placement;
import dev.px.core.layout.Bounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * What the layout editors do with groups ({@code hud/DESIGN.md} §6), the same in
 * both UIs:
 *
 * <ul>
 *   <li><b>Pulling out.</b> Dragging an attached element takes it out of its group
 *       once the cursor has really moved (a click only selects); the rest close up.
 *       Dragging a group's root moves the whole group.</li>
 *   <li><b>Snapping on.</b> Dropping an element over another, or with an edge close
 *       to one, attaches it to the side it needs to move least to reach, lined up
 *       with the nearest of that side's start, centre or end. An element is never
 *       left inside another.</li>
 * </ul>
 *
 * <p>Snapping happens <em>while</em> dragging, not after: this is Core's drag
 * constraint ({@link #constrain}), so an element is held against the side it will
 * attach to on every frame, and is never shown inside another. Letting go attaches
 * it there.
 *
 * <p>The editor installs it with {@code setDragConstraint} while open, and calls
 * {@link #pressed} after Core's press, {@link #moving} every frame before Core's
 * update, and {@link #released} after Core's release, inside the same undo step.
 */
public final class GroupEditing implements HudEditor.DragConstraint {

    /** How far the cursor travels before a press on an attached element pulls it out. */
    private static final float PULL_DISTANCE = 3f;

    /** An edge this close to another element's, sharing some of its length, attaches without overlapping. */
    private static final float ATTACH_DISTANCE = 4f;

    /** Overlap smaller than this is two edges touching, give or take rounding, not one inside the other. */
    private static final float TOUCHING = 0.01f;

    private String pressedId;
    private float pressX;
    private float pressY;
    private boolean moved;

    /** After Core's press: remembers what is being dragged, if anything. */
    public void pressed(HudEditor editor, float x, float y) {
        pressedId = editor.view().isDragging() ? editor.getSelectedId() : null;
        pressX = x;
        pressY = y;
        moved = false;
    }

    /** Before Core's update, every frame: pulls an attached element out once it is really dragged. */
    public void moving(HudEditor editor, float x, float y) {
        HudGroups groups = HudGroups.get();
        if (groups == null || pressedId == null || moved || !editor.view().isDragging()) {
            return;
        }
        float dx = x - pressX;
        float dy = y - pressY;
        if (dx * dx + dy * dy < PULL_DISTANCE * PULL_DISTANCE) {
            return;
        }
        moved = true;
        // Core has been moving its layout under the cursor all along; out of the group, that is where it is.
        if (groups.linkOf(pressedId) != null) {
            groups.detach(pressedId);
        }
    }

    /**
     * After Core's release: snaps what was dropped onto what it landed on.
     *
     * @return whether it attached to something
     */
    public boolean released(HudEditor editor) {
        String dropped = moved ? pressedId : null;
        pressedId = null;
        moved = false;
        HudGroups groups = HudGroups.get();
        if (dropped == null || groups == null) {
            return false;
        }
        return drop(groups, dropped, editor.view().getPlacements());
    }

    /**
     * Every frame of a drag, as Core's {@link HudEditor.DragConstraint}: an element
     * over another, or with an edge close to one, is held against that element's
     * nearest side rather than going inside it, so the drag shows where it will land.
     * An attached element not yet pulled out is left alone: its group holds it.
     */
    @Override
    public Bounds constrain(Placement dragged, Bounds proposed, List<Placement> placements) {
        HudGroups groups = HudGroups.get();
        if (groups == null || groups.linkOf(dragged.getId()) != null) {
            return proposed;
        }
        Snap snap = nearest(groups, dragged.getId(), proposed, placements);
        if (snap == null) {
            return proposed;
        }
        return HudGroups.against(snap.target().getBounds(), proposed.getWidth(), proposed.getHeight(),
                new HudGroups.Link(snap.target().getId(), snap.side(), snap.along()));
    }

    /** Attaches {@code id} to whatever it was dropped on or against, on the side it moves least to reach. */
    static boolean drop(HudGroups groups, String id, List<Placement> placements) {
        Placement dragged = find(placements, id);
        if (dragged == null) {
            return false;
        }
        Snap snap = nearest(groups, id, dragged.getBounds(), placements);
        return snap != null && groups.attach(id, snap.target().getId(), snap.side(), snap.along());
    }

    /** Where an element at some bounds would snap to: onto this element, on this side, lined up this way. */
    private record Snap(Placement target, HudGroups.Side side, HudGroups.Along along) {
    }

    /**
     * @return the side of another element that {@code id}, at {@code d}, overlaps or
     *         nearly touches and would move least to sit on; null when it is clear of
     *         everything. Never anything that moves with it
     */
    private static Snap nearest(HudGroups groups, String id, Bounds d, List<Placement> placements) {
        Set<String> own = groups.subtreeOf(id);
        float screenWidth = Core.platform().getScreenWidth();
        float screenHeight = Core.platform().getScreenHeight();
        List<Candidate> candidates = new ArrayList<>();
        for (Placement other : placements) {
            if (own.contains(other.getId()) || other.getLayout().isHidden()) {
                continue;
            }
            Bounds t = other.getBounds();
            float acrossX = Math.min(d.getRight(), t.getRight()) - Math.max(d.getX(), t.getX());
            float acrossY = Math.min(d.getBottom(), t.getBottom()) - Math.max(d.getY(), t.getY());
            boolean overlapping = acrossX > 0f && acrossY > 0f;
            for (HudGroups.Side side : HudGroups.Side.values()) {
                // Sitting on a side means sharing some of its length.
                if ((side.vertical() ? acrossX : acrossY) <= 0f) {
                    continue;
                }
                float cost = distanceTo(d, t, side);
                if ((overlapping || cost <= ATTACH_DISTANCE) && fits(t, d, side, screenWidth, screenHeight)) {
                    candidates.add(new Candidate(new Snap(other, side, along(d, t, side)), cost));
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(Candidate::cost));
        // The nearest spot that is clear of everything else: a side shared with a third
        // element (Debug under the Watermark, say) would only trade one overlap for another.
        for (Candidate candidate : candidates) {
            Snap snap = candidate.snap();
            Bounds there = HudGroups.against(snap.target().getBounds(), d.getWidth(), d.getHeight(),
                    new HudGroups.Link(snap.target().getId(), snap.side(), snap.along()));
            if (clear(there, placements, own)) {
                return snap;
            }
        }
        return candidates.get(0).snap();
    }

    /** One side an element could snap to, and how far it would move to get there. */
    private record Candidate(Snap snap, float cost) {
    }

    /** @return whether {@code b} overlaps nothing but what moves with it (touching is fine) */
    private static boolean clear(Bounds b, List<Placement> placements, Set<String> own) {
        for (Placement other : placements) {
            if (own.contains(other.getId()) || other.getLayout().isHidden()) {
                continue;
            }
            Bounds t = other.getBounds();
            float acrossX = Math.min(b.getRight(), t.getRight()) - Math.max(b.getX(), t.getX());
            float acrossY = Math.min(b.getBottom(), t.getBottom()) - Math.max(b.getY(), t.getY());
            if (acrossX > TOUCHING && acrossY > TOUCHING) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return whether {@code d} sitting on {@code t}'s {@code side} is wholly on screen.
     *         A side with no room (above an element at the top) is never chosen: the
     *         screen's clamp would push it back over what it was kept off
     */
    private static boolean fits(Bounds t, Bounds d, HudGroups.Side side, float screenWidth, float screenHeight) {
        return switch (side) {
            case BELOW -> t.getBottom() + d.getHeight() <= screenHeight;
            case ABOVE -> t.getY() - d.getHeight() >= 0f;
            case RIGHT -> t.getRight() + d.getWidth() <= screenWidth;
            case LEFT -> t.getX() - d.getWidth() >= 0f;
        };
    }

    /** @return how far {@code d} must move to sit on {@code t}'s {@code side} */
    private static float distanceTo(Bounds d, Bounds t, HudGroups.Side side) {
        return switch (side) {
            case BELOW -> Math.abs(d.getY() - t.getBottom());
            case ABOVE -> Math.abs(d.getBottom() - t.getY());
            case RIGHT -> Math.abs(d.getX() - t.getRight());
            case LEFT -> Math.abs(d.getRight() - t.getX());
        };
    }

    /** @return the nearest of the side's start, centre and end for {@code d} to line up with */
    private static HudGroups.Along along(Bounds d, Bounds t, HudGroups.Side side) {
        float start = side.vertical() ? Math.abs(d.getX() - t.getX()) : Math.abs(d.getY() - t.getY());
        float end = side.vertical() ? Math.abs(d.getRight() - t.getRight()) : Math.abs(d.getBottom() - t.getBottom());
        float centre = side.vertical() ? Math.abs(d.getCenterX() - t.getCenterX()) : Math.abs(d.getCenterY() - t.getCenterY());
        if (centre < start && centre < end) {
            return HudGroups.Along.CENTER;
        }
        return end < start ? HudGroups.Along.END : HudGroups.Along.START;
    }

    private static Placement find(List<Placement> placements, String id) {
        for (Placement placement : placements) {
            if (placement.getId().equals(id)) {
                return placement;
            }
        }
        return null;
    }
}
