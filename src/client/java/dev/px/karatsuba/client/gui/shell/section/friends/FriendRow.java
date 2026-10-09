package dev.px.karatsuba.client.gui.shell.section.friends;

import dev.px.core.input.MouseButton;
import dev.px.core.social.Friend;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Motion;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A friend in the list: their initial in a circle with a dot when they are
 * online, their alias (or name) with their real name under it when they have
 * one, and a word or two on the right saying where they are.
 *
 * <p>Pressing selects them for the inspector. A cross appears on hover to remove
 * them at once; the list offers Undo, so it needs no confirmation.
 */
public final class FriendRow extends Widget {

    private final Friend friend;
    private final Presence presence;
    private final BooleanSupplier selected;
    private final Runnable select;
    private final Consumer<Friend> remove;

    private final Motion hover = Motion.hover();
    private final long arrived;

    FriendRow(Friend friend, Presence presence, BooleanSupplier selected, Runnable select,
              Consumer<Friend> remove, boolean justAdded) {
        this.friend = friend;
        this.presence = presence;
        this.selected = selected;
        this.select = select;
        this.remove = remove;
        this.arrived = justAdded ? System.nanoTime() : 0L;
    }

    public String display() {
        return friend.getDisplayName();
    }

    /** @return the real name, when an alias is shown instead; empty otherwise */
    public String realName() {
        return friend.getAlias() == null || friend.getAlias().isEmpty() ? "" : friend.getName();
    }

    public String name() {
        return friend.getName();
    }

    public boolean isOnline() {
        return presence.isOnline(friend.getName());
    }

    public String status() {
        return presence.brief(friend.getName());
    }

    public boolean isSelected() {
        return selected.getAsBoolean();
    }

    public float hover() {
        return hover.toward(isHovered());
    }

    /** @return a highlight fading over a second after the row was added, 1 to 0 */
    public float flash() {
        if (arrived == 0L || !Animations.enabled()) {
            return 0f;
        }
        return 1f - Animations.progress(arrived, 1000f);
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        var cross = part("remove");
        if (cross != null && cross.contains(x, y) && isHovered()) {
            remove.accept(friend);
        } else {
            select.run();
        }
        return true;
    }

    @Override
    protected boolean activated() {
        select.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
