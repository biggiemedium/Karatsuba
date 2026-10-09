package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.setting.Setting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.gui.Popup;
import dev.px.karatsuba.client.gui.shell.popup.Menu;
import dev.px.karatsuba.client.gui.shell.popup.MenuItem;
import dev.px.karatsuba.client.gui.shell.popup.Unroll;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Motion;
import dev.px.karatsuba.client.gui.shell.style.Palette;
import dev.px.karatsuba.client.setting.ChoiceSetting;

import java.util.List;

/**
 * One of several modes.
 *
 * <p>Up to three short options show as a segmented control, every choice in
 * view and one press away, with the highlight sliding to the one chosen. More
 * than that, or a row too narrow, and the row shows the current one on a chip
 * that opens a menu of them all, so a long enum never crowds the row.
 *
 * <p>Works the same for Core's {@link EnumSetting}, whose options are fixed, and
 * the client's {@link ChoiceSetting}, whose options are read live: both are seen
 * through {@link Choices}.
 */
public final class ChoiceRow extends SettingRow<Setting<?>> {

    /** Widest the segments may be in all before the row falls back to a menu. */
    private static final float SEGMENTED_WIDTH = 132f;

    /** What the row needs of a setting with options: how many, what each says, which is chosen. */
    private interface Choices {
        int count();

        String label(int index);

        int chosen();

        void choose(int index);
    }

    private final Choices choices;
    private final Motion slide;

    private ChoiceRow(Setting<?> setting, Choices choices) {
        super(setting);
        this.choices = choices;
        this.slide = new Motion(Animations.SWITCH * 1.5f, Math.max(0, choices.chosen()));
    }

    public static <E extends Enum<E>> ChoiceRow of(EnumSetting<E> setting) {
        return new ChoiceRow(setting, new Choices() {
            public int count() {
                return setting.getOptions().size();
            }

            public String label(int index) {
                return setting.labelOf(setting.getOptions().get(index));
            }

            public int chosen() {
                return setting.getOptions().indexOf(setting.get());
            }

            public void choose(int index) {
                setting.set(setting.getOptions().get(index));
            }
        });
    }

    public static ChoiceRow of(ChoiceSetting setting) {
        return new ChoiceRow(setting, new Choices() {
            public int count() {
                return setting.getOptions().size();
            }

            public String label(int index) {
                return setting.labelOf(setting.getOptions().get(index));
            }

            public int chosen() {
                return setting.getOptions().indexOf(setting.resolved());
            }

            public void choose(int index) {
                setting.set(setting.getOptions().get(index));
            }
        });
    }

    /** @return how many options there are, now */
    public int count() {
        return choices.count();
    }

    public int chosen() {
        return choices.chosen();
    }

    public String label(int index) {
        return choices.label(index);
    }

    public String current() {
        int chosen = chosen();
        return chosen < 0 ? "" : label(chosen);
    }

    /** @return where the highlight is, as an option index that slides between whole numbers */
    public float slide() {
        return slide.toward(Math.max(0, chosen()));
    }

    /**
     * @return whether every option fits on the row as its own segment, beside
     *         the name, at the width the row had last frame
     */
    public boolean segmented() {
        int count = count();
        if (count > 3) {
            return false;
        }
        float width = 4f;
        for (int i = 0; i < count; i++) {
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
            for (int i = 0; i < count(); i++) {
                if (inside("option" + i, x, y)) {
                    choices.choose(i);
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
            int count = count();
            if (count > 0) {
                choices.choose((Math.max(0, chosen()) + 1) % count);
            }
        } else {
            openMenu();
        }
        return true;
    }

    private void openMenu() {
        Menu menu = new Menu();
        Unroll[] popup = new Unroll[1];
        int count = count();
        for (int i = 0; i < count; i++) {
            int index = i;
            menu.add(new MenuItem(label(i), () -> chosen() == index, false, () -> {
                choices.choose(index);
                getScreen().close(popup[0]);
            }));
        }
        popup[0] = Unroll.open(getScreen(), menu, Popup.below(this).gap(2f).matchWidth());
    }
}
