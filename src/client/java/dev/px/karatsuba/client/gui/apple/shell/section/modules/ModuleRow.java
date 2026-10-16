package dev.px.karatsuba.client.gui.apple.shell.section.modules;

import dev.px.core.module.Module;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A module in its category's card ({@code gui/apple/DESIGN.md} §2.6): its name,
 * its switch and a ›. The switch and the rest of the row are separate targets: the
 * switch turns the module on or off, anywhere else opens its page.
 */
public final class ModuleRow extends ControlRow {

    private final Module module;
    private final Supplier<String> caption;
    private final Consumer<Module> open;
    private final Motion knob;
    private java.util.function.BooleanSupplier selected = () -> false;
    private java.util.function.BooleanSupplier chevron = () -> true;

    /** @param caption the words after the name (its category, while searching); empty for none */
    public ModuleRow(Module module, Supplier<String> caption, Consumer<Module> open) {
        this.module = module;
        this.caption = caption;
        this.open = open;
        this.knob = Motion.toggle(module.isEnabled());
        if (!module.getDescription().isEmpty()) {
            tooltip(module.getDescription());
        }
    }

    @Override
    public String name() {
        return module.getName();
    }

    public String caption() {
        return caption.get();
    }

    /** Reads, live, whether this is the module open beside the list, and whether its › shows (not beside the list). */
    public ModuleRow split(java.util.function.BooleanSupplier open, java.util.function.BooleanSupplier showChevron) {
        this.selected = open;
        this.chevron = showChevron;
        return this;
    }

    @Override
    public boolean isSelected() {
        return selected.getAsBoolean();
    }

    public boolean showsChevron() {
        return chevron.getAsBoolean();
    }

    /** @return the switch's knob, 0 off to 1 on */
    public float knob() {
        return knob.toward(module.isEnabled());
    }

    @Override
    protected boolean pressed(float x, float y) {
        if (inside("switch", x, y)) {
            module.toggle();
        } else {
            open.accept(module);
        }
        return true;
    }

    /** Enter on a focused row opens its page, as a press on it would. */
    @Override
    protected boolean activated() {
        open.accept(module);
        return true;
    }

    /** A right press does nothing here: a module has no default to go back to. */
    @Override
    protected void reset() {
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
