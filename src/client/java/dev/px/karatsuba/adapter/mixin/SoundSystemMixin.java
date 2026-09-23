package dev.px.karatsuba.adapter.mixin;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.SoundPlayEvent;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every sound, before it reaches the mixer.
 *
 * <p>{@code SoundSystem.play} is the single point everything the client plays
 * passes through &mdash; a mob, a block, a UI click, a record &mdash; which is
 * what makes one injection enough. Cancelling here stops the sound being queued
 * at all rather than muting it, so nothing is allocated and no channel is taken.
 *
 * <h2>What a sound instance knows at HEAD</h2>
 * Less than its accessors suggest. {@code getId}, {@code getCategory} and the
 * coordinates are fields on the instance and are readable immediately.
 * {@code getVolume} and {@code getPitch} are not: they multiply through the
 * resolved {@code Sound}, which {@code play} attaches a few lines further down
 * by calling {@code getSoundSet}. Reading either here dereferences a null field
 * and takes the game down on the first UI click.
 *
 * <p>That is why {@link SoundPlayEvent} carries neither. The alternative &mdash;
 * injecting after the lookup &mdash; buys two numbers and loses the ability to
 * cancel before the work is done, which is the entire point of being here.
 */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void karatsuba$play(SoundInstance sound, CallbackInfo callback) {
        if (sound == null || sound.getId() == null || sound.getCategory() == null) {
            return;
        }
        SoundPlayEvent event = new SoundPlayEvent(
                sound.getId().toString(),
                sound.getCategory().getName(),
                Vec3.of(sound.getX(), sound.getY(), sound.getZ()));

        if (Events.cancelled(event)) {
            callback.cancel();
        }
    }
}
