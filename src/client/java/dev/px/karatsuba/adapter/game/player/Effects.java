package dev.px.karatsuba.adapter.game.player;

import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.asset.GameTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * The local player's status effects: what is on them, how strong, and for how long.
 *
 * <p>Snapshots, in plain values, so the client can list them without naming a game
 * type; and each effect's icon, read from the game's textures (a resource pack's,
 * if one replaces them). Game thread only.
 */
public final class Effects {

    /**
     * One effect on the player, as it was read.
     *
     * @param id        the effect's registry id, {@code minecraft:speed}
     * @param name      its name in the player's language, {@code Speed}
     * @param amplifier 0 for level I, 1 for II, and so on
     * @param ticksLeft how long it has left, in game ticks; meaningless when {@code infinite}
     * @param infinite  whether it never runs out (a beacon's, a command's)
     */
    public record Active(String id, String name, int amplifier, int ticksLeft, boolean infinite) {

        /** @return seconds left, rounded down; 0 for an infinite effect */
        public int secondsLeft() {
            return infinite ? 0 : ticksLeft / 20;
        }
    }

    private Effects() {
    }

    /** @return the effects on the player now, in the game's order; empty outside a world */
    public static List<Active> active() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            return List.of();
        }
        List<Active> active = new ArrayList<>();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            String id = effect.getEffectType().getKey().map(key -> key.getValue().toString()).orElse("");
            active.add(new Active(id, effect.getEffectType().value().getName().getString(), effect.getAmplifier(),
                    effect.getDuration(), effect.isInfinite()));
        }
        return active;
    }

    /**
     * @param effectId an effect's registry id, {@code minecraft:speed}
     * @return its icon, the 18 by 18 picture the inventory shows, drawn sharp; null if
     *         the effect has none
     */
    public static Texture icon(String effectId) {
        int colon = effectId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : effectId.substring(0, colon);
        String path = colon < 0 ? effectId : effectId.substring(colon + 1);
        return GameTextures.pixelArt(namespace + ":textures/mob_effect/" + path + ".png");
    }
}
