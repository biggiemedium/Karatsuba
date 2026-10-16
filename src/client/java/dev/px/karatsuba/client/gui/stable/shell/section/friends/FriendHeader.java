package dev.px.karatsuba.client.gui.stable.shell.section.friends;

import dev.px.core.social.Friend;
import dev.px.gui.Widget;

/**
 * The inspector's heading for a friend: a large initial, the name the list
 * shows, their real name under it when that differs, and where they are now.
 */
public final class FriendHeader extends Widget {

    private final Friend friend;
    private final Presence presence;

    FriendHeader(Friend friend, Presence presence) {
        this.friend = friend;
        this.presence = presence;
    }

    public String display() {
        return friend.getDisplayName();
    }

    public String name() {
        return friend.getName();
    }

    public boolean hasAlias() {
        return friend.getAlias() != null && !friend.getAlias().isEmpty();
    }

    public boolean isOnline() {
        return presence.isOnline(friend.getName());
    }

    public String status() {
        return presence.full(friend.getName());
    }
}
