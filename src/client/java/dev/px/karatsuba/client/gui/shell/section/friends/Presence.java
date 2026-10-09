package dev.px.karatsuba.client.gui.shell.section.friends;

import dev.px.core.Core;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.client.social.FriendRecords;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Who is where, worked out once a frame from the server's player list: online on
 * this server, near enough to be loaded, or neither, and when they were last seen.
 * Always said in words as well as shown with a dot, so the state never rests on
 * colour alone.
 */
final class Presence {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT);

    private final Set<String> online = new HashSet<>();
    private final List<String> onlineNames = new ArrayList<>();

    /** Reads the player list. Once a frame. */
    void refresh() {
        online.clear();
        onlineNames.clear();
        for (String name : PlayerList.names()) {
            online.add(name.toLowerCase(Locale.ROOT));
            onlineNames.add(name);
        }
    }

    boolean isOnline(String name) {
        return online.contains(name.toLowerCase(Locale.ROOT));
    }

    /** @return everyone on the server's list but you, as they are spelled there */
    List<String> onlineNames() {
        return onlineNames;
    }

    /** @return a few words for a list row: "12 m away", "Online", "3 h ago", "Offline" */
    String brief(String name) {
        if (isOnline(name)) {
            float distance = PlayerList.distanceTo(name);
            return distance >= 0f ? Math.round(distance) + " m away" : "Online";
        }
        long seen = FriendRecords.of(name).lastSeen();
        // Under the Offline heading, "Offline" on every row would only repeat it.
        return seen > 0L ? ago(seen) : "";
    }

    /** @return a sentence for the inspector: where they are, or where they were last seen */
    String full(String name) {
        if (isOnline(name)) {
            float distance = PlayerList.distanceTo(name);
            String host = Core.server().getHost();
            String where = host == null || host.isEmpty() ? "this server" : host;
            return distance >= 0f
                    ? "Online, " + Math.round(distance) + " blocks away"
                    : "Online on " + where;
        }
        return "Offline";
    }

    /** @return when and where a friend was last seen, or that they never have been */
    static String lastSeen(String name) {
        FriendRecords.Record record = FriendRecords.of(name);
        if (record.lastSeen() <= 0L) {
            return "Not yet";
        }
        String where = record.lastServer().isEmpty() ? "" : " on " + record.lastServer();
        return ago(record.lastSeen()) + where;
    }

    /** @return the date a friend was added, or "Before this was recorded" */
    static String added(String name) {
        long added = FriendRecords.of(name).added();
        return added <= 0L ? "Before this was recorded"
                : DATE.format(Instant.ofEpochMilli(added).atZone(ZoneId.systemDefault()));
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago", then the date. */
    static String ago(long epochMillis) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - epochMillis) / 1000L);
        if (seconds < 60L) {
            return "just now";
        }
        if (seconds < 3600L) {
            return seconds / 60L + " min ago";
        }
        if (seconds < 86400L) {
            return seconds / 3600L + " h ago";
        }
        long days = seconds / 86400L;
        if (days < 30L) {
            return days == 1L ? "yesterday" : days + " days ago";
        }
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
