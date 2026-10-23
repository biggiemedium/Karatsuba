package dev.px.karatsuba.client.gui.apple.shell;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Render;
import dev.px.gui.Screen;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.gui.container.Row;
import dev.px.gui.container.Stack;
import dev.px.karatsuba.client.gui.apple.shell.frame.ContentColumn;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.frame.Panel;
import dev.px.karatsuba.client.gui.apple.shell.frame.ResizeGrip;
import dev.px.karatsuba.client.gui.apple.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.apple.shell.frame.SectionHost;
import dev.px.karatsuba.client.gui.apple.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.apple.shell.frame.Gutter;
import dev.px.karatsuba.client.gui.apple.shell.frame.ToolbarDock;
import dev.px.karatsuba.client.gui.apple.shell.frame.Veil;
import dev.px.karatsuba.client.gui.apple.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.apple.shell.header.HeaderBar;
import dev.px.karatsuba.client.gui.apple.shell.look.Materials;
import dev.px.karatsuba.client.gui.apple.shell.look.ModuleLook;
import dev.px.karatsuba.client.gui.apple.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.apple.shell.section.layout.EditorToolbar;
import dev.px.karatsuba.client.gui.apple.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.apple.shell.section.modules.ModulesSection;
import dev.px.karatsuba.client.gui.apple.shell.section.settings.SettingsSection;
import dev.px.karatsuba.client.gui.apple.shell.sidebar.SidebarPane;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Easing;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.render.Glass;
import dev.px.karatsuba.client.settings.ClientSettings;
import dev.px.karatsuba.client.settings.gui.GuiSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Apple UI: one window holding every {@link Section}, switched from a glass
 * sidebar floating inside its left edge, and the full-screen Layout editor it
 * steps aside for.
 *
 * <h2>Layers</h2>
 * A shader pass cannot be drawn in the middle of a {@link Screen}, nor inside a
 * clip, transform or alpha push, so each material level is a Screen of its own and
 * the passes go between them ({@code gui/apple/DESIGN.md} §1.5). Bottom to top:
 * <ol>
 *   <li>the game, dimmed and blurred like a sheet's background;</li>
 *   <li>the window: a solid surface, never translucent;</li>
 *   <li>the <b>content</b> Screen: the sections in their capped, centred column, the resize grips;</li>
 *   <li>glass behind the sidebar and the floating ‹ › capsule, frosting the window under them
 *       (one capture, taken over the window and its content);</li>
 *   <li>the <b>control</b> Screen: the sidebar and the header;</li>
 *   <li>the <b>popover</b> Screen: menus and popovers, solid and elevated (step 14).</li>
 * </ol>
 * Two captures a frame: the backdrop, and the window for the sidebar and floating
 * controls. Popovers come later. Input goes the
 * other way, popovers first, except that a content popup open still takes the
 * next press, so a press outside it closes it as before.
 *
 * <h2>Motion</h2>
 * The game dims and blurs behind the window as it opens. Opening and closing form and
 * melt the glass (its presence) while each Screen's content fades and the window
 * grows or shrinks a little about its centre; the passes are scaled by hand, since
 * they cannot be drawn inside the scale. Switching section crossfades the content
 * Screen alone; the sidebar stays put.
 *
 * <p>The shell owns the window (moved by the sidebar, resized by its edges, kept
 * where it was left), the motion and the keys: Ctrl and a number goes to that
 * section, Ctrl+Tab to the next, Ctrl+E to the Layout editor, Ctrl+F to the
 * section's search. Escape backs out one step at a time.
 */
public final class ClientGui implements Shell {

    /** The section the GUI last showed in its window, to reopen on: Modules until anything else is chosen. */
    private static String lastSection = ModulesSection.ID;

    private final Screen content;
    private final Stack contentRoot;
    private final Screen controls;
    private final Stack controlRoot;
    private final Screen popovers;
    private final Stack popoverRoot;
    private final Screen bar;
    private final Stack barRoot;

