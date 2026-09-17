package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandInfo;
import dev.px.core.util.text.ChatColor;

/**
 * Lists the registered commands.
 *
 * <p>Reads the registry rather than a hand-kept list, so a command written
 * tomorrow appears here without anyone remembering to add it.
 */
@CommandInfo(name = "help", aliases = {"?", "commands"}, description = "Lists every command")
public final class HelpCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        String prefix = Core.commands().getPrefix().get();

        context.reply(ChatColor.GRAY + Core.get().getClientName() + " " + Core.get().getClientVersion());

        for (Command command : Core.commands().all()) {
            String usage = command.getUsage().isEmpty() ? command.getName() : command.getUsage();
            context.reply(ChatColor.WHITE + prefix + usage
                    + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + command.getDescription());
        }
    }
}
