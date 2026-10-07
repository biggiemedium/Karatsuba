package dev.px.karatsuba.client.module.inventory;

import dev.px.core.event.Subscribe;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.karatsuba.adapter.event.game.network.ScreenCloseEvent;

/**
 * Keeps items in the four crafting slots of the player's own inventory.
 *
 * <p>Closing the inventory normally drops whatever is in the crafting grid,
 * because the client tells the server the screen is closed and the server tidies
 * up. Suppressing that one message leaves the items where they are, and the four
 * slots become four more slots of carrying capacity.
 *
 * <p>The shortest module in the client, and it is worth reading for what is
 * <em>not</em> in it. The original of this checks the packet type and compares a
 * sync id against the player's screen handler &mdash; two game concepts, both in
 * the module. Here the adapter has already answered the only question that
 * matters, so this is one condition and a cancel.
 *
 * <p><b>Cancelling for a chest would be a bug</b>, not a feature: the server
 * would keep the container open, and the next one the player tried to open would
 * not respond. {@link ScreenCloseEvent#isOwnInventory()} is the guard, and the
 * reason the event carries that flag at all.
 */
@ModuleInfo(name = "X Carry", description = "Carries items in the crafting slots",
        category = "Inventory")
public final class XCarryModule extends Module {

    @Subscribe
    private void onScreenClose(ScreenCloseEvent event) {
        if (event.isOwnInventory()) {
            event.cancel();
        }
    }
}