    private final WindowFrame frame;
    private final Panel panel;
    private final SidebarPane sidebar;
    private final HeaderBar header;
    private final History history = new History();
    private final SectionHost host;
    private final List<Section> sections;
    /** Everything glass: the sidebar and the floating ‹ › capsule, over the window. */
    private final List<GlassSurface> glass = new ArrayList<>();
    private final List<ToolbarDock> docks = new ArrayList<>();

    /** The content arriving as the section changes: a crossfade of the content Screen. */
    private final Reveal switchReveal = new Reveal();

    /** 0 in the window, 1 in a full-screen section; eased between. */
    private final Motion fullscreen = new Motion(250f, 200f, Easing.DECELERATE, 0f);

    private Section active;
    private Section returnTo;

    /** A step back or forward sliding the content in: from the right going forward (1), the left going back (-1). */
    private int slideDirection;
    private long slideStarted;

    private float width = -1f;
    private float height = -1f;

    public ClientGui(String startSection) {
        this.contentRoot = new Stack();
        this.content = screen(contentRoot).clipboard(Core.platform()::getClipboard, Core.platform()::setClipboard);
        this.controlRoot = new Stack();
        this.controls = screen(controlRoot).clipboard(Core.platform()::getClipboard, Core.platform()::setClipboard);
        this.popoverRoot = new Stack();
        this.popovers = screen(popoverRoot);
        this.barRoot = new Stack();
        this.bar = screen(barRoot);

        this.frame = new WindowFrame(new SavedFrame());
        this.panel = contentRoot.add(new Panel(frame), 0f, 0f);
        Row body = panel.add(new Row());

        // Sections first: the sidebar lists them, and they hang their veils on the panel.
        this.sections = Sections.create(this, frame);
        this.active = find(ModulesSection.ID);

        // The content sits beside the sidebar, which floats over the window's left edge,
        // below the header in the window's top area, in a column capped and centred.
        body.add(new Gutter(this::sidebarSpace));
        Column page = body.add(new Column());
        page.grow(1f);
        page.add(new Spacer(Palette.CONTENT_TOP));
        ContentColumn column = page.add(new ContentColumn());
        column.grow(1f);
        this.host = column.add(new SectionHost());
        for (Section section : sections) {
            Widget widget = section.content();
            host.add(widget).grow(1f);
            widget.visibleWhen(() -> active == section && !section.fullscreen());
            Widget toolbar = section.toolbar();
            if (toolbar != null) {
                // At the bottom centre, as Control Center's edit controls are (§10.2b).
                ToolbarDock dock = barRoot.add(new ToolbarDock(), Align.CENTER, Align.END);
                dock.add(toolbar);
                dock.visibleWhen(() -> active == section);
                docks.add(dock);
            }
        }
        panel.add(new ResizeGrip(frame, ResizeGrip.Edge.RIGHT));
        panel.add(new ResizeGrip(frame, ResizeGrip.Edge.BOTTOM));
        panel.add(new ResizeGrip(frame, ResizeGrip.Edge.CORNER));

        this.sidebar = controlRoot.add(new SidebarPane(this, frame), 0f, 0f);
        glass.add(sidebar);
        this.header = controlRoot.add(new HeaderBar(this, frame, () -> contentSpan()[1]), 0f, 0f);
        glass.add(header.capsule());

        // Wherever it was left, so you carry on. Home has no place in the sidebar
        // (gui/apple/DESIGN.md §2.2), so the GUI no longer opens on it by itself.
        Section start = find(startSection != null ? startSection : lastSection);
        if (start == null) {
            start = find(ModulesSection.ID);
        }
        if (start.fullscreen()) {
            returnTo = find(HudSection.ID);
            fullscreen.snap(1f);
        }
        active = start;
        active.entered();
    }

    private static Screen screen(Stack root) {
        return new Screen(ModuleLook.build(Core.get().getLogger()), root)
                .tooltipMaxWidth(190f)
                .tooltipDelay(450L);
    }

