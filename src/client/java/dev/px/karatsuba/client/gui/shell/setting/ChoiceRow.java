package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.setting.impl.EnumSetting;
import dev.px.gui.Popup;
import dev.px.karatsuba.client.gui.shell.popup.Menu;
import dev.px.karatsuba.client.gui.shell.popup.MenuItem;
import dev.px.karatsuba.client.gui.shell.popup.Unroll;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Motion;
import dev.px.karatsuba.client.gui.shell.style.Palette;

import java.util.List;

/**
 * One of several modes.
 *
 * <p>Up to three short options show as a segmented control, every choice in
 * view and one press away, with the highlight sliding to the one chosen. More
 * than that, or a row too narrow, and the row shows the current one on a chip
 * that opens a menu of them all, so a long enum never crowds the row.
 */
public final class ChoiceRow<E extends Enum<E>> extends SettingRow<EnumSetting<E>> {

    /** Widest the segments may be in all before the row falls back to a menu. */
    private static final float SEGMENTED_WIDTH = 132f;

    private final Motion slide;

    public ChoiceRow(EnumSetting<E> setting) {
        super(setting);
        this.slide = new Motion(Animations.SWITCH * 1.5f, setting.getOptions().indexOf(setting.get()));
    }

    public List<E> options() {
        return setting.getOptions();
    }

    public int chosen() {
        return options().indexOf(setting.get());
    }

    public String label(int index) {
        return setting.labelOf(options().get(index));
    }

    public String current() {
        return setting.labelOf(setting.get());
    }

    /** @return where the highlight is, as an option index that slides between whole numbers */
    public float slide() {
        return slide.toward(chosen());
    }

    /**
     * @return whether every option fits on the row as its own segment, beside
     *         the name, at the width the row had last frame
     */
    public boolean segmented() {
        List<E> options = options();
        if (options.size() > 3) {
            return false;
        }
        float width = 4f;
        for (int i = 0; i < options.size(); i++) {
            width += Palette.body().widthOf(label(i)) + 12f;
        }
        float rowWidth = getBounds() == null ? 0f : getBounds().getWidth();
        float room = rowWidth <= 0f ? SEGMENTED_WIDTH
                : rowWidth - Palette.body().widthOf(setting.getName()) - Palette.INSET * 3f;
        return width <= Math.min(SEGMENTED_WIDTH, room);
    }

    @Override
    protected boolean pressed(float x, float y) {
        if (segmented()) {
            for (int i = 0; i < options().size(); i++) {
                if (inside("option" + i, x, y)) {
                    setting.set(options().get(i));
                    return true;
                }
            }
            return true;
        }
        openMenu();
        return true;
    }

    @Override
    protected boolean activated() {
        if (segmented()) {
            setting.cycle();
        } else {
            openMenu();
        }
        return true;
    }

    private void openMenu() {
        Menu menu = new Menu();
        Unroll[] popup = new Unroll[1];
        for (E option : options()) {
            menu.add(new MenuItem(setting.labelOf(option), () -> setting.is(option), false, () -> {
                setting.set(option);
                getScreen().close(popup[0]);
            }));
        }
        popup[0] = Unroll.open(getScreen(), menu, Popup.below(this).gap(2f).matchWidth());
    }
}
