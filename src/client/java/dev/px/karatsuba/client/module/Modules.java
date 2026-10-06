package dev.px.karatsuba.client.module;

import dev.px.core.Core;
import dev.px.core.module.Module;
import dev.px.karatsuba.client.module.combat.KillAuraModule;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.exploit.AntiHungerModule;
import dev.px.karatsuba.client.module.misc.FakePlayerModule;
import dev.px.karatsuba.client.module.misc.RecorderModule;
import dev.px.karatsuba.client.module.misc.ServerAlertsModule;
import dev.px.karatsuba.client.module.misc.UpdateCheckModule;
import dev.px.karatsuba.client.module.movement.SpeedModule;
import dev.px.karatsuba.client.module.movement.SprintModule;
import dev.px.karatsuba.client.module.render.BlockOverlayModule;
import dev.px.karatsuba.client.module.render.EspModule;
import dev.px.karatsuba.client.module.render.FreeCamModule;
import dev.px.karatsuba.client.module.render.FullbrightModule;
import dev.px.karatsuba.client.module.render.HoleEspModule;
import dev.px.karatsuba.client.module.render.NameTagsModule;
import dev.px.karatsuba.client.module.render.RenderAdjustmentsModule;
import dev.px.karatsuba.client.module.utility.AntiAfkModule;
import dev.px.karatsuba.client.module.utility.AntiSoundModule;
import dev.px.karatsuba.client.module.utility.AutoToolModule;
import dev.px.karatsuba.client.module.utility.MiddleClickModule;
import dev.px.karatsuba.client.module.utility.XCarryModule;

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
                new KillAuraModule(),
                new VelocityModule(),

                // Movement
                new SpeedModule(),
                new SprintModule(),

                // Render
                new BlockOverlayModule(),
                new EspModule(),
                new FreeCamModule(),
                new FullbrightModule(),
                new HoleEspModule(),
                new NameTagsModule(),
                new RenderAdjustmentsModule(),

                // Player

                // Utility
                new AntiAfkModule(),
                new AntiSoundModule(),
                new AutoToolModule(),
                new MiddleClickModule(),
                new XCarryModule(),

                // Exploit
                new AntiHungerModule(),

                // Misc
                new FakePlayerModule(),
                new RecorderModule(),
                new ServerAlertsModule(),
                new UpdateCheckModule(),
        };
    }
}
