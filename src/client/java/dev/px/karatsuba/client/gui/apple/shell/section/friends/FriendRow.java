package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.core.social.Friend;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;

/**
 * A friend in their card ({@code gui/apple/DESIGN.md} §2.6b): their initial in a
 * circle with a dot while they are online, the name the list shows with their
 * real name beside it when that differs, where they are, and a ›. The whole row
 * opens their page; removing them is done there, and can be undone.
 */
public final class FriendRow extends ControlRow {

    private final Friend friend;
    private final Presence presence;
    private final Runnable open;

    FriendRow(Friend friend, Presence presence, Runnable open) {
        this.friend = friend;
        this.presence = presence;
        this.open = open;
    }

    /** @return the name the list shows: their alias, or their username */
    @Override
    public String name() {
        return friend.getDisplayName();
    }

    /** @return their username, for the avatar's initial and colour */
    public String username() {
        return friend.getName();
    }

    /** @return the real name, when an alias is shown instead; empty otherwise */
    public String realName() {
        return Aliases.differs(friend) ? friend.getName() : "";
    }

    public boolean isOnline() {
        return presence.isOnline(friend.getName());
    }

    /** @return a few words on where they are: "12 m away", "3 h ago" */
    public String status() {
        return presence.brief(friend.getName());
    }

    @Override
    protected boolean pressed(float x, float y) {
        open.run();
        return true;
    }

    @Override
    protected boolean activated() {
        open.run();
        return true;
    }

    /** Nothing to put back: the row goes somewhere, it holds no value. */
    @Override
    protected void reset() {
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
