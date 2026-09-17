package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandException;
import dev.px.core.command.CommandInfo;
import dev.px.core.util.text.ChatColor;

import java.util.List;

/**
 * Saves, loads and lists config profiles from chat.
 *
 * <p>A profile is one JSON file holding every registered section: modules and
 * their settings, HUD layouts, the theme, keybinds, friends. Switching profiles is
 * therefore switching the whole client state at once, not just a colour scheme.
 */
@CommandInfo(
        name = "config",
        aliases = {"cfg", "profile"},
        description = "Saves, loads and lists config profiles",
        usage = "config <save|load|list|delete> [profile]")
public final class ConfigCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        String action = context.get(0).toLowerCase();

        switch (action) {
            case "list" -> list(context);
            case "save" -> save(context);
            case "load" -> load(context);
            case "delete" -> delete(context);
            default -> context.failUsage();
        }
    }

    private void list(CommandContext context) {
        List<String> profiles = Core.config().listProfiles();
        if (profiles.isEmpty()) {
            context.reply("No profiles saved yet");
            return;
        }
        context.reply(ChatColor.GRAY + "Profiles: " + ChatColor.WHITE + String.join(", ", profiles));
    }

    /** With no name, saves over the profile currently in use. */
    private void save(CommandContext context) {
        if (context.has(1)) {
            Core.config().saveAs(context.rest(1));
        } else {
            Core.config().save();
        }
        Core.notifications().success("Config", "Saved");
        context.reply("Saved");
    }

    private void load(CommandContext context) {
        String profile = requireProfile(context);
        Core.config().load(profile);
        Core.notifications().success("Config", "Loaded " + profile);
        context.reply("Loaded " + profile);
    }

    private void delete(CommandContext context) {
        String profile = requireProfile(context);
        if (!Core.config().delete(profile)) {
            throw new CommandException("Could not delete " + profile);
        }
        context.reply("Deleted " + profile);
    }

    private static String requireProfile(CommandContext context) {
        String profile = context.rest(1);
        if (!Core.config().exists(profile)) {
            throw new CommandException("No profile called " + profile);
        }
        return profile;
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        if (index == 0) {
            return List.of("save", "load", "list", "delete");
        }
        // Only the actions that take an existing profile should offer one.
        String action = context.get(0, "").toLowerCase();
        boolean takesProfile = action.equals("load") || action.equals("delete") || action.equals("save");

        return index == 1 && takesProfile ? Core.config().listProfiles() : List.of();
    }
}
