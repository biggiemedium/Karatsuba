package dev.px.karatsuba.adapter.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.px.karatsuba.adapter.network.NoBlockList;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.BlockListChecker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turns off Mojang's server block list.
 *
 * <p>{@code AllowedAddressResolver.DEFAULT}, which every connection resolves its
 * address through, is built once with {@code BlockListChecker.create()}: the only
 * call to it. Wrapped here to hand back {@link NoBlockList} without calling the
 * original, so Mojang's list is never downloaded, and the address, the server
 * address and every SRV redirect are all allowed.
 */
@Mixin(AllowedAddressResolver.class)
public abstract class AllowedAddressResolverMixin {

    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/BlockListChecker;create()Lnet/minecraft/client/network/BlockListChecker;"))
    private static BlockListChecker karatsuba$noBlockList(Operation<BlockListChecker> original) {
        return NoBlockList.INSTANCE;
    }
}
