package dev.px.karatsuba.client.module.movement;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.module.mode.ModalModule;
import dev.px.karatsuba.client.module.movement.noslow.GrimNoSlow;
import dev.px.karatsuba.client.module.movement.noslow.VanillaNoSlow;

/**
 * Keeps the player's speed while using an item.
 *
 * <p>Like Strafe and Elytra Fly, this file has no logic in it. Adding a mode is
 * a constant, a class in {@code noslow/} and two lines here.
 *
 * @author James
 */
@ModuleInfo(name = "No Slow", description = "Keeps your speed while eating, drinking, blocking or drawing a bow", category = "Movement")
public class NoSlowModule extends ModalModule<NoSlowModule.Mode> {

    public enum Mode {
        VANILLA,
        GRIM
    }

    // Field order is the order the panels appear in, so it matches the enum. Vanilla
    // has no settings yet; give it a panelFor(...) field once it does.
    private final VanillaNoSlow vanilla = attach(new VanillaNoSlow());

    private final GrimNoSlow grim = attach(new GrimNoSlow());
    private final GroupSetting grimPanel = panelFor(grim);

    public NoSlowModule() {
        super(Mode.VANILLA);
    }
}
