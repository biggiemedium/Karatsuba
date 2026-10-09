package dev.px.karatsuba.client.gui.shell;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Render;
import dev.px.gui.Screen;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.gui.container.Stack;
import dev.px.karatsuba.client.gui.shell.frame.Panel;
import dev.px.karatsuba.client.gui.shell.frame.ResizeGrip;
import dev.px.karatsuba.client.gui.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.shell.frame.SectionHost;
import dev.px.karatsuba.client.gui.shell.frame.ToolbarDock;
import dev.px.karatsuba.client.gui.shell.frame.Veil;
import dev.px.karatsuba.client.gui.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.shell.look.ModuleLook;
import dev.px.karatsuba.client.gui.shell.rail.Rail;
import dev.px.karatsuba.client.gui.shell.section.home.HomeSection;
import dev.px.karatsuba.client.gui.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.shell.section.modules.ModulesSection;
import dev.px.karatsuba.client.gui.shell.section.settings.SettingsSection;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Easing;
import dev.px.karatsuba.client.gui.shell.style.Motion;
import dev.px.karatsuba.client.gui.shell.style.Palette;
import dev.px.karatsuba.client.settings.ClientSettings;
import dev.px.karatsuba.client.settings.gui.GuiSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The client's GUI: one window holding every {@link Section} (Modules, HUD,
 * Themes and whatever comes next), switched from a rail down its left side, and
 * the full-screen Layout editor it steps aside for.
 *
 * <p>The shell owns what every section shares: the window (moved by its rail,
 * resized by its edges, kept where it was left), the rail, the motion between
 * sections, and the keys. Ctrl and a number goes to that section, Ctrl+Tab to
 * the next, Ctrl+E to the Layout editor, Ctrl+F to the section's search. Escape
 * backs out one step at a time: a drag, an open menu, focus, the section's own
 * step (a selection in the editor), the editor itself, and last the GUI.
 *
 * <p>Switching section is a quick fade with no movement, since sections have no
 * order to move along; the rail and the window stay still. Entering the editor,
 * the window fades and shrinks away and the editor's toolbar comes down from
 * the top; leaving reverses it. The section last open is the one the GUI reopens
 * on.
 */
public final class ClientGui implements Shell {

    /** The section the GUI last showed in its window, to reopen on. */
    /** The section the GUI last showed in its window, to reopen on: Home until anything else is chosen. */
    private static String lastSection = HomeSection.ID;

    /** The server the GUI was last opened on; joining another one opens Home, with its fresh status. */
    private static String lastHost;
    private static boolean openedThisSession;

    private final Screen window;
    private final Stack windowRoot;
    private final Screen bar;
    private final Stack barRoot;
    private final WindowFrame frame;
    private final Panel panel;
    private final Rail rail;
    private final SectionHost host;
    private final List<Section> sections;
    private final List<ToolbarDock> docks = new ArrayList<>();

    /** The window's content arriving as the section changes. */
    private final Reveal switchReveal = new Reveal();

    /** 0 in the window, 1 in a full-screen section; eased between. */
    private final Motion fullscreen = new Motion(250f, 200f, Easing.DECELERATE, 0f);

    private Section active;
    private Section returnTo;

    private float width = -1f;
    private float height = -1f;

    public ClientGui(String startSection) {
        this.windowRoot = new Stack();
        this.window = new Screen(ModuleLook.build(Core.get().getLogger()), windowRoot)
                .tooltipMaxWidth(190f)
                .tooltipDelay(450L)
                .clipboard(Core.platform()::getClipboard, Core.platform()::setClipboard);
        this.barRoot = new Stack();
        this.bar = new Screen(ModuleLook.build(Core.get().getLogger()), barRoot)
                .tooltipMaxWidth(190f)
                .tooltipDelay(450L);

        this.frame = new WindowFrame(new SavedFrame());
        this.panel = windowRoot.add(new Panel(frame), 0f, 0f);
        Row body = panel.add(new Row());

        // Sections first: the rail lists them, and they hang their veils on the panel.
        this.sections = Sections.create(this, frame);
        this.active = find(ModulesSection.ID);
        this.rail = body.add(new Rail(this, Core.get().getClientName(), frame));
        this.host = body.add(new SectionHost());
        host.grow(1f);
        for (Section section : sections) {
            Widget content = section.content();
            host.add(content).grow(1f);
            content.visibleWhen(() -> active == section && !section.fullscreen());
            Widget toolbar = section.toolbar();
            if (toolbar != null) {
                ToolbarDock dock = barRoot.add(new ToolbarDock(), Align.CENTER, Align.START);
                dock.add(toolbar);
                dock.visibleWhen(() -> active == section);
                docks.add(dock);
            }
        }

        panel.add(new Veil(switchReveal, () -> host));
        panel.add(new ResizeGrip(frame, ResizeGrip.Edge.RIGHT));
        panel.add(new ResizeGrip(frame, ResizeGrip.Edge.BOTTOM));
        panel.add(new ResizeGrip(frame, ResizeGrip.Edge.CORNER));

        // Home the first time each session and after a change of server, when the
        // overview has news; otherwise wherever it was left, so you carry on.
        String host = Core.server().getHost();
        if (!openedThisSession || !java.util.Objects.equals(host, lastHost)) {
            lastSection = HomeSection.ID;
        }
        openedThisSession = true;
        lastHost = host;
        Section start = find(startSection != null ? startSection : lastSection);
        if (start == null) {
            start = find(ModulesSection.ID);
        }
        if (start.fullscreen()) {
            returnTo = find(HudSection.ID);
            active = start;
            fullscreen.snap(1f);
        } else {
            active = start;
        }
        active.entered();
    }

