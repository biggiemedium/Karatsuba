package dev.px.karatsuba;

import dev.px.karatsuba.client.Karatsuba;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Fabric's way in, and the one file allowed to see both halves of the mod.
 *
 * <p>The composition root. {@code client} may use {@code adapter}, and
 * {@code adapter} may never use {@code client} &mdash; that one-way rule is what
 * keeps the seam a seam. An entrypoint has to break it in both directions at once:
 * it implements a Fabric interface, which is the game, and it starts the client,
 * which is the other side. So it sits above both packages rather than inside
 * either, where it would make one of them a liar.
 *
 * <p>That is what leaves {@code dev.px.karatsuba.client} with no
 * {@code net.minecraft} import anywhere in it &mdash; the whole client is written
 * against Core and the adapter's seams, and this file is the proof rather than the
 * exception.
 *
 * <p>The boot is deferred to {@code CLIENT_STARTED} rather than run here. This
 * entrypoint fires while the client is still assembling itself, and the adapter
 * creates a NanoVG context, which needs a current GL context on the render thread.
 * {@code CLIENT_STARTED} is the first moment both are guaranteed.
 */
public final class KaratsubaEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(ignored -> Karatsuba.boot());
    }
}
