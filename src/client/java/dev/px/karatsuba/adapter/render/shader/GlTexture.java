package dev.px.karatsuba.adapter.render.shader;

/**
 * A Core {@code Texture} that is a GL texture underneath, so a shader can sample it.
 *
 * <p>Core's {@code Uniforms.sampler(name, texture, unit)} hands over the same
 * {@code Texture} handle the 2D backend draws with. The backend can only bind it if
 * it can name the GL object, and this is how it asks. An image loaded through
 * {@code Assets} implements it, and so does {@link SceneTexture}.
 */
public interface GlTexture {

    /** @return the GL texture name, or 0 if there is none any more */
    int glTextureId();
}
