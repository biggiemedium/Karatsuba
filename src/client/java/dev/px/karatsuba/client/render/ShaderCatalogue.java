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

    /** A night in deep space: stars, a galaxy and nebulae, in place of the vanilla sky. */
    public static final String SKY_SPACE = "sky_space";

    /** An aurora over a clear night, in place of the vanilla sky. */
    public static final String SKY_AURORA = "sky_aurora";

    /** Frosted glass behind a rounded rect of UI; drawn through {@link Glass}. */
    public static final String GLASS = "glass";

    private ShaderCatalogue() {
    }

    public static void registerAll(Core core) {
        core.getShaderService().registerAll(
                ShaderSource.named(SILHOUETTE)
                        .vertex("core/quad.vsh")
                        .fragment("effect/silhouette.fsh")
                        .build(),
                ShaderSource.named(SKY_SPACE)
                        .vertex("core/quad.vsh")
                        .fragment("effect/sky/space.fsh")
                        .build(),
                ShaderSource.named(SKY_AURORA)
                        .vertex("core/quad.vsh")
                        .fragment("effect/sky/aurora.fsh")
                        .build(),
                ShaderSource.named(GLASS)
                        .vertex("core/quad.vsh")
                        .fragment("effect/glass.fsh")
                        .build());
    }
}
