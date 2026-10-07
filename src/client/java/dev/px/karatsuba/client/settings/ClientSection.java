package dev.px.karatsuba.client.settings;

import dev.px.core.Core;
import dev.px.core.setting.SettingHolder;

/**
 * One section of the client's own settings: how the whole client behaves, as
 * opposed to what a module does in game.
 *
 * <h2>Above the modules</h2>
 * Client settings sit higher than modules. A section tells modules how to behave
 * &mdash; {@link InteractionSettings} decides how every module's rotations, clicks
 * and packets go out &mdash; and modules read it. A section never reads or names a
 * module. Sections are registered before any module exists and wired to what they
 * govern in {@link #onRegister}, so by a module's first tick the rules it plays by
 * are already in place.
 *
 * <h2>The shape</h2>
 * The parent of every section, the way {@code Module} is the parent of every
 * module, but lighter, because a section is a page of settings and nothing else:
 * <ul>
 *   <li><b>Who it is</b>, given once to the constructor: an id (its config file),
 *       a name (its window title) and a description.</li>
 *   <li><b>Its settings</b>, registered by being declared, exactly as on a module.</li>
 *   <li><b>{@link #onRegister}</b>: wiring itself to what it governs, once, at boot.</li>
 * </ul>
 * No on/off state, no keybind, no event handlers of its own: those are what make
 * something a module. A section that needs to react to the game subscribes in
 * {@link #onRegister}.
 *
 * <p>Adding one is a subclass in a subpackage of its own ({@code settings/<section>/},
 * like {@code module/<category>/}) and a line in {@link ClientSettings}.
 */
public abstract class ClientSection extends SettingHolder {

    private final String id;
    private final String name;
    private final String description;

    /**
     * @param id          the config section id, which is also its file name
     *                    ({@code profiles/<profile>/<id>.json})
     * @param name        what the Client Settings screen titles it
     * @param description what it governs, in one line
     */
    protected ClientSection(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    /** @return the config section id, and its file name */
    public final String getId() {
        return id;
    }

    @Override
    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    /**
     * Wires this section to what it governs. Called once at boot, after its config
     * section is registered and before {@code Core.start()} loads the saved values
     * &mdash; so read settings lazily (suppliers, method references), never copy a
     * value here. Does nothing unless overridden.
     */
    protected void onRegister(Core core) {
    }
}
