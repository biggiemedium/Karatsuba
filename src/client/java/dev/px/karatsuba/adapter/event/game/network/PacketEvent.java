package dev.px.karatsuba.adapter.event.game.network;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;

/**
 * A packet on its way in or out, before the game has dealt with it.
 *
 * <p>The event most of a utility client ends up built on, because a packet is the
 * only thing the server and the client actually agree on: everything else is a
 * rendering of it. Cancelling one suppresses it outright.
 *
 * <h2>Matching without importing the game</h2>
 * The packet is carried as an {@link Object}, and the useful handle on it is
 * {@link #getName()} &mdash; the packet class's simple name, resolved once when
 * the event is built. A module matches on that and stays free of
 * {@code net.minecraft}:
 *
 * <pre>{@code
 * @Subscribe
 * private void onSend(PacketEvent.Send event) {
 *     if (event.is("PlayerMoveC2SPacket")) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <p>That is a deliberate trade. A name is a string, so the compiler will not
 * catch a typo and a Mojang rename will not break the build &mdash; it will
 * simply stop matching. In exchange a module compiles against no game version at
 * all. Where a module needs more than the identity, the answer is not to cast
 * the packet but to add a named event to this package that carries what the
 * module actually wants: {@link ScreenCloseEvent} is that pattern, and exists
 * because "did my own inventory just close" is a better question than "is this a
 * {@code CloseHandledScreenC2SPacket} whose sync id matches".
 *
 * <h2>Direction</h2>
 * {@link Send} and {@link Receive} are separate types rather than a flag, so a
 * handler narrows by declaring the parameter it wants. Core dispatches to
 * supertypes, so a handler on {@code PacketEvent} still sees both.
 *
 * <h2>Threading</h2>
 * <b>{@link Send} arrives on whichever thread sent the packet</b>, which is
 * normally the game thread. <b>{@link Receive} arrives on a Netty worker</b>,
 * which is never the game thread: it fires as the packet comes off the socket,
 * before the game has scheduled it. Reading a field is fine; touching the world,
 * the HUD or anything a frame is drawing from is not. Hand that to
 * {@code Core.threads().sync(...)} and it runs on the next tick.
 */
@Getter
public abstract class PacketEvent extends CancellableEvent {

    /**
     * The packet itself, as an opaque handle.
     *
     * <p>Untyped on purpose: typing it would put a {@code net.minecraft} class in
     * the signature, and every module that touched the event would inherit the
     * import. Code in the adapter that genuinely needs the packet can cast it;
     * code in the client should match on {@link #getName()}.
     */
    private final Object packet;

    /**
     * The packet class's simple name, for example {@code PlayerMoveC2SPacket}.
     *
     * <p>Resolved once here rather than per handler: a busy tick posts hundreds
     * of these, and {@code getClass().getSimpleName()} is not free.
     */
    private final String name;

    private PacketEvent(Object packet) {
        this.packet = packet;
        this.name = packet.getClass().getSimpleName();
    }

    /** @return whether this is the named packet. Case-sensitive, like the class name. */
    public final boolean is(String simpleName) {
        return name.equals(simpleName);
    }

    /** @return whether this is any of the named packets. */
    public final boolean isAny(String... simpleNames) {
        for (String candidate : simpleNames) {
            if (name.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }

    /**
     * A packet the client is about to send.
     *
     * <p>Cancelling stops it reaching the server. Posted on the sending thread,
     * which is the game thread for anything a module causes.
     */
    public static final class Send extends PacketEvent {

        public Send(Object packet) {
            super(packet);
        }
    }

    /**
     * A packet that has arrived and not yet been handled.
     *
     * <p>Cancelling stops the game seeing it. <b>Posted on a Netty worker</b>
     * &mdash; see the threading note on this class.
     */
    public static final class Receive extends PacketEvent {

        public Receive(Object packet) {
            super(packet);
        }
    }
}
