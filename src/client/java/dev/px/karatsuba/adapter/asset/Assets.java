package dev.px.karatsuba.adapter.asset;

import dev.px.core.Core;
import dev.px.core.render.Texture;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.NanoVGTexture;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads images and fonts bundled in the mod jar.
 *
 * <p>Deliberately not Minecraft's resource manager. NanoVG owns its own GL texture
 * and its own font atlas, so an {@code Identifier} would be the wrong handle
 * entirely &mdash; these come off the classpath as bytes and go straight to the
 * backend. That also means they load before any resource reload and survive one.
 *
 * <p>Paths are absolute classpath paths, so they always start with a slash:
 * {@code "/assets/karatsuba/texture/icon/sword.png"}.
 *
 * <p>A texture is decoded once and cached. A failed load is cached too, as null,
 * because a HUD element asks for its icon every frame and a missing file must not
 * mean re-reading the jar sixty times a second.
 */
public final class Assets {

    /** Root for everything this mod bundles. */
    public static final String ROOT = "/assets/karatsuba/";

    private static final Map<String, Texture> TEXTURES = new HashMap<>();

    private Assets() {
    }

    /**
     * @param path absolute classpath path to a PNG, JPEG, TGA or BMP
     * @return the decoded texture, or null if it is missing or unreadable
     */
    public static Texture texture(String path) {
        if (TEXTURES.containsKey(path)) {
            return TEXTURES.get(path);
        }
        Texture loaded = decode(path);
        TEXTURES.put(path, loaded);
        return loaded;
    }

    private static Texture decode(String path) {
        try (InputStream stream = Assets.class.getResourceAsStream(path)) {
            if (stream == null) {
                Core.log().warn("No asset at " + path);
                return null;
            }
            return NanoVGTexture.load(MinecraftAdapter.render2D(), stream);
        } catch (IOException | RuntimeException failure) {
            Core.log().error("Could not load texture " + path, failure);
            return null;
        }
    }

    /**
     * Loads a bundled typeface and registers it with Core under {@code name}.
     *
     * <p>A face is registered per size, so load each size you draw at rather than
     * scaling one handle: NanoVG rasterises at the size it was given, and scaling
     * the result is exactly as blurry as it sounds.
     *
     * @return the font, or Core's default if the resource is missing
     */
    public static Font font(String name, String fileName, float size) {
        try {
            return Core.fonts().load(name, ROOT + "font/" + fileName, size);
        } catch (RuntimeException failure) {
            Core.log().error("Could not load font " + fileName + "; falling back to the default", failure);
            return Core.fonts().getDefault();
        }
    }

    /**
     * Releases every cached texture.
     *
     * <p>Called on shutdown. NanoVG textures live in GL memory the JVM knows
     * nothing about, so nothing else would ever free them.
     */
    public static void dispose() {
        TEXTURES.values().stream()
                .filter(texture -> texture != null)
                .forEach(Texture::dispose);
        TEXTURES.clear();
    }
}
