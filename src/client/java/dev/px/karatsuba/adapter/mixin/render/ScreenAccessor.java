package dev.px.karatsuba.adapter.mixin.render;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to a screen's title, which is final and set by the constructor.
 * For {@code DeathScreenEvent.setTitle}: the death screen draws {@code title}
 * every frame, so replacing it before the screen opens is the whole change.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Mutable
    @Accessor("title")
    void karatsuba$setTitle(Text title);
}
