package dev.px.karatsuba.client.gui.apple.shell.section.home;

import dev.px.core.Core;
import dev.px.core.module.Module;
import dev.px.core.module.toggle.ModuleToggleEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The modules switched on or off most recently this session, newest first: what
 * Home offers under "Jump back in", since what was just used is the likeliest
 * thing to be wanted again.
 *
 * <p>Toggles from a keybind count as much as toggles from the GUI. Kept for the
 * session only: a fresh launch starts from what is enabled, not from history.
 */
public final class RecentModules {

    private static final int KEEP = 5;
    private static final List<Module> RECENT = new ArrayList<>();
    private static boolean installed;

    private RecentModules() {
    }

    /** Starts listening. At boot, so toggles made before the GUI first opens count too. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        Core.bus().on(ModuleToggleEvent.class, event -> {
            // Config loads at startup switch modules too; those are not something you did.
            if (!Core.platform().isInGame()) {
                return;
            }
            Module module = event.getModule();
            RECENT.remove(module);
            RECENT.add(0, module);
            while (RECENT.size() > KEEP) {
                RECENT.remove(RECENT.size() - 1);
            }
        });
    }

    /** @return the recent modules, newest first */
    public static List<Module> all() {
        return List.copyOf(RECENT);
    }
}
