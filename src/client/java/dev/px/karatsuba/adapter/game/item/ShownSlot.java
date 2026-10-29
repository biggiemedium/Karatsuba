package dev.px.karatsuba.adapter.game.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * The hotbar slot the player is shown, when it is not the one selected.
 *
 * <p>For a slot that has to be <em>really</em> selected but that the player should
 * not see: eating, where the server and Grim must both hold the food for every
 * tick of the bite. The hotbar's highlight, the item-name popup and the
 * first-person hand show this slot instead ({@code InGameHudMixin},
 * {@code HeldItemRendererMixin}). Nothing else changes: the game, every packet and
 * the third-person model use the slot really selected, so to the server it is an
 * ordinary eat.
 *
 * <p>Set and cleared by the client's inventory service once a tick. Game thread
 * only.
 */
public final class ShownSlot {

    private static int slot = Inventories.NOT_FOUND;

    private ShownSlot() {
    }

    /** Shows {@code slot}, 0 to 8, in place of the one selected, until {@link #clear()}. */
    public static void show(int slot) {
        ShownSlot.slot = slot >= 0 && slot < Inventories.HOTBAR_SIZE ? slot : Inventories.NOT_FOUND;
    }

    /** Shows the slot selected again. */
    public static void clear() {
        slot = Inventories.NOT_FOUND;
    }

    /** @return the slot shown in place of the selected one, or {@link Inventories#NOT_FOUND} */
    public static int get() {
        return slot;
    }

    /** For the mixins: the slot to draw as selected, given the one that is. */
    public static int selected(int selected) {
        return slot == Inventories.NOT_FOUND ? selected : slot;
    }

    /** For the mixins: the stack to draw in the main hand, given the one there. */
    public static ItemStack mainHand(ItemStack held) {
        if (slot == Inventories.NOT_FOUND) {
            return held;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? held : player.getInventory().getStack(slot);
    }
}