    /** @return the room the sidebar takes at the window's left: the pane, narrower collapsed, and its inset from the edge */
    private float sidebarSpace() {
        // The pane and its inset from the window's left edge; the content column adds its own
        // margin beyond, the same either side, so the content sits centred in the rest (step 8c fix).
        return (sidebar == null ? Palette.SIDEBAR_PANE_WIDTH : sidebar.paneWidth()) + Palette.INSET_WINDOW;
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
        content.closePopups();
        content.clearFocus();
        // A search belongs to the page it was typed on: a new page starts without one.
        search("");
        controls.clearFocus();
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

    /** @return the width the sections have: the window less the sidebar's room */
    @Override
    public float windowWidth() {
        return contentSpan()[1];
    }

    /** @return the content column's offset beside the sidebar and its width: where the header lines up */
    private float[] contentSpan() {
        return ContentColumn.span(frame.getWidth() - sidebarSpace());
    }

    @Override
    public boolean canGoBack() {
        return history.canGoBack();
    }

    @Override
    public boolean canGoForward() {
        return history.canGoForward();
    }

    @Override
    public void goBack() {
        travel(history.back(), -1);
    }

    @Override
    public void goForward() {
        travel(history.forward(), 1);
    }

    @Override
    public String search() {
        return sidebar == null ? "" : sidebar.search().getText();
    }

    @Override
    public void search(String text) {
        if (sidebar != null && !sidebar.search().getText().equals(text)) {
            sidebar.search().setText(text);
        }
    }

    @Override
    public boolean focusSearch() {
        if (sidebar == null || sidebar.isCollapsed()) {
            return false;
        }
        content.clearFocus();
        sidebar.search().grab();
        sidebar.search().selectAll();
        return true;
    }

    @Override
    public boolean typeIntoSearch(char character) {
        if (sidebar == null || sidebar.isCollapsed() || sidebar.search().isFocused()) {
            return false;
        }
        content.clearFocus();
        sidebar.search().grab();
        sidebar.search().insert(String.valueOf(character));
        return true;
    }

    /** Goes to a page from the history, sliding it in from the side it is on. */
    private void travel(History.Page page, int direction) {
        if (page == null || inFullscreen()) {
            return;
        }
        Section section = find(page.section());
        if (section == null) {
            return;
        }
        boolean within = section == active;
        show(section.id());
        // Within a section that pushes its own pages, the section slides them; across
        // sections, or within one that does not, the shell shifts the content as before.
        section.restore(page.place(), within ? direction : 0);
        if (!(within && section.slidesPlaces())) {
            slideDirection = direction;
            slideStarted = System.nanoTime();
        }
    }

    // ----------------------------------------------------------------- frame

    /**
     * Lays out and draws one frame at the screen's size, with the cursor where it is.
     *
     * @param presence 0 to 1: how far open the GUI is; glass forms with it and content fades with it
     * @param scale    the window's size about its centre, for opening and closing
     */
    public void frame(float screenWidth, float screenHeight, float mouseX, float mouseY,
                      float presence, float scale) {
        if (screenWidth != width || screenHeight != height) {
            width = screenWidth;
            height = screenHeight;
            content.resize(width, height);
            controls.resize(width, height);
            popovers.resize(width, height);
            bar.resize(width, height);
        }
        placeWindow();
        active.tick();
        if (!active.fullscreen()) {
            history.observe(new History.Page(active.id(), active.place()));
        }

        float away = fullscreen.toward(active.fullscreen() ? 1f : 0f);

        // Behind everything: the game dimmed and blurred, like a sheet's background,
        // clearing as the window steps aside for the editor (§5.1).
        Materials.backdrop(Materials.layer(), width, height, presence * (1f - away));

        // The full-screen section, under everything, fading in as the window goes. It fades
        // itself: a glass pass refuses inside an alpha push, even one at full opacity (§1.4),
        // so the editor draws its elements' glass first and its fade after.
        if (active.fullscreen() && away > 0f) {
            active.drawScreen(width, height, away * presence);
        }

        // The window, in its layers, fading and shrinking a little as it steps aside.
        if (away < 1f) {
            boolean live = away == 0f && presence >= 1f && scale >= 1f;
            float x = live ? mouseX : -1f;
            float y = live ? mouseY : -1f;
            float[] before = {frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight()};
            popovers.update(x, y);
            controls.update(x, y);
            content.update(x, y);
            // A drag (the sidebar, the header, a resize grip) moves the window during the
            // update, after this frame's layout was placed. Place it again and lay out
            // once more, so the window is drawn under the cursor this frame, not the last.
            if (frame.getX() != before[0] || frame.getY() != before[1]
                    || frame.getWidth() != before[2] || frame.getHeight() != before[3]) {
                placeWindow();
                popovers.update(x, y);
                controls.update(x, y);
                content.update(x, y);
            }

            float s = scale * (1f - (1f - Animations.CLOSE_SCALE) * away);
            float formed = presence * (1f - away);
            Bounds at = frame.bounds();
            float cx = at.getCenterX();
            float cy = at.getCenterY();

            Materials.pane(scaled(at.getX(), cx, s), scaled(at.getY(), cy, s),
                    at.getWidth() * s, at.getHeight() * s, Palette.R_WINDOW * s, formed);
            float crossfade = switchReveal.active() ? switchReveal.progress() : 1f;
            drawLayer(content, formed * crossfade, s, cx, cy, slide());

            // The sidebar and the floating controls frost the window they sit on: one
            // capture, taken over the window and its content, shared since none overlap.
            drawGlass(Materials.layer(), glass, formed, s, cx, cy);
            drawLayer(controls, formed, s, cx, cy);
            drawLayer(popovers, formed, s, cx, cy);
        }

        // The toolbar, rising from the bottom as the window goes. Its glass first, outside the
        // fade and the slide, which no pass may be drawn inside (§1.4).
        if (away > 0f && active.fullscreen()) {
            bar.update(mouseX, mouseY);
            float shown = active.toolbar() instanceof EditorToolbar editor ? editor.presence() : 1f;
            float rise = (1f - away) * 8f;
            active.drawToolbarGlass(away * shown * presence, rise);
            Render.pushAlpha(away * shown * presence);
            Render.pushTransform();
            Render.translate(0f, rise);
            try {
                bar.draw();
            } finally {
                Render.popTransform();
                Render.popAlpha();
            }
        }
    }

    /** Fits the window to the screen and puts the panel, sidebar and header where it is. */
    private void placeWindow() {
        frame.fit(width, height);
        contentRoot.move(panel, frame.getX(), frame.getY());
        controlRoot.move(sidebar, frame.getX() + Palette.INSET_WINDOW, frame.getY() + Palette.INSET_WINDOW);
        controlRoot.move(header, frame.getX() + sidebarSpace() + contentSpan()[0],
                frame.getY() + Palette.INSET_WINDOW);
    }

    /** Draws the glass behind each surface, scaled about the window's centre by hand. */
    private static void drawGlass(Glass.Layer layer, List<GlassSurface> surfaces, float formed,
                                  float s, float cx, float cy) {
        for (GlassSurface surface : surfaces) {
            Bounds b = surface.glassBounds();
            if (b != null && b.getWidth() > 0f && b.getHeight() > 0f) {
                Materials.glass(layer, scaled(b.getX(), cx, s), scaled(b.getY(), cy, s),
                        b.getWidth() * s, b.getHeight() * s, surface.glassRadius() * s, formed,
                        surface.glassPress());
            }
        }
    }

    private static void drawLayer(Screen screen, float alpha, float scale, float cx, float cy) {
        drawLayer(screen, alpha, scale, cx, cy, 0f);
    }

    /**
     * Draws one layer's Screen, faded, scaled about the window's centre and shifted
     * sideways by {@code shift}, doing none of it that it does not need. Drawing
     * only: while it shifts, a click lands where the content is laid out.
     */
    private static void drawLayer(Screen screen, float alpha, float scale, float cx, float cy, float shift) {
        if (alpha <= 0f) {
            return;
        }
        if (alpha >= 1f && scale == 1f && shift == 0f) {
            screen.draw();
            return;
        }
        Render.pushAlpha(alpha);
        Render.pushTransform();
        try {
            Render.translate(shift, 0f);
            if (scale == 1f) {
                screen.draw();
            } else {
                Render.scaled(cx, cy, scale, screen::draw);
            }
        } finally {
            Render.popTransform();
            Render.popAlpha();
        }
    }

    /** @return how far the content is still shifted by a step back or forward, easing to 0 */
    private float slide() {
        if (slideStarted == 0L || !Animations.enabled()) {
            slideStarted = 0L;
            return 0f;
        }
        float duration = slideDirection > 0 ? Animations.PAGE : Animations.PAGE_BACK;
        float progress = Animations.progress(slideStarted, duration);
        if (progress >= 1f) {
            slideStarted = 0L;
            return 0f;
        }
        return slideDirection * Animations.PAGE_SHIFT * (1f - Easing.DECELERATE.apply(progress));
    }

    private static float scaled(float value, float centre, float scale) {
        return centre + (value - centre) * scale;
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

    /**
     * @return the Screen keys go to: the content's when anything there has focus
     *         (Ctrl+F can put the caret there while a sidebar row still holds
     *         focus), then the sidebar's, then the content's
     */
    private Screen focused() {
        if (inFullscreen()) {
            return bar;
        }
        if (content.getFocused() == null && controls.getFocused() != null) {
            return controls;
        }
        return content;
    }

    public void press(float x, float y, MouseButton button) {
        if (inFullscreen()) {
            if (!bar.press(x, y, button)) {
                active.press(x, y, button);
            }
            return;
        }
        // An open content popup still takes the next press, so a press outside closes it.
        if (!content.getPopups().isEmpty()) {
            content.press(x, y, button);
            return;
        }
        if (popovers.press(x, y, button)) {
            content.clearFocus();
            return;
        }
        if (controls.press(x, y, button)) {
            content.clearFocus();
            return;
        }
        controls.clearFocus();
        content.press(x, y, button);
    }

    public void release(float x, float y) {
        if (inFullscreen()) {
            bar.release(x, y, MouseButton.LEFT);
            active.release(x, y);
            return;
        }
        popovers.release(x, y, MouseButton.LEFT);
        controls.release(x, y, MouseButton.LEFT);
        content.release(x, y, MouseButton.LEFT);
    }

    public void scroll(float amount, float x, float y) {
        if (inFullscreen()) {
            active.scroll(amount);
            return;
        }
        if (!popovers.scroll(x, y, amount) && !controls.scroll(x, y, amount)) {
            content.scroll(x, y, amount);
        }
    }

    public void modifiers(Set<Modifier> modifiers) {
        active.modifiers(modifiers);
    }

    /** @return whether a character was taken */
    public boolean typed(char character) {
        if (inFullscreen()) {
            return true;
        }
        return focused().typed(character) || active.typed(character);
    }

    /**
     * @return whether the key was used; Escape that nothing inside the GUI wanted
     *         returns false, which is the host's cue to close
     */
    public boolean key(Key key, Set<Modifier> modifiers) {
        Screen screen = focused();
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
                Screen menus = !inFullscreen() && !content.getPopups().isEmpty() ? content : screen;
                if (menus.isDragging() || !menus.getPopups().isEmpty()
                        || menus.getFocused() instanceof dev.px.gui.widget.TextField) {
                    if (menus.cancel()) {
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
        if (key == Key.LEFT_BRACKET) {
            goBack();
            return true;
        }
        if (key == Key.RIGHT_BRACKET) {
            goForward();
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
