package dev.px.karatsuba.adapter.mixin.game;

import dev.px.karatsuba.adapter.game.player.Digging;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's block breaking, pointed at a block a module names.
 *
 * <p>HEAD and cancellable: when {@link Digging} has a request for this tick, it digs
 * it here, exactly where and how the game would have dug the crosshair's block, and
 * the game's own handling (which would cancel it, the key not being held) is
 * skipped. With the player's own attack key held off for the tick
 * ({@code Digging.holdOffHand}), it lets go of the block as the game does with the
 * key up. Otherwise, without a request or with the player's own attack key down,
 * the game runs as it always does.
 */
@Mixin(MinecraftClient.class)
public abstract class BlockBreakingMixin {

    @Shadow
    public int attackCooldown;

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void karatsuba$handleBlockBreaking(boolean breaking, CallbackInfo callback) {
        if (Digging.onHandleBlockBreaking(breaking)) {
            // What the game does first with the key up, kept so skipping the rest
            // leaves its state as it would have been.
            attackCooldown = 0;
            callback.cancel();
        }
    }
}
