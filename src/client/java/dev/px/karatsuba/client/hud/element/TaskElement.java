package dev.px.karatsuba.client.hud.element;

import dev.px.core.flow.FlowHandle;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.navigation.Progress;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.automation.Automation;
import dev.px.karatsuba.client.automation.task.Task;
import dev.px.karatsuba.client.hud.preset.ColorRole;
import dev.px.karatsuba.client.hud.preset.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.automation.navigation.Trip;

import java.util.Locale;

/**
 * The running task: what it is, what it is doing now, and how far it has left.
 *
 * <p>Shows nothing while no task runs. A paused or stuck task says so, in the
 * "fair" colour and in words, with the reason, so it reads the same without colour.
 */
public final class TaskElement extends StyledElement {

    private final BooleanSetting step = bool("Step", true)
            .describe("Show the step the task is on, innermost last");

    private final BooleanSetting remaining = bool("Remaining", true)
            .describe("Show the blocks left to the goal, at least");

    public TaskElement() {
        super("task", "Task", HudLayout.at(Anchor.TOP_CENTER, 0f, 4f));
    }

    @Override
    protected void describe(Content body) {
        if (isEditing()) {
            sample(body);
            return;
        }
        Automation automation = Automation.get();
        Task task = automation.getTask();
        FlowHandle flow = automation.getFlow();
        if (task == null || flow == null) {
            return;
        }
        Progress progress = progress(automation);
        boolean paused = flow.getState() == FlowHandle.State.PAUSED;
        boolean stuck = progress != null && progress.isStuck();
        watch(paused || stuck);

        row(body, task.getName(), task.describe(), tokens().text(TextRole.VALUE));
        if (paused || stuck) {
            String why = paused ? flow.getReason() : progress.getReason();
            row(body, paused ? "Paused" : "Stuck", why == null ? "" : why, tokens().color(ColorRole.FAIR));
        } else if (step.isOn()) {
            row(body, "Doing", flow.view().getRoot().describe(), tokens().text(TextRole.SECONDARY));
        }
        if (remaining.isOn() && progress != null && !Double.isNaN(progress.getRemaining())) {
            row(body, "Left", String.format(Locale.ROOT, "%.1f blocks", progress.getRemaining()), tokens().text(TextRole.VALUE));
        }
    }

    private void sample(Content body) {
        row(body, "Goto", "to 100, 64, -20", tokens().text(TextRole.VALUE));
        if (step.isOn()) {
            row(body, "Doing", "walk to 100, 64, -20", tokens().text(TextRole.SECONDARY));
        }
        if (remaining.isOn()) {
            row(body, "Left", "42.5 blocks", tokens().text(TextRole.VALUE));
        }
    }

    /** @return the progress of the trip under way, or null when the task is not travelling */
    private static Progress progress(Automation automation) {
        for (Trip trip : automation.getNavigation().getActiveTrips()) {
            if (trip.getProgress() != null) {
                return trip.getProgress();
            }
        }
        return null;
    }

    private void row(Content body, String label, String value, Color color) {
        body.row(row -> {
            row.align(Align.CENTER);
            label(row, label);
            row.space(LABEL_GAP, 0f);
            text(row, value, color);
        });
    }
}
