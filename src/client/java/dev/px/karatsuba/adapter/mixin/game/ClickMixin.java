package dev.px.karatsuba.adapter.mixin.game;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.ClickEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clicks in the world, before the game acts on them: {@link ClickEvent}.
 *
 * <p>HEAD and cancellable on both. {@code doAttack} would hit the entity or start
 * the dig, and swing; {@code doItemUse} would interact, place or use, and swing.
 * Cancelled, neither runs. The use hook sits ahead of {@code doItemUse}'s own
 * "already breaking a block" bail-out, so a right click still arrives while a dig
 * is under way.
 */
@Mixin(MinecraftClient.class)
public abstract class ClickMixin {

    /** What the game waits between repeated uses while the key is held. */
    private static final int USE_COOLDOWN = 4;

    @Shadow
    public HitResult crosshairTarget;

    @Shadow
    public ClientPlayerEntity player;

    @Shadow
    public ClientWorld world;

    @Shadow
    public int attackCooldown;

    @Shadow
    private int itemUseCooldown;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void karatsuba$doAttack(CallbackInfoReturnable<Boolean> callback) {
        // The game's own reasons not to attack at all come first.
        if (attackCooldown > 0 || player == null || player.isRiding() || crosshairTarget == null) {
            return;
        }
        if (Events.cancelled(event(ClickEvent.Button.ATTACK, true))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void karatsuba$doItemUse(CallbackInfo callback) {
        if (player == null || player.isRiding() || crosshairTarget == null) {
            return;
        }
        if (Events.cancelled(event(ClickEvent.Button.USE, Inputs.playerHolds(Inputs.Control.USE)))) {
            // As the game does for any use, so a held key repeats at its pace.
            itemUseCooldown = USE_COOLDOWN;
            callback.cancel();
        }
    }

    private ClickEvent event(ClickEvent.Button button, boolean byPlayer) {
        if (crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && world != null && !world.getBlockState(hit.getBlockPos()).isAir()) {
            return new ClickEvent(button, ClickEvent.Target.BLOCK,
                    Voxels.toVec3i(hit.getBlockPos()), Voxels.toCore(hit.getSide()), byPlayer);
        }
        ClickEvent.Target target = crosshairTarget.getType() == HitResult.Type.ENTITY
                ? ClickEvent.Target.ENTITY : ClickEvent.Target.NONE;
        return new ClickEvent(button, target, null, null, byPlayer);
    }
}
