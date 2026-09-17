package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandException;
import dev.px.core.command.CommandInfo;
import dev.px.core.module.Module;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Toggles a module by name.
 *
 * <p>There is no error plumbing here on purpose: {@code ctx.rest} validates and
 * throws {@link CommandException}, the registry turns that into a chat message,
 * and the happy path is the only path this method spells out.
 */
@CommandInfo(name = "toggle", aliases = {"t"}, description = "Toggles a module", usage = "toggle <module>")
public final class ToggleCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        String name = context.rest(0);

        Module module = Core.modules().find(name)
                .orElseThrow(() -> new CommandException("No module called " + name));

        module.toggle();
        context.reply(module.getName() + (module.isEnabled() ? " enabled" : " disabled"));
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        if (index != 0) {
            return List.of();
        }
        return Core.modules().all().stream()
                .map(Module::getName)
                .collect(Collectors.toList());
    }
}
