package dev.px.karatsuba.client.hud;

import dev.px.core.Core;
import dev.px.core.gui.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Content;
import dev.px.core.notification.Notification;
import dev.px.core.render.Color;
import dev.px.core.render.Render;

import java.util.List;

/**
 * Draws the toast queue.
 *
 * <p>Core owns a notification's lifetime, its fade and its dismissal; what it does
 * not own is what one looks like, so this is a loop over
 * {@code NotificationService.getActive()} that keeps no per-notification state of
 * its own. Each toast carries its own {@code presence} animation, which is what
 * drives the slide and the fade here.
 *
 * <p>Being a HUD element rather than a bare overlay means the queue can be moved,
 * scaled and re-anchored like anything else, for free.
 *
 * <p>A toast is a {@link Content#custom} part rather than a described box: it
 * slides horizontally out of its own rectangle and draws at an alpha the layout
 * knows nothing about, which is exactly what the escape hatch is for. The row
 * size it reports is still what Core measures, so the element as a whole sizes
 * and clamps itself normally.
 */
public final class NotificationElement extends AbstractHudElement {

    private static final float WIDTH = 120f;
    private static final float PADDING = 4f;
    private static final float SPACING = 2f;
    private static final float ACCENT_BAR = 2f;

    public NotificationElement() {
        super("notifications", "Notifications", HudLayout.at(Anchor.BOTTOM_RIGHT, -4f, -4f));
    }

    @Override
    public void content(Content content) {
        List<Notification> active = active();

        if (active.isEmpty()) {
            if (!isEditing()) {
                // No toasts and nobody positioning: no size, no clicks, nothing drawn.
                return;
            }
            // A stable placeholder, so the queue can be moved while it is empty.
            content.background(GuiStyle.surface(), GuiStyle.radius()).padding(PADDING);
            content.width(WIDTH);
            content.text("Notifications", GuiStyle.textMuted());
            return;
        }

        content.gap(SPACING);
        for (Notification notification : active) {
            content.custom(WIDTH, rowHeight(), (x, y, w, h) -> drawToast(notification, x, y, w, h));
        }
    }

    private void drawToast(Notification notification, float x, float y, float w, float height) {
        float presence = notification.getPresence().get();

        // Slides in from the side it is anchored to, so it never travels across the
        // screen to reach its resting place.
        boolean fromRight = Core.hud().layoutOf(this).getAnchor().isRight();
        float slide = (1f - presence) * 12f * (fromRight ? 1f : -1f);

        Render.pushAlpha(presence);
        try {
            float toastX = x + slide;
            Color accent = notification.getType().getColor();

            Render.roundRect(toastX, y, w, height, GuiStyle.radius(w, height), GuiStyle.surface());
            // A coloured edge rather than a coloured background: the type has to be
            // readable at a glance without the text losing contrast.
            Render.roundRect(toastX, y, ACCENT_BAR, height, 0f, accent);

            float textX = toastX + ACCENT_BAR + PADDING;
            Render.text(notification.getTitle(), textX, y + PADDING, accent);
            Render.text(notification.getMessage(), textX, y + PADDING + Render.textHeight() + 1f,
                    GuiStyle.text());

            // A countdown along the bottom, so a toast that is about to vanish says so.
            Render.rect(toastX, y + height - 1f, w * notification.getRemaining(), 1f,
                    accent.withAlpha(140));
        } finally {
            Render.popAlpha();
        }
    }

    private static List<Notification> active() {
        return Core.notifications().getActive();
    }

    private static float rowHeight() {
        return Render.textHeight() * 2f + PADDING * 2f + 2f;
    }
}
