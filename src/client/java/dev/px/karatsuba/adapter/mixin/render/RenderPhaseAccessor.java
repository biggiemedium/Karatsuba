package dev.px.karatsuba.adapter.mixin.render;

import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A render layer's bare name, such as {@code entity_cutout_no_cull} or
 * {@code text_see_through}.
 *
 * <p>{@code toString()} is no substitute: on a layer it appends every phase,
 * texture paths included, so a check for "text" would match every entity
 * texture under {@code textures/}. The name is the one stable label a layer has.
 */
@Mixin(RenderPhase.class)
public interface RenderPhaseAccessor {

    @Accessor("name")
    String karatsuba$getName();
}
