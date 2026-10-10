package dev.px.karatsuba.client.settings.automation;

import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.settings.ClientSection;

/**
 * How automation runs: how hard it holds the controls, how it turns, where its
 * routes may go, and what it shows and says while it works.
 *
 * <p>A client setting, as {@code InteractionSettings} is: no task reads its own
 * copy of these. Every task runs under them, read live through
 * {@code Automation}, so a change applies to a running task on the next tick.
 *
 * <p>The defaults walk like a careful player: the camera turns with the route,
 * falls are kept to three blocks (more costs health), and lava, fire, cactus and
 * the rest are walked round. Water is avoided too: the planner simulates walking,
 * not swimming, so a route through water is one it cannot follow.
 */
public final class AutomationSettings extends ClientSection {

    public static final String ID = "automation";

    // ------------------------------------------------------------- control

    private final NumberSetting<Integer> priority = integer("Priority", RotationPriority.NORMAL,
            RotationPriority.LOWEST, RotationPriority.HIGHEST)
            .describe("How firmly a task holds the keys and the head; a module asking higher wins them");

    private final BooleanSetting yieldToPlayer = bool("Yield To Player", true)
            .describe("Stop the running task when you press a movement key");

    private final EnumSetting<RotationMode> rotationMode = enumOf("Rotation Mode", RotationMode.CLIENT)
            .describe("CLIENT turns the camera with the route; SILENT turns only what the server sees, and walks by the Move Fix");

    private final NumberSetting<Float> turnSpeed = number("Turn Speed", 180f, 10f, 180f)
            .step(5f)
            .describe("The most the head turns in a tick; turns ease in under it. Lower looks calmer, walks replan more");

    // -------------------------------------------------------------- routes

    private final BooleanSetting sprint = bool("Sprint", true)
            .describe("Plan sprints and sprint-jumps when hunger allows");

    private final NumberSetting<Integer> maxFall = integer("Max Fall", 3, 0, 32)
            .describe("The highest drop a route may take, in blocks; more than 3 costs health");

    private final BooleanSetting avoidHazards = bool("Avoid Hazards", true)
            .describe("Keep routes out of lava, fire, magma, cactus, berry bushes, wither roses and powder snow");

    private final BooleanSetting avoidWater = bool("Avoid Water", true)
            .describe("Keep routes out of water; the planner walks and cannot plan a swim");

    private final NumberSetting<Integer> range = integer("Planner Range", 24, 8, 64)
            .describe("How far ahead each plan reaches, in blocks; further goals are reached a plan at a time");

    private final NumberSetting<Integer> searchBudget = integer("Search Budget", 2000, 250, 20000)
            .describe("Moves one plan may try before taking the closest it found; higher finds more, costs more");

    private final NumberSetting<Integer> stuckTicks = integer("Stuck After", 100, 20, 600)
            .describe("Ticks without getting closer before a task counts as stuck");

    private final BooleanSetting giveUpWhenStuck = bool("Give Up When Stuck", false)
            .describe("End a stuck task as failed, rather than keep trying");

    // ------------------------------------------------------------ feedback

    private final BooleanSetting showRoute = bool("Show Route", true)
            .describe("Draw the stretch of route being walked");

    private final BooleanSetting themeColours = bool("Theme Colours", true)
            .describe("Draw the route in the client theme's colours instead of the one set here")
            .visibleWhen(showRoute);

    private final ColorSetting routeColour = color("Route Colour", Color.of(90, 200, 255, 220))
            .visibleWhen(() -> showRoute.isOn() && !themeColours.isOn());

    private final BooleanSetting notify = bool("Notify", true)
            .describe("Say when a task arrives, fails or is stopped");

    public AutomationSettings() {
        super(ID, "Automation", "How tasks hold the controls, plan their routes and report back");
    }

    public int priority() {
        return priority.getInt();
    }

    public boolean yieldToPlayer() {
        return yieldToPlayer.isOn();
    }

    public RotationMode rotationMode() {
        return rotationMode.get();
    }

    public float turnSpeed() {
        return turnSpeed.getFloat();
    }

    public boolean sprint() {
        return sprint.isOn();
    }

    public int maxFall() {
        return maxFall.getInt();
    }

    public boolean avoidHazards() {
        return avoidHazards.isOn();
    }

    public boolean avoidWater() {
        return avoidWater.isOn();
    }

    public int range() {
        return range.getInt();
    }

    public int searchBudget() {
        return searchBudget.getInt();
    }

    public int stuckTicks() {
        return stuckTicks.getInt();
    }

    public boolean giveUpWhenStuck() {
        return giveUpWhenStuck.isOn();
    }

    public boolean showRoute() {
        return showRoute.isOn();
    }

    public boolean themeColours() {
        return themeColours.isOn();
    }

    public ColorSetting routeColour() {
        return routeColour;
    }

    public boolean announce() {
        return notify.isOn();
    }
}
