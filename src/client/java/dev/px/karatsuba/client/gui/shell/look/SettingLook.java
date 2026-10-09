package dev.px.karatsuba.client.gui.shell.look;

import dev.px.core.Core;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Content;
import dev.px.core.layout.Draw;
import dev.px.core.math.Range;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.shell.setting.ActionRow;
import dev.px.karatsuba.client.gui.shell.setting.AnchorRow;
import dev.px.karatsuba.client.gui.shell.setting.BindRow;
import dev.px.karatsuba.client.gui.shell.setting.ChoiceRow;
import dev.px.karatsuba.client.gui.shell.setting.ColorRow;
import dev.px.karatsuba.client.gui.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.shell.setting.FieldRow;
import dev.px.karatsuba.client.gui.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.shell.setting.ItemRow;
import dev.px.karatsuba.client.gui.shell.setting.MultiChoiceRow;
import dev.px.karatsuba.client.gui.shell.setting.RangeRow;
import dev.px.karatsuba.client.gui.shell.setting.SliderRow;
import dev.px.karatsuba.client.gui.shell.setting.SwitchRow;
import dev.px.karatsuba.client.gui.shell.setting.TextRow;
import dev.px.karatsuba.client.gui.shell.setting.ToggleRow;
import dev.px.karatsuba.client.gui.shell.setting.ValueRow;

import java.util.function.Consumer;

import static dev.px.karatsuba.client.gui.shell.look.Shapes.*;
import static dev.px.karatsuba.client.gui.shell.style.Palette.*;

/**
 * One renderer per kind of setting row.
 *
 * <p>Every row is built from the same few pieces: a top line with the name on the
 * left and the control on the right; on a detailed row, the description under
 * it, a note (a clash) under that, a bar in the accent at the edge when the value
 * is not its default, and a reset button beside the control.
 */
final class SettingLook {

