package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Content;
import dev.px.core.layout.Draw;
import dev.px.core.notification.Notification;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.hud.style.Style;
import dev.px.karatsuba.client.hud.style.StyledElement;

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
 * <p>A card per toast rather than one behind the queue ({@link #platesItself}), in
 * the style's plate, radius, padding, font and colours. The queue
 * eases to its new height as toasts arrive and leave, like any element.
 *
 * <p>A toast is a {@link Content#custom} part rather than a described box: it
 * slides horizontally out of its own rectangle and draws at an alpha the layout
 * knows nothing about, which is exactly what the escape hatch is for.
 */
public final class NotificationElement extends StyledElement {

    private static final float ACCENT_BAR = 2f;

    private final NumberSetting<Integer> width = integer("Width", 120, 80, 240)
            .describe("How wide each toast is");

    public NotificationElement() {
        super("notifications", "Notifications", HudLayout.at(Anchor.MIDDLE_RIGHT, -4f, 0f));
    }

    @Override
    protected boolean platesItself() {
        return true;
    }

    @Override
    protected void describe(Content body) {
        List<Notification> active = Core.notifications().getActive();
        if (active.isEmpty()) {
            if (isEditing()) {
                // A stable placeholder, so the queue can be moved while it is empty.
                body.custom(width.getInt(), rowHeight(), (x, y, w, h) -> placeholder(x, y, w, h));
            }
            return;
        }
        body.gap(style().gap());
        for (Notification notification : active) {
            body.custom(width.getInt(), rowHeight(), toast(notification));
        }
    }

    private Draw toast(Notification notification) {
        Style style = style();
        TextPainter painter = painter();
        boolean fromRight = Core.hud().layoutOf(this).getAnchor().isRight();
        return (x, y, w, height) -> {
            float presence = notification.getPresence().get();
            // Slides in from the side it is anchored to, so it never travels across the
            // screen to reach its resting place.
            float slide = (1f - presence) * 12f * (fromRight ? 1f : -1f);
            Render.pushAlpha(presence);
            try {
                float toastX = x + slide;
                Color accent = notification.getType().getColor();
                Render.roundRect(toastX, y, w, height, Math.min(style.radius(), height / 2f), style.plate());
                // A coloured edge rather than a coloured background: the type has to be
                // readable at a glance without the text losing contrast.
                Render.roundRect(toastX, y, ACCENT_BAR, height, 0f, accent);

                float pad = style.padding();
                float textX = toastX + ACCENT_BAR + pad;
                Font font = style.font();
                painter.draw(notification.getTitle(), textX, y + pad, accent);
                painter.draw(notification.getMessage(), textX, y + pad + font.getHeight() + 1f, style.value());

                // A countdown along the bottom, so a toast that is about to vanish says so.
                Render.rect(toastX, y + height - 1f, w * notification.getRemaining(), 1f, accent.withAlpha(140));
            } finally {
                Render.popAlpha();
            }
        };
    }

    private void placeholder(float x, float y, float w, float h) {
        Render.roundRect(x, y, w, h, Math.min(style().radius(), h / 2f), style().plate());
        Render.text(style().font(), "Notifications", x + style().padding(), y + style().padding(), style().muted());
    }

    private float rowHeight() {
        return style().font().getHeight() * 2f + style().padding() * 2f + 2f;
    }
}
