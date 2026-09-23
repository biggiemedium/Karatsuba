package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandInfo;
import dev.px.core.movement.timeline.TimelineEntry;
import dev.px.karatsuba.client.module.misc.RecorderModule;

import java.util.List;

/**
 * Starts, marks and stops movement recordings from chat.
 *
 * <p>A front end for {@link RecorderModule} rather than a second way to record:
 * starting and stopping toggle the module, so a keybind, the click GUI and this
 * command all agree on whether a recording is running.
 *
 * <p>Marks are the reason it exists. {@code .record mark jump} drops a named
 * point into the timeline, and {@code Timeline.between("jump", "landed")} slices
 * the recording between two of them afterwards &mdash; far easier than finding
 * the right tick by eye in a file of a hundred thousand lines.
 */
@CommandInfo(name = "record", aliases = {"rec"}, description = "Records movement to a timeline file",
        usage = "record <start|stop|mark|status> [label]")
public final class RecordCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        switch (context.get(0).toLowerCase()) {
            case "start" -> start(context);
            case "stop" -> stop(context);
            case "mark" -> mark(context);
            case "status" -> status(context);
            default -> context.failUsage();
        }
    }

    private void start(CommandContext context) {
        if (Core.timeline().isRecording()) {
            context.fail("Already recording; .record stop first");
        }
        recorder().start(context.has(1) ? context.rest(1) : null);
    }

    private void stop(CommandContext context) {
        if (!Core.timeline().isRecording()) {
            context.fail("Not recording");
        }
        recorder().disable();
    }

    private void mark(CommandContext context) {
        TimelineEntry mark = Core.timeline().mark(context.rest(1));
        if (mark == null) {
            context.fail("Not recording");
        }
        context.reply("Marked \"" + mark.getLabel() + "\" at tick " + mark.getTick());
    }

    private void status(CommandContext context) {
        context.reply((Core.timeline().isRecording() ? "Recording" : "Not recording")
                + "; " + RecorderModule.describe(Core.simulation().getDrift()));
    }

    private static RecorderModule recorder() {
        return Core.modules().require(RecorderModule.class);
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        return index == 0 ? List.of("start", "stop", "mark", "status") : List.of();
    }
}
