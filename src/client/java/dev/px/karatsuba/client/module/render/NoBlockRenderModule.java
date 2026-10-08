package dev.px.karatsuba.client.module.render;

import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.render.BlockVisibility;
import dev.px.karatsuba.client.setting.ItemSetting;

/**
 * Draws the world with only the blocks on the list: everything else, stone and
 * dirt and all, is simply not drawn, so ores, debris and spawners stand out in
 * open space.
 *
 * <p>A shown block draws every face a hidden neighbour would have covered, and
 * chunk culling is off so nothing behind a wall of hidden stone is skipped. With
 * {@code Full Bright} the shown blocks are lit fully: buried in stone they have no
 * light of their own and would otherwise be black. All of it lives in the
 * adapter's {@link BlockVisibility}; this only hands it the list.
 *
 * <p>Turning it on or off, or changing the list, rebuilds every chunk, which
 * takes a moment. It only changes what is drawn: what the server sends, what you
 * can walk through and what you can click are all as before. A server running an
 * anti-xray engine sends fake ores, and those draw like real ones.
 *
 * <p>Vanilla's chunk renderer only; with Sodium installed it has no effect.
 */
@ModuleInfo(name = "No Block Render", description = "Draws only the blocks on your list",
        category = "Render")
public final class NoBlockRenderModule extends Module {

    private final ItemSetting shown = ItemSetting.blocks("Shown Blocks",
                    "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
                    "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
                    "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
                    "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
                    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
                    "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
                    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
                    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
                    "minecraft:nether_quartz_ore", "minecraft:ancient_debris",
                    "minecraft:spawner", "minecraft:water", "minecraft:lava")
            .describe("The only blocks drawn while this is on")
            .onChange(ids -> apply());

    private final BooleanSetting fullBright = bool("Full Bright", true)
            .describe("Light shown blocks fully; buried ones are dark otherwise")
            .onChange(on -> apply());

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        BlockVisibility.all();
    }

    private void apply() {
        if (isEnabled()) {
            BlockVisibility.only(shown.get(), fullBright.isOn());
        }
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(shown.size());
    }
}
