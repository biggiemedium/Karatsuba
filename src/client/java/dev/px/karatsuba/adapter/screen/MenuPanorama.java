package dev.px.karatsuba.adapter.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.util.Identifier;

/**
 * Which panorama turns behind the title screen and every other menu out of a
 * world: the game's own, or one the client bundles.
 *
 * <p>A seam like {@link LoadingScreen}, but the client hands over pictures rather
 * than drawing: a panorama is six cube faces the game already knows how to turn,
 * with its Panorama Speed option, its vignette and the title screen's fade in, so
 * only the faces are swapped. {@code RotatingCubeMapRendererMixin} draws whatever
 * {@link #renderer()} answers in place of the game's cube.
 *
 * <h2>Loading the faces</h2>
 * The cube binds each face with {@code RenderSystem.setShaderTexture}, which only
 * looks the texture up and never loads it: a face nobody registered draws as
 * nothing, and the menus turn black. The game registers its own faces before its
 * first resource load, which is what loads them. The client boots after that load
 * has started, so registering would leave them empty until the next one; instead
 * they are loaded outright on the first frame that draws them.
 *
 * <p>One slot. Empty until the client {@linkplain #install installs} one, and the
 * game's panorama while it is.
 */
public final class MenuPanorama {

    /** The mod's namespace, which the bundled faces load under. */
    private static final String NAMESPACE = "karatsuba";

    /** A cube map has six faces, {@code _0} to {@code _5}. */
    private static final int FACES = 6;

    private static volatile CubeMapRenderer installed;

    /** The installed faces' path without the suffix, to load them by. */
    private static volatile Identifier faces;

    /** Whether {@link #faces} are loaded into the texture manager yet. */
    private static boolean loaded;

    private MenuPanorama() {
    }

    /**
     * Turns the client's panorama behind every menu out of a world.
     *
     * @param faces the faces' path under {@code assets/karatsuba/}, without the
     *              suffix: {@code "texture/background/panorama/end-city/panorama"} for
     *              {@code panorama_0.png} to {@code panorama_5.png}, in the game's
     *              order (front, right, back, left, up, down); null for the game's own
     */
    public static void install(String path) {
        Identifier base = path == null ? null : Identifier.of(NAMESPACE, path);
        faces = base;
        installed = base == null ? null : new CubeMapRenderer(base);
        loaded = false;
    }

    /**
     * @return the cube to draw in place of the game's, its faces loaded, or null
     *         for the game's own. Render thread.
     */
    public static CubeMapRenderer renderer() {
        CubeMapRenderer cube = installed;
        if (cube != null && !loaded) {
            // getTexture loads a texture it does not have yet, and keeps it through
            // later reloads. CubeMapRenderer.registerTextures would only queue each
            // face for the next reload, which at this point has already happened.
            var textures = MinecraftClient.getInstance().getTextureManager();
            for (int face = 0; face < FACES; face++) {
                textures.getTexture(faces.withPath(faces.getPath() + "_" + face + ".png"));
            }
            loaded = true;
        }
        return cube;
    }
}
