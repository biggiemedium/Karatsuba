package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Content;
import dev.px.core.layout.Draw;
import dev.px.core.notification.Notification;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.hud.preset.role.HudCategory;
import dev.px.karatsuba.client.hud.preset.HudPlate;
import dev.px.karatsuba.client.hud.preset.HudPreset;
import dev.px.karatsuba.client.hud.preset.HudTokens;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.notify.Source;

import java.util.ArrayList;
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
 * the preset's plate, and its fonts and colours by role. The queue
 * eases to its new height as toasts arrive and leave, like any element.
 *
 * <p>A toast is a {@link Content#custom} part rather than a described box: it
 * slides horizontally out of its own rectangle and draws at an alpha the layout
 * knows nothing about, which is exactly what the escape hatch is for.
 */
public final class NotificationElement extends StyledElement {

    /** The element's id: its config key, and how {@code Notices} finds it. */
    public static final String ID = "notifications";

    private final NumberSetting<Integer> width = integer("Width", 120, 80, 240)
            .describe("How wide each toast is");

    // Which sources post at all (hud/DESIGN.md §2.13b): all on until the player says otherwise.
    private final BooleanSetting moduleToggles = source(Source.MODULE_TOGGLES);
    private final BooleanSetting moduleMessages = source(Source.MODULE_MESSAGES);
    private final BooleanSetting tasks = source(Source.TASKS);
    private final BooleanSetting server = source(Source.SERVER);
    private final BooleanSetting client = source(Source.CLIENT);

    private final GroupSetting show = group("Show", true)
            .containing(moduleToggles, moduleMessages, tasks, server, client)
            .describe("Which notifications appear. One switched off is never posted");

    public NotificationElement() {
        super(ID, "Notifications", HudLayout.at(Anchor.MIDDLE_RIGHT, -4f, 0f));
    }

    private static BooleanSetting source(Source source) {
        return bool(source.label(), true).describe(source.description());
    }

    /** @return whether notifications from {@code source} show */
    public boolean shows(Source source) {
        return switch (source) {
            case MODULE_TOGGLES -> moduleToggles.isOn();
            case MODULE_MESSAGES -> moduleMessages.isOn();
            case TASKS -> tasks.isOn();
            case SERVER -> server.isOn();
            case CLIENT -> client.isOn();
        };
    }

    @Override
    protected boolean platesItself() {
        return true;
    }

    /**
     * One plate per toast, where each is drawn: a capsule, sliding and forming with
     * its toast, for a preset that draws its plates behind (glass). While editing
     * with no toasts, the placeholder's.
     */
    @Override
    public List<HudPlate> plates(float x, float y, float scale, float naturalW, float naturalH) {
        List<Notification> active = Core.notifications().getActive();
        if (active.isEmpty()) {
            return isEditing() ? List.of(new HudPlate(getId(), x, y, naturalW * scale, naturalH * scale, true,
                    lastFade(), 0f)) : List.of();
        }
        float side = Core.hud().layoutOf(this).getAnchor().isRight() ? 1f : -1f;
        float row = rowHeight();
        float step = row + tokens().gap();
        List<HudPlate> plates = new ArrayList<>(active.size());
        for (int i = 0; i < active.size(); i++) {
            // Toasts always move, whatever Motion says (§2.10), as the toasts themselves do.
            float presence = active.get(i).getPresence().get();
            float slide = preset().toastSlide(presence) * side;
            plates.add(new HudPlate(getId(), x + slide * scale, y + i * step * scale, width.getInt() * scale, row * scale,
                    true, presence * lastFade(), 0f));
        }
        return plates;
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
        body.gap(tokens().gap());
        for (Notification notification : active) {
            body.custom(width.getInt(), rowHeight(), toast(notification));
        }
    }

    private Draw toast(Notification notification) {
        HudTokens tokens = tokens();
        HudPreset preset = preset();
        TextPainter title = painter(TextRole.PRIMARY);
        // The message in primary text but the regular weight, as a system notification reads (§4.11b).
        TextPainter message = painter(TextRole.SECONDARY);
        HudCategory category = HudCategory.of(notification.getType());
        float side = Core.hud().layoutOf(this).getAnchor().isRight() ? 1f : -1f;
        return (x, y, w, height) -> {
            // Toasts always slide and fade, whatever Motion says, so none is missed (§2.10).
            float presence = notification.getPresence().get();
            // In from the side it is anchored to, so it never travels across the screen
            // to reach its place: Legacy's short slide, Apple's spring (§4.12).
            float slide = preset.toastSlide(presence) * side;
            Render.pushAlpha(presence);
            try {
                float toastX = x + slide;
                preset.drawPlate(toastX, y, w, height, tokens);
                // What kind of notification it is, as the preset shows a category: readable at a
                // glance without the text losing contrast, and never by colour alone (§2.13).
                float textX = toastX + preset.categoryMarker(toastX, y, height, tokens, category);

                float pad = tokens.padding();
                Font font = tokens.font(TextRole.PRIMARY);
                title.draw(notification.getTitle(), textX, y + pad, tokens.categoryTitle(category));
                message.draw(notification.getMessage(), textX, y + pad + font.getHeight() + 1f,
                        tokens.text(TextRole.PRIMARY));

                // How long it has left, for a preset that shows it.
                preset.countdown(toastX, y, w, height, notification.getRemaining(), tokens, category);
            } finally {
                Render.popAlpha();
            }
        };
    }

    private void placeholder(float x, float y, float w, float h) {
        preset().drawPlate(x, y, w, h, tokens());
        Render.text(tokens().font(TextRole.PRIMARY), "Notifications", x + tokens().padding(), y + tokens().padding(), tokens().text(TextRole.SECONDARY));
    }

    private float rowHeight() {
        return tokens().font(TextRole.PRIMARY).getHeight() * 2f + tokens().padding() * 2f + 2f;
    }
}
