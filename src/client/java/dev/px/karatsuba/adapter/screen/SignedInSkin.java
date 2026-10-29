package dev.px.karatsuba.adapter.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Util;
import net.minecraft.util.Uuids;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The skin of whoever is signed in now, for drawing their face on the menus.
 *
 * <p>Follows {@code MinecraftClient.getSession()}, not {@code getGameProfile()}:
 * the account switcher replaces the session, while the game profile stays the
 * launcher's for the whole run. A bare name and UUID carry no skin, so on each new
 * session the full profile is fetched from Mojang on the game's download thread
 * and handed to the game's own skin provider, which downloads and caches the
 * texture. Until then, and for an offline account, which Mojang does not know,
 * it is the default skin for that UUID, as the game shows such a player.
 *
 * <p>Render thread only.
 */
public final class SignedInSkin {

    private static UUID loadedFor;
    private static Supplier<SkinTextures> skin;

    private SignedInSkin() {
    }

    /** @return the signed-in player's skin, or the default one while it loads */
    public static SkinTextures current() {
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();
        UUID id = session.getUuidOrNull() != null
                ? session.getUuidOrNull()
                : Uuids.getOfflinePlayerUuid(session.getUsername());
        if (!id.equals(loadedFor)) {
            load(client, id, session.getUsername());
        }
        return skin.get();
    }

    private static void load(MinecraftClient client, UUID id, String name) {
        loadedFor = id;
        GameProfile bare = new GameProfile(id, name);
        skin = () -> DefaultSkinHelper.getSkinTextures(bare);
        CompletableFuture
                .supplyAsync(() -> client.getSessionService().fetchProfile(id, true), Util.getDownloadWorkerExecutor())
                .exceptionally(failed -> null)
                .thenAcceptAsync(result -> loaded(client, id, result), client);
    }

    /** Back on the render thread; ignored if the session changed meanwhile. */
    private static void loaded(MinecraftClient client, UUID id, ProfileResult result) {
        if (result != null && id.equals(loadedFor)) {
            skin = client.getSkinProvider().getSkinTexturesSupplier(result.profile());
        }
    }
}
