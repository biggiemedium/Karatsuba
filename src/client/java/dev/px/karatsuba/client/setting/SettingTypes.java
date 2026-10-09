package dev.px.karatsuba.client.setting;

import dev.px.karatsuba.adapter.screen.Gui;
import dev.px.core.Core;
import dev.px.gui.legacy.SettingRenderer;

/**
 * The setting types Karatsuba adds to Core's, and the GUI row for each.
 *
 * <p>Declaring one of these as a field is all a module does; this registers the
 * row that draws it, so it appears in the click GUI, the Client Settings screen
 * and the HUD editor alike.
 */
public final class SettingTypes {

    private SettingTypes() {
    }

    /** @param core unused; taken so every catalogue has the same shape */
    public static void registerAll(Core core) {
        Gui.service().getRenderers().register(
                SettingRenderer.of(ItemSetting.class, (ItemSetting setting) -> new ItemSettingRow(setting)));
    }
}
