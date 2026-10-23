package dev.px.karatsuba.adapter.asset;

import dev.px.core.Core;
import dev.px.core.render.Texture;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGFont;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGFontProvider;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGTexture;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;

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

    /** Backdrops still being decoded on a worker; see {@link #backdrop}. */
    private static final Map<String, Future<NanoVGTexture.Decoded>> DECODING = new HashMap<>();

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

    /**
     * A large image, decoded on a worker so asking for it never stalls a frame, with
     * mipmaps: a backdrop, or anything drawn much smaller than it is (the logo).
     *
     * <p>For backdrops: a 4K PNG takes stb a few hundred milliseconds, which on the
     * render thread is a visible freeze. The first call starts the decode and
     * returns null; each later call returns null until the pixels are ready, then
     * uploads them (with mipmaps, since a backdrop is drawn smaller than it is) and
     * returns the texture from then on. Call it every frame and draw a fallback
     * while it is null. Render thread only, like {@link #texture}.
     *
     * @param path absolute classpath path to a PNG, JPEG, TGA or BMP
     * @return the texture once decoded and uploaded; null while decoding, or for
     *         good if it is missing or unreadable
     */
    public static Texture backdrop(String path) {
        if (TEXTURES.containsKey(path)) {
            return TEXTURES.get(path);
        }
        Future<NanoVGTexture.Decoded> pending = DECODING.computeIfAbsent(path,
                ignored -> Core.threads().submit(() -> decodeOffThread(path)));
        if (!pending.isDone()) {
            return null;
        }
        DECODING.remove(path);
        Texture loaded = upload(path, pending);
        TEXTURES.put(path, loaded);
        return loaded;
    }

    private static NanoVGTexture.Decoded decodeOffThread(String path) throws IOException {
        try (InputStream stream = Assets.class.getResourceAsStream(path)) {
            if (stream == null) {
                Core.log().warn("No asset at " + path);
                return null;
            }
            return NanoVGTexture.decode(stream);
        }
    }

    private static Texture upload(String path, Future<NanoVGTexture.Decoded> pending) {
        try {
            NanoVGTexture.Decoded decoded = pending.get();
            return decoded == null ? null : NanoVGTexture.upload(MinecraftAdapter.render2D(), decoded, true);
        } catch (ExecutionException failure) {
            Core.log().error("Could not decode texture " + path, failure.getCause());
        } catch (IOException | RuntimeException failure) {
            Core.log().error("Could not load texture " + path, failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
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

    /** The mod's id, whose jar the bundled fonts are listed from. */
    private static final String MOD_ID = "karatsuba";

    /** The font files bundled, listed once: what is in a jar does not change while it runs. */
    private static List<String> fontFiles;

    /**
     * @return every font bundled in {@code assets/karatsuba/font}, TrueType and
     *         OpenType, by its path under that folder ({@code sf-pro/sf-pro.ttf}: one
     *         folder per family), sorted; empty when there are none
     *
     * <p>Listed through Fabric's view of the mod, which is a folder in a dev run and
     * the jar's own file system in a built one: a classpath folder cannot be listed
     * directly, only its files opened by name.
     */
    public static List<String> fontFiles() {
        if (fontFiles == null) {
            fontFiles = listFonts();
        }
        return fontFiles;
    }

    /**
     * A bundled font file at {@code size}, loaded the first time and kept after.
     *
     * @param fileName its path under {@code assets/karatsuba/font}: {@code sf-pro/sf-pro.ttf}
     * @return the font, or Core's default if the file is missing or unreadable
     */
    public static Font fontFile(String fileName, float size) {
        String name = "asset:" + fileName + "@" + size;
        var existing = Core.fonts().find(name);
        return existing.isPresent() ? existing.get() : font(name, fileName, size);
    }

    private static List<String> listFonts() {
        try {
            return listBundledFonts();
        } catch (RuntimeException | LinkageError noLoader) {
            // Outside Fabric (a test harness): nothing bundled to offer, and no reason to fail.
            return List.of();
        }
    }

    private static List<String> listBundledFonts() {
        var container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty()) {
            return List.of();
        }
        var folder = container.get().findPath("assets/" + MOD_ID + "/font");
        if (folder.isEmpty() || !Files.isDirectory(folder.get())) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Path root = folder.get();
        // Each family sits in a folder of its own (font/sf-pro/sf-pro.ttf), so walk them,
        // naming each file by its path under font/, always with forward slashes.
        try (Stream<Path> files = Files.walk(root, 2)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return lower.endsWith(".ttf") || lower.endsWith(".otf");
                    })
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .forEach(names::add);
        } catch (IOException failure) {
            Core.log().error("Could not list the bundled fonts", failure);
        }
        Collections.sort(names);
        Core.log().info("Bundled fonts: " + (names.isEmpty() ? "none" : String.join(", ", names)));
        return Collections.unmodifiableList(names);
    }

    /**
     * An installed system typeface at {@code size}, registered with Core under
     * {@code name}; the first family found wins.
     *
     * <p>For a type scale without bundling a TTF: each family is a font file's
     * name without {@code .ttf}, such as {@code "seguisb"} for Segoe UI Semibold.
     * With none found, the default font's own family at this size; with that gone
     * too, the default itself. Registered fonts are kept, so asking again is free.
     */
    public static Font systemFont(String name, float size, String... families) {
        var existing = Core.fonts().find(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        var provider = new NanoVGFontProvider(MinecraftAdapter.render2D());
        for (String family : families) {
            if (provider.system(family, size) instanceof NanoVGFont found && found.isValid()) {
                return Core.fonts().register(name, found);
            }
        }
        if (provider.system(Core.fonts().getDefault().getName(), size) instanceof NanoVGFont same && same.isValid()) {
            return Core.fonts().register(name, same);
        }
        return Core.fonts().getDefault();
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
        // A decode still running when the game closes: its pixels are off-heap,
        // so free them if they arrived, and let the rest die with the worker.
        DECODING.values().forEach(pending -> {
            if (pending.isDone()) {
                try {
                    NanoVGTexture.Decoded decoded = pending.get();
                    if (decoded != null) {
                        decoded.free();
                    }
                } catch (ExecutionException | InterruptedException | RuntimeException ignored) {
                    // Nothing was decoded, so there is nothing to free.
                }
            } else {
                pending.cancel(true);
            }
        });
        DECODING.clear();
    }
}
