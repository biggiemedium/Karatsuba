package dev.px.karatsuba.adapter.asset;

import dev.px.core.Core;
import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGTexture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Images off the web, as textures: cover art, avatars.
 *
 * <p>{@link #texture} is asked every frame and never waits. The first ask for a URL
 * starts a download on the thread pool and answers null; the bytes are decoded and
 * uploaded on the game thread between frames, since NanoVG needs its GL context
 * and must not be handed a new image in the middle of drawing one; every ask after
 * that answers the texture.
 *
 * <p>Few are kept, and the oldest is released when a new one arrives: a playlist
 * walks through a new cover every few minutes, and each one is GL memory the JVM
 * cannot see. A failed URL is remembered as failed, so a broken link costs one
 * request, not sixty a second.
 *
 * <p>Only {@code https}, and only up to {@link #MAX_BYTES}: what comes back is fed
 * to a native decoder.
 */
public final class RemoteImages {

    /** How many textures are kept before the least recently drawn is released. */
    private static final int CAPACITY = 8;

    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Loaded, failed (null) or downloading ({@link #PENDING}). Game thread only; access order for the eviction. */
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>(16, 0.75f, true);

    private static final Entry PENDING = new Entry(null);
    private static final Entry FAILED = new Entry(null);

    private RemoteImages() {
    }

    private record Entry(Texture texture) {
    }

    /**
     * @param url an {@code https} image URL; null or empty answers null
     * @return the texture, or null while it downloads or if it could not be had.
     *         Call from the game thread.
     */
    public static Texture texture(String url) {
        if (url == null || url.isEmpty() || !url.startsWith("https://")) {
            return null;
        }
        Entry entry = ENTRIES.get(url);
        if (entry == null) {
            ENTRIES.put(url, PENDING);
            Core.threads().submit(() -> download(url));
            return null;
        }
        Texture texture = entry.texture();
        return texture != null && texture.isValid() ? texture : null;
    }

    /** Releases every texture. Called on shutdown, from the game thread. */
    public static void dispose() {
        for (Entry entry : ENTRIES.values()) {
            if (entry.texture() != null) {
                entry.texture().dispose();
            }
        }
        ENTRIES.clear();
    }

    private static void download(String url) {
        byte[] bytes;
        try {
            HttpResponse<byte[]> response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            bytes = response.body();
            if (bytes.length > MAX_BYTES) {
                throw new IOException(bytes.length + " bytes is too large for an image");
            }
        } catch (IOException | RuntimeException failure) {
            Core.log().warn("Could not download " + url + ": " + failure.getMessage());
            GameThread.later(() -> settle(url, FAILED));
            return;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return;
        }
        // later, never run: an upload must land between frames, not inside one.
        GameThread.later(() -> settle(url, upload(url, bytes)));
    }

    private static Entry upload(String url, byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return new Entry(NanoVGTexture.load(MinecraftAdapter.render2D(), in));
        } catch (IOException | RuntimeException failure) {
            Core.log().warn("Could not decode " + url + ": " + failure.getMessage());
            return FAILED;
        }
    }

    private static void settle(String url, Entry entry) {
        if (ENTRIES.get(url) != PENDING) {
            // Disposed meanwhile, at shutdown: nothing to keep it for.
            if (entry.texture() != null) {
                entry.texture().dispose();
            }
            return;
        }
        ENTRIES.put(url, entry);
        evict();
    }

    private static void evict() {
        int loaded = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.texture() != null) {
                loaded++;
            }
        }
        Iterator<Map.Entry<String, Entry>> oldestFirst = ENTRIES.entrySet().iterator();
        while (loaded > CAPACITY && oldestFirst.hasNext()) {
            Entry entry = oldestFirst.next().getValue();
            if (entry.texture() != null) {
                entry.texture().dispose();
                oldestFirst.remove();
                loaded--;
            }
        }
    }
}
