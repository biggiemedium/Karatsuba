package dev.px.karatsuba.client.module.combat;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.client.module.ClientModule;

/**
 * @author James
 */
@ModuleInfo(name = "Phase", description = "Allows you to phase into or through blocks", category = "Combat")
public class PhaseModule extends ClientModule {

    EnumSetting<Mode> mode = enumOf("Mode", Mode.Pearl);

    private enum Mode {
        Pearl, Phase
    }


}
