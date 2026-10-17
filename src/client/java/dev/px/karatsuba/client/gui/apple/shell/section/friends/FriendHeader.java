package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.core.social.Friend;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;

/**
 * The first card on a friend's page, as an account card leads a System Settings
 * page: a large initial, the name the list shows, and under it their real name
 * when that differs and where they are now. A heading, not a control: it never
 * highlights.
 */
public final class FriendHeader extends ControlRow {

    private final Friend friend;
    private final Presence presence;

    FriendHeader(Friend friend, Presence presence) {
        this.friend = friend;
        this.presence = presence;
    }

    @Override
    public String name() {
        return friend.getDisplayName();
    }

    public String username() {
        return friend.getName();
    }

    public boolean hasAlias() {
        return Aliases.differs(friend);
    }

    public boolean isOnline() {
        return presence.isOnline(friend.getName());
    }

    public String status() {
        return presence.full(friend.getName());
    }

    @Override
    public float hover() {
        return 0f;
    }

    @Override
    protected boolean pressed(float x, float y) {
        return false;
    }

    @Override
    protected void reset() {
    }
}
