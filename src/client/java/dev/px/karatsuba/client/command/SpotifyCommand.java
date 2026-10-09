package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandException;
import dev.px.core.command.CommandInfo;
import dev.px.core.integration.MediaTrack;
import dev.px.core.util.text.ChatColor;
import dev.px.karatsuba.adapter.integration.Spotify;

import java.util.List;

/**
 * Drives Spotify from chat: skip, go back, pause.
 *
 * <p>Connecting is not here. It is all on the Spotify page of Settings, Client ID
 * to sign-in, so there is one way to do it; this is for the controls, which are
 * quicker typed than reached through a screen.
 */
@CommandInfo(
        name = "spotify",
        aliases = {"music"},
        description = "Skips, goes back or pauses what Spotify is playing",
        usage = "spotify [status|next|prev|pause]")
public final class SpotifyCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        switch (context.get(0, "status").toLowerCase()) {
            case "status" -> status(context);
            case "next", "skip" -> playing(context, Spotify::next, "Skipping");
            case "prev", "previous", "back" -> playing(context, Spotify::previous, "Going back");
            case "pause", "play", "toggle" -> playing(context, Spotify::togglePlayback, "Toggling playback");
            default -> context.failUsage();
        }
    }

    private void status(CommandContext context) {
        if (Spotify.isConnecting()) {
            context.reply("Waiting for you to sign in in the browser");
            return;
        }
        if (!Spotify.isConnected()) {
            context.reply("Spotify is not connected. " + ChatColor.GRAY + "Connect it in Settings > Spotify");
            return;
        }
        if (!Core.integrations().getMediaEnabled().isOn()) {
            context.reply("Spotify is connected. " + ChatColor.GRAY
                    + "Switch on Now Playing in Settings > Spotify to see what is playing");
            return;
        }
        MediaTrack track = Spotify.nowPlaying();
        context.reply(track == null
                ? "Spotify is connected. " + ChatColor.GRAY + "Nothing is playing"
                : (track.isPlaying() ? "Playing " : "Paused on ") + ChatColor.WHITE + track.getLabel());
    }

    private void playing(CommandContext context, Runnable action, String doing) {
        if (!Spotify.isConnected()) {
            throw new CommandException("Spotify is not connected. Connect it in Settings > Spotify");
        }
        action.run();
        context.reply(doing + "...");
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        return index == 0 ? List.of("status", "next", "prev", "pause") : List.of();
    }
}
