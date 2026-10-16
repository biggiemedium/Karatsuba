package dev.px.karatsuba.client.gui.stable.shell.section.layout;

import dev.px.core.layout.Align;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

/**
 * The editor's floating toolbar: what mode this is, the edit's few tools in
 * groups, what is selected, and Done.
 *
 * <p>It gets out of the way of the work: while an element is being dragged near
 * it, it fades nearly away, so it never hides where something is going.
 */
public final class EditorToolbar extends Row {

    private final Motion fade = new Motion(Animations.HOVER_IN, Animations.HOVER_OUT,
            dev.px.karatsuba.client.gui.stable.shell.style.Easing.STANDARD, 1f);
    private boolean inTheWay;

    public EditorToolbar() {
        gap(4f);
        align(Align.CENTER);
    }

    /** Whether something is being dragged near it, so it should step back. */
    void inTheWay(boolean near) {
        this.inTheWay = near;
    }

    /** @return how present it is, 1 normally, nearly 0 while in the way */
    public float presence() {
        return fade.toward(inTheWay ? 0.2f : 1f);
    }

    /** A press on the bar itself stops there, rather than reaching the editor beneath and dropping the selection. */
    @Override
    protected boolean mousePressed(float x, float y, dev.px.core.input.MouseButton button) {
        return true;
    }

    /** A thin upright line between groups of tools. */
    public static final class Divider extends Widget {
    }

    /**
     * What is selected, at a fixed width, so the bar does not shift sideways under
     * the cursor as the words change.
     */
    public static final class Status extends dev.px.gui.widget.Label {

        public Status(java.util.function.Supplier<String> text) {
            super(text);
        }
    }

    /** The mode's name, always in view: "Editing HUD". */
    public static final class ModeLabel extends Widget {

        private final String text;

        public ModeLabel(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }
}
