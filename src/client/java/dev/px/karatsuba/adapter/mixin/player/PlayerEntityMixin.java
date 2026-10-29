package dev.px.karatsuba.adapter.mixin.player;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.movement.AttackSlowdown;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The slowdown after a sprinting hit, which {@link AttackSlowdown} can skip; and
 * the server's word that an item was finished, for {@link Players#finishedUses()}.
 *
 * <p>Both slowdown calls sit together in {@code attack}, after the target's knockback:
 * the velocity times 0.6 and the end of the sprint. They are the only
 * {@code setVelocity} and {@code setSprinting} on the attacker in that method.
 * Only the local player's are ever skipped.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @WrapWithCondition(method = "attack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private boolean karatsuba$attackSlow(PlayerEntity self, Vec3d velocity) {
        return !karatsuba$keepSprint();
    }

    @WrapWithCondition(method = "attack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V"))
    private boolean karatsuba$attackStopSprint(PlayerEntity self, boolean sprinting) {
        return !karatsuba$keepSprint();
    }

    /**
     * The server saying the local player finished an item ({@code CONSUME_ITEM},
     * sent by {@code ServerPlayerEntity.consumeItem}): the bite counted. Counted
     * before vanilla handles it, so whether the client still thought it was eating
     * makes no difference.
     */
    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void karatsuba$handleStatus(byte status, CallbackInfo callback) {
        if (status == EntityStatuses.CONSUME_ITEM && (Object) this == MinecraftClient.getInstance().player) {
            Players.noteFinishedUse();
        }
    }

    @Unique
    private boolean karatsuba$keepSprint() {
        return (Object) this == MinecraftClient.getInstance().player && AttackSlowdown.skipped();
    }
}
