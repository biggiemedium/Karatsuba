package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandException;
import dev.px.core.command.CommandInfo;
import dev.px.core.util.text.ChatColor;
import dev.px.karatsuba.client.config.ProfilePart;
import dev.px.karatsuba.client.config.Profiles;

import java.util.EnumSet;
import java.util.List;

/**
 * Saves, loads and lists config profiles from chat.
 *
 * <p>A profile is a folder of config sections that switch together: modules and
 * their settings, HUD layouts, the theme, keybinds. It may include only some of
 * them, the rest coming from Default; which, is chosen on the GUI's Profiles page.
 * A profile saved from here includes everything. Everything goes through
 * {@link Profiles}, so a switch never loses what changed on the profile it leaves.
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
        StringBuilder out = new StringBuilder();
        for (String profile : Profiles.all()) {
            if (out.length() > 0) {
                out.append(ChatColor.GRAY).append(", ");
            }
            out.append(Profiles.isActive(profile) ? ChatColor.WHITE + profile + " (in use)" : ChatColor.WHITE + profile);
        }
        context.reply(ChatColor.GRAY + "Profiles: " + out);
    }

    /**
     * With no name, saves the profile in use. With the name of a profile, saves what
     * is on now over it and switches to it; with a new name, creates a profile of
     * everything.
     */
    private void save(CommandContext context) {
        if (!context.has(1)) {
            Core.config().save();
            context.reply("Saved " + Profiles.active());
            return;
        }
        String name = context.rest(1);
        String existing = Profiles.find(name);
        if (existing != null) {
            Core.config().saveAs(existing);
            context.reply("Saved over " + existing);
            return;
        }
        String problem = Profiles.problem(name);
        if (problem != null) {
            throw new CommandException(problem);
        }
        context.reply("Created " + Profiles.create(name, EnumSet.allOf(ProfilePart.class)));
    }

    private void load(CommandContext context) {
        String profile = requireProfile(context);
        Profiles.load(profile);
        context.reply("Switched to " + profile);
    }

    private void delete(CommandContext context) {
        String profile = requireProfile(context);
        if (Profiles.isDefault(profile)) {
            throw new CommandException("Default cannot be deleted: other profiles take what they leave out from it");
        }
        if (Profiles.delete(profile) == null) {
            throw new CommandException("Could not delete " + profile);
        }
        context.reply("Deleted " + profile + " (kept in configs/deleted until another of that name is deleted)");
    }

    private static String requireProfile(CommandContext context) {
        String profile = Profiles.find(context.rest(1));
        if (profile == null) {
            throw new CommandException("No profile called " + context.rest(1));
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

        return index == 1 && takesProfile ? Profiles.all() : List.of();
    }
}
