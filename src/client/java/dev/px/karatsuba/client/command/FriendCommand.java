package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandInfo;
import dev.px.core.social.Friend;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the friends list.
 *
 * <p>The worked example for a <b>command with subcommands</b>, and for how little
 * of one is error handling. {@code ctx.get(i)} throws when an argument is missing,
 * {@code ctx.failUsage()} throws the usage line from {@link CommandInfo}, and the
 * registry turns either into a chat message &mdash; so there is no {@code if
 * (args.length < 2)} anywhere below and no message text repeated in three branches.
 *
 * <p>The friends list itself is Core's. {@code SocialService} owns it, persists it
 * through the ordinary config, and is what targeting, nametags and chat consult, so
 * this command is a view onto it rather than a second copy of it.
 */
@CommandInfo(name = "friend", aliases = {"f"}, description = "Adds, removes or lists friends",
        usage = "friend <add|remove|list> [name]")
public final class FriendCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        switch (context.get(0).toLowerCase()) {
            case "add" -> add(context);
            case "remove", "rem", "del" -> remove(context);
            case "list" -> list(context);
            // Anything else is a usage error, and the usage line is already written
            // on the annotation; repeating it here is how the two drift apart.
            default -> context.failUsage();
        }
    }

    private void add(CommandContext context) {
        String name = context.get(1);
        if (Core.social().isFriend(name)) {
            context.fail(name + " is already a friend");
        }
        Friend friend = Core.social().add(name);
        context.reply("Added " + friend.getName());
    }

    private void remove(CommandContext context) {
        String name = context.get(1);
        if (!Core.social().remove(name)) {
            context.fail(name + " is not a friend");
        }
        context.reply("Removed " + name);
    }

    private void list(CommandContext context) {
        List<String> names = Core.social().getFriends().all().stream()
                .map(Friend::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        context.reply(names.isEmpty()
                ? "No friends yet"
                : names.size() + " friends: " + String.join(", ", names));
    }

    /**
     * Completion, per argument position.
     *
     * <p>Position 1 completes differently per subcommand: there is no point
     * offering existing friends to {@code add}, and no point offering anything else
     * to {@code remove}.
     */
    @Override
    public List<String> complete(CommandContext context, int index) {
        if (index == 0) {
            return List.of("add", "remove", "list");
        }
        if (index == 1 && context.get(0, "").equalsIgnoreCase("remove")) {
            return Core.social().getFriends().all().stream()
                    .map(Friend::getName)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
