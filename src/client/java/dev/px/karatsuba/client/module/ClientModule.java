package dev.px.karatsuba.client.module;

import dev.px.core.module.Module;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import lombok.Getter;

/**
 * Every Karatsuba module: a {@link Module} with a key and an ArrayList switch.
 *
 * <p>Up to Core 2.2 every module got both from Core. Since 2.3 a module has no
 * settings it did not declare, and a client that wants every module bindable says
 * so once, in its own base class &mdash; this one. The names are the ones Core
 * used, so configs saved before the upgrade keep their binds and their hidden
 * modules.
 */
@Getter
public abstract class ClientModule extends Module {

    /** Toggles the module. Unbound by default; middle-click the row for hold. */
    private final BindSetting keybind = toggledBy(bind("Keybind"));

    /** Whether the module shows in the ArrayList HUD element while enabled. */
    private final BooleanSetting visible = bool("Visible", true)
            .describe("Show this module in the ArrayList");
}
