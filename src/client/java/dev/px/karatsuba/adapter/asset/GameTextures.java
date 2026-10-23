package dev.px.karatsuba.adapter.asset;

import dev.px.core.Core;
import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGTexture;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The game's own textures, as NanoVG textures: an effect's icon, say.
 *
 * <p>Read from the game's resource manager, so a resource pack's icons are the ones
 * shown, and decoded into NanoVG rather than drawn by the game. That is what lets a
 * HUD element fade, clip and place them like anything else it draws, inside the
 * NanoVG frame, where the game itself may not draw ({@code DESIGN.md} §1.8).
 *
 * <p>Cached by id, and dropped when resources reload, so a pack change shows at once.
 * Render thread only.
 */
public final class GameTextures {

    /** By id; a null value is a texture that is missing, remembered so it is not looked for every frame. */
    private static final Map<String, Texture> CACHE = new HashMap<>();

    private GameTextures() {
    }

    /** Drops the cache whenever the game reloads its resources. Once, from the adapter's install. */
    public static void install() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of("karatsuba", "game_textures");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        dispose();
                    }
                });
    }

    /**
     * @param id a game texture, as {@code namespace:path}, such as
     *           {@code minecraft:textures/mob_effect/speed.png}
     * @return it as pixel art, sampled nearest so it stays sharp when drawn larger;
     *         null if there is no such texture or it cannot be read
     */
    public static Texture pixelArt(String id) {
        if (CACHE.containsKey(id)) {
            return CACHE.get(id);
        }
        Texture loaded = load(id);
        CACHE.put(id, loaded);
        return loaded;
    }

    private static Texture load(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || MinecraftAdapter.render2D() == null) {
            return null;
        }
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(identifier);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().getInputStream()) {
            return NanoVGTexture.upload(MinecraftAdapter.render2D(), NanoVGTexture.decode(stream), false, true);
        } catch (IOException | RuntimeException failure) {
            Core.log().warn("Could not load the game texture " + id + ": " + failure.getMessage());
            return null;
        }
    }

    /** Releases every texture loaded. On resource reload, and when the client stops. */
    public static void dispose() {
        CACHE.values().stream().filter(texture -> texture != null).forEach(Texture::dispose);
        CACHE.clear();
    }
}
