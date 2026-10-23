package dev.px.karatsuba.adapter.game.entity;

import com.mojang.authlib.GameProfile;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.combat.explosion.state.TargetState;
import dev.px.karatsuba.adapter.combat.ExplosionProfile;
import dev.px.karatsuba.adapter.combat.GameBlocks;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import dev.px.karatsuba.adapter.world.Voxels;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

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

    // ------------------------------------------------------------ movement

    /**
     * Moves it, the way the game moves any other player from a server update:
     * eased over a few frames, legs walking, head turned.
     *
     * <p>Through the same tracked-position path real players arrive by, so the
     * limbs swing at the speed it moves and every tracker (prediction included)
     * sees an ordinary moving player.
     */
    public static void moveTo(int id, Vec3 position, Vec2 rotation, boolean sneaking) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake == null) {
            return;
        }
        fake.updateTrackedPositionAndAngles(position.getX(), position.getY(), position.getZ(),
                rotation.getYaw(), rotation.getPitch(), 3);
        fake.updateTrackedHeadRotation(rotation.getYaw(), 3);
        fake.setSneaking(sneaking);
        fake.setPose(sneaking ? EntityPose.CROUCHING : EntityPose.STANDING);
    }

    /** @return where its feet are, or the origin if it is gone */
    public static Vec3 position(int id) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        return fake == null ? Vec3.of(0d, 0d, 0d) : Vec3.of(fake.getX(), fake.getY(), fake.getZ());
    }

    /** Puts it somewhere at once, with no easing: the start of a loop, say. */
    public static void teleport(int id, Vec3 position, Vec2 rotation) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake != null) {
            fake.refreshPositionAndAngles(position.getX(), position.getY(), position.getZ(),
                    rotation.getYaw(), rotation.getPitch());
            fake.updateTrackedPositionAndAngles(position.getX(), position.getY(), position.getZ(),
                    rotation.getYaw(), rotation.getPitch(), 1);
            fake.setHeadYaw(rotation.getYaw());
            fake.setBodyYaw(rotation.getYaw());
        }
    }

    // -------------------------------------------------------------- damage

    /**
     * Makes its armour count.
     *
     * <p>A real player's armour points come from the server; a fake has none, so
     * however much it wears it would take damage as if naked. Sums the armour and
     * toughness of what it wears into its attributes, as the server would. Call
     * after changing its equipment.
     */
    public static void refreshArmor(int id) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake == null) {
            return;
        }
        double[] totals = new double[2];
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            AttributeModifiersComponent modifiers = fake.getEquippedStack(slot)
                    .get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (modifiers == null) {
                continue;
            }
            modifiers.applyModifiers(slot, (attribute, modifier) -> {
                if (attribute == EntityAttributes.ARMOR) {
                    totals[0] += modifier.value();
                } else if (attribute == EntityAttributes.ARMOR_TOUGHNESS) {
                    totals[1] += modifier.value();
                }
            });
        }
        setBase(fake, EntityAttributes.ARMOR, totals[0]);
        setBase(fake, EntityAttributes.ARMOR_TOUGHNESS, totals[1]);
    }

    /**
     * @return what the local player's swing would do to it right now, after its
     *         armour and Protection, worked out as the game does
     *
     * <p>The weapon's damage (the player's attack attribute, Strength included)
     * scaled by the attack cooldown; Sharpness on top, scaled by the cooldown
     * alone; half again for a critical hit (falling, not sprinting, cooldown
     * nearly full); then armour and toughness, then Protection at one point a level.
     */
    public static float meleeDamage(int id) {
        ClientWorld world = Voxels.world();
        OtherClientPlayerEntity fake = find(world, id);
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (fake == null || self == null) {
            return 0f;
        }
        float charge = self.getAttackCooldownProgress(0.5f);
        float base = (float) self.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        float sharpness = level(self.getMainHandStack(), Enchantments.SHARPNESS);
        float bonus = sharpness > 0f ? 0.5f * sharpness + 0.5f : 0f;

        float damage = base * (0.2f + charge * charge * 0.8f) + bonus * charge;
        boolean critical = charge > 0.9f && self.fallDistance > 0f && !self.isOnGround() && !self.isClimbing()
                && !self.isTouchingWater() && !self.hasStatusEffect(StatusEffects.BLINDNESS)
                && !self.hasVehicle() && !self.isSprinting();
        if (critical) {
            damage *= 1.5f;
        }
        DamageSource source = world.getDamageSources().playerAttack(self);
        damage = DamageUtil.getDamageLeft(fake, damage, source, (float) fake.getArmor(),
                (float) fake.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
        int protection = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                protection += level(fake.getEquippedStack(slot), Enchantments.PROTECTION);
            }
        }
        return DamageUtil.getInflictedDamage(damage, protection);
    }

    /**
     * @return what an explosion would do to it, after armour, Blast Protection and
     *         the rest: the same 1.21.4 rules the auras score with
     */
    public static float explosionDamage(int id, Vec3 origin, double power) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake == null) {
            return 0f;
        }
        Vec3 feet = Vec3.of(fake.getX(), fake.getY(), fake.getZ());
        double distance = feet.distanceTo(origin);
        if (distance > ExplosionProfile.FALLOFF.range(power)) {
            return 0f;
        }
        double exposure = ExplosionProfile.EXPOSURE.of(origin, Voxels.toBox(fake.getBoundingBox()), GameBlocks.INSTANCE);
        double damage = ExplosionProfile.FALLOFF.damage(distance, exposure, power);
        TargetState state = ExplosionProfile.CAPTURE.capture(fake);
        damage = ExplosionProfile.DIFFICULTY.apply(damage, state);
        damage = ExplosionProfile.ARMOUR.apply(damage, state);
        damage = ExplosionProfile.RESISTANCE.apply(damage, state);
        return (float) ExplosionProfile.PROTECTION.apply(damage, state);
    }

    /** @return its absorption: golden hearts on top of health */
    public static float absorption(int id) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        return fake == null ? 0f : fake.getAbsorptionAmount();
    }

    /** Sets its absorption. A fake has no Absorption effect to raise its cap, so the cap is raised here. */
    public static void setAbsorption(int id, float amount) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake == null) {
            return;
        }
        setBase(fake, EntityAttributes.MAX_ABSORPTION, Math.max(amount, 0d));
        fake.setAbsorptionAmount(Math.max(0f, amount));
    }

    /**
     * Shows a status effect on it, by id ({@code minecraft:regeneration}), or with
     * {@code ticks} 0 takes it off. For display: what Combat Info and the like
     * read. The client does not count a fake's effects down, so the caller does.
     */
    public static void setEffect(int id, String effectId, int level, int ticks) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        Identifier key = Identifier.tryParse(effectId);
        if (fake == null || key == null || !Registries.STATUS_EFFECT.containsId(key)) {
            return;
        }
        RegistryEntry<StatusEffect> effect = Registries.STATUS_EFFECT.getEntry(Registries.STATUS_EFFECT.get(key));
        fake.removeStatusEffect(effect);
        if (ticks > 0) {
            fake.addStatusEffect(new StatusEffectInstance(effect, ticks, Math.max(0, level - 1)), null);
        }
    }

    private static void setBase(LivingEntity entity, RegistryEntry<EntityAttribute> attribute, double value) {
        EntityAttributeInstance instance = entity.getAttributeInstance(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static int level(ItemStack stack, RegistryKey<Enchantment> key) {
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    // ----------------------------------------------------------- body double

    /**
     * Adds a copy of the local player where they stand: their skin, name, kit and
     * pose. For views that leave the body behind, such as Free Cam, where the game
     * draws nothing there in first person.
     *
     * <p>Only something to look at. It cannot be hit, pushed or collided with, shows
     * no name tag, and {@link #isBodyDouble} keeps it out of every entity list the
     * client's modules read, so nothing targets it. Removed with {@link #remove}.
     *
     * @return its entity id, or {@link #NO_ENTITY} out of a world
     */
    public static int spawnBodyDouble() {
        ClientWorld world = Voxels.world();
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (world == null || self == null) {
            return NO_ENTITY;
        }
        BodyDouble copy = new BodyDouble(world, self);
        copy.setId(nextId--);
        copy.refreshPositionAndAngles(self.getX(), self.getY(), self.getZ(), self.getYaw(), self.getPitch());
        copy.setHeadYaw(self.getHeadYaw());
        copy.setBodyYaw(self.getBodyYaw());
        copy.mirror();
        world.addEntity(copy);
        return copy.getId();
    }

    /** Brings a body double up to date with the player: place, the look last sent, pose and kit. Once a tick. */
    public static void mirrorSelf(int id) {
        if (find(Voxels.world(), id) instanceof BodyDouble copy) {
            copy.mirror();
        }
    }

    /** Shows or hides a fake without removing it. */
    public static void setShown(int id, boolean shown) {
        OtherClientPlayerEntity fake = find(Voxels.world(), id);
        if (fake != null) {
            fake.setInvisible(!shown);
        }
    }

    /** @return whether {@code entity} is a body double, which nothing should treat as a player */
    public static boolean isBodyDouble(Entity entity) {
        return entity instanceof BodyDouble;
    }

    /** The local player's likeness, and nothing more. */
    private static final class BodyDouble extends OtherClientPlayerEntity {

        private final ClientPlayerEntity original;

        BodyDouble(ClientWorld world, ClientPlayerEntity original) {
            // The player's name, under a fresh UUID: the world refuses a second entity with theirs.
            super(world, new GameProfile(UUID.randomUUID(), original.getGameProfile().getName()));
            this.original = original;
        }

        /** Their skin, cape and model: looked up by UUID, a fresh one would get a default skin. */
        @Override
        public SkinTextures getSkinTextures() {
            return original.getSkinTextures();
        }

        @Override
        public boolean canHit() {
            return false;
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean isCollidable() {
            return false;
        }

        /**
         * Hides the name tag ({@code LivingEntityRenderer.hasLabel}). The body is
         * hidden only while it is also invisible, which {@link #setShown} decides.
         */
        @Override
        public boolean isInvisibleTo(PlayerEntity player) {
            return true;
        }

        /**
         * As the server has the player: looking where the last movement packet said,
         * silent rotations included, not where the client's head is.
         */
        void mirror() {
            Vec2 sent = MotionBridge.lastSentRotation();
            float yaw = sent == null ? original.getYaw() : sent.getYaw();
            float pitch = sent == null ? original.getPitch() : sent.getPitch();
            updateTrackedPositionAndAngles(original.getX(), original.getY(), original.getZ(), yaw, pitch, 3);
            updateTrackedHeadRotation(yaw, 3);
            setSneaking(original.isSneaking());
            setPose(original.getPose());
            setMainArm(original.getMainArm());
            getDataTracker().set(PLAYER_MODEL_PARTS, original.getDataTracker().get(PLAYER_MODEL_PARTS));
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack theirs = original.getEquippedStack(slot);
                if (!ItemStack.areEqual(theirs, getEquippedStack(slot))) {
                    equipStack(slot, theirs.copy());
                }
            }
        }
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
