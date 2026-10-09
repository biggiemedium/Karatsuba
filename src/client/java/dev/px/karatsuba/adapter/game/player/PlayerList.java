package dev.px.karatsuba.adapter.game.player;

import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * @return the names on the server's player list (the tab list), yourself left
     *         out; empty when not on a server
     */
    public static List<String> names() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<String> names = new ArrayList<>();
        ClientPlayNetworkHandler network = client == null ? null : client.getNetworkHandler();
        if (network == null) {
            return names;
        }
        String self = client.getSession().getUsername();
        for (PlayerListEntry entry : network.getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name != null && !name.equalsIgnoreCase(self)) {
                names.add(name);
            }
        }
        return names;
    }

    /** @return how far away a player is, in blocks, or -1 when they are not loaded near you */
    public static float distanceTo(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return -1f;
        }
        ClientWorld world = client.world;
        if (world == null) {
            return -1f;
        }
        for (PlayerEntity player : world.getPlayers()) {
            if (player != client.player && player.getName().getString().equalsIgnoreCase(name)) {
                return client.player.distanceTo(player);
            }
        }
        return -1f;
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
