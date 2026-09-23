package dev.px.karatsuba.adapter.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes into a movement packet that has already been built.
 *
 * <p>The fields are {@code protected final}, which is right for a value the game
 * builds and sends immediately and wrong for the one thing a movement module
 * needs to do. {@link Mutable} lifts the final so an accessor can write.
 *
 * <p>Mixed into the base class rather than the four subclasses, because that is
 * where the fields live &mdash; the position-only, look-only, both and neither
 * variants all inherit them, so one accessor covers every packet the game can
 * choose to send.
 *
 * <p>Nothing here checks whether the field it is writing is one this particular
 * packet will serialise. {@code MovementPacketEvent} does, through
 * {@code hasPosition()}, and is the only thing that should be calling this.
 */
@Mixin(PlayerMoveC2SPacket.class)
public interface PlayerMoveC2SPacketAccessor {

    @Mutable
    @Accessor("x")
    void karatsuba$setX(double x);

    @Mutable
    @Accessor("y")
    void karatsuba$setY(double y);

    @Mutable
    @Accessor("z")
    void karatsuba$setZ(double z);

    @Mutable
    @Accessor("onGround")
    void karatsuba$setOnGround(boolean onGround);
}
