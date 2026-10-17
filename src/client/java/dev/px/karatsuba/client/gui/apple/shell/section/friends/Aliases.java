package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.core.social.Friend;

/** Whether a friend goes by something other than their username, so both are worth showing. */
final class Aliases {

    private Aliases() {
    }

    /** @return whether {@code friend} has an alias that is not just their username again */
    static boolean differs(Friend friend) {
        String alias = friend.getAlias();
        return alias != null && !alias.isBlank() && !alias.trim().equalsIgnoreCase(friend.getName());
    }
}
