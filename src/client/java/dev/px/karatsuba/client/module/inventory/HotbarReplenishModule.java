package dev.px.karatsuba.client.module.inventory;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.inventory.cleaner.Junk;
import dev.px.karatsuba.client.module.inventory.replenish.RefillPlanner;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.inventory.InventoryClick;

/**
 * Keeps the hotbar stocked from the rest of the inventory, with no kit to set
 * up: whatever is in the hotbar is what you want there.
 *
 * <ul>
 *   <li><b>Topping up:</b> a stack below {@code Refill Below} percent full gets
 *       a matching stack shift-clicked onto it.</li>
 *   <li><b>Used up:</b> a slot whose item ran out gets another of it, in that same
 *       slot.</li>
 * </ul>
 * See {@link RefillPlanner}. Nothing Inventory Cleaner would throw out is brought
 * to the hotbar ({@link Junk}).
 *
 * <h2>How it clicks</h2>
 * Through {@code Services.inventory()}, which sends one click a tick, only on a
 * tick the server believes you are standing still. It asks at
 * {@link ClaimPriority#NORMAL}: under Armor Manager's armour, over the cleaner.
 * It never stops you to click.
 */
@ModuleInfo(name = "Hotbar Replenish", description = "Keeps hotbar stacks topped up from the rest of the inventory",
        category = "Inventory")
public final class HotbarReplenishModule extends ClientModule {

    private final NumberSetting<Integer> refillBelow = integer("Refill Below", 50, 5, 95)
            .describe("Top a stack up once it is below this percent full");

    private final BooleanSetting refillEmptied = bool("Refill Used Up", true)
            .describe("When you use the last of a hotbar stack, put another of it in the same slot");

    private final RefillPlanner planner = new RefillPlanner();

    @Override
    protected void onEnable() {
        planner.reset();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        planner.reset();
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
        planner.observe();
        InventoryClick click = planner.next(refillBelow.getInt() / 100f, refillEmptied.isOn(), Junk::is);
        if (click != null) {
            Services.inventory().click(this, click, ClaimPriority.NORMAL);
        }
    }
}
