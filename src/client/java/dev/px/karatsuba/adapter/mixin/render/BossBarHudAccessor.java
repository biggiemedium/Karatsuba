package dev.px.karatsuba.adapter.mixin.render;

import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * The boss bars on screen, for {@code GameHud} to know how far down the top of the
 * screen they reach. Package-private in the game, read-only here.
 */
@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {

    @Accessor("bossBars")
    Map<UUID, ClientBossBar> karatsuba$bossBars();
}