    // ----------------------------------------------------------------- Shell

    @Override
    public List<Section> sections() {
        return sections;
    }

    @Override
    public Section active() {
        return active;
    }

    @Override
    public void show(String sectionId) {
        Section next = find(sectionId);
        if (next == null || next == active) {
            return;
        }
        window.closePopups();
        window.clearFocus();
        Section previous = active;
        previous.left();
        if (next.fullscreen()) {
            returnTo = previous.fullscreen() ? returnTo : previous;
        } else {
            lastSection = next.id();
            if (!previous.fullscreen()) {
                switchReveal.start();
            }
        }
        active = next;
        active.entered();
    }

    @Override
    public void returnFromFullscreen() {
        show(returnTo != null ? returnTo.id() : HudSection.ID);
    }

    @Override
    public void veil(Reveal reveal, Supplier<Widget> region) {
        panel.add(new Veil(reveal, region));
    }

    @Override
    public float windowWidth() {
        return frame.getWidth();
    }

    // ----------------------------------------------------------------- frame

    /** Lays out and draws one frame at the screen's size, with the cursor where it is. */
    public void frame(float screenWidth, float screenHeight, float mouseX, float mouseY) {
        if (screenWidth != width || screenHeight != height) {
            width = screenWidth;
            height = screenHeight;
            window.resize(width, height);
            bar.resize(width, height);
        }
        frame.fit(width, height);
        windowRoot.move(panel, frame.getX(), frame.getY());
        rail.labelled(frame.getWidth() >= 520f);
        active.tick();

        float away = fullscreen.toward(active.fullscreen() ? 1f : 0f);

        // The full-screen section, under everything, fading in as the window goes.
        if (active.fullscreen() && away > 0f) {
            Render.pushAlpha(away);
            try {
                active.drawScreen(width, height);
            } finally {
                Render.popAlpha();
            }
        }

        // The window, fading and shrinking a little as it steps aside.
        if (away < 1f) {
            boolean live = away == 0f;
            window.update(live ? mouseX : -1f, live ? mouseY : -1f);
            if (away == 0f) {
                window.draw();
            } else {
                Bounds at = frame.bounds();
                float scale = 1f - (1f - Animations.CLOSE_SCALE) * away;
                Render.pushAlpha(1f - away);
                try {
                    Render.scaled(at.getCenterX(), at.getCenterY(), scale, window::draw);
                } finally {
                    Render.popAlpha();
                }
            }
        }

        // The toolbar, coming down from the top as the window goes.
        if (away > 0f && active.fullscreen()) {
            bar.update(mouseX, mouseY);
            float presence = active.toolbar() instanceof dev.px.karatsuba.client.gui.shell.section.layout.EditorToolbar
                    editor ? editor.presence() : 1f;
            Render.pushAlpha(away * presence);
            Render.pushTransform();
            Render.translate(0f, -(1f - away) * 8f);
            try {
                bar.draw();
            } finally {
                Render.popTransform();
                Render.popAlpha();
            }
        }
    }

    /** @return how much the game behind should be dimmed, 0 to 1 of the usual scrim */
    public float scrim() {
        // The editor dims less: the game behind is what the HUD is being arranged over.
        return 1f - 0.45f * fullscreen.value();
    }

    /** @return where the window is, for the opening and closing to grow and shrink about */
    public Bounds windowBounds() {
        return active.fullscreen() ? Bounds.of(0f, 0f, width, height) : frame.bounds();
    }

    /** The GUI is closing: leaves the active section, so the editor never outlives it. */
    public void closed() {
        active.left();
        if (active.fullscreen() && returnTo != null) {
            lastSection = returnTo.id();
        }
    }

    // ----------------------------------------------------------------- input

    private boolean inFullscreen() {
        return active.fullscreen();
    }

    public void press(float x, float y, MouseButton button) {
        if (inFullscreen()) {
            if (!bar.press(x, y, button)) {
                active.press(x, y, button);
            }
            return;
        }
        window.press(x, y, button);
    }

