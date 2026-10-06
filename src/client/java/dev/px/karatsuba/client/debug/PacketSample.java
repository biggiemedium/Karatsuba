package dev.px.karatsuba.client.debug;

import dev.px.core.network.packet.PacketKind;
import lombok.Getter;

/**
 * One packet as the debug monitor saw it: already described, so a name and a
 * kind rather than a game object. Recorded off the game thread, read on the
 * render thread, so it holds nothing that could change underneath either.
 */
@Getter
public final class PacketSample {

    private final String name;
    private final PacketKind kind;
    private final boolean inbound;

    /** On {@code Core.network().nanoTime()}, the clock every network service shares. */
    private final long nanos;

    PacketSample(String name, PacketKind kind, boolean inbound, long nanos) {
        this.name = name;
        this.kind = kind;
        this.inbound = inbound;
        this.nanos = nanos;
    }
}
