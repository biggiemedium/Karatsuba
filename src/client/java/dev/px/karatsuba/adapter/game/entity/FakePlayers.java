package dev.px.karatsuba.adapter.game.entity;

import com.mojang.authlib.GameProfile;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

/**
 * Player entities that exist only on this client.
 *
 * <p>The server has never heard of them. They are drawn, ticked and found by
 * {@link Entities} like anyone else, so every module that looks at players sees
 * one &mdash; which is the point: a dummy to test ESP, name tags and combat
 * modules against without a second account.
 *
 * <p>Addressed by entity id, like every other seam. Ids are handed out from a
 * reserved negative range rather than the game's own counter: in multiplayer the
 * server numbers its entities independently, and a fake that borrowed the next
 * client id could later share it with a real entity the server sends.
 *
 * <p>The server would ignore an attack on an id it does not know, so hits never
 * reach it; the module that owns a fake decides what a hit does, through
 * {@link #hurt} and {@link #popTotem}. <b>Game thread only.</b>
 */
public final class FakePlayers {

    /** An id no entity has. What a failed {@link #spawn} returns. */
    public static final int NO_ENTITY = Integer.MIN_VALUE;

    /** Ids count down from here, well clear of anything the game hands out. */
    private static final int FIRST_ID = -1_000_000;

    private static int nextId = FIRST_ID;

    private FakePlayers() {
    }

    /**
     * Adds a fake player to the world.
     *
     * @param name     shown over its head and in its profile
     * @param position where its feet go
     * @param rotation which way it faces
     * @return its entity id, or {@link #NO_ENTITY} out of a world
     */
    public static int spawn(String name, Vec3 position, Vec2 rotation) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return NO_ENTITY;
        }
        // A fresh UUID each time: the world indexes entities by UUID and refuses a
        // second one with the same, so reusing the player's own would silently
        // never spawn.
        OtherClientPlayerEntity fake = new OtherClientPlayerEntity(world, new GameProfile(UUID.randomUUID(), name));
        fake.setId(nextId--);
        fake.refreshPositionAndAngles(position.getX(), position.getY(), position.getZ(),
                rotation.getYaw(), rotation.getPitch());
        fake.setHeadYaw(rotation.getYaw());
        fake.setBodyYaw(rotation.getYaw());

        world.addEntity(fake);
        return fake.getId();
    }

    /** Takes a fake player out of the world. Does nothing for an id that is not one. */
    public static void remove(int id) {
        ClientWorld world = Voxels.world();
        if (world != null && find(world, id) != null) {
            world.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }

    /**
     * @return whether the fake is still in the current world. False after a world
     *         change or a dimension change, either of which drops it.
     */
    public static boolean exists(int id) {
        ClientWorld world = Voxels.world();
        return world != null && find(world, id) != null;
    }

    /**
     * Turns it to face a direction.
     *
     * <p>Head and body together, because a fake has no movement for the body to
     * lag behind. The game interpolates from last tick's rotation on its own, so
     * calling this once a tick turns it smoothly.
     */
    public static void setRotation(int id, Vec2 rotation) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake != null) {
            fake.setYaw(rotation.getYaw());
            fake.setPitch(rotation.getPitch());
            fake.setHeadYaw(rotation.getYaw());
            fake.setBodyYaw(rotation.getYaw());
        }
    }

    /** Sets its health, clamped to 0..max. Display only: the client has no say in anyone's real health. */
    public static void setHealth(int id, float health) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake != null) {
            fake.setHealth(Math.max(0f, Math.min(fake.getMaxHealth(), health)));
        }
    }

    /** @return its maximum health, or 20 (a player's) if it is gone */
    public static float maxHealth(int id) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        return fake == null ? 20f : fake.getMaxHealth();
    }

    /**
     * Plays a hit on it: the red flash, the flinch and the hurt sound, as the
     * game would for a real player struck by the local one. Health is not changed.
     */
    public static void hurt(int id) {
        ClientWorld world = Voxels.world();
        OtherClientPlayerEntity fake = find(world, id);
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (fake != null && self != null) {
            fake.onDamaged(world.getDamageSources().playerAttack(self));
        }
    }

    /** Plays a totem of undying going off: the burst of particles and the sound. */
    public static void popTotem(int id) {
        ClientWorld world = Voxels.world();
        OtherClientPlayerEntity fake = find(world, id);
        if (fake == null) {
            return;
        }
        // What the game itself does on the totem status, minus the item flying up
        // the screen, which is only ever shown for the local player's own totem.
        MinecraftClient.getInstance().particleManager.addEmitter(fake, ParticleTypes.TOTEM_OF_UNDYING, 30);
        world.playSound(fake.getX(), fake.getY(), fake.getZ(), SoundEvents.ITEM_TOTEM_USE,
                fake.getSoundCategory(), 1f, 1f, false);
    }

    /**
     * @return the fake with this id, or null
     *
     * <p>Only ids from the reserved range count. Every other player in the world
     * is an {@code OtherClientPlayerEntity} too, and without this check
     * {@link #remove} would happily delete a real one from the client.
     */
    private static OtherClientPlayerEntity find(ClientWorld world, int id) {
        if (world == null || id == NO_ENTITY || id > FIRST_ID) {
            return null;
        }
        return world.getEntityById(id) instanceof OtherClientPlayerEntity fake ? fake : null;
    }
}