    private SettingLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(ToggleRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> r.custom("switch", 18f, 10f, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(SliderRow.class, (row, c) -> {
            float fill = row.fill();
            float grip = row.grip();
            slider(row, c, trimmed(row.value()), (x, y, w, h) -> {
                float mid = y + h / 2f;
                Render.roundRect(x, mid - 1.5f, w, 3f, 1.5f, TRACK);
                Render.roundRect(x, mid - 1.5f, w * fill, 3f, 1.5f, accent());
                knob(x + w * fill, mid, grip);
            });
        }));

        look.register(WidgetRenderer.of(RangeRow.class, (row, c) -> {
            float grip = row.isHovered() || row.isPressed() ? 1f : 0f;
            Range range = row.setting().get();
            slider(row, c, number(range.getLower()) + " – " + number(range.getUpper()), (x, y, w, h) -> {
                float mid = y + h / 2f;
                float from = x + w * row.progressOf(range.getLower());
                float to = x + w * row.progressOf(range.getUpper());
                Render.roundRect(x, mid - 1.5f, w, 3f, 1.5f, TRACK);
                Render.roundRect(from, mid - 1.5f, to - from, 3f, 1.5f, accent());
                knob(from, mid, grip);
                knob(to, mid, grip);
            });
        }));

        look.register(WidgetRenderer.of(ChoiceRow.class, (row, c) -> {
            if (!row.segmented()) {
                line(row, c, r -> chip(r, row.current(), TEXT));
                return;
            }
            int count = row.count();
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

        // A key cap: the key's name on a raised chip, outlined in the accent while listening.
        look.register(WidgetRenderer.of(BindRow.class, (row, c) -> line(row, c, r -> r.row(chip -> chip
                .padding(6f, 2f)
                .backdrop((x, y, w, h) -> {
                    Render.roundRect(x, y, w, h, RADIUS_CONTROL, row.isListening() ? accentTint(40) : RAISED);
                    if (row.isListening()) {
                        Render.roundRectOutline(x, y, w, h, RADIUS_CONTROL, 1f, accent());
                    } else {
                        // The key cap's lower lip.
                        Render.rect(x + 2f, y + h - 1f, w - 4f, 1f, LINE);
                    }
                })
                .text(body(), row.isListening() || row.setting().isBound() ? row.chip() : "Not bound",
                        row.isListening() || row.setting().isBound() ? TEXT : TEXT_FAINT)))));

        look.register(WidgetRenderer.of(TextRow.class, (row, c) -> {
            boolean detailed = row.isDetailed();
            c.align(Align.STRETCH).column(column -> {
                column.align(Align.STRETCH).padding(INSET, detailed ? 5f : 0f, detailed ? INSET : 2f, detailed ? 5f : 0f)
                        .backdrop(rowBackdrop(row.isHovered() ? 1f : 0f, detailed && row.isModified()))
                        .row(top -> top.height(ROW_HEIGHT + 2f).gap(INSET).align(Align.CENTER)
                                .text(body(), row.name(), TEXT)
                                .fill()
                                .column(field -> field.width(120f).slot()));
                if (detailed && !row.description().isEmpty()) {
                    column.row(text -> text.padding(0f, 0f, INSET, 0f).paragraph(small(), row.description(), TEXT_MUTED));
                }
            });
        }));

        // ---- rows bound to plain values, looking exactly like the setting rows

        look.register(WidgetRenderer.of(FieldRow.class, (row, c) -> c.align(Align.STRETCH).row(r -> r
                .height(ROW_HEIGHT + 2f).padding(INSET, 0f).gap(INSET).align(Align.CENTER)
                .backdrop(rowBackdrop(row.isHovered() ? 1f : 0f, false))
                .text(body(), row.name(), TEXT)
                .fill()
                .column(field -> field.width(150f).slot()))));

        // A fact: its value in the muted colour, since it is read, not set; an action beside it if it has one.
        look.register(WidgetRenderer.of(InfoRow.class, (row, c) -> {
            float hover = row.hover();
            String action = row.actionLabel();
            float mouseX = Core.platform().getMouseX();
            float mouseY = Core.platform().getMouseY();
            var part = row.part("action");
            boolean over = part != null && row.isHovered() && part.contains(mouseX, mouseY);
            line(row, c, r -> {
                r.text(body(), row.value(), TEXT_MUTED);
                if (action != null) {
                    r.row(button -> button.name("action").padding(6f, 2f)
                            .background(over ? RAISED_HIGH : RAISED, RADIUS_CONTROL)
                            .text(small(), action, over ? TEXT : TEXT_MUTED));
                }
            });
        }));

        look.register(WidgetRenderer.of(SwitchRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> r.custom("switch", 18f, 10f, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(ValueRow.class, (row, c) -> {
            float fill = row.fill();
            float grip = row.grip();
            slider(row, c, row.text(), (x, y, w, h) -> {
                float mid = y + h / 2f;
                Render.roundRect(x, mid - 1.5f, w, 3f, 1.5f, TRACK);
                Render.roundRect(x, mid - 1.5f, w * fill, 3f, 1.5f, accent());
                knob(x + w * fill, mid, grip);
            });
        }));

        // Nine cells laid out like the screen; the chosen one slides to where it is picked.
        look.register(WidgetRenderer.of(AnchorRow.class, (row, c) -> {
            float column = row.column();
            float line = row.row();
            float hover = row.hover();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT + 10f).padding(INSET, 0f).gap(INSET).align(Align.CENTER)
                    .backdrop(rowBackdrop(hover, false))
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
            float mouseX = Core.platform().getMouseX();
            float mouseY = Core.platform().getMouseY();
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
    }

    // ----------------------------------------------------------------- pieces

    /**
     * The shape every simple row shares: a top line with the name on the left
     * and {@code control} on the right; on a detailed row, the description and any
     * note under it.
     */
    private static void line(ControlRow row, Content c, Consumer<Content> control) {
        float hover = row.hover();
        boolean detailed = row.isDetailed();
        c.align(Align.STRETCH).column(column -> {
            column.align(Align.STRETCH).padding(INSET, detailed ? 5f : 0f, INSET, detailed ? 5f : 0f)
                    .backdrop(rowBackdrop(hover, row.showsReset()))
                    .row(top -> {
                        top.height(ROW_HEIGHT).gap(INSET).align(Align.CENTER)
                                .text(body(), row.name(), TEXT)
                                .fill();
                        reset(top, row, hover);
                        control.accept(top);
                    });
            under(column, row);
        });
    }

    /** A slider's shape: the name and value on top, the description, then the track. */
    private static void slider(ControlRow row, Content c, String value, Draw track) {
        float hover = row.hover();
        boolean detailed = row.isDetailed();
        c.align(Align.STRETCH).column(column -> {
            column.align(Align.STRETCH).padding(INSET, detailed ? 5f : 4f, INSET, detailed ? 5f : 3f)
                    .backdrop(rowBackdrop(hover, row.showsReset()))
                    .row(top -> {
                        top.height(ROW_HEIGHT - 6f).align(Align.CENTER).gap(INSET)
                                .text(body(), row.name(), TEXT)
                                .fill();
                        reset(top, row, hover);
                        top.text(body(), value, TEXT_MUTED);
                    });
            under(column, row);
            column.space(0f, 3f).custom("track", 0f, 9f, track);
        });
    }

    /** The description and the note under a detailed row's top line, wrapping to the row. */
    private static void under(Content column, ControlRow row) {
        if (!row.isDetailed()) {
            return;
        }
        if (!row.description().isEmpty()) {
            column.space(0f, 1f).paragraph(small(), row.description(), TEXT_MUTED);
        }
        String note = row.note();
        if (!note.isEmpty()) {
            column.space(0f, 2f).paragraph(small(), note, WARN);
        }
    }

    /** The reset button beside the control, on a detailed row whose value is changed. */
    private static void reset(Content top, ControlRow row, float hover) {
        if (row.showsReset()) {
            top.custom("reset", 9f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.RESET, x, y, w,
                    faded(TEXT_MUTED, 0.45f + 0.55f * hover)));
        }
    }

    /** A row's background: the hover tint, and the accent bar of a changed value. */
    private static Draw rowBackdrop(float hover, boolean modified) {
        return (x, y, w, h) -> {
            Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover));
            if (modified) {
                Render.roundRect(x, y + 4f, 2f, h - 8f, 1f, accent());
            }
        };
    }

    /** Where a segmented control's highlight sits, part way between two segments while it slides. */
    private static Bounds between(ChoiceRow row, float slide, int count) {
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
