package dev.px.karatsuba.client.gui.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Content;
import dev.px.core.math.Range;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.shell.setting.BindRow;
import dev.px.karatsuba.client.gui.shell.setting.ChoiceRow;
import dev.px.karatsuba.client.gui.shell.setting.ColorRow;
import dev.px.karatsuba.client.gui.shell.setting.ItemRow;
import dev.px.karatsuba.client.gui.shell.setting.MultiChoiceRow;
import dev.px.karatsuba.client.gui.shell.setting.RangeRow;
import dev.px.karatsuba.client.gui.shell.setting.ActionRow;
import dev.px.karatsuba.client.gui.shell.setting.AnchorRow;
import dev.px.karatsuba.client.gui.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.shell.setting.SwitchRow;
import dev.px.karatsuba.client.gui.shell.setting.ValueRow;
import dev.px.karatsuba.client.gui.shell.setting.SliderRow;
import dev.px.karatsuba.client.gui.shell.setting.TextRow;
import dev.px.karatsuba.client.gui.shell.setting.ToggleRow;

import java.util.function.Consumer;

import static dev.px.karatsuba.client.gui.shell.look.Shapes.*;
import static dev.px.karatsuba.client.gui.shell.style.Palette.*;

/** One renderer per kind of setting row. */
final class SettingLook {

