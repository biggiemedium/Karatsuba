package dev.px.karatsuba.adapter.event;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Translates GLFW input codes into Core's own enums.
 *
 * <p>This table is the whole reason a bind saved today still works after a version
 * bump. Core refuses to store a numeric key code &mdash; LWJGL 2 and GLFW disagree
 * about what {@code 57} means &mdash; so the number is turned into a {@link Key}
 * here, at the boundary, and the config only ever holds the name.
 */
public final class Keys {

    private static final Map<Integer, Key> BY_GLFW_CODE = new HashMap<>();

    private Keys() {
    }

    static {
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            put(GLFW.GLFW_KEY_A + (letter - 'A'), Key.valueOf(String.valueOf(letter)));
        }
        for (int digit = 0; digit <= 9; digit++) {
            put(GLFW.GLFW_KEY_0 + digit, Key.valueOf("NUM_" + digit));
            put(GLFW.GLFW_KEY_KP_0 + digit, Key.valueOf("NUMPAD_" + digit));
        }
        for (int number = 1; number <= 12; number++) {
            put(GLFW.GLFW_KEY_F1 + (number - 1), Key.valueOf("F" + number));
        }

        put(GLFW.GLFW_KEY_ESCAPE, Key.ESCAPE);
        put(GLFW.GLFW_KEY_TAB, Key.TAB);
        put(GLFW.GLFW_KEY_CAPS_LOCK, Key.CAPS_LOCK);
        put(GLFW.GLFW_KEY_SPACE, Key.SPACE);
        put(GLFW.GLFW_KEY_ENTER, Key.ENTER);
        put(GLFW.GLFW_KEY_BACKSPACE, Key.BACKSPACE);
        put(GLFW.GLFW_KEY_DELETE, Key.DELETE);
        put(GLFW.GLFW_KEY_INSERT, Key.INSERT);
        put(GLFW.GLFW_KEY_HOME, Key.HOME);
        put(GLFW.GLFW_KEY_END, Key.END);
        put(GLFW.GLFW_KEY_PAGE_UP, Key.PAGE_UP);
        put(GLFW.GLFW_KEY_PAGE_DOWN, Key.PAGE_DOWN);

        put(GLFW.GLFW_KEY_LEFT_SHIFT, Key.LEFT_SHIFT);
        put(GLFW.GLFW_KEY_RIGHT_SHIFT, Key.RIGHT_SHIFT);
        put(GLFW.GLFW_KEY_LEFT_CONTROL, Key.LEFT_CONTROL);
        put(GLFW.GLFW_KEY_RIGHT_CONTROL, Key.RIGHT_CONTROL);
        put(GLFW.GLFW_KEY_LEFT_ALT, Key.LEFT_ALT);
        put(GLFW.GLFW_KEY_RIGHT_ALT, Key.RIGHT_ALT);
        put(GLFW.GLFW_KEY_LEFT_SUPER, Key.LEFT_SUPER);
        put(GLFW.GLFW_KEY_RIGHT_SUPER, Key.RIGHT_SUPER);

        put(GLFW.GLFW_KEY_UP, Key.UP);
        put(GLFW.GLFW_KEY_DOWN, Key.DOWN);
        put(GLFW.GLFW_KEY_LEFT, Key.LEFT);
        put(GLFW.GLFW_KEY_RIGHT, Key.RIGHT);

        put(GLFW.GLFW_KEY_MINUS, Key.MINUS);
        put(GLFW.GLFW_KEY_EQUAL, Key.EQUALS);
        put(GLFW.GLFW_KEY_LEFT_BRACKET, Key.LEFT_BRACKET);
        put(GLFW.GLFW_KEY_RIGHT_BRACKET, Key.RIGHT_BRACKET);
        put(GLFW.GLFW_KEY_BACKSLASH, Key.BACKSLASH);
        put(GLFW.GLFW_KEY_SEMICOLON, Key.SEMICOLON);
        put(GLFW.GLFW_KEY_APOSTROPHE, Key.QUOTE);
        put(GLFW.GLFW_KEY_GRAVE_ACCENT, Key.GRAVE);
        put(GLFW.GLFW_KEY_COMMA, Key.COMMA);
        put(GLFW.GLFW_KEY_PERIOD, Key.PERIOD);
        put(GLFW.GLFW_KEY_SLASH, Key.SLASH);

        put(GLFW.GLFW_KEY_KP_ADD, Key.NUMPAD_ADD);
        put(GLFW.GLFW_KEY_KP_SUBTRACT, Key.NUMPAD_SUBTRACT);
        put(GLFW.GLFW_KEY_KP_MULTIPLY, Key.NUMPAD_MULTIPLY);
        put(GLFW.GLFW_KEY_KP_DIVIDE, Key.NUMPAD_DIVIDE);
        put(GLFW.GLFW_KEY_KP_DECIMAL, Key.NUMPAD_DECIMAL);
        put(GLFW.GLFW_KEY_KP_ENTER, Key.NUMPAD_ENTER);

        put(GLFW.GLFW_KEY_PRINT_SCREEN, Key.PRINT_SCREEN);
        put(GLFW.GLFW_KEY_SCROLL_LOCK, Key.SCROLL_LOCK);
        put(GLFW.GLFW_KEY_PAUSE, Key.PAUSE);
        put(GLFW.GLFW_KEY_MENU, Key.MENU);
    }

    private static void put(int glfwCode, Key key) {
        BY_GLFW_CODE.put(glfwCode, key);
    }

    /**
     * @return Core's key for a GLFW code, or {@link Key#UNKNOWN} for a physical key
     *         Core has no constant for. Never null, so a handler never has to check.
     */
    public static Key fromGlfw(int glfwCode) {
        return BY_GLFW_CODE.getOrDefault(glfwCode, Key.UNKNOWN);
    }

    /** Unpacks GLFW's modifier bitmask into the set Core's events carry. */
    public static Set<Modifier> modifiers(int glfwMods) {
        Set<Modifier> active = EnumSet.noneOf(Modifier.class);
        if ((glfwMods & GLFW.GLFW_MOD_CONTROL) != 0) {
            active.add(Modifier.CTRL);
        }
        if ((glfwMods & GLFW.GLFW_MOD_SHIFT) != 0) {
            active.add(Modifier.SHIFT);
        }
        if ((glfwMods & GLFW.GLFW_MOD_ALT) != 0) {
            active.add(Modifier.ALT);
        }
        if ((glfwMods & GLFW.GLFW_MOD_SUPER) != 0) {
            active.add(Modifier.SUPER);
        }
        return active;
    }
}
