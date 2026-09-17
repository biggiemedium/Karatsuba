package dev.px.karatsuba.client.config;

import com.google.gson.JsonObject;
import dev.px.core.Core;
import dev.px.core.config.ConfigSection;
import dev.px.core.setting.Setting;

/**
 * Persists the action keybinds: Click GUI, HUD Editor, Profiles.
 *
 * <p>Core saves a module's keybind as part of the {@code modules} section, but the
 * binds registered through {@code InputService.register} are not part of any
 * section it creates. {@code InputService.getActionSettings()} exists and is
 * documented as being "so a config section can persist them" &mdash; Core just
 * never registers one. Without this, rebinding the click GUI works until the next
 * restart and then silently reverts.
 *
 * <p>Keyed by the action name rather than by index, matching how Core's own
 * sections behave: adding, removing or reordering actions cannot then shift a
 * saved bind onto the wrong one.
 *
 * <p>This is also the shape to copy for any client-owned state worth keeping. A
 * {@code ConfigSection} is three methods, it joins the same profile file as
 * everything else, and it is saved and loaded by the same machinery.
 */
public final class BindsSection implements ConfigSection {

    private static final String ID = "binds";

    private BindsSection() {
    }

    /**
     * Registers the section with the config service.
     *
     * <p>Must run before {@code Core.start()}, because the load inside
     * {@code start()} is what applies the saved binds over the defaults.
     */
    public static void install() {
        Core.config().register(new BindsSection());
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonObject save() {
        JsonObject json = new JsonObject();
        for (Setting<?> bind : Core.input().getActionSettings()) {
            json.add(bind.getName(), bind.toJson());
        }
        return json;
    }

    @Override
    public void load(JsonObject json) {
        for (Setting<?> bind : Core.input().getActionSettings()) {
            // An action in the file that no longer exists is ignored, and one that
            // exists but is absent from the file keeps its default.
            if (json.has(bind.getName())) {
                bind.fromJson(json.get(bind.getName()));
            }
        }
    }
}
