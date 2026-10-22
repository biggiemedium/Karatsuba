package dev.px.karatsuba.client.setting;

import dev.px.core.setting.Setting;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a setting is called on screen, where that differs from its name. A
 * setting's name is its config key, so renaming it loses what players saved; a
 * label changes only what they read. The HUD's per-element "Style" group, say, is
 * shown as "Display" ({@code hud/DESIGN.md} §1.8) and still saved as "Style".
 */
public final class SettingLabels {

    private static final Map<Setting<?>, String> LABELS = Collections.synchronizedMap(new WeakHashMap<>());

    private SettingLabels() {
    }

    /** Shows {@code setting} as {@code label}. @return the setting */
    public static <S extends Setting<?>> S relabel(S setting, String label) {
        LABELS.put(setting, label);
        return setting;
    }

    /** @return the name to show for {@code setting}: its label, or its name */
    public static String of(Setting<?> setting) {
        String label = LABELS.get(setting);
        return label != null ? label : setting.getName();
    }
}
