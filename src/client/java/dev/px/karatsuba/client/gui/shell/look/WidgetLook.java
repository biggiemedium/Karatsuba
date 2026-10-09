package dev.px.karatsuba.client.gui.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.shell.widget.Description;
import dev.px.karatsuba.client.gui.shell.widget.InspectorHeader;
import dev.px.karatsuba.client.gui.shell.widget.ListItem;
import dev.px.karatsuba.client.gui.shell.widget.FieldMessage;
import dev.px.karatsuba.client.gui.shell.widget.ListHeading;
import dev.px.karatsuba.client.gui.shell.widget.NavItem;
import dev.px.karatsuba.client.gui.shell.widget.PageIntro;
import dev.px.karatsuba.client.gui.shell.widget.PillButton;
import dev.px.karatsuba.client.gui.shell.widget.SectionHeader;
import dev.px.karatsuba.client.gui.shell.widget.SectionTitle;

import static dev.px.karatsuba.client.gui.shell.look.Shapes.chevron;
import static dev.px.karatsuba.client.gui.shell.look.Shapes.toggle;
import static dev.px.karatsuba.client.gui.shell.style.Palette.*;

/**
 * The widgets every section shares: the title row, list items, the inspector's
 * heading, group headings and buttons. One look for them all is what makes the
 * sections feel like one GUI.
 */
final class WidgetLook {

