package dev.px.karatsuba.client.command;

import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandInfo;
import dev.px.core.flow.FlowHandle;
import dev.px.core.flow.view.FlowView;
import dev.px.core.navigation.Progress;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.Automation;
import dev.px.karatsuba.client.automation.task.Task;
import dev.px.karatsuba.client.automation.task.TaskArgs;
import dev.px.karatsuba.client.automation.task.TaskType;
import dev.px.karatsuba.client.automation.task.Tasks;
import dev.px.karatsuba.client.automation.navigation.Trip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tasks from chat: starting one, and showing, stopping, pausing or resuming the
 * one running.
 *
 * <pre>
 * .task goto 100 64 -20       start a task: any kind {@code .task list} shows
 * .task                        what is running, and how it is going
 * .task stop | pause | resume
 * </pre>
 *
 * <p>Knows no task by name: every kind comes from {@link Tasks}, with its own
 * usage and its own arguments, so a new kind needs no change here.
 */
@CommandInfo(name = "task", aliases = {"t", "auto"}, description = "Starts, shows, stops, pauses or resumes a task",
        usage = "task [<kind> args | list | status | stop | pause | resume]")
public final class TaskCommand extends Command {

    private static final List<String> CONTROLS = List.of("list", "status", "stop", "pause", "resume");

    @Override
    public void execute(CommandContext context) {
        Automation automation = Automation.get();
        String first = context.has(0) ? context.word(0) : "status";
        switch (first) {
            case "list" -> list(context);
            case "status" -> status(context, automation);
            case "stop", "cancel" -> {
                if (!automation.stop("by command")) {
                    context.fail("No task is running");
                }
                context.reply("Stopped");
            }
            case "pause" -> {
                if (!automation.pause()) {
                    context.fail(automation.isActive() ? "The task is already paused" : "No task is running");
                }
                context.reply("Paused");
            }
            case "resume" -> {
                if (!automation.resume()) {
                    context.fail(automation.isActive() ? "The task is not paused" : "No task is running");
                }
                context.reply("Resumed");
            }
            default -> start(context, automation, first);
        }
    }

    private static void start(CommandContext context, Automation automation, String kind) {
        TaskType type = Tasks.find(kind);
        if (type == null) {
            context.fail("No task called " + kind + "; .task list shows them");
        }
        if (!Players.present()) {
            context.fail("Not in a world");
        }
        Task task = type.create(new TaskArgs(context, 1));
        automation.run(task);
        context.reply(task.getName() + " " + task.describe());
    }

    private static void list(CommandContext context) {
        for (TaskType type : Tasks.all()) {
            String usage = type.getUsage().isEmpty() ? "" : " " + type.getUsage();
            context.reply(type.getName() + usage + " - " + type.getDescription());
        }
    }

    private static void status(CommandContext context, Automation automation) {
        Task task = automation.getTask();
        FlowHandle flow = automation.getFlow();
        if (task == null || flow == null) {
            context.reply("No task is running");
            return;
        }
        FlowView view = flow.view();
        context.reply(task.getName() + " " + task.describe() + " ("
                + flow.getState().name().toLowerCase(Locale.ROOT) + ")");
        context.reply("Doing: " + view.getRoot().describe());
        if (view.getReason() != null) {
            context.reply("Why: " + view.getReason());
        }
        for (Trip trip : automation.getNavigation().getActiveTrips()) {
            Progress progress = trip.getProgress();
            if (progress != null) {
                context.reply("Route: " + progress + ", " + trip.getStats());
            }
        }
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        if (index == 0) {
            List<String> words = new ArrayList<>(CONTROLS);
            for (TaskType type : Tasks.all()) {
                words.add(type.getName());
            }
            return words;
        }
        TaskType type = context.has(0) ? Tasks.find(context.get(0)) : null;
        return type == null ? List.of() : type.complete(index - 1);
    }
}
