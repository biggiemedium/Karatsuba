package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Easing;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.hud.preset.glass.HudGlass;
import dev.px.karatsuba.client.hud.preset.HudTokens;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The editor's toolbar ({@code hud/DESIGN.md} §7.1c, {@code gui/apple/DESIGN.md}
 * §10.2b): a capsule of the HUD's own glass at the bottom centre of the screen,
 * holding Undo, Redo, Snap, Reset All and Done.
 *
 * <p>Its glass is the HUD's, not the GUI's: it sits over the game with the elements,
 * so it follows the HUD's Glass, Appearance, Reduce Transparency and Increase
 * Contrast, and its tools take their colours from that glass ({@link #tokens()}).
 * No widget draws a pass, so the shell has the section draw the glass at
 * {@link #glassBounds()} before the toolbar's own Screen.
 *
 * <p>It gets out of the way of the work: while an element is being dragged near
 * it, it fades nearly away, glass and all, so it never hides where something is
 * going.
 */
public final class EditorToolbar extends Row {

    /** Its name to the HUD's glass, which keeps an appearance per owner. */
    public static final String OWNER = "#editor-toolbar";

    private final Motion fade = new Motion(Animations.HOVER_IN, Animations.HOVER_OUT, Easing.STANDARD, 1f);
    private boolean inTheWay;

    public EditorToolbar() {
        gap(Palette.TOOLBAR_GAP);
        align(Align.CENTER);
    }

    /** Adds a tool. */
    Tool tool(Glyph glyph, String label, boolean iconOnly, Tool.Kind kind, BooleanSupplier lit,
              BooleanSupplier enabled, Runnable action) {
        return add(new Tool(this, glyph, label, iconOnly, kind, lit, enabled, action));
    }

    /** Adds a thin upright line between groups of tools. */
    void divider() {
        add(new Divider(this));
    }

    /** Whether something is being dragged near it, so it should step back. */
    void inTheWay(boolean near) {
        this.inTheWay = near;
    }

    /** @return how present it is, 1 normally, nearly 0 while in the way */
    public float presence() {
        return fade.toward(inTheWay ? 0.2f : 1f);
    }

    /** @return this frame's colours for what sits on its glass: text, separators, the accent */
    public HudTokens tokens() {
        return HudGlass.chromeTokens(OWNER);
    }

    /** @return where its glass goes, from the last update; null before the first */
    public Bounds glassBounds() {
        return part(GlassSurface.PART);
    }

    /** A press on the bar itself stops there, rather than reaching the editor beneath and dropping the selection. */
    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        return true;
    }

    /** A thin upright line between groups of tools, in the glass's separator colour. */
    public static final class Divider extends Widget {

        private final EditorToolbar bar;

        Divider(EditorToolbar bar) {
            this.bar = bar;
        }

        public HudTokens tokens() {
            return bar.tokens();
        }
    }

    /**
     * One tool: a glyph, with its label beside it or as its tooltip. No fill of its own
     * on the glass (no glass on glass, and nothing that reads as a card): a faint tint
     * under the cursor, the accent on Snap's glyph while it is on, and Done alone
     * filled with the accent, the one thing to do next.
     */
    public static final class Tool extends Widget {

        public enum Kind {
            PLAIN, TOGGLE, PRIMARY
        }

        private final EditorToolbar bar;
        private final Glyph glyph;
        private Supplier<String> label;
        private final boolean iconOnly;
        private final Kind kind;
        private final BooleanSupplier lit;
        private final Runnable action;
        private final Motion hover = Motion.hover();
        private final Motion on;

        Tool(EditorToolbar bar, Glyph glyph, String label, boolean iconOnly, Kind kind, BooleanSupplier lit,
             BooleanSupplier enabled, Runnable action) {
            this.bar = bar;
            this.glyph = glyph;
            this.label = () -> label;
            this.iconOnly = iconOnly;
            this.kind = kind;
            this.lit = lit == null ? () -> false : lit;
            this.action = action;
            this.on = Motion.toggle(this.lit.getAsBoolean());
            if (enabled != null) {
                enabledWhen(enabled);
            }
            if (iconOnly) {
                tooltip(() -> this.label.get());
            }
        }

        /** Reads the label live, for a tool whose words change (one asking to confirm). */
        Tool labelledBy(Supplier<String> source) {
            this.label = source;
            return this;
        }

        public Glyph glyph() {
            return glyph;
        }

        public String label() {
            return label.get();
        }

        public boolean iconOnly() {
            return iconOnly;
        }

        public Kind kind() {
            return kind;
        }

        public HudTokens tokens() {
            return bar.tokens();
        }

        public float hover() {
            return hover.toward(isHovered() && isEnabled());
        }

        /** @return how lit a toggle looks, 0 to 1 */
        public float on() {
            return on.toward(lit.getAsBoolean());
        }

        @Override
        protected boolean mousePressed(float x, float y, MouseButton button) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            action.run();
            return true;
        }

        @Override
        protected boolean activated() {
            action.run();
            return true;
        }

        @Override
        public boolean isFocusable() {
            return true;
        }
    }
}
