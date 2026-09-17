package dev.px.karatsuba.client.module;

import dev.px.core.Core;
import dev.px.core.module.Module;
import dev.px.karatsuba.client.module.misc.UpdateCheckModule;
import dev.px.karatsuba.client.module.movement.SprintModule;
import dev.px.karatsuba.client.module.render.EspModule;
import dev.px.karatsuba.client.module.render.FullbrightModule;

/**
 * The module catalogue.
 *
 * <p>One list, grouped by category, so adding a module is two steps and neither of
 * them is "read the bootstrap": write the class under {@code module/<category>/},
 * then add a line here.
 *
 * <p>It lives beside the modules it lists rather than at the top of the client
 * package. A catalogue is part of the thing it catalogues &mdash; the list and the
 * classes on it are edited in the same breath &mdash; and keeping the two together
 * leaves the top level as the boot sequence and nothing else.
 *
 * <p>Order is display order in the click GUI within a category, so the list is
 * kept alphabetical inside each group.
 */
public final class Modules {

    private Modules() {
    }

    /** Registers every module. See {@code Karatsuba} for where this sits in the boot. */
    public static void registerAll(Core core) {
        core.getModuleRegistry().registerAll(all());
    }

    public static Module[] all() {
        return new Module[]{
                // Combat

                // Movement
                new SprintModule(),

                // Render
                new EspModule(),
                new FullbrightModule(),

                // Player

                // Misc
                new UpdateCheckModule(),
        };
    }
}
