package dev.px.karatsuba.client.render;

import dev.px.core.Core;
import dev.px.core.shader.ShaderSource;

/**
 * The shader catalogue: every GLSL program the client draws with, by name.
 *
 * <p>Same shape as the other catalogues. Adding an effect is two steps: write the
 * GLSL under {@code assets/karatsuba/shaders/} (see the readme there), then add a
 * line here. Registering is pure data; nothing is read or compiled until the first
 * draw, so an effect nobody switches on costs nothing.
 *
 * <p>Draw with the adapter's {@code ShaderPasses}, naming the constant.
 */
public final class ShaderCatalogue {

    /** Fill, outline or glow around a silhouette mask. */
    public static final String SILHOUETTE = "silhouette";

    /** A two-colour sky with stars, in place of the vanilla one. */
    public static final String SKY = "sky";

    private ShaderCatalogue() {
    }

    public static void registerAll(Core core) {
        core.getShaderService().registerAll(
                ShaderSource.named(SILHOUETTE)
                        .vertex("core/quad.vsh")
                        .fragment("effect/silhouette.fsh")
                        .build(),
                ShaderSource.named(SKY)
                        .vertex("core/quad.vsh")
                        .fragment("effect/sky.fsh")
                        .build());
    }
}
