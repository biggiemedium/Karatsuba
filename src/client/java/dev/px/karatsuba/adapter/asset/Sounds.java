package dev.px.karatsuba.adapter.asset;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * The clips this mod bundles, played through the game's sound system.
 *
 * <p>A clip is {@code assets/karatsuba/sounds/<name>.ogg}, named in
 * {@code assets/karatsuba/sounds.json}; see that folder's readme. It is played as
 * an interface sound: heard at the same volume wherever the player is, under the
 * Master slider, like a menu click.
 *
 * <p>No registry entry: the game resolves a sound by its id against
 * {@code sounds.json} when it plays it, and only a sound sent over the network
 * needs registering. These never are.
 *
 * <p><b>Game thread only.</b>
 */
public final class Sounds {

    private Sounds() {
    }

    /**
     * Plays a bundled clip once.
     *
     * @param name   the clip's name in {@code sounds.json}, for example {@code "zeldagameover"}
     * @param volume 0 to 1; the game clamps anything louder
     */
    public static void play(String name, float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSoundManager() == null) {
            return;
        }
        SoundEvent sound = SoundEvent.of(Identifier.of("karatsuba", name));
        client.getSoundManager().play(PositionedSoundInstance.master(sound, 1f, volume));
    }
}
