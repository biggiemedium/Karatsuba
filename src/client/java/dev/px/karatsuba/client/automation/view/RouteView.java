package dev.px.karatsuba.client.automation.view;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.navigation.Route;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.karatsuba.client.render.ThemeColors;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;
import dev.px.navigation.Navigator;
import dev.px.navigation.Travel;

import java.util.List;

/**
 * The route being walked, drawn in the world: a line along the feet from where
 * the player is to the end of the stretch, and a marker where it ends.
 *
 * <p>Only the stretch the navigator is following, and only the part still ahead:
 * a precise route has a position for every tick, so the line is exactly where
 * the feet are planned to go, sprint-jumps included. Drawn in theme colours, from
 * the primary accent here to the secondary at the end, unless the settings give
 * a colour of their own.
 *
 * <p>Not a module: what it draws exists only while a task walks, and whether to
 * draw it is the Automation settings' Show Route.
 */
public final class RouteView {

    /** Lifted off the ground so the line is not lost in it. */
    private static final double LIFT = 0.05d;

    private static final float THICKNESS = 2f;

    /** The end marker: about a player's footprint, and flat. */
    private static final double MARKER_WIDTH = 0.6d;
    private static final double MARKER_HEIGHT = 0.02d;

    private final Navigation navigation;
    private final AutomationSettings settings;

    public RouteView(Navigation navigation, AutomationSettings settings) {
        this.navigation = navigation;
        this.settings = settings;
    }

    /** Draws every route under way. Called inside the world render pass. */
    public void draw() {
        if (!settings.showRoute()) {
            return;
        }
        for (Travel.Trip trip : navigation.getActiveTrips()) {
            Navigator navigator = trip.getNavigator();
            Route section = navigator.getSection();
            if (section != null) {
                draw(section.getWaypoints(), section.isPrecise() ? navigator.getSectionTick() : 0);
            }
        }
    }

    private void draw(List<Vec3> waypoints, int from) {
        int last = waypoints.size() - 1;
        if (from > last) {
            return;
        }
        boolean themed = settings.themeColours();
        float span = Math.max(1, last - from);
        for (int i = from; i < last; i++) {
            Render.line3D(lifted(waypoints.get(i)), lifted(waypoints.get(i + 1)), THICKNESS,
                    ThemeColors.of(settings.routeColour(), themed, (i - from) / span));
        }
        Render.boxOutline(Box.around(lifted(waypoints.get(last)), MARKER_WIDTH, MARKER_HEIGHT), THICKNESS,
                ThemeColors.of(settings.routeColour(), themed, 1f));
    }

    private static Vec3 lifted(Vec3 feet) {
        return feet.add(0d, LIFT, 0d);
    }
}