    private SettingLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(ToggleRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> r.custom("switch", 18f, 10f, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(SliderRow.class, (row, c) -> {
            float hover = row.hover();
            float fill = row.fill();
            float grip = row.grip();
            c.align(Align.STRETCH).column(column -> column.align(Align.STRETCH).padding(INSET, 4f, INSET, 3f)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                    .row(top -> top.align(Align.CENTER)
                            .text(body(), row.name(), TEXT)
                            .fill()
                            .text(body(), trimmed(row.value()), TEXT_MUTED))
                    .space(0f, 3f)
                    .custom("track", 0f, 9f, (x, y, w, h) -> {
                        float mid = y + h / 2f;
                        Render.roundRect(x, mid - 1.5f, w, 3f, 1.5f, TRACK);
                        Render.roundRect(x, mid - 1.5f, w * fill, 3f, 1.5f, accent());
                        knob(x + w * fill, mid, grip);
                    }));
        }));

        look.register(WidgetRenderer.of(RangeRow.class, (row, c) -> {
            float hover = row.hover();
            float grip = row.isHovered() || row.isPressed() ? 1f : 0f;
            Range range = row.setting().get();
            c.align(Align.STRETCH).column(column -> column.align(Align.STRETCH).padding(INSET, 4f, INSET, 3f)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                    .row(top -> top.align(Align.CENTER)
                            .text(body(), row.name(), TEXT)
                            .fill()
                            .text(body(), number(range.getLower()) + " – " + number(range.getUpper()), TEXT_MUTED))
                    .space(0f, 3f)
                    .custom("track", 0f, 9f, (x, y, w, h) -> {
                        float mid = y + h / 2f;
                        float from = x + w * row.progressOf(range.getLower());
                        float to = x + w * row.progressOf(range.getUpper());
                        Render.roundRect(x, mid - 1.5f, w, 3f, 1.5f, TRACK);
                        Render.roundRect(from, mid - 1.5f, to - from, 3f, 1.5f, accent());
                        knob(from, mid, grip);
                        knob(to, mid, grip);
                    }));
        }));

        look.register(WidgetRenderer.of(ChoiceRow.class, (row, c) -> {
            if (!row.segmented()) {
                line(row, c, r -> chip(r, row.current(), TEXT));
                return;
            }
            int count = row.options().size();
            int chosen = row.chosen();
            float slide = row.slide();
            line(row, c, r -> r.row(segments -> {
                segments.padding(2f).align(Align.CENTER).backdrop((x, y, w, h) -> {
                    Render.roundRect(x, y, w, h, RADIUS_CONTROL, TRACK);
                    Bounds highlight = between(row, slide, count);
                    if (highlight != null) {
                        Render.roundRect(highlight.getX(), highlight.getY(), highlight.getWidth(),
                                highlight.getHeight(), RADIUS_CONTROL - 1f, accentTint(90));
                    }
                });
                for (int i = 0; i < count; i++) {
                    String name = "option" + i;
                    String label = row.label(i);
                    // Each label brightens as the highlight arrives under it.
                    float under = Math.max(0f, 1f - Math.abs(slide - i));
                    segments.row(segment -> segment.name(name)
                            .padding(6f, 2f).text(body(), label, TEXT_MUTED.lerp(TEXT, under)));
                }
            }));
        }));

        look.register(WidgetRenderer.of(MultiChoiceRow.class, (row, c) ->
                line(row, c, r -> chip(r, row.summary(), row.isEmpty() ? TEXT_FAINT : TEXT))));

        look.register(WidgetRenderer.of(ItemRow.class, (row, c) ->
                line(row, c, r -> chip(r, row.summary(), row.isEmpty() ? TEXT_FAINT : TEXT))));

        look.register(WidgetRenderer.of(ColorRow.class, (row, c) -> line(row, c, r -> {
            var colour = row.setting();
            if (colour.isSyncToTheme() || colour.isRainbow()) {
                r.text(small(), colour.isSyncToTheme() ? "Theme" : "Rainbow", TEXT_FAINT).space(6f, 0f);
            }
            r.custom(20f, 11f, (x, y, w, h) -> swatch(x, y, w, h, colour.resolve()));
        })));

        look.register(WidgetRenderer.of(BindRow.class, (row, c) -> line(row, c, r -> r.row(chip -> chip
                .padding(6f, 2f)
                .backdrop((x, y, w, h) -> {
                    Render.roundRect(x, y, w, h, RADIUS_CONTROL, row.isListening() ? accentTint(40) : RAISED);
                    if (row.isListening()) {
                        Render.roundRectOutline(x, y, w, h, RADIUS_CONTROL, 1f, accent());
                    }
                })
                .text(body(), row.chip(), row.isListening() || row.setting().isBound() ? TEXT : TEXT_FAINT)))));

        // ---- rows bound to plain values, looking exactly like the setting rows

        look.register(WidgetRenderer.of(SwitchRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> r.custom("switch", 18f, 10f, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(ValueRow.class, (row, c) -> {
            float hover = row.hover();
            float fill = row.fill();
            float grip = row.grip();
            c.align(Align.STRETCH).column(column -> column.align(Align.STRETCH).padding(INSET, 4f, INSET, 3f)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                    .row(top -> top.align(Align.CENTER)
                            .text(body(), row.name(), TEXT)
                            .fill()
                            .text(body(), row.text(), TEXT_MUTED))
                    .space(0f, 3f)
                    .custom("track", 0f, 9f, (x, y, w, h) -> {
                        float mid = y + h / 2f;
                        Render.roundRect(x, mid - 1.5f, w, 3f, 1.5f, TRACK);
                        Render.roundRect(x, mid - 1.5f, w * fill, 3f, 1.5f, accent());
                        knob(x + w * fill, mid, grip);
                    }));
        }));

        // Nine cells laid out like the screen; the chosen one slides to where it is picked.
        look.register(WidgetRenderer.of(AnchorRow.class, (row, c) -> {
            float column = row.column();
            float line = row.row();
            float hover = row.hover();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT + 10f).padding(INSET, 0f).gap(INSET).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                    .text(body(), row.name(), TEXT)
                    .fill()
                    .column(grid -> {
                grid.padding(3f).gap(2f).backdrop((x, y, w, h) -> {
                    Render.roundRect(x, y, w, h, RADIUS_CONTROL, TRACK);
                    float cell = 6f;
                    float step = cell + 2f;
                    Render.roundRect(x + 3f + column * step, y + 3f + line * step, cell, cell, 1.5f, accent());
                });
                for (int gridRow = 0; gridRow < 3; gridRow++) {
                    int base = gridRow * 3;
                    grid.row(cells -> {
                        cells.gap(2f);
                        for (int i = 0; i < 3; i++) {
                            String name = "cell" + (base + i);
                            cells.custom(name, 6f, 6f, (x, y, w, h) -> Render.roundRect(x, y, w, h, 1.5f, HOVER));
                        }
                    });
                }
            }));
        }));

        look.register(WidgetRenderer.of(ActionRow.class, (row, c) -> {
            float mouseX = dev.px.core.Core.platform().getMouseX();
            float mouseY = dev.px.core.Core.platform().getMouseY();
            int over = row.isHovered() ? row.hovered(mouseX, mouseY) : -1;
            line(row, c, r -> {
                r.gap(4f);
                for (int i = 0; i < row.actions().size(); i++) {
                    String label = row.actions().get(i).label();
                    String name = "action" + i;
                    boolean lit = i == over;
                    r.row(button -> button.name(name)
                            .padding(6f, 2f).background(lit ? RAISED_HIGH : RAISED, RADIUS_CONTROL)
                            .text(small(), label, lit ? TEXT : TEXT_MUTED));
                }
            });
        }));

        look.register(WidgetRenderer.of(TextRow.class, (row, c) -> c.align(Align.STRETCH).row(r -> r
                .height(ROW_HEIGHT + 2f).padding(INSET, 0f, 2f, 0f).gap(INSET).align(Align.CENTER)
                .text(body(), row.name(), TEXT)
                .column(field -> field.min(70f, 0f).slot()))));
    }

    /**
     * The shape every simple setting shares: a row-tall line with the name on the
     * left and {@code control} on the right, tinted while hovered.
     */
    private static void line(ControlRow row, Content c, Consumer<Content> control) {
        float hover = row.hover();
        c.align(Align.STRETCH).row(r -> {
            r.height(ROW_HEIGHT).padding(INSET, 0f).gap(INSET).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                    .text(body(), row.name(), TEXT)
                    .fill();
            control.accept(r);
        });
    }

    /** Where a segmented control's highlight sits, part way between two segments while it slides. */
    private static Bounds between(ChoiceRow<?> row, float slide, int count) {
        int below = Math.max(0, Math.min(count - 1, (int) Math.floor(slide)));
        int above = Math.min(below + 1, count - 1);
        Bounds from = row.part("option" + below);
        Bounds to = row.part("option" + above);
        if (from == null || to == null) {
            return null;
        }
        float t = slide - below;
        return Bounds.of(lerp(from.getX(), to.getX(), t), from.getY(),
                lerp(from.getWidth(), to.getWidth(), t), from.getHeight());
    }
}
