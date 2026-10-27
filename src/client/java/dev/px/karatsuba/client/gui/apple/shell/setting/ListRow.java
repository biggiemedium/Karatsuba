package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.layout.Bounds;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;
import dev.px.karatsuba.client.setting.ListSetting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Any number of a list of options ({@code gui/apple/DESIGN.md} §2.5): the setting's
 * name and a summary ("3 of 5") on the first line, then every option on a line of
 * its own with a checkbox, ticked by a press anywhere on that line.
 *
 * <p>Each option is a named part ({@code option:0}, {@code option:1}, ...), so what
 * is pressed is what is drawn. The row lights the option under the cursor, not
 * itself, and the card draws that light ({@link #litOption()}) so its corners
 * follow the card's on the last line. From the keyboard, Up and Down move between
 * options and Enter or Space ticks the one chosen.
 */
public final class ListRow extends SettingRow<ListSetting> {

    private final Map<String, Motion> ticks = new HashMap<>();
    private final Motion light = Motion.hover();
    /** The option the keyboard is on, while the row has focus. */
    private int cursor;

    public ListRow(ListSetting setting) {
        super(setting);
    }

    public List<String> options() {
        return setting.getOptions();
    }

    public String label(String option) {
        return setting.labelOf(option);
    }

    /** @return what the first line says on the right: "None", "All", the one ticked, or "2 of 5" */
    public String summary() {
        return setting.displayValue();
    }

    /** @return how ticked {@code option}'s box is, eased, 0 to 1 */
    public float tick(String option) {
        return ticks.computeIfAbsent(option, o -> Motion.toggle(setting.has(o))).toward(setting.has(option));
    }

    /** @return whether pressing {@code option} would change it: not while the row is off, nor the last one required */
    public boolean canToggle(String option) {
        return isEnabled() && setting.canToggle(option);
    }

    /** The row lights one option, never itself; see {@link #litOption()}. */
    @Override
    public float hover() {
        return 0f;
    }

    /** @return the index of the option lit: the one under the cursor, else the keyboard's while focused; -1 for none */
    public int litIndex() {
        List<String> options = options();
        for (int i = 0; i < options.size(); i++) {
            Bounds part = part(part(i));
            if (part != null && isHovered() && part.contains(Core.platform().getMouseX(), Core.platform().getMouseY())) {
                return i;
            }
        }
        return isFocused() && cursor < options.size() ? cursor : -1;
    }

    /** @return the lit option's line, for the card to highlight; null for none */
    public Bounds litOption() {
        int lit = litIndex();
        return lit < 0 ? null : part(part(lit));
    }

    /** @return whether the lit option is the last line of the row */
    public boolean litIsLast() {
        return litIndex() == options().size() - 1;
    }

    /** @return how strongly the lit option is lit, eased, 0 to 1 */
    public float light() {
        return light.toward(litIndex() >= 0 && isEnabled());
    }

    /** @return the named part of option {@code index} */
    public static String part(int index) {
        return "option:" + index;
    }

    @Override
    protected boolean pressed(float x, float y) {
        List<String> options = options();
        for (int i = 0; i < options.size(); i++) {
            if (inside(part(i), x, y)) {
                cursor = i;
                setting.toggle(options.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean activated() {
        List<String> options = options();
        if (options.isEmpty()) {
            return false;
        }
        setting.toggle(options.get(Math.min(cursor, options.size() - 1)));
        return true;
    }

    @Override
    protected boolean keyPressed(Key key, Set<Modifier> modifiers) {
        int last = options().size() - 1;
        if (key == Key.DOWN && cursor < last) {
            cursor++;
            return true;
        }
        if (key == Key.UP && cursor > 0) {
            cursor--;
            return true;
        }
        return false;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
