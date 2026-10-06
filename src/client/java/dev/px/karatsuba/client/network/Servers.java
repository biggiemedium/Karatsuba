package dev.px.karatsuba.client.network;

import dev.px.core.Core;
import dev.px.core.network.server.ServerSoftware;

/**
 * The server software catalogue: what {@code Core.server()} can name a brand as.
 *
 * <p>Core recognises none on purpose; a built-in list goes stale and then names
 * the wrong thing with confidence. So the list is ours, and it is plain data: a
 * name and the words its brand contains, matched as whole words ignoring case.
 *
 * <p><b>Order is precedence.</b> Where a brand names two pieces of software the
 * one listed first wins, so every fork is listed above what it forked. Proxies
 * are matched separately from the server behind them, which is why
 * {@code "Paper (Velocity)"} and {@code "BungeeCord <- Paper"} both resolve to one
 * of each without either format being spelled out here.
 *
 * <p>Adding one is one line. Anything unlisted still has its raw brand in
 * {@code Core.server().getBrand().getRaw()}; it is just {@code UNKNOWN} as software.
 */
public final class Servers {

    private Servers() {
    }

    public static void registerAll(Core core) {
        core.getServerService().registerSoftware(software());
        core.getServerService().registerSoftware(proxies());
    }

    /** Forks before parents. */
    public static ServerSoftware[] software() {
        return new ServerSoftware[]{
                ServerSoftware.of("Folia"),
                ServerSoftware.of("Purpur"),
                ServerSoftware.of("Pufferfish"),
                ServerSoftware.of("Paper"),
                ServerSoftware.of("Spigot"),
                ServerSoftware.of("CraftBukkit", "craftbukkit", "bukkit"),
                ServerSoftware.of("NeoForge"),
                ServerSoftware.of("Forge", "forge", "fml"),
                ServerSoftware.of("Fabric"),
                ServerSoftware.of("Quilt"),
                ServerSoftware.of("Vanilla"),
        };
    }

    /** Forks before parents here too: Waterfall's brand can mention BungeeCord. */
    public static ServerSoftware[] proxies() {
        return new ServerSoftware[]{
                ServerSoftware.proxy("Velocity"),
                ServerSoftware.proxy("Waterfall"),
                ServerSoftware.proxy("BungeeCord", "bungeecord", "bungee"),
        };
    }
}
