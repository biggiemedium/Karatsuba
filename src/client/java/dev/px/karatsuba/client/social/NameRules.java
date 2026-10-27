package dev.px.karatsuba.client.social;

/**
 * What a Minecraft username may be: 3 to 16 letters, numbers and underscores.
 *
 * <p>Checked here, without asking Mojang, so adding a friend or an offline
 * account never waits on the network and never tells Mojang whom you are looking
 * up. Messages say what is wrong and how to put it right, never just "invalid".
 */
public final class NameRules {

    private static final int MIN = 3;
    private static final int MAX = 16;

    private NameRules() {
    }

    /** @return what is wrong with {@code name}, or null if it could be a username */
    public static String problem(String name) {
        if (name.isEmpty()) {
            return "Enter a username";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!allowed) {
                return "Usernames can only contain letters, numbers and underscores";
            }
        }
        if (name.length() < MIN) {
            return "Usernames are at least " + MIN + " characters";
        }
        if (name.length() > MAX) {
            return "Usernames are " + MAX + " characters or fewer";
        }
        return null;
    }

    public static boolean valid(String name) {
        return problem(name) == null;
    }
}