    public void release(float x, float y) {
        if (inFullscreen()) {
            bar.release(x, y, MouseButton.LEFT);
            active.release(x, y);
            return;
        }
        window.release(x, y, MouseButton.LEFT);
    }

    public void scroll(float amount, float x, float y) {
        if (inFullscreen()) {
            active.scroll(amount);
            return;
        }
        window.scroll(x, y, amount);
    }

    public void modifiers(Set<Modifier> modifiers) {
        active.modifiers(modifiers);
    }

    /** @return whether a character was taken */
    public boolean typed(char character) {
        if (inFullscreen()) {
            return true;
        }
        return window.typed(character) || active.typed(character);
    }

    /**
     * @return whether the key was used; Escape that nothing inside the GUI wanted
     *         returns false, which is the host's cue to close
     */
    public boolean key(Key key, Set<Modifier> modifiers) {
        Screen screen = inFullscreen() ? bar : window;
        // What has focus comes first: a text field's keys, a bind being set.
        if (screen.key(key, modifiers)) {
            return true;
        }
        if (inFullscreen() && key != Key.ESCAPE && !modifiers.contains(Modifier.CTRL) && active.key(key, modifiers)) {
            return true;
        }
        if (modifiers.contains(Modifier.CTRL) && shortcut(key, modifiers)) {
            return true;
        }
        switch (key) {
            case TAB:
                if (modifiers.contains(Modifier.SHIFT)) {
                    screen.focusPrevious();
                } else {
                    screen.focusNext();
                }
                return true;
            case ENTER:
                return screen.activate();
            case ESCAPE:
                // A drag, an open menu, or the caret in a text field is a step to back out of.
                // Focus left on a tab or a row by clicking it is not: it would cost an
                // extra press of Escape that nothing on screen explains.
                if (screen.isDragging() || !screen.getPopups().isEmpty()
                        || screen.getFocused() instanceof dev.px.gui.widget.TextField) {
                    if (screen.cancel()) {
                        return true;
                    }
                }
                screen.clearFocus();
                if (active.back()) {
                    return true;
                }
                if (inFullscreen()) {
                    returnFromFullscreen();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /** The Ctrl keys that work everywhere in the GUI. */
    private boolean shortcut(Key key, Set<Modifier> modifiers) {
        int number = digit(key);
        if (number >= 1 && number <= sections.size()) {
            show(sections.get(number - 1).id());
            return true;
        }
        if (key == Key.TAB) {
            cycle(modifiers.contains(Modifier.SHIFT) ? -1 : 1);
            return true;
        }
        if (key == Key.E) {
            if (inFullscreen()) {
                returnFromFullscreen();
            } else {
                show(LayoutSection.ID);
            }
            return true;
        }
        if (key == Key.COMMA) {
            show(SettingsSection.ID);
            return true;
        }
        if (key == Key.F && !inFullscreen()) {
            return active.find();
        }
        // A section's own Ctrl keys: the editor's undo and redo, Friends' undo of a removal.
        return active.key(key, modifiers);
    }

    /** Moves to the next or previous section in the window, skipping full-screen ones. */
    private void cycle(int step) {
        List<Section> windowed = new ArrayList<>();
        for (Section section : sections) {
            if (!section.fullscreen()) {
                windowed.add(section);
            }
        }
        Section from = inFullscreen() && returnTo != null ? returnTo : active;
        int at = windowed.indexOf(from);
        show(windowed.get(Math.floorMod(at + step, windowed.size())).id());
    }

    /** @return the number on a digit key across the top of the keyboard, or -1 */
    private static int digit(Key key) {
        String name = key.name();
        return name.startsWith("NUM_") ? name.charAt(4) - '0' : -1;
    }

    @Override
    public Section section(String sectionId) {
        return find(sectionId);
    }

    private Section find(String id) {
        for (Section section : sections) {
            if (section.id().equals(id)) {
                return section;
            }
        }
        return null;
    }

    /** The window's place, kept in the Interface settings; nowhere at all if they are not registered. */
    private static final class SavedFrame implements WindowFrame.Store {

        @Override
        public float[] load() {
            GuiSettings section = section();
            return section != null && section.hasFrame() ? section.frame() : null;
        }

        @Override
        public void save(float x, float y, float width, float height) {
            GuiSettings section = section();
            if (section != null) {
                section.saveFrame(x, y, width, height);
            }
        }

        @Override
        public void clear() {
            GuiSettings section = section();
            if (section != null) {
                section.clearFrame();
            }
        }

        private static GuiSettings section() {
            try {
                return ClientSettings.get(GuiSettings.class);
            } catch (IllegalStateException notRegistered) {
                return null;
            }
        }
    }
}
