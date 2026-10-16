package dev.px.karatsuba.client.render;

import dev.px.core.render.Color;
import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.render.shader.ShaderPasses;

/**
 * Glass and frosted materials behind UI ({@code effect/glass.fsh}): the screen
 * under a rounded rect, blurred and tinted, and for glass bent and lit at its rim.
 *
 * <p>Shapes are drawn through a {@link Layer}: its first shape copies the screen
 * as painted so far, and the rest reuse that copy, so shapes that sit side by side
 * in one layer cost one capture between them. Shapes in one layer must not
 * overlap: none of them sees the others.
 *
 * <p>Like every {@link ShaderPasses#rect} it refuses inside a clip, transform or
 * alpha push, so it belongs with whatever hosts a screen, never in a widget's
 * renderer: scale the rect yourself to follow an animation, and animate
 * {@code presence} rather than opacity.
 */
public final class Glass {

    /**
     * How a shape treats what is behind it.
     *
     * @param rim        the lensing band at the edge, in screen units
     * @param refraction how far the band bends, 1 for glass, 0 for none
     * @param specular   the lit edge, 1 for glass, 0 for none
     * @param blur       the mip level the blur reads, about 3 for frosted
     * @param saturation 1 for none, a little over for glass's richer colour
     * @param aberration a colour split at the rim in framebuffer pixels, 0 for off
     */
    public record Optics(float rim, float refraction, float specular, float blur,
                         float saturation, float aberration) {

        /** Liquid Glass: a lensing rim lit from the top left. */
        public static Optics glass(float rim, float blur) {
            return new Optics(rim, 1f, 1f, blur, 1.18f, 0f);
        }

        /** A standard material: blurred and tinted, no rim, no light. */
        public static Optics material(float blur) {
            return new Optics(0f, 0f, 0f, blur, 1.08f, 0f);
        }

        public Optics withAberration(float pixels) {
            return new Optics(rim, refraction, specular, blur, saturation, pixels);
        }
    }

    private Glass() {
    }

    /** @return a new layer: shapes drawn through it share one copy of the screen */
    public static Layer layer() {
        return new Layer();
    }

    /** Shapes that share one capture. Use one per layer per frame, then let it go. */
    public static final class Layer {

        private Texture backdrop;

        private Layer() {
        }

        /**
         * @param radius   the corner radius, in screen units
         * @param tint     its colour, mixed in by its alpha
         * @param presence 0 to 1: how formed the shape is; 0 draws nothing
         * @param press    0 to 1: a press brightening the rim
         * @return whether it drew
         */
        public boolean draw(float x, float y, float width, float height, float radius,
                            Optics optics, Color tint, float presence, float press) {
            if (presence <= 0f) {
                return false;
            }
            float formed = Math.min(1f, presence);
            return ShaderPasses.rect(ShaderCatalogue.GLASS, x, y, width, height, u -> {
                if (backdrop == null || !backdrop.isValid()) {
                    // After the frame so far is painted: the first shape's moment.
                    backdrop = ShaderPasses.sceneMipmapped();
                }
                u.sampler("uScene", backdrop, 0)
                        .set("uRadius", radius)
                        .set("uRim", optics.rim())
                        .set("uRefract", optics.refraction())
                        .set("uSpecular", optics.specular())
                        .set("uBlur", optics.blur())
                        .set("uSaturation", optics.saturation())
                        .set("uAberration", optics.aberration())
                        .set("uTint", tint.redF(), tint.greenF(), tint.blueF(), tint.alphaF())
                        .set("uPresence", formed)
                        .set("uPress", Math.max(0f, Math.min(1f, press)));
            });
        }
    }
}
