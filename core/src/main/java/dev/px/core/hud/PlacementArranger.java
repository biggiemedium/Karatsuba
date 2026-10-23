package dev.px.core.hud;

import dev.px.core.layout.Bounds;

import java.util.Map;

/**
 * Places elements relative to one another, after each has been placed on its own.
 *
 * <p>For layouts no single element can express: one element held against another's
 * edge, so that it follows as the other grows, shrinks or moves. Where
 * {@link PlacementAdjuster} answers for one element at a time, an arranger sees
 * every element of the frame at once, since where one goes depends on where its
 * neighbour went.
 *
 * <p>Consulted in the editor too, unlike an adjuster: an arrangement is part of the
 * layout being edited, so it has to hold while things are dragged. Nothing is
 * written back to any layout.
 */
@FunctionalInterface
public interface PlacementArranger {

    /**
     * @param placed       every resolved element's bounds this frame, by id, as its
     *                     layout (and any adjuster) placed it
     * @param screenWidth  the screen, for keeping what is moved on it
     * @param screenHeight the screen's height
     * @return the bounds to use instead, by id; an id left out keeps its own
     */
    Map<String, Bounds> arrange(Map<String, Bounds> placed, float screenWidth, float screenHeight);
}
