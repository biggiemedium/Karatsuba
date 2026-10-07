package dev.px.karatsuba.adapter.mixin.player;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Tells the server which hotbar slot is selected, now, the way the game does.
 *
 * <p>The game keeps this private and calls it before every attack and use. A
 * swap of hands acts on the slot the server believes is selected, so moving a
 * hotbar item to the offhand needs the selection sent first &mdash; through this,
 * so the game's own record of what it last sent stays true and it does not send
 * a stale one later.
 */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerInvoker {

    @Invoker("syncSelectedSlot")
    void karatsuba$syncSelectedSlot();

    /**
     * The slot the game last told the server is selected: the server's view of
     * the hotbar. {@code syncSelectedSlot} sends only when the selection differs
     * from it, which is what keeps a slot from being sent twice in a row.
     */
    @Accessor("lastSelectedSlot")
    int karatsuba$getLastSelectedSlot();
}
