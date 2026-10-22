package dev.px.karatsuba.adapter.mixin.game;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.HotbarPickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hotbar number key, after it has selected its slot: {@link HotbarPickEvent}.
 *
 * <p>The one write to {@code selectedSlot} in {@code handleInputEvents} is the
 * hotbar keys'. The mouse wheel's pick is in {@code MouseMixin}.
 */
@Mixin(MinecraftClient.class)
public abstract class HotbarPickMixin {

    @Shadow
    public ClientPlayerEntity player;

    @Inject(method = "handleInputEvents", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void karatsuba$hotbarKey(CallbackInfo callback) {
        if (player != null) {
            Events.post(new HotbarPickEvent(player.getInventory().selectedSlot));
        }
    }
}
