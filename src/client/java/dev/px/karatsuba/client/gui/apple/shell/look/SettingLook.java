package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.Core;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Content;
import dev.px.core.layout.Draw;
import dev.px.core.math.Range;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.section.hud.ElementRow;
import dev.px.karatsuba.client.gui.apple.shell.section.modules.ModuleRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ActionRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.AnchorRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.BindRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ChoiceRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ColorRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.FieldRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ItemRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.LinkRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.MultiChoiceRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.OptionsRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.RangeRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.SliderRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.StepperRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.SwitchRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.TextRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ToggleRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ValueRow;

import java.util.function.Consumer;

import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.lerp;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.number;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.raised;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.sliderKnob;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.toggle;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.trimmed;
import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * One renderer per kind of setting row, in System Settings' style
 * ({@code gui/apple/DESIGN.md} §2.5): the name on the left, the control on the
 * right, one {@link dev.px.karatsuba.client.gui.apple.shell.style.Palette#CELL_HEIGHT}
 * tall unless a description sits under the name.
 *
 * <p>In a card a row draws no background: the card draws its highlight and the
 * hairlines. Outside one (a section not yet rebuilt) it draws its own hover.
 * Controls are solid, never glass (§5.3); the accent appears only where something
 * is on, filled or chosen.
 */
final class SettingLook {


    private SettingLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // A module in its category's card: its name (its category beside it while searching),
        // its switch, and a › for its page (§2.6).
        look.register(WidgetRenderer.of(ModuleRow.class, (row, c) -> {
            float knob = row.knob();
            String caption = row.caption();
            line(row, c, r -> {
                if (!caption.isEmpty()) {
                    r.text(text(), caption, LABEL_SECONDARY.get()).space(GAP_CONTROL, 0f);
                }
                r.custom("switch", SWITCH_WIDTH, SWITCH_HEIGHT, (x, y, w, h) -> toggle(x, y, w, h, knob));
                if (row.showsChevron()) {
                    r.space(2f, 0f).custom(6f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.FORWARD, x - 1.5f, y, h,
                            LABEL_TERTIARY.get()));
                }
            });
        }));

        // A HUD element in its card: its name, its Shown switch, and a › for its page (hud/DESIGN.md §7.1b).
        look.register(WidgetRenderer.of(ElementRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> {
                r.custom("switch", SWITCH_WIDTH, SWITCH_HEIGHT, (x, y, w, h) -> toggle(x, y, w, h, knob));
                r.space(2f, 0f).custom(6f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.FORWARD, x - 1.5f, y, h,
                        LABEL_TERTIARY.get()));
            });
        }));

        look.register(WidgetRenderer.of(ToggleRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> r.custom("switch", SWITCH_WIDTH, SWITCH_HEIGHT, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(SwitchRow.class, (row, c) -> {
            float knob = row.knob();
            line(row, c, r -> r.custom("switch", SWITCH_WIDTH, SWITCH_HEIGHT, (x, y, w, h) -> toggle(x, y, w, h, knob)));
        }));

        look.register(WidgetRenderer.of(SliderRow.class, (row, c) -> {
            float fill = row.fill();
            float grip = row.grip();
            Draw track = (x, y, w, h) -> track(x, y, w, h, 0f, fill, grip, false);
            if (row.low() != null) {
                // Labelled at both ends instead of a value: "Clear ━━●━━ Tinted" (§7.3).
                line(row, c, r -> r.text(caption(), row.low(), LABEL_SECONDARY.get()).space(GAP_CONTROL, 0f)
                        .custom("track", SLIDER_WIDTH, SLIDER_KNOB_HEIGHT + 4f, track)
                        .space(GAP_CONTROL, 0f).text(caption(), row.high(), LABEL_SECONDARY.get()));
                return;
            }
            line(row, c, r -> slider(r, trimmed(row.value()), track));
        }));

        look.register(WidgetRenderer.of(ValueRow.class, (row, c) -> {
            float fill = row.fill();
            float grip = row.grip();
            line(row, c, r -> slider(r, row.text(), (x, y, w, h) -> track(x, y, w, h, 0f, fill, grip, false)));
        }));

        look.register(WidgetRenderer.of(RangeRow.class, (row, c) -> {
            float grip = row.isHovered() || row.isPressed() ? 1f : 0f;
            Range range = row.setting().get();
            float from = row.progressOf(range.getLower());
            float to = row.progressOf(range.getUpper());
            line(row, c, r -> slider(r, number(range.getLower()) + " – " + number(range.getUpper()),
                    (x, y, w, h) -> track(x, y, w, h, from, to, grip, true)));
        }));

        look.register(WidgetRenderer.of(StepperRow.class, (row, c) -> line(row, c, r -> {
            r.text(text(), row.value(), LABEL_SECONDARY.get()).space(GAP_CONTROL, 0f);
            stepper(r, row.canLower(), row.canRaise());
        })));

        look.register(WidgetRenderer.of(ChoiceRow.class, (row, c) -> {
            if (!row.segmented()) {
                line(row, c, r -> popup(r, row.current(), LABEL.get()));
                return;
            }
            // A neutral capsule, equal segments with centred labels, and a raised lighter
            // pill under the chosen one that slides across; the rest read secondary.
            int count = row.count();
            float slide = row.slide();
            float segment = 0f;
            for (int i = 0; i < count; i++) {
                segment = Math.max(segment, text().widthOf(row.label(i)));
            }
            float width = segment + 20f;
            line(row, c, r -> r.row(segments -> {
                segments.height(SEGMENTED_HEIGHT).padding(INSET_SEGMENT).align(Align.STRETCH).backdrop((x, y, w, h) -> {
                    Render.roundRect(x, y, w, h, R_SEGMENTED, CONTROL_OFF.get());
                    Bounds thumb = between(row, slide, count);
                    if (thumb != null) {
                        raised(thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight(), R_SEGMENT,
                                CONTROL_THUMB.get());
                    }
                });
                for (int i = 0; i < count; i++) {
                    String name = "option" + i;
                    String label = row.label(i);
                    float under = Math.max(0f, 1f - Math.abs(slide - i));
                    Color ink = LABEL_SECONDARY.get().lerp(LABEL.get(), under);
                    segments.custom(name, width, SEGMENTED_HEIGHT - INSET_SEGMENT * 2f, (x, y, w, h) -> {
                        float tw = text().widthOf(label);
                        Render.text(text(), label, x + (w - tw) / 2f, y + (h - text().getHeight()) / 2f, ink);
                    });
                }
            }));
        }));

        look.register(WidgetRenderer.of(OptionsRow.class, (row, c) -> line(row, c, r -> {
            r.gap(4f);
            Color accent = accent();
            for (int i = 0; i < row.count(); i++) {
                String name = "option" + i;
                boolean on = row.isOn(i);
                String label = row.label(i);
                r.row(option -> option.name(name).height(SEGMENTED_HEIGHT).padding(8f, 0f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, h / 2f, on ? accent : CONTROL_OFF.get()))
                        .text(on ? textStrong() : text(), label, on ? onAccent(accent) : LABEL.get()));
            }
        })));

        look.register(WidgetRenderer.of(MultiChoiceRow.class, (row, c) ->
                line(row, c, r -> popup(r, row.summary(), row.isEmpty() ? LABEL_TERTIARY.get() : LABEL.get()))));

        // A row that opens a page: its value in secondary text, then a ›.
        look.register(WidgetRenderer.of(LinkRow.class, (row, c) ->
                line(row, c, r -> disclosure(r, row.value(), LABEL_SECONDARY.get()))));

        look.register(WidgetRenderer.of(ItemRow.class, (row, c) ->
                line(row, c, r -> disclosure(r, row.summary(), row.isEmpty() ? LABEL_TERTIARY.get() : LABEL_SECONDARY.get()))));

        look.register(WidgetRenderer.of(ColorRow.class, (row, c) -> line(row, c, r -> {
            var colour = row.setting();
            if (colour.isSyncToTheme() || colour.isRainbow()) {
                r.text(text(), colour.isSyncToTheme() ? "Theme" : "Rainbow", LABEL_SECONDARY.get()).space(GAP_CONTROL, 0f);
            }
            r.custom(16f, 16f, (x, y, w, h) -> {
                float cx = x + w / 2f;
                float cy = y + h / 2f;
                Render.circle(cx, cy, w / 2f, SEPARATOR.get());
                Render.circle(cx, cy, w / 2f - 1f, colour.resolve().withAlpha(255));
            });
        })));

        // A push button showing the key, or "None"; "Press a key…" with an accent edge while it listens.
        look.register(WidgetRenderer.of(BindRow.class, (row, c) -> line(row, c, r -> {
            boolean listening = row.isListening();
            boolean bound = row.setting().isBound();
            Color accent = accent();
            r.row(cap -> cap.height(BUTTON_HEIGHT).padding(10f, 0f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        raised(x, y, w, h, R_BUTTON, BUTTON.get());
                        if (listening) {
                            Render.roundRectOutline(x, y, w, h, R_BUTTON, 1.5f, accent);
                        }
                    })
                    .text(text(), row.chip(), listening || bound ? LABEL.get() : LABEL_SECONDARY.get()));
        })));

        look.register(WidgetRenderer.of(TextRow.class, (row, c) -> {
            boolean detailed = row.isDetailed();
            c.align(Align.STRETCH).column(column -> {
                column.align(Align.STRETCH).padding(CELL_INSET, detailed ? 5f : 0f, CELL_INSET, detailed ? 5f : 0f)
                        .backdrop(own(row.isCarded(), row.isHovered() ? 1f : 0f))
                        .row(top -> top.height(CELL_HEIGHT).gap(GAP_CONTROL).align(Align.CENTER)
                                .text(text(), row.name(), LABEL.get())
                                .fill()
                                .column(field -> field.width(FIELD_WIDTH).slot()));
                if (detailed && !row.description().isEmpty()) {
                    column.row(text -> text.paragraph(caption(), row.description(), LABEL_SECONDARY.get()));
                }
            });
        }));

        // ---- rows bound to plain values, looking exactly like the setting rows

        look.register(WidgetRenderer.of(FieldRow.class, (row, c) -> c.align(Align.STRETCH).row(r -> r
                .height(CELL_HEIGHT).padding(CELL_INSET, 0f).gap(GAP_CONTROL).align(Align.CENTER)
                .backdrop(own(row.isCarded(), row.isHovered() ? 1f : 0f))
                .text(text(), row.name(), LABEL.get())
                // The row places its own field, at a set width on the right (FieldRow).
                .column(field -> field.slot()))));

        // A fact: its value in the secondary colour, since it is read, not set; an action beside it if it has one.
        look.register(WidgetRenderer.of(InfoRow.class, (row, c) -> {
            String action = row.actionLabel();
            line(row, c, r -> {
                r.text(text(), row.value(), LABEL_SECONDARY.get());
                if (action != null) {
                    r.space(GAP_CONTROL, 0f);
                    button(r, "action", action, lit(row, "action"));
                }
            });
        }));

        look.register(WidgetRenderer.of(ActionRow.class, (row, c) -> {
            float mouseX = Core.platform().getMouseX();
            float mouseY = Core.platform().getMouseY();
            int over = row.isHovered() ? row.hovered(mouseX, mouseY) : -1;
            line(row, c, r -> {
                r.gap(6f);
                for (int i = 0; i < row.actions().size(); i++) {
                    button(r, "action" + i, row.actions().get(i).label(), i == over);
                }
            });
        }));

        // Nine cells laid out like the screen; the chosen one slides to where it is picked.
        look.register(WidgetRenderer.of(AnchorRow.class, (row, c) -> {
            float column = row.column();
            float line = row.row();
            Color accent = accent();
            c.align(Align.STRETCH).row(r -> r.height(CELL_HEIGHT + 12f).padding(CELL_INSET, 0f).gap(GAP_CONTROL)
                    .align(Align.CENTER)
                    .backdrop(own(row.isCarded(), row.hover()))
                    .text(text(), row.name(), LABEL.get())
                    .fill()
                    .column(grid -> {
                        grid.padding(3f).gap(2f).backdrop((x, y, w, h) -> {
                            Render.roundRect(x, y, w, h, R_BUTTON, CONTROL_OFF.get());
                            float cell = 6f;
                            float step = cell + 2f;
                            Render.roundRect(x + 3f + column * step, y + 3f + line * step, cell, cell, 1.5f, accent);
                        });
                        for (int gridRow = 0; gridRow < 3; gridRow++) {
                            int base = gridRow * 3;
                            grid.row(cells -> {
                                cells.gap(2f);
                                for (int i = 0; i < 3; i++) {
                                    String name = "cell" + (base + i);
                                    cells.custom(name, 6f, 6f, (x, y, w, h) ->
                                            Render.roundRect(x, y, w, h, 1.5f, HOVER_TINT.get()));
                                }
                            });
                        }
                    }));
        }));
    }

    // ----------------------------------------------------------------- pieces

    /**
     * The shape every row shares: the name on the left and {@code control} on the
     * right; on a detailed row, the description and any note under the name.
     */
    private static void line(ControlRow row, Content c, Consumer<Content> control) {
        float hover = row.hover();
        boolean detailed = row.isDetailed();
        c.align(Align.STRETCH).column(column -> {
            column.align(Align.STRETCH).padding(CELL_INSET, detailed ? 5f : 0f, CELL_INSET, detailed ? 5f : 0f)
                    .backdrop(own(row.isCarded(), hover))
                    .row(top -> {
                        top.height(CELL_HEIGHT).gap(GAP_CONTROL).align(Align.CENTER)
                                .text(text(), row.name(), LABEL.get())
                                .fill();
                        reset(top, row, hover);
                        control.accept(top);
                    });
            if (detailed) {
                if (!row.description().isEmpty()) {
                    column.paragraph(caption(), row.description(), LABEL_SECONDARY.get());
                }
                String note = row.note();
                if (!note.isEmpty()) {
                    column.space(0f, 2f).paragraph(caption(), note, WARN);
                }
            }
        });
    }

    /** A row's own background, only outside a card: a hover tint. */
    private static Draw own(boolean carded, float hover) {
        return (x, y, w, h) -> {
            if (!carded && hover > 0.01f) {
                Render.roundRect(x, y, w, h, R_BUTTON, faded(HOVER_TINT.get(), hover));
            }
        };
    }

    /** The reset button beside the control, on a detailed row whose value is changed. */
    private static void reset(Content top, ControlRow row, float hover) {
        if (row.showsReset()) {
            top.custom("reset", 9f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.RESET, x, y, w,
                    LABEL_TERTIARY.get().lerp(LABEL_SECONDARY.get(), hover)));
        }
    }

    /** A value in secondary text and a slider beside it, the slider's track a named part the row reads presses on. */
    private static void slider(Content r, String value, Draw track) {
        r.text(text(), value, LABEL_SECONDARY.get()).space(GAP_CONTROL, 0f);
        r.custom("track", SLIDER_WIDTH, SLIDER_KNOB_HEIGHT + 4f, track);
    }

    /**
     * Control Center's slider: a thin track, filled with the accent from {@code from}
     * to {@code to}, and a white elongated knob with a shadow at each moving end.
     */
    private static void track(float x, float y, float w, float h, float from, float to, float grip, boolean both) {
        float mid = y + h / 2f;
        float half = SLIDER_TRACK / 2f;
        // The full width of the part, the same span the row maps the cursor across,
        // so the knob stays under the cursor all the way along (a knob at either end
        // overhangs the part by half its width, into the row's padding).
        float left = x;
        float span = w;
        Render.roundRect(left, mid - half, span, SLIDER_TRACK, half, CONTROL_OFF.get());
        Render.roundRect(left + span * from, mid - half, span * (to - from), SLIDER_TRACK, half, accent());
        if (both) {
            sliderKnob(left + span * from, mid, grip);
        }
        sliderKnob(left + span * to, mid, grip);
    }

    /** − and +, a raised capsule split down the middle, each half a named part; a half dims at its end. */
    private static void stepper(Content r, boolean lower, boolean raise) {
        r.row(pill -> pill.height(STEPPER_HEIGHT).align(Align.STRETCH)
                .backdrop((x, y, w, h) -> {
                    raised(x, y, w, h, h / 2f, BUTTON.get());
                    Render.rect(x + w / 2f - 0.5f, y + 4f, 1f, h - 8f, SEPARATOR.get());
                })
                .custom("minus", STEPPER_WIDTH / 2f, STEPPER_HEIGHT, (x, y, w, h) ->
                        Render.line(x + w / 2f - 3.5f, y + h / 2f, x + w / 2f + 3.5f, y + h / 2f, 1.3f,
                                lower ? LABEL.get() : LABEL_TERTIARY.get()))
                .custom("plus", STEPPER_WIDTH / 2f, STEPPER_HEIGHT, (x, y, w, h) -> {
                    Color ink = raise ? LABEL.get() : LABEL_TERTIARY.get();
                    Render.line(x + w / 2f - 3.5f, y + h / 2f, x + w / 2f + 3.5f, y + h / 2f, 1.3f, ink);
                    Render.line(x + w / 2f, y + h / 2f - 3.5f, x + w / 2f, y + h / 2f + 3.5f, 1.3f, ink);
                }));
    }

    /** A pop-up, as Tahoe draws one in a row: the value and a small pair of chevrons, no box. */
    private static void popup(Content r, String value, Color ink) {
        r.row(button -> button.height(BUTTON_HEIGHT).gap(5f).align(Align.CENTER)
                .text(text(), value, ink)
                .custom(6f, 9f, (x, y, w, h) -> {
                    Color chevrons = LABEL_SECONDARY.get();
                    float cx = x + w / 2f;
                    Render.line(x, y + 3f, cx, y, 1.1f, chevrons);
                    Render.line(cx, y, x + w, y + 3f, 1.1f, chevrons);
                    Render.line(x, y + h - 3f, cx, y + h, 1.1f, chevrons);
                    Render.line(cx, y + h, x + w, y + h - 3f, 1.1f, chevrons);
                }));
    }

    /** A value and a › after it: a row that opens something of its own. */
    private static void disclosure(Content r, String value, Color ink) {
        r.text(text(), value, ink).space(6f, 0f)
                .custom(6f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.FORWARD, x - 1.5f, y, h, LABEL_TERTIARY.get()));
    }

    /** A push button in a row: the raised bright face, a shade darker while the cursor presses on it. */
    private static void button(Content r, String part, String label, boolean lit) {
        r.row(button -> button.name(part).height(BUTTON_HEIGHT).padding(10f, 0f).align(Align.CENTER)
                .backdrop((x, y, w, h) -> raised(x, y, w, h, R_BUTTON, lit ? BUTTON_PRESSED.get() : BUTTON.get()))
                .text(text(), label, LABEL.get()));
    }

    /** @return whether the cursor is over a row's named part */
    private static boolean lit(ControlRow row, String part) {
        Bounds bounds = row.part(part);
        return bounds != null && row.isHovered()
                && bounds.contains(Core.platform().getMouseX(), Core.platform().getMouseY());
    }

    /** Where a segmented control's thumb sits, part way between two segments while it slides. */
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
