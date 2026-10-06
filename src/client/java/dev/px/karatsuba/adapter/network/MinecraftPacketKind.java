package dev.px.karatsuba.adapter.network;

import dev.px.core.network.packet.PacketKind;

/**
 * What a packet is, as {@link MinecraftPacketDescriber} files it.
 *
 * <p>Core ships no kinds but {@link PacketKind#OTHER}; these are ours. The names
 * are what a recording stores, so the ones up to {@link #TRANSACTION} match Core
 * 1.x's built-in enum and old recordings still read the same.
 *
 * <p>A kind is only a label for filters and queries. Nothing in Core reads it:
 * what a service reads is a role the describer gives &mdash; {@code asCorrection()},
 * {@code withWorldAge()} and so on.
 */
public enum MinecraftPacketKind implements PacketKind {

    /** The client reporting its own position, rotation or ground state. */
    MOVEMENT,

    /** The client reporting an action: sprint or sneak toggles, swings, use, attack. */
    ACTION,

    /** The server setting the player's position outright. */
    TELEPORT,

    /** The server setting the player's velocity. */
    VELOCITY,

    /** An explosion, which pushes the player. */
    EXPLOSION,

    /** Abilities: flying, fly speed, walk speed. */
    ABILITIES,

    /** A potion effect added or removed. */
    EFFECT,

    /** An entity attribute, such as movement speed, changed. */
    ATTRIBUTE,

    /** A block or chunk change that may alter what the player collides with. */
    WORLD_STATE,

    /** Respawn or dimension change. */
    RESPAWN,

    /**
     * Keep-alives, pings and teleport confirms: packets that exist to be
     * answered, and so the ones that tie an outbound action to its reply.
     */
    TRANSACTION,

    /** The server's clock: world age and time of day. */
    TIME,

    /** A plugin message, the brand among them. */
    PAYLOAD,

    /** A tab-list update: players joining, leaving, and their latency. */
    PLAYER_LIST
}
