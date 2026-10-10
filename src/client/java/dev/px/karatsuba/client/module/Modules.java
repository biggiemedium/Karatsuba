package dev.px.karatsuba.client.module;

import dev.px.core.Core;
import dev.px.core.module.Module;
import dev.px.karatsuba.client.module.building.ScaffoldModule;
import dev.px.karatsuba.client.module.combat.AutoTotemModule;
import dev.px.karatsuba.client.module.combat.AutoXPModule;
import dev.px.karatsuba.client.module.combat.BedAuraModule;
import dev.px.karatsuba.client.module.combat.CrystalAuraModule;
import dev.px.karatsuba.client.module.combat.HoleFillModule;
import dev.px.karatsuba.client.module.combat.KillAuraModule;
import dev.px.karatsuba.client.module.combat.SurroundModule;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.combat.WebAuraModule;
import dev.px.karatsuba.client.module.inventory.InventoryManagerModule;
import dev.px.karatsuba.client.module.inventory.XCarryModule;
import dev.px.karatsuba.client.module.inventory.ElytraSwapModule;
import dev.px.karatsuba.client.module.mining.AutoToolModule;
import dev.px.karatsuba.client.module.movement.ElytraFlyModule;
import dev.px.karatsuba.client.module.movement.ParkourModule;
import dev.px.karatsuba.client.module.movement.SafeWalkModule;
import dev.px.karatsuba.client.module.movement.SpeedModule;
import dev.px.karatsuba.client.module.movement.SprintModule;
import dev.px.karatsuba.client.module.movement.StepModule;
import dev.px.karatsuba.client.module.render.BlockOverlayModule;
import dev.px.karatsuba.client.module.render.EspModule;
import dev.px.karatsuba.client.module.render.FreeCamModule;
import dev.px.karatsuba.client.module.render.HoleEspModule;
import dev.px.karatsuba.client.module.render.NameTagsModule;
import dev.px.karatsuba.client.module.render.NoBlockRenderModule;
import dev.px.karatsuba.client.module.render.PredictEspModule;
import dev.px.karatsuba.client.module.render.ProjectilesModule;
import dev.px.karatsuba.client.module.render.CombatInfoModule;
import dev.px.karatsuba.client.module.render.RenderAdjustmentsModule;
import dev.px.karatsuba.client.module.render.SearchModule;
import dev.px.karatsuba.client.module.render.ShadersModule;
import dev.px.karatsuba.client.module.utility.AntiAfkModule;
import dev.px.karatsuba.client.module.utility.AntiHungerModule;
import dev.px.karatsuba.client.module.utility.AntiSoundModule;
import dev.px.karatsuba.client.module.utility.FakePlayerModule;
import dev.px.karatsuba.client.module.utility.MiddleClickModule;
import dev.px.karatsuba.client.module.utility.RecorderModule;

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
                new AutoTotemModule(),
                new AutoXPModule(),
                new BedAuraModule(),
                new CrystalAuraModule(),
                new HoleFillModule(),
                new KillAuraModule(),
                new SurroundModule(),
                new VelocityModule(),
                new WebAuraModule(),

                // Movement
                new ElytraFlyModule(),
                new ParkourModule(),
                new SafeWalkModule(),
                new SpeedModule(),
                new SprintModule(),
                new StepModule(),

                // Building
                new ScaffoldModule(),

                // Mining
                new AutoToolModule(),

                // Inventory
                new ElytraSwapModule(),
                new InventoryManagerModule(),
                new XCarryModule(),

                // Render
                new BlockOverlayModule(),
                new CombatInfoModule(),
                new EspModule(),
                new FreeCamModule(),
                new HoleEspModule(),
                new NameTagsModule(),
                new NoBlockRenderModule(),
                new PredictEspModule(),
                new ProjectilesModule(),
                new RenderAdjustmentsModule(),
                new SearchModule(),
                new ShadersModule(),

                // Utility
                new AntiAfkModule(),
                new AntiHungerModule(),
                new AntiSoundModule(),
                new FakePlayerModule(),
                new MiddleClickModule(),
                new RecorderModule(),
        };
    }
}
