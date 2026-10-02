package dev.px.karatsuba.adapter.game.player;

import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * What the tab list knows about another player: their ping and game mode.
 *
 * <p>Neither is part of the entity. The server reports them through the player
 * list, keyed by account, so this looks the entity up by id and the list entry
 * up by its UUID. Either can be missing &mdash; an NPC that is a player entity but
 * not a real account, or a list update that has not arrived yet &mdash; and
 * both readers say so rather than inventing a value.
 *
 * <p><b>Game thread only.</b>
 */
public final class PlayerList {

    /** Returned by {@link #ping} when there is no list entry to read. */
    public static final int UNKNOWN_PING = -1;

    public enum GameMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR
    }

    private PlayerList() {
    }

    /** @return the player's latency in milliseconds, or {@link #UNKNOWN_PING} */
    public static int ping(int entityId) {
        PlayerListEntry entry = entryFor(entityId);
        return entry == null ? UNKNOWN_PING : entry.getLatency();
    }

    /** @return the player's game mode, or null if it is not known */
    public static GameMode gameMode(int entityId) {
        PlayerListEntry entry = entryFor(entityId);
        if (entry == null || entry.getGameMode() == null) {
            return null;
        }
        return switch (entry.getGameMode()) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }

    private static PlayerListEntry entryFor(int entityId) {
        ClientWorld world = Voxels.world();
        ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
        if (world == null || network == null) {
            return null;
        }
        Entity entity = world.getEntityById(entityId);
        return entity instanceof PlayerEntity player ? network.getPlayerListEntry(player.getUuid()) : null;
    }
}
