package dev.px.karatsuba.client.settings;

import dev.px.core.Core;
import dev.px.core.config.section.SettingsSection;
import dev.px.karatsuba.client.settings.interaction.InteractionSettings;
import dev.px.karatsuba.client.settings.updates.UpdateSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The client's own settings: the catalogue of {@link ClientSection}s.
 *
 * <p>Same shape as {@code Modules}: one list, in the folder with the things it
 * lists, exposing {@code registerAll(Core)}. Each section is saved as a config
 * section of its own (its {@link ClientSection#getId() id} is its file) and
 * edited in the Client Settings screen, apart from the click GUI.
 *
 * <h2>Above the modules</h2>
 * Registered during boot after the services (a section hands itself to the one it
 * governs) and <b>before any module</b>, so the rules modules play by exist before
 * the first of them is created. The dependency runs one way: modules read client
 * settings; a client setting never names a module.
 *
 * <p>Adding a section is a {@link ClientSection} subclass under {@code settings/<section>/}
 * and a line in {@link #create()}.
 */
public final class ClientSettings {

    private static List<ClientSection> sections = Collections.emptyList();

    private ClientSettings() {
    }

    /** Every section, in the order the screen shows them. */
    private static ClientSection[] create() {
        return new ClientSection[]{
                new InteractionSettings(),
                new UpdateSettings(),
        };
    }

    /**
     * Creates every section, registers its config section, and lets it wire itself
     * to what it governs.
     *
     * <p>After {@code Services.registerAll}, before {@code Modules.registerAll}, and
     * before {@code Core.start()}, which loads the saved values over the defaults.
     */
    public static void registerAll(Core core) {
        List<ClientSection> created = new ArrayList<>();
        for (ClientSection section : create()) {
            core.getConfigService().register(new SettingsSection(section.getId(), section));
            section.onRegister(core);
            created.add(section);
        }
        sections = Collections.unmodifiableList(created);
    }

    /** @return every section, in the order the screen shows them */
    public static List<ClientSection> all() {
        return sections;
    }

    /**
     * @return the section of this type
     * @throws IllegalStateException before {@link #registerAll}, or for a type not
     *         in the catalogue: either is a boot-order or catalogue mistake
     */
    public static <S extends ClientSection> S get(Class<S> type) {
        for (ClientSection section : sections) {
            if (type.isInstance(section)) {
                return type.cast(section);
            }
        }
        throw new IllegalStateException(type.getSimpleName() + " is not registered;"
                + " ClientSettings.registerAll(core) runs during boot, before Core.start(),"
                + " and lists every section in create()");
    }

    /** How every module's rotations, clicks and packets are checked and sent. */
    public static InteractionSettings interaction() {
        return get(InteractionSettings.class);
    }

    /** Update checks. */
    public static UpdateSettings updates() {
        return get(UpdateSettings.class);
    }
}
