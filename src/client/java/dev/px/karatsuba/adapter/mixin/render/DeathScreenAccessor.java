package dev.px.karatsuba.adapter.mixin.render;

import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * What a death screen was built with, for {@code DeathScreenEvent}. Both are
 * private and final, with no getters.
 */
@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {

    /** @return the death message, or null when the game gave none */
    @Accessor("message")
    Text karatsuba$getMessage();

    @Accessor("isHardcore")
    boolean karatsuba$isHardcore();
}
