package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.widget.Description;
import dev.px.karatsuba.client.gui.apple.shell.widget.EmptyState;
import dev.px.karatsuba.client.gui.apple.shell.widget.InspectorHeader;
import dev.px.karatsuba.client.gui.apple.shell.widget.ListItem;
import dev.px.karatsuba.client.gui.apple.shell.widget.FieldMessage;
import dev.px.karatsuba.client.gui.apple.shell.widget.ListHeading;
import dev.px.karatsuba.client.gui.apple.shell.widget.NavItem;
import dev.px.karatsuba.client.gui.apple.shell.widget.PageIntro;
import dev.px.karatsuba.client.gui.apple.shell.widget.SectionHeader;
import dev.px.karatsuba.client.gui.apple.shell.widget.SectionTitle;
import dev.px.karatsuba.client.gui.apple.shell.widget.UndoBar;

import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.chevron;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.lit;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.raised;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.toggle;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.trailing;
import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * The widgets every section shares: the title row, list items, the inspector's
 * heading, group headings and buttons. One look for them all is what makes the
 * sections feel like one GUI.
 */
final class WidgetLook {

    private WidgetLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // The header carries the page title now (gui/apple/DESIGN.md §2.3), so a
        // section's own title row takes no room until its section is rebuilt.
        look.register(WidgetRenderer.of(SectionTitle.class, (title, c) -> c.min(0f, 0f)));

        look.register(WidgetRenderer.of(ListItem.class, (row, c) -> {
            boolean selected = row.isSelected();
            float hover = row.hover();
            float knob = row.knob();
            String caption = row.caption();
            // The switch alone says on or off; the row open in the detail pane gets a
            // neutral highlight, never the accent (step 8d).
            c.align(Align.STRETCH).row(r -> {
                r.height(CELL_HEIGHT).padding(CELL_INSET, 0f).gap(6f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> {
                            if (selected) {
                                Render.roundRect(x, y, w, h, R_BUTTON, SELECTED.get());
                            } else if (hover > 0.01f) {
                                Render.roundRect(x, y, w, h, R_BUTTON, faded(HOVER_TINT.get(), hover));
                            }
                        })
                        .text(body(), row.name(), LABEL.get())
                        .fill();
                if (!caption.isEmpty()) {
                    r.text(small(), caption, LABEL_TERTIARY.get());
                }
                r.custom("switch", SWITCH_WIDTH, SWITCH_HEIGHT, (x, y, w, h) -> toggle(x, y, w, h, knob));
            });
        }));

