package dev.px.karatsuba.client.gui.apple.shell.widget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** When something happened, written the same way on every page: "3 h ago", then the date. */
public final class Times {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT);

    private Times() {
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago", then the date. */
    public static String ago(long epochMillis) {
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
        return date(epochMillis);
    }

    /** "3 May 2026". */
    public static String date(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
