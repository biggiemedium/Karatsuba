package dev.px.karatsuba.adapter.network;

import net.minecraft.client.network.Address;
import net.minecraft.client.network.BlockListChecker;
import net.minecraft.client.network.ServerAddress;

/**
 * A server block list that blocks nothing, in place of Mojang's.
 *
 * <p>The game's own ({@code BlockListChecker.create()}) loads Mojang's
 * {@code patchy} supplier, which downloads hashed host names from
 * {@code sessionserver.mojang.com/blockedservers}, and refuses to connect to any
 * server that matches. This one is installed in its place
 * ({@code AllowedAddressResolverMixin}), so the list is never fetched and every
 * server can be joined.
 */
public final class NoBlockList implements BlockListChecker {

    public static final NoBlockList INSTANCE = new NoBlockList();

    private NoBlockList() {
    }

    @Override
    public boolean isAllowed(Address address) {
        return true;
    }

    @Override
    public boolean isAllowed(ServerAddress address) {
        return true;
    }
}
