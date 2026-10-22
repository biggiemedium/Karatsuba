package dev.px.karatsuba.client.notify;

import dev.px.core.Core;
import dev.px.karatsuba.client.hud.element.NotificationElement;

/**
 * The client's way to post a notification: by source, so the player's choice of
 * which sources show is honoured before anything is posted ({@code hud/DESIGN.md}
 * §2.13b). A source switched off in the Notifications element posts nothing.
 *
 * <p>Posting straight to {@code Core.notifications()} bypasses the choice: only for
 * prompts the player must act on.
 */
public final class Notices {

    private Notices() {
    }

    public static void info(Source source, String title, String message) {
        if (shows(source)) {
            Core.notifications().info(title, message);
        }
    }

    public static void success(Source source, String title, String message) {
        if (shows(source)) {
            Core.notifications().success(title, message);
        }
    }

    public static void warn(Source source, String title, String message) {
        if (shows(source)) {
            Core.notifications().warn(title, message);
        }
    }

    public static void error(Source source, String title, String message) {
        if (shows(source)) {
            Core.notifications().error(title, message);
        }
    }

    /** @return whether notifications from {@code source} show: yes unless the Notifications element says no */
    public static boolean shows(Source source) {
        return Core.hud().find(NotificationElement.ID)
                .map(element -> !(element instanceof NotificationElement notifications) || notifications.shows(source))
                .orElse(true);
    }
}
