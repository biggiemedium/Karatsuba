package dev.px.karatsuba.client.module.inventory;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.inventory.cleaner.Junk;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.inventory.InventoryClick;
import dev.px.karatsuba.client.setting.ItemSetting;

/**
 * Throws out the items on its list, one a tick: from the main inventory, and
 * from the hotbar too with {@code Clean Hotbar}.
 *
 * <p>While on, it publishes what it calls junk ({@link Junk}), so Hotbar
 * Replenish never brings junk to the hotbar for it to throw back out. Toggle it
 * with its bind for looting.
 *
 * <h2>How it clicks</h2>
 * Through {@code Services.inventory()}, which sends one click a tick, only on a
 * tick the server believes you are standing still. It asks lowest
 * ({@link ClaimPriority#LOW}), so armour, refills, Auto Totem and anything else
 * go first, and it never stops you to click.
 */
@ModuleInfo(name = "Inventory Cleaner", description = "Throws out junk", category = "Inventory")
public final class InventoryCleanerModule extends ClientModule {

    private final ItemSetting items = ItemSetting.items("Items",
                    "minecraft:rotten_flesh", "minecraft:poisonous_potato", "minecraft:spider_eye",
                    "minecraft:bone_meal")
            .describe("What to throw out");

    private final BooleanSetting hotbar = bool("Clean Hotbar", true)
            .describe("Also throw listed items from the hotbar");

    @Override
    protected void onEnable() {
        Junk.use(this::isJunk);
    }

    @Override
    protected void onDisable() {
        Junk.clear();
    }

    /**
     * Plans at the end of the tick, from the inventory as this tick left it; the
     * service sends the click at the start of the next.
     */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            return;
        }
        int junk = firstJunk();
        if (junk != Inventories.NOT_FOUND) {
            Services.inventory().click(this, InventoryClick.drop(junk), ClaimPriority.LOW);
        }
    }

    /** @return the first slot holding junk, main inventory before the hotbar, or NOT_FOUND */
    private int firstJunk() {
        for (int slot = Inventories.MAIN_START; slot < Inventories.ARMOR_START; slot++) {
            if (isJunk(Inventories.stackAt(slot))) {
                return slot;
            }
        }
        if (hotbar.isOn()) {
            for (int slot = 0; slot < Inventories.HOTBAR_SIZE; slot++) {
                if (isJunk(Inventories.stackAt(slot))) {
                    return slot;
                }
            }
        }
        return Inventories.NOT_FOUND;
    }

    /** Whether this item is on the list. */
    private boolean isJunk(GameItem item) {
        return !item.isEmpty() && items.contains(item.getId());
    }
}
