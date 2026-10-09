package dev.px.karatsuba.adapter.render.shader;

import dev.px.core.Core;
import dev.px.core.shader.ShaderLoader;
import dev.px.core.shader.ShaderService;
import dev.px.karatsuba.adapter.event.Events;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Plugs the game's GL context into Core's {@code ShaderService}.
 *
 * <ul>
 *   <li><b>The backend</b>, {@link GlShaderBackend}.</li>
 *   <li><b>The loader.</b> A {@code shaders} folder in the client's data directory
 *       ({@code .minecraft/karatsuba/shaders}) is read first, then the jar's
 *       {@code assets/karatsuba/shaders}. A file in the folder replaces the jar's
 *       file of the same path, so GLSL can be edited with the game running, and a
 *       player can restyle an effect without a rebuild.</li>
 *   <li><b>Reload on F3+T.</b> A resource reload calls {@code Core.shaders().reload()},
 *       so every program is read and built again on its next draw, failed ones
 *       included. With the folder above, that is the edit loop: save, F3+T, look.</li>
 *   <li><b>Globals</b> every pass gets on top of Core's builtins:
 *       {@code uViewport} (the framebuffer size in pixels, for
 *       {@code gl_FragCoord}) and {@code uPartialTicks}.</li>
 * </ul>
 *
 * <p>Registering a shader is the client's: {@code Core.shaders().register(...)}
 * with paths relative to that root, before or after {@code start()}.
 */
public final class Shaders {

    /** Inside the jar, and the root every registered path and {@code #include} is relative to. */
    public static final String ROOT = "assets/karatsuba/shaders";

    /** Inside the client's data directory, read before the jar. */
    public static final String FOLDER = "shaders";

    private Shaders() {
    }

    /** From {@code MinecraftAdapter.install}, between {@code build()} and {@code start()}. */
    public static void install(Core core) {
        ShaderService shaders = core.getShaderService();
        shaders.setBackend(new GlShaderBackend());

        File folder = new File(Core.platform().getDataDirectory(), FOLDER);
        // The mod's own class loader: Core's would be the same jar in production but
        // need not be in development, where each module has its own output folder.
        ShaderLoader files = ShaderLoader.directory(folder)
                .orElse(ShaderLoader.classpath(Shaders.class.getClassLoader(), ROOT));
        // Core resolves an #include against the including file's folder but keeps
        // any "..", which a jar's resource lookup will not follow.
        shaders.setLoader(path -> files.read(collapse(path)));

        MinecraftClient client = MinecraftClient.getInstance();
        shaders.addGlobalUniforms(uniforms -> {
            Window window = client.getWindow();
            uniforms.set("uViewport", (float) window.getFramebufferWidth(), (float) window.getFramebufferHeight())
                    .set("uPartialTicks", client.getRenderTickCounter().getTickDelta(false));
        });

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of("karatsuba", "shaders");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        if (Events.isArmed()) {
                            Core.shaders().reload();
                        }
                    }
                });
    }

    /**
     * {@code effect/../core/sky.glsl} to {@code core/sky.glsl}. A path that climbs
     * above the root keeps its {@code ..} and so fails to load, rather than reaching
     * outside the shader folder.
     */
    static String collapse(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..") && !parts.isEmpty() && !parts.peekLast().equals("..")) {
                parts.removeLast();
            } else {
                parts.addLast(part);
            }
        }
        return String.join("/", parts);
    }
}
