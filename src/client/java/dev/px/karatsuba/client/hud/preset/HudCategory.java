package dev.px.karatsuba.client.hud.preset;

import dev.px.core.notification.NotificationType;
import dev.px.core.render.Color;

/**
 * A small fixed set of kinds, shown by the categoryMarker role ({@code hud/DESIGN.md}
 * §2.13, §3.2): what a notification is about. Each preset draws them its own way:
 * Legacy by colour and shape, Apple by an icon.
 */
public enum HudCategory {
    INFO(NotificationType.INFO),
    SUCCESS(NotificationType.SUCCESS),
    WARNING(NotificationType.WARNING),
    ERROR(NotificationType.ERROR);

    private final NotificationType type;

    HudCategory(NotificationType type) {
        this.type = type;
    }

    /** @return Core's colour for this kind of notification: what Legacy has always drawn it in */
    Color coreColour() {
        return type.getColor();
    }

    /** @return the category of a notification's type */
    public static HudCategory of(NotificationType type) {
        for (HudCategory each : values()) {
            if (each.type == type) {
                return each;
            }
        }
        return INFO;
    }
}
