package dev.px.karatsuba.adapter.mixin.game;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to the game's session.
 *
 * <p>{@code MinecraftClient.session} is final and has no setter, because vanilla
 * takes it from the launcher once and never changes it. Applying a login at
 * runtime means writing it anyway, which is what {@code AuthProvider.apply}
 * exists to do &mdash; and why Core leaves that method to the adapter rather than
 * describing a session swap it could not perform.
 *
 * <p>An accessor rather than reflection: the field is remapped per version, so
 * reflection by name would be a string that silently stops matching, whereas this
 * fails at build time.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Mutable
    @Accessor("session")
    void karatsuba$setSession(Session session);
}
