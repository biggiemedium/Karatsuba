package dev.px.karatsuba.client.gui.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Motion;

/**
 * A heading over a group of settings. A module's own groups fold away with a
 * press, and remember whether they are open; the sections the GUI adds itself
 * ("General", "Module") are plain headings.
 */
public final class SectionHeader extends Widget {

    private final String title;
    private final GroupSetting group;
    private final Motion hover = Motion.hover();
    private final Motion open;

    public SectionHeader(String title, GroupSetting group) {
        this.title = title;
        this.group = group;
        this.open = new Motion(Animations.FOLD, isOpen() ? 1f : 0f);
        if (group != null) {
            visibleWhen(group::isVisible);
            if (!group.getDescription().isEmpty()) {
                tooltip(group.getDescription());
            }
        }
    }

    public String title() {
        return title;
    }

    /** @return whether it folds: a module's own group, not a heading the GUI added */
    public boolean folds() {
        return group != null;
    }

    public boolean isOpen() {
        return group == null || group.isExpanded();
    }

    /** @return the chevron's turn, 0 pointing right (shut) to 1 pointing down (open) */
    public float openness() {
        return open.toward(isOpen());
    }

    public float hover() {
        return hover.toward(folds() && isHovered());
    }

    /** How many of the group's settings are showing, for the heading's count. */
    public int count() {
        return group == null ? 0 : group.visibleChildren().size();
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (group == null || button != MouseButton.LEFT) {
            return false;
        }
        group.toggleExpanded();
        return true;
    }

    @Override
    protected boolean activated() {
        if (group != null) {
            group.toggleExpanded();
        }
        return group != null;
    }

    @Override
    public boolean isFocusable() {
        return group != null;
    }
}
