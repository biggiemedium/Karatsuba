package dev.px.karatsuba.client.module.mining;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Holds the fastest tool for whatever is being mined.
 *
 * <p>Deliberately the <b>other half</b> of the slot arbitration test. It wants
 * the hotbar for the same reason {@code KillAuraModule} does and asks at
 * {@link ClaimPriority#NORMAL} rather than {@code HIGH}, so the two have a
 * settled answer to wanting it at once: mid-fight the sword wins, and the moment
 * the aura stops asking this takes the hotbar back without either module
 * knowing the other exists.
 *
 * <p>Before the arbiter that was the classic silent conflict &mdash; both
 * assigning the selected slot, the winner decided by registration order, and the
 * fix being a direct reference from one module to the other.
 *
 * <h2>No block-break event needed</h2>
 * The original of this hangs off an "attacked a block" event. The same thing
 * falls out of two seams that already exist: the attack key is down, and the
 * crosshair is on a block. That is what mining <em>is</em>, it needs no mixin,
 * and it has the useful property of claiming the tool a tick before the first
 * hit rather than during it.
 */
@ModuleInfo(name = "Auto Tool", description = "Holds the best tool for the block being mined",
        category = "Mining")
public final class AutoToolModule extends Module {

    private final BooleanSetting whileHolding = bool("Only While Mining", true)
            .describe("Off keeps the tool selected whenever a block is under the crosshair");

    /** What the tool was picked for, for the ArrayList readout. */
    private String blockName = "";

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        Targets.Block target = Targets.block();
        if (target == null || (whileHolding.isOn() && !Inputs.pressed(Inputs.Control.ATTACK))) {
            blockName = "";
            return;
        }

        int tool = Inventories.bestToolSlotFor(target.getBlockId());
        if (tool == Inventories.NOT_FOUND) {
            // Nothing beats a bare hand here, so do not claim: taking the hotbar
            // to hold the same thing the player already had is a swap for nothing.
            blockName = "";
            return;
        }

        blockName = shortName(target.getBlockId());
        Services.inventory().request(this, tool, ClaimPriority.NORMAL);
    }

    /** {@code minecraft:oak_log} reads better as {@code oak_log} in a HUD row. */
    private static String shortName(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }

    @Override
    protected void onDisable() {
        blockName = "";
    }

    @Override
    public String getDisplayInfo() {
        return blockName;
    }
}