        // The name and its switch; the switch says on or off itself, so no word beside it (step 8b).
        look.register(WidgetRenderer.of(InspectorHeader.class, (header, c) -> {
            float knob = header.knob();
            c.align(Align.STRETCH).row(r -> r
                    .height(TITLE_HEIGHT).align(Align.CENTER).padding(0f, 0f, 0f, 2f)
                    .text(title(), header.name(), LABEL.get())
                    .fill()
                    .custom("switch", SWITCH_WIDTH, SWITCH_HEIGHT, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(NavItem.class, (item, c) -> {
            boolean selected = item.isSelected();
            float hover = item.hover();
            float chosen = item.chosen();
            String caption = item.caption();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT).padding(INSET, 0f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER_TINT.get(), hover));
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(SELECTED.get(), chosen));
                        float bar = (h - 10f) * chosen;
                        if (bar > 0.5f) {
                            Render.roundRect(x, y + (h - bar) / 2f, 2f, bar, 1f, accent());
                        }
                    })
                    .text(selected ? strong() : body(), item.name(), LABEL_SECONDARY.get().lerp(LABEL.get(), chosen))
                    .fill()
                    .text(small(), caption, accent()));
        }));

        look.register(WidgetRenderer.of(ListHeading.class, (heading, c) -> c.align(Align.STRETCH).row(r -> r
                .height(ROW_HEIGHT - 4f).padding(6f, 0f).gap(6f).align(Align.CENTER)
                .text(small(), heading.title().toUpperCase(java.util.Locale.ROOT), LABEL_TERTIARY.get())
                .text(small(), heading.count(), LABEL_TERTIARY.get()))));

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
            dev.px.core.render.Color ink = error ? WARN : LABEL_SECONDARY.get();
            c.align(Align.STRETCH).padding(left, 3f, 4f, 0f)
                    .backdrop((x, y, w, h) -> {
                        if (error) {
                            Glyphs.draw(dev.px.karatsuba.client.gui.apple.shell.rail.Glyph.REMOVE, x + 5f, y + 4f, 7f, WARN);
                        }
                    })
                    .custom(0f, lines.size() * line, (x, y, w, h) -> {
                        for (int i = 0; i < lines.size(); i++) {
                            Render.text(small(), lines.get(i), x, y + i * line, ink);
                        }
                    });
        }));

        // Centred: an icon, a short title, one sentence, and one primary button (step 8d).
        look.register(WidgetRenderer.of(EmptyState.class, (empty, c) -> {
            Color fill = accentControl();
            c.align(Align.STRETCH).column(outer -> outer.align(Align.CENTER).padding(0f, 48f, 0f, 48f)
                    .column(inner -> inner.width(EMPTY_WIDTH).align(Align.CENTER).gap(6f)
                            .custom(28f, 28f, (x, y, w, h) -> Glyphs.draw(empty.glyph(), x, y, w, LABEL_TERTIARY.get()))
                            .space(0f, 4f)
                            .text(textStrong(), empty.title(), LABEL.get())
                            .paragraph(text(), empty.sentence(), LABEL_SECONDARY.get())
                            .space(0f, 6f)
                            .row(button -> button.name("button").height(BUTTON_HEIGHT).padding(12f, 0f).align(Align.CENTER)
                                    .backdrop((x, y, w, h) -> {
                                        Render.roundRect(x, y + 0.75f, w, h, R_BUTTON, SHADOW);
                                        Render.roundRect(x, y, w, h, R_BUTTON, fill);
                                    })
                                    .text(text(), empty.button(), Color.WHITE))));
        }));

        // "Removed Steve · Undo": a solid strip at the top of a page, rising into place.
        look.register(WidgetRenderer.of(UndoBar.class, (bar, c) -> {
            float arrival = bar.arrival();
            boolean lit = lit(bar, "undo");
            c.align(Align.STRETCH).row(r -> r.height(CELL_HEIGHT + 4f).padding(CELL_INSET, 0f, CELL_INSET - 4f, 0f)
                    .gap(GAP_CONTROL).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        float lift = (1f - arrival) * 6f;
                        Render.shadow(x, y + lift + 1f, w, h, R_CARD, faded(SHADOW, arrival));
                        Render.roundRect(x, y + lift, w, h, R_CARD, faded(POPOVER.get(), arrival));
                    })
                    .text(text(), bar.message(), faded(LABEL.get(), arrival))
                    .fill()
                    .row(undo -> undo.name("undo").height(BUTTON_HEIGHT).padding(10f, 0f).align(Align.CENTER)
                            .backdrop((x, y, w, h) -> {
                                if (arrival > 0.99f) {
                                    raised(x, y, w, h, R_BUTTON, lit ? BUTTON_PRESSED.get() : BUTTON.get());
                                }
                            })
                            .text(text(), "Undo", faded(LABEL.get(), arrival))));
        }));

        // A page's sentence about itself, flush with the cards under it.
        look.register(WidgetRenderer.of(PageIntro.class, (intro, c) -> c
                .paragraph(text(), intro.getText(), LABEL_SECONDARY.get())));

        look.register(WidgetRenderer.of(Description.class, (description, c) -> c
                .paragraph(body(), description.getText(), LABEL_SECONDARY.get())));

        // Tahoe's section header: a semibold title, and for a group that folds a
        // trailing "Show All (9)" / "Show Less" in secondary text that brightens and
        // underlines on hover. No chevron, no background (step 8b).
        look.register(WidgetRenderer.of(SectionHeader.class, (header, c) -> {
            float hover = header.hover();
            c.align(Align.STRETCH).row(r -> {
                r.height(CELL_HEIGHT).align(Align.CENTER)
                        .text(textStrong(), header.title(), LABEL.get())
                        .fill();
                if (header.folds()) {
                    String action = header.isOpen() ? "Show Less" : "Show All (" + header.count() + ")";
                    trailing(r, null, action, hover);
                }
            });
        }));
    }
}
