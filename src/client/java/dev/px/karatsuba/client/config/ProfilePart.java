package dev.px.karatsuba.client.config;

import dev.px.karatsuba.client.gui.apple.shell.style.Appearance;
import dev.px.karatsuba.client.hud.group.HudGroups;
import dev.px.karatsuba.client.hud.style.HudStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a profile can include, in words a person would choose by: the modules, the
 * HUD, the look, the keybinds, and the client's own settings.
 *
 * <p>Each part is a group of config sections. A section this table does not name
 * is a client setting, so a section added later is never left out of every part;
 * it goes in Client Settings until it is given a better home here.
 */
public enum ProfilePart {

    MODULES("Modules", "Every module: on or off, its keybind and its settings", "modules"),
    HUD("HUD", "Where each HUD element sits, its settings, and the HUD Style", "hud", HudGroups.ID, HudStyle.ID),
    APPEARANCE("Appearance", "The theme, the interface and how it looks", "theme", Appearance.ID, "gui", "interface"),
    KEYBINDS("Keybinds", "The keys that open the GUI and the editors", BindsSection.ID),
    SETTINGS("Client Settings", "Interaction, mining, automation, alerts and the rest of Client Settings");

    private final String label;
    private final String description;
    private final List<String> sections;

    ProfilePart(String label, String description, String... sections) {
        this.label = label;
        this.description = description;
        this.sections = List.of(sections);
    }

    /** @return the key it is saved under: "modules", "hud" */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    /** @return one sentence on what it covers */
    public String description() {
        return description;
    }

    /** @return the part a config section belongs to: the one that names it, else {@link #SETTINGS} */
    public static ProfilePart of(String sectionId) {
        for (ProfilePart part : values()) {
            if (part.sections.contains(sectionId)) {
                return part;
            }
        }
        return SETTINGS;
    }

    /** @return the part saved under {@code key}, or null */
    public static ProfilePart byKey(String key) {
        for (ProfilePart part : values()) {
            if (part.key().equals(key)) {
                return part;
            }
        }
        return null;
    }

    /** @return every part's key, in order: the options of a list of parts */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (ProfilePart part : values()) {
            keys.add(part.key());
        }
        return keys;
    }

    /** @return the label for a key, or the key itself if no part has it */
    public static String labelOf(String key) {
        ProfilePart part = byKey(key);
        return part == null ? key : part.label;
    }

    /** @return {@code parts} as words, in order: "Modules, HUD" or "Everything" */
    public static String describe(Set<ProfilePart> parts) {
        if (parts.size() == values().length) {
            return "Everything";
        }
        List<String> labels = new ArrayList<>();
        for (ProfilePart part : values()) {
            if (parts.contains(part)) {
                labels.add(part.label);
            }
        }
        return labels.isEmpty() ? "Nothing" : String.join(", ", labels);
    }
}
