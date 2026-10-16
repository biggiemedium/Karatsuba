package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.gui.widget.TextField;
import dev.px.karatsuba.client.gui.stable.shell.widget.SearchField;

import static dev.px.karatsuba.client.gui.stable.shell.look.Shapes.magnifier;
import static dev.px.karatsuba.client.gui.stable.shell.style.Palette.*;

/** Text fields: the search box, and free-text settings. */
final class FieldLook {

    private FieldLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(TextField.class, (field, c) -> field(field, c, false)));
        look.register(WidgetRenderer.of(SearchField.class, (field, c) -> field(field, c, true)));
    }

    private static void field(TextField field, Content c, boolean search) {
        boolean focused = field.isFocused();
        c.align(Align.STRETCH).row(r -> {
            r.height(ROW_HEIGHT).padding(6f, 0f).gap(6f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, focused ? RAISED_HIGH : RAISED);
                        if (focused) {
                            Render.roundRectOutline(x, y, w, h, RADIUS_CONTROL, 1f, accent().withAlpha(160));
                        }
                    });
            if (search) {
                r.custom(8f, 8f, (x, y, w, h) -> magnifier(x, y, w, h, focused ? TEXT_MUTED : TEXT_FAINT));
            }
            r.column(text -> text.name("text").grow().min(0f, body().getHeight()).backdrop((x, y, w, h) -> {
                Render.pushClip(x, y, w, h);
                boolean empty = field.getText().isEmpty();
                float left = x - field.getScrollX();
                if (field.hasSelection()) {
                    float from = field.offsetOf(field.getSelectionStart());
                    float to = field.offsetOf(field.getSelectionEnd());
                    Render.rect(left + from, y, to - from, h, accentTint(90));
                }
                Render.text(body(), empty ? field.getPlaceholder() : field.getText(), left, y, empty ? TEXT_FAINT : TEXT);
                if (focused && blink()) {
                    Render.rect(left + field.offsetOf(field.getCaret()), y, 1f, h, accent());
                }
                Render.popClip();
            }));
        });
    }

    private static boolean blink() {
        return (System.currentTimeMillis() / 530L) % 2L == 0L;
    }
}