    private WidgetLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(SectionTitle.class, (title, c) -> c.align(Align.STRETCH).row(r -> r
                .height(TITLE_HEIGHT).padding(INSET, 0f, 2f, 0f).gap(6f).align(Align.CENTER)
                .text(title(), title.title(), TEXT)
                .fill()
                .text(small(), title.caption(), TEXT_FAINT))));

        look.register(WidgetRenderer.of(ListItem.class, (row, c) -> {
            boolean selected = row.isSelected();
            float hover = row.hover();
            float knob = row.knob();
            String caption = row.caption();
            c.align(Align.STRETCH).row(r -> {
                r.height(ROW_HEIGHT + 2f).padding(10f, 0f, INSET, 0f).gap(6f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> {
                            Render.roundRect(x, y, w, h, RADIUS_CONTROL, selected ? RAISED : faded(HOVER, hover));
                            if (knob > 0.01f) {
                                float bar = (h - 10f) * knob;
                                Render.roundRect(x, y + (h - bar) / 2f, 2f, bar, 1f, accent());
                            }
                        })
                        .text(body(), row.name(), TEXT_MUTED.lerp(TEXT, selected ? 1f : knob))
                        .fill();
                if (!caption.isEmpty()) {
                    r.text(small(), caption, TEXT_FAINT);
                }
                r.custom("switch", 18f, 10f, (x, y, w, h) -> toggle(x, y, w, h, knob));
            });
        }));

        look.register(WidgetRenderer.of(InspectorHeader.class, (header, c) -> {
            float knob = header.knob();
            // The word beside the switch goes first when the pane is too narrow for it.
            Bounds last = header.getBounds();
            boolean roomy = last == null || last.getWidth() <= 0f
                    || last.getWidth() > title().widthOf(header.name()) + 100f;
            c.align(Align.STRETCH).row(r -> {
                r.height(TITLE_HEIGHT).align(Align.CENTER).padding(0f, 0f, 0f, 2f)
                        .text(title(), header.name(), TEXT)
                        .fill();
                if (roomy) {
                    r.text(small(), header.state(), TEXT_FAINT.lerp(TEXT_MUTED, knob)).space(8f, 0f);
                }
                r.custom("switch", 24f, 13f, (x, y, w, h) -> toggle(x, y, w, h, knob));
            });
        }));

        look.register(WidgetRenderer.of(NavItem.class, (item, c) -> {
            boolean selected = item.isSelected();
            float hover = item.hover();
            float chosen = item.chosen();
            String caption = item.caption();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT).padding(INSET, 0f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover));
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(RAISED, chosen));
                        float bar = (h - 10f) * chosen;
                        if (bar > 0.5f) {
                            Render.roundRect(x, y + (h - bar) / 2f, 2f, bar, 1f, accent());
                        }
                    })
                    .text(selected ? strong() : body(), item.name(), TEXT_MUTED.lerp(TEXT, chosen))
                    .fill()
                    .text(small(), caption, accent()));
        }));

        look.register(WidgetRenderer.of(ListHeading.class, (heading, c) -> c.align(Align.STRETCH).row(r -> r
                .height(ROW_HEIGHT - 4f).padding(6f, 0f).gap(6f).align(Align.CENTER)
                .text(small(), heading.title().toUpperCase(java.util.Locale.ROOT), TEXT_FAINT)
                .text(small(), heading.count(), TEXT_FAINT))));

        // An error says so twice: in amber, and with a cross before it.
        look.register(WidgetRenderer.of(FieldMessage.class, (message, c) -> {
            boolean error = message.kind() == FieldMessage.Kind.ERROR;
            // Wrapped here against the width the line had last frame, not left to a
            // paragraph: a widget's natural width is measured unwrapped, and a long
            // message would otherwise widen the whole column it sits in.
            float left = error ? 16f : 4f;
            var last = message.getBounds();
            float room = (last != null && last.getWidth() > 0f ? last.getWidth() : 180f) - left - 4f;
            java.util.List<String> lines = Shapes.wrap(message.text(), small(), room);
            float line = small().getLineHeight();
            dev.px.core.render.Color ink = error ? WARN : TEXT_MUTED;
            c.align(Align.STRETCH).padding(left, 3f, 4f, 0f)
                    .backdrop((x, y, w, h) -> {
                        if (error) {
                            Glyphs.draw(dev.px.karatsuba.client.gui.shell.rail.Glyph.REMOVE, x + 5f, y + 4f, 7f, WARN);
                        }
                    })
                    .custom(0f, lines.size() * line, (x, y, w, h) -> {
                        for (int i = 0; i < lines.size(); i++) {
                            Render.text(small(), lines.get(i), x, y + i * line, ink);
                        }
                    });
        }));

        look.register(WidgetRenderer.of(PageIntro.class, (intro, c) -> c
                .padding(INSET, 0f).paragraph(body(), intro.getText(), TEXT_MUTED)));

        look.register(WidgetRenderer.of(Description.class, (description, c) -> c
                .paragraph(body(), description.getText(), TEXT_MUTED)));

        look.register(WidgetRenderer.of(SectionHeader.class, (header, c) -> {
            float hover = header.hover();
            float open = header.openness();
            c.align(Align.STRETCH).row(r -> {
                r.height(ROW_HEIGHT - 2f).padding(INSET, 0f).gap(6f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                        .text(strong(), header.title(), TEXT)
                        .fill();
                if (header.folds()) {
                    if (!header.isOpen()) {
                        int count = header.count();
                        r.text(small(), count + (count == 1 ? " setting" : " settings"), TEXT_FAINT);
                    }
                    r.custom(7f, 7f, (x, y, w, h) -> chevron(x, y, w, h, open, TEXT_MUTED));
                }
            });
        }));

        look.register(WidgetRenderer.of(PillButton.class, (button, c) -> {
            float hover = button.hover();
            float on = button.on();
            boolean enabled = button.isEnabled();
            PillButton.Style style = button.style();
            Color fill;
            Color ink;
            if (style == PillButton.Style.PRIMARY) {
                fill = accent().lerp(Color.WHITE, hover * 0.12f);
                ink = onAccent(accent());
            } else {
                fill = faded(RAISED, 1f).lerp(RAISED_HIGH, hover).lerp(accentTint(70), on);
                ink = !enabled ? TEXT_FAINT : TEXT_MUTED.lerp(TEXT, Math.max(hover, on));
            }
            Color glyphInk = style == PillButton.Style.TOGGLE ? ink.lerp(accent(), on) : ink;
            // Centred in whatever width it is given: its own in a toolbar, a whole column under a list.
            c.align(Align.STRETCH).row(r -> {
                r.height(ROW_HEIGHT).padding(button.iconOnly() ? 5f : 8f, 0f).gap(5f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, button.isRounded() ? h / 2f : RADIUS_CONTROL,
                                enabled ? fill : faded(fill, 0.5f)))
                        .fill()
                        .custom(10f, 10f, (x, y, w, h) -> Glyphs.draw(button.glyph(), x, y, w, glyphInk));
                if (!button.iconOnly()) {
                    r.text(small(), button.label(), ink);
                }
                r.fill();
            });
        }));
    }
}
