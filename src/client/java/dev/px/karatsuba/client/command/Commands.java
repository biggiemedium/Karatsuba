package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;

/**
 * The command catalogue.
 *
 * <p>Same shape as {@link dev.px.karatsuba.client.module.Modules}, and in the same
 * place relative to what it lists: write the class beside this one, add a line
 * here. Nothing else registers commands, so this list is the whole answer to
 * "what can I type in chat".
 */
public final class Commands {

    private Commands() {
    }

    public static void registerAll(Core core) {
        core.getCommandRegistry().registerAll(all());
    }

    public static Command[] all() {
        return new Command[]{
                new HelpCommand(),
                new AccountCommand(),
                new ToggleCommand(),
                new ConfigCommand(),
                new FriendCommand(),
                new RecordCommand(),
        };
    }
}
