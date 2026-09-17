package dev.px.karatsuba.adapter.render;

import dev.px.core.Core;
import dev.px.core.render.font.Font;
import dev.px.core.render.font.FontProvider;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Loads typefaces into the NanoVG context.
 *
 * <p>NanoVG rasterises TrueType outlines itself, so it needs TTF bytes and nothing
 * else &mdash; no GL texture, no resource pack, no {@code Identifier}.
 *
 * <h2>Why there is no real game font</h2>
 * {@link #gameDefault()} is meant to be the always-available fallback, but
 * Minecraft's own font is a bitmap atlas plus a hex dump, neither of which NanoVG
 * can read. So the fallback is the best system typeface we can find, and failing
 * that a font that measures zero and draws nothing &mdash; which is precisely what
 * Core already expects from a backend with no font, so the client still boots and
 * every layout still resolves.
 *
 * <p>Bundle a TTF under {@code src/client/resources/assets/karatsuba/font/} and load
 * it explicitly for a font you actually control:
 *
 * <pre>{@code
 * Core.fonts().load("ui", "/assets/karatsuba/font/Inter-Regular.ttf", 9f);
 * }</pre>
 */
public final class NanoVGFontProvider implements FontProvider {

    /** Matches the vanilla font's apparent size at the default GUI scale. */
    private static final float DEFAULT_SIZE = 9f;

    /** Tried in order for {@link #gameDefault()}; the first that resolves wins. */
    private static final List<String> FALLBACK_FAMILIES =
            List.of("Segoe UI", "Helvetica Neue", "DejaVu Sans", "Liberation Sans", "Arial", "Verdana");

    private static final List<String> FONT_DIRECTORIES = List.of(
            System.getenv("WINDIR") == null ? "C:/Windows/Fonts" : System.getenv("WINDIR") + "/Fonts",
            System.getProperty("user.home") + "/Library/Fonts",
            "/Library/Fonts",
            "/System/Library/Fonts",
            "/usr/share/fonts",
            "/usr/local/share/fonts",
            System.getProperty("user.home") + "/.fonts");

    private final NanoVGRender2D backend;

    public NanoVGFontProvider(NanoVGRender2D backend) {
        this.backend = backend;
    }

    @Override
    public Font load(String name, String resourcePath, float size) throws IOException {
        try (InputStream stream = NanoVGFontProvider.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("No font resource at " + resourcePath);
            }
            return register(name, stream.readAllBytes(), size);
        }
    }

    @Override
    public Font system(String family, float size) {
        Path file = findSystemFont(family);
        if (file == null) {
            return missing(family, size);
        }
        try {
            return register(family, Files.readAllBytes(file), size);
        } catch (IOException failure) {
            return missing(family, size);
        }
    }

    @Override
    public Font gameDefault() {
        for (String family : FALLBACK_FAMILIES) {
            Font candidate = system(family, DEFAULT_SIZE);
            if (candidate instanceof NanoVGFont typeface && typeface.isValid()) {
                Core.log().info("Default font resolved to " + family);
                return candidate;
            }
        }
        // Nothing usable on this machine. Core tolerates a font that measures zero,
        // so the client still boots -- but silently invisible text is the kind of
        // thing that costs an hour to diagnose, so say so here.
        Core.log().warn("No system font found; text will not render. "
                + "Bundle a TTF and load it with Core.fonts().load(...)");
        return missing("default", DEFAULT_SIZE);
    }

    private NanoVGFont register(String name, byte[] ttf, float size) {
        // NanoVG keeps the pointer rather than copying, so this buffer has to be
        // off-heap and has to outlive the handle -- the font holds the only reference.
        ByteBuffer data = MemoryUtil.memAlloc(ttf.length);
        data.put(ttf).flip();

        // The face is registered under a unique name because NanoVG keys faces by
        // name, and the same family at two sizes must not collide.
        String faceName = name + "#" + size;
        int handle = NanoVG.nvgCreateFontMem(backend.handle(), faceName, data, false);
        if (handle == NanoVGFont.NO_HANDLE) {
            MemoryUtil.memFree(data);
            return missing(name, size);
        }
        return new NanoVGFont(backend.handle(), name, size, handle, data);
    }

    private NanoVGFont missing(String name, float size) {
        return new NanoVGFont(backend.handle(), name, size, NanoVGFont.NO_HANDLE, null);
    }

    /**
     * Looks for a family by filename across the platform font directories.
     *
     * <p>Crude on purpose: matching properly means parsing every font's name table,
     * and this only has to find one readable fallback.
     */
    private static Path findSystemFont(String family) {
        String needle = family.toLowerCase(Locale.ROOT).replace(" ", "");

        for (String directory : FONT_DIRECTORIES) {
            File root = new File(directory);
            if (!root.isDirectory()) {
                continue;
            }
            Path match = search(root.toPath(), needle);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static Path search(Path root, String needle) {
        try (var files = Files.walk(root, 3)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!fileName.endsWith(".ttf")) {
                            return false;
                        }
                        String stem = fileName.substring(0, fileName.length() - 4).replace(" ", "");
                        // Prefer the plain weight: "arial" over "arialbd" or "ariali".
                        return stem.equals(needle) || stem.equals(needle + "-regular");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}
