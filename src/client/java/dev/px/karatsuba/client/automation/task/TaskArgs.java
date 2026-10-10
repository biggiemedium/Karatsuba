package dev.px.karatsuba.client.automation.task;

import dev.px.core.command.CommandContext;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * A task's arguments, as typed after its name: {@code .task goto 100 64 -20}
 * hands {@link TaskType} the {@code 100 64 -20}, counted from 0.
 *
 * <p>Wraps the command's context, so a missing or malformed argument fails the
 * command with a message, and adds what tasks share: coordinates, with {@code ~}
 * meaning where the player stands as in vanilla's commands.
 */
public final class TaskArgs {

    private final CommandContext context;
    private final int offset;

    /** @param offset the context's index of this task's first argument */
    public TaskArgs(CommandContext context, int offset) {
        this.context = context;
        this.offset = offset;
    }

    public int size() {
        return Math.max(0, context.size() - offset);
    }

    public boolean has(int index) {
        return context.has(index + offset);
    }

    /** @throws dev.px.core.command.CommandException if it is missing */
    public String get(int index) {
        return context.get(index + offset);
    }

    public String get(int index, String fallback) {
        return context.get(index + offset, fallback);
    }

    /** @throws dev.px.core.command.CommandException if it is missing or not a number */
    public double number(int index) {
        return context.getDouble(index + offset);
    }

    /** @return the argument as a number, or {@code fallback} when it is not there */
    public double number(int index, double fallback) {
        return has(index) ? number(index) : fallback;
    }

    /** @return the argument as a block coordinate, {@code ~} counting from {@code here} */
    public int coordinate(int index, int here) {
        String value = get(index);
        boolean relative = value.startsWith("~");
        String number = relative ? value.substring(1) : value;
        if (relative && number.isEmpty()) {
            return here;
        }
        try {
            int parsed = (int) Math.floor(Double.parseDouble(number));
            return relative ? here + parsed : parsed;
        } catch (NumberFormatException e) {
            fail(value + " is not a coordinate");
            return 0;
        }
    }

    /** @return the block named by three coordinates from {@code index} */
    public Vec3i block(int index) {
        Vec3i at = Players.blockPosition();
        return Vec3i.of(coordinate(index, at.getX()), coordinate(index + 1, at.getY()),
                coordinate(index + 2, at.getZ()));
    }

    /** Fails the command with {@code message}. Never returns. */
    public void fail(String message) {
        context.fail(message);
    }
}
