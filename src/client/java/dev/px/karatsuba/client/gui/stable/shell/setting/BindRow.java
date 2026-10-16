package dev.px.karatsuba.client.gui.stable.shell.setting;

import dev.px.core.input.BindMode;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.setting.impl.BindSetting;

import java.util.Set;

/**
 * A keybind: a chip naming the key. Press it and the next key you press is the
 * bind; Escape cancels and Backspace or Delete clears. A mouse button other than
 * the left one binds too. The middle button switches between toggling on a press
 * and holding, as in the old click GUI.
 */
public final class BindRow extends SettingRow<BindSetting> {

    private boolean listening;

    /** Set when a key was just bound, so the character that key types goes nowhere. */
    private boolean swallowNext;

    public BindRow(BindSetting setting) {
        super(setting);
    }

    public boolean isListening() {
        return listening;
    }

    /** What the chip says. */
    public String chip() {
        if (listening) {
            return "Press a key…";
        }
        String key = setting.isBound() ? setting.get().getDisplay() : "None";
        return setting.isHold() ? key + " (hold)" : key;
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (listening) {
            if (button != MouseButton.LEFT) {
                setting.bindTo(button);
            }
            listening = false;
            return true;
        }
        if (button == MouseButton.MIDDLE) {
            setting.setMode(setting.isHold() ? BindMode.PRESS : BindMode.HOLD);
            return true;
        }
        return super.mousePressed(x, y, button);
    }

    @Override
    protected boolean pressed(float x, float y) {
        listen();
        return true;
    }

    @Override
    protected boolean activated() {
        listen();
        return true;
    }

    private void listen() {
        listening = true;
        focus();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    protected boolean keyPressed(Key key, Set<Modifier> modifiers) {
        // A key that types nothing (F5, say) leaves no character to swallow.
        swallowNext = false;
        if (!listening) {
            return false;
        }
        if (key == Key.ESCAPE) {
            listening = false;
        } else if (key == Key.BACKSPACE || key == Key.DELETE) {
            setting.clear();
            listening = false;
        } else if (key.isBound() && !key.isModifier()) {
            setting.bindTo(key, modifiers.toArray(new Modifier[0]));
            listening = false;
            swallowNext = true;
        }
        // A lone modifier waits for the key it modifies.
        return true;
    }

    @Override
    protected boolean charTyped(char character) {
        if (swallowNext) {
            swallowNext = false;
            return true;
        }
        return listening;
    }

    @Override
    protected void focusLost() {
        listening = false;
    }
}
