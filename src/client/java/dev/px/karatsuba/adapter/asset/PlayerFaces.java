package dev.px.karatsuba.adapter.asset;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.px.core.Core;
import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.resource.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Players' faces, as textures: the front of the head from their skin, with the hat
 * layer over it, 8x8 and sampled nearest so it stays sharp drawn at any size.
 *
 * <p>{@link #face} is asked every frame and never waits, like {@link RemoteImages}.
 * The first ask for a UUID starts the work on the thread pool and answers null;
 * the face is uploaded on the game thread between frames.
 *
 * <h2>Where a skin comes from</h2>
 * <ol>
 *   <li><b>The disk cache,</b> {@code <game>/karatsuba/cache/skins/<uuid>.png}: the
 *       skin as last downloaded, so a face shows at once, offline too.</li>
 *   <li><b>Mojang,</b> once per UUID per session: the profile from the session
 *       server, then the skin it names. A skin that changed replaces the cached one
 *       and the face shown.</li>
 *   <li><b>The game's default skin</b> for that UUID (Steve, Alex and the rest),
 *       when there is neither: an offline account, or no connection and no cache.</li>
 * </ol>
 */
public final class PlayerFaces {

    /** A skin is 64x64, about 2 KB; anything far bigger is not one. */
    private static final int MAX_BYTES = 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** By UUID: the face, or null while it loads or if there is none. Game thread only. */
    private static final Map<UUID, Texture> FACES = new HashMap<>();

    private PlayerFaces() {
    }

    /**
     * @param uuid a player's UUID as text; null or malformed answers null
     * @return their face, or null while it loads or if it cannot be had. Game thread.
     */
    public static Texture face(String uuid) {
        UUID id = parse(uuid);
        if (id == null) {
            return null;
        }
        if (!FACES.containsKey(id)) {
            FACES.put(id, null);
            Core.threads().submit(() -> load(id));
        }
        Texture face = FACES.get(id);
        return face != null && face.isValid() ? face : null;
    }

    /** @return the UUID the game is playing as now, as text; null if the session has none */
    public static String currentUuid() {
        UUID id = MinecraftClient.getInstance().getSession().getUuidOrNull();
        return id == null ? null : id.toString();
    }

    /** Releases every face. Called on shutdown, from the game thread. */
    public static void dispose() {
        for (Texture face : FACES.values()) {
            if (face != null) {
                face.dispose();
            }
        }
        FACES.clear();
    }

    // ----------------------------------------------------------- off thread

    private static void load(UUID id) {
        File file = cacheFile(id);
        byte[] cached = read(file);
        byte[] cachedFace = cached == null ? null : crop(cached);
        if (cachedFace != null) {
            GameThread.later(() -> settle(id, cachedFace));
        }

        byte[] fresh = download(id);
        if (fresh != null && !Arrays.equals(fresh, cached)) {
            byte[] freshFace = crop(fresh);
            if (freshFace != null) {
                write(file, fresh);
                GameThread.later(() -> settle(id, freshFace));
                return;
            }
        }
        if (cachedFace == null && (fresh == null || crop(fresh) == null)) {
            // The resource manager is the game thread's.
            GameThread.later(() -> settleDefault(id));
        }
    }

    /** @return the skin Mojang has for {@code id}; null for none (an offline account) or on failure */
    private static byte[] download(UUID id) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ProfileResult profile = client.getSessionService().fetchProfile(id, true);
            if (profile == null) {
                return null;
            }
            MinecraftProfileTextures textures = client.getSessionService().getTextures(profile.profile());
            MinecraftProfileTexture skin = textures.skin();
            if (skin == null || skin.getUrl() == null) {
                return null;
            }
            // Mojang names its skins over http; the same path is served over https.
            String url = skin.getUrl().replaceFirst("^http://", "https://");
            HttpResponse<byte[]> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > MAX_BYTES) {
                throw new IOException("HTTP " + response.statusCode() + ", " + response.body().length + " bytes");
            }
            return response.body();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException failure) {
            Core.log().warn("Could not download the skin of " + id + ": " + failure.getMessage());
            return null;
        }
    }

    /**
     * @return the face of a skin as an 8x8 PNG: the head's front, made opaque as
     *         the game draws it, with the hat layer over it; null if it is not a skin
     *
     * <p>A legacy 64x32 skin often fills its hat layer with solid colour; the game
     * ignores that layer when it has no transparent pixel, and so does this.
     */
    private static byte[] crop(byte[] skinPng) {
        try {
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinPng));
            if (skin == null || skin.getWidth() < 64 || skin.getWidth() % 64 != 0
                    || (skin.getHeight() != skin.getWidth() && skin.getHeight() * 2 != skin.getWidth())) {
                return null;
            }
            int unit = skin.getWidth() / 64;
            boolean legacy = skin.getHeight() * 2 == skin.getWidth();
            boolean hat = !legacy || hasTransparency(skin, 40 * unit, 8 * unit, 8 * unit);

            BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = skin.getRGB((8 + x) * unit, (8 + y) * unit) | 0xFF000000;
                    int over = hat ? skin.getRGB((40 + x) * unit, (8 + y) * unit) : 0;
                    face.setRGB(x, y, over(base, over));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(face, "png", out);
            return out.toByteArray();
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private static boolean hasTransparency(BufferedImage image, int left, int top, int size) {
        for (int y = top; y < top + size; y++) {
            for (int x = left; x < left + size; x++) {
                if ((image.getRGB(x, y) >>> 24) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code top} over the opaque {@code base}, by its alpha. */
    private static int over(int base, int top) {
        int alpha = top >>> 24;
        if (alpha == 0) {
            return base;
        }
        int rgb = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int b = base >> shift & 0xFF;
            int t = top >> shift & 0xFF;
            rgb |= ((t * alpha + b * (255 - alpha) + 127) / 255) << shift;
        }
        return 0xFF000000 | rgb;
    }

    private static File cacheFile(UUID id) {
        return new File(new File(Core.platform().getDataDirectory(), "cache/skins"), id + ".png");
    }

    private static byte[] read(File file) {
        try {
            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
        } catch (IOException failure) {
            return null;
        }
    }

    private static void write(File file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), bytes);
        } catch (IOException failure) {
            Core.log().warn("Could not cache a skin at " + file + ": " + failure.getMessage());
        }
    }

    // ---------------------------------------------------------- game thread

    private static void settle(UUID id, byte[] facePng) {
        if (!FACES.containsKey(id) || MinecraftAdapter.render2D() == null) {
            // Disposed meanwhile, at shutdown.
            return;
        }
        try (InputStream in = new ByteArrayInputStream(facePng)) {
            Texture face = NanoVGTexture.upload(MinecraftAdapter.render2D(), NanoVGTexture.decode(in), false, true);
            Texture old = FACES.put(id, face);
            if (old != null) {
                old.dispose();
            }
        } catch (IOException | RuntimeException failure) {
            Core.log().warn("Could not load the face of " + id + ": " + failure.getMessage());
        }
    }

    /** The game's default skin for {@code id}, from its own resources. */
    private static void settleDefault(UUID id) {
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager()
                .getResource(DefaultSkinHelper.getSkinTextures(id).texture());
        if (resource.isEmpty()) {
            return;
        }
        try (InputStream stream = resource.get().getInputStream()) {
            byte[] face = crop(stream.readAllBytes());
            if (face != null) {
                settle(id, face);
            }
        } catch (IOException failure) {
            Core.log().warn("Could not read the default skin: " + failure.getMessage());
        }
    }

    private static UUID parse(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
