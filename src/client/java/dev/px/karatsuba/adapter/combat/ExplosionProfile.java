package dev.px.karatsuba.adapter.combat;

import dev.px.combat.explosion.ExplosionModel;
import dev.px.combat.explosion.Explosive;
import dev.px.combat.explosion.rule.Exposure;
import dev.px.combat.explosion.rule.Falloff;
import dev.px.combat.explosion.rule.Mitigation;
import dev.px.combat.explosion.rule.SampleGrid;
import dev.px.combat.explosion.state.StateCapture;
import dev.px.combat.explosion.state.StateMitigation;
import dev.px.combat.explosion.state.TargetState;
import dev.px.core.entity.Tracked;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * How explosions hurt in 1.21.4: the profile the combat library's
 * {@link ExplosionModel} is put together from.
 *
 * <p>Every number here was read from the game's own code for this version, not
 * the wiki, and each rule names the method it mirrors. When Mojang changes one,
 * change the one rule; the damage monitor and test vectors say which.
 *
 * <table>
 *   <caption>The rules</caption>
 *   <tr><th>Rule</th><th>1.21.4</th><th>From</th></tr>
 *   <tr><td>distance</td><td>to the target's feet</td>
 *       <td>{@code ExplosionBehavior.calculateDamage}</td></tr>
 *   <tr><td>{@link #FALLOFF}</td>
 *       <td>within {@code 2·power}: {@code 7·power·(i² + i) + 1}, {@code i = (1 − d/2p)·exposure}; not
 *           truncated</td>
 *       <td>{@code ExplosionBehavior.calculateDamage}</td></tr>
 *   <tr><td>{@link #EXPOSURE}</td>
 *       <td>share of rays from a grid over the hitbox to the centre that reach it,
 *           against collision shapes</td>
 *       <td>{@code ExplosionImpl.calculateReceivedDamage}</td></tr>
 *   <tr><td>{@link #DIFFICULTY}</td><td>players only: peaceful 0, easy {@code min(d/2+1, d)}, hard ×1.5</td>
 *       <td>{@code PlayerEntity.damage}</td></tr>
 *   <tr><td>{@link #ARMOUR}</td><td>{@code d·(1 − clamp(armor − d/(2 + toughness/4), armor/5, 20)/25)}</td>
 *       <td>{@code DamageUtil.getDamageLeft}</td></tr>
 *   <tr><td>{@link #RESISTANCE}</td><td>{@code d·(25 − 5·level)/25}</td>
 *       <td>{@code LivingEntity.modifyAppliedDamage}</td></tr>
 *   <tr><td>{@link #PROTECTION}</td><td>{@code d·(1 − min(points, 20)/25)}; Protection 1 a level, Blast
 *           Protection 2</td>
 *       <td>{@code DamageUtil.getInflictedDamage}</td></tr>
 * </table>
 *
 * <p>Steps run in that order, as the game applies them. Absorption is not a
 * step: it does not reduce damage, it is health ({@code Vitals.pool}).
 *
 * <h2>Not modelled</h2>
 * A raised shield blocks an explosion it faces. Whether it faces one depends on
 * where the explosion is, which a mitigation step is never told, so a shielding
 * target is over-predicted. Status effects of other entities reach the client
 * only as far as the server sends them.
 *
 * <h2>Live and replayed</h2>
 * Every step reads a {@link TargetState} captured off the entity by
 * {@link #CAPTURE}, so the same steps run in game ({@link #model()}) and against
 * recorded explosions ({@link #replayModel()}): the profile you test is the
 * profile you play with.
 */
public final class ExplosionProfile {

    /** An end crystal: power 6, from where the crystal stands ({@code EndCrystalEntity.damage}). */
    public static final Explosive END_CRYSTAL = Explosive.of("end crystal", 6);

    /** A bed used where beds do not work: power 5, from the centre of its head ({@code BedBlock.onUse}). */
    public static final Explosive BED = Explosive.of("bed", 5);

    /** A charged respawn anchor used where anchors do not work: power 5, from its centre. */
    public static final Explosive RESPAWN_ANCHOR = Explosive.of("respawn anchor", 5);

    /** {@link TargetState} keys {@link #CAPTURE} fills in and the steps read. */
    public static final String ARMOR = "armor";
    public static final String TOUGHNESS = "toughness";
    public static final String RESISTANCE_LEVEL = "resistance";
    public static final String PROTECTION_POINTS = "protection";
    public static final String PLAYER = "player";
    public static final String DIFFICULTY_LEVEL = "difficulty";

    /**
     * Range {@code 2·power}; within it, the damage curve. The game computes it in
     * doubles and stores it as a float, which the cast mirrors.
     */
    public static final Falloff FALLOFF = Falloff.of(
            power -> 2d * power,
            (distance, exposure, power) -> {
                double impact = (1d - distance / (2d * power)) * exposure;
                return (float) ((impact * impact + impact) / 2d * 7d * (2d * power) + 1d);
            });

    /**
     * The sample points: a grid over the hitbox, {@code 1/(size·2 + 1)} apart on
     * each axis, centred in x and z. Stepped in doubles, as the game steps it.
     */
    public static final SampleGrid GRID = (box, sink) -> {
        double width = box.getWidth();
        double height = box.getHeight();
        double depth = box.getDepth();
        double minX = box.getMin().getX();
        double minY = box.getMin().getY();
        double minZ = box.getMin().getZ();
        double stepX = 1d / (width * 2d + 1d);
        double stepY = 1d / (height * 2d + 1d);
        double stepZ = 1d / (depth * 2d + 1d);
        double offsetX = (1d - Math.floor(1d / stepX) * stepX) / 2d;
        double offsetZ = (1d - Math.floor(1d / stepZ) * stepZ) / 2d;
        if (stepX < 0d || stepY < 0d || stepZ < 0d) {
            return;
        }
        for (double fx = 0d; fx <= 1d; fx += stepX) {
            for (double fy = 0d; fy <= 1d; fy += stepY) {
                for (double fz = 0d; fz <= 1d; fz += stepZ) {
                    sink.point(minX + fx * width + offsetX, minY + fy * height, minZ + fz * depth + offsetZ);
                }
            }
        }
    };

    public static final Exposure EXPOSURE = Exposure.sampled(GRID);

    /** What the steps below read off a living entity. */
    public static final StateCapture<LivingEntity> CAPTURE = entity -> TargetState.builder()
            // getArmor() floors the attribute; toughness is used as is.
            .put(ARMOR, Math.floor(entity.getAttributeValue(EntityAttributes.ARMOR)))
            .put(TOUGHNESS, entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS))
            .put(RESISTANCE_LEVEL, resistanceLevel(entity))
            .put(PROTECTION_POINTS, protectionPoints(entity))
            .put(PLAYER, entity instanceof PlayerEntity)
            .put(DIFFICULTY_LEVEL, difficulty())
            .build();

    /** Explosions scale with difficulty against players, and against nothing else. */
    public static final StateMitigation DIFFICULTY = (damage, state) -> {
        if (!state.flag(PLAYER)) {
            return damage;
        }
        switch ((int) state.get(DIFFICULTY_LEVEL)) {
            case 0:
                return 0d;
            case 1:
                return Math.min(damage / 2d + 1d, damage);
            case 3:
                return damage * 3d / 2d;
            default:
                return damage;
        }
    };

    public static final StateMitigation ARMOUR = (damage, state) -> {
        float armor = (float) state.get(ARMOR);
        float toughness = (float) state.get(TOUGHNESS);
        float hit = (float) damage;
        float breakdown = 2f + toughness / 4f;
        float effective = Math.max(armor * 0.2f, Math.min(20f, armor - hit / breakdown));
        return hit * (1f - effective / 25f);
    };

    public static final StateMitigation RESISTANCE = (damage, state) -> {
        int level = (int) state.get(RESISTANCE_LEVEL);
        if (level <= 0) {
            return damage;
        }
        return Math.max((float) damage * (25 - level * 5) / 25f, 0f);
    };

    public static final StateMitigation PROTECTION = (damage, state) -> {
        float points = (float) state.get(PROTECTION_POINTS);
        if (points <= 0f) {
            return damage;
        }
        return (float) damage * (1f - Math.max(0f, Math.min(20f, points)) / 25f);
    };

    private ExplosionProfile() {
    }

    /** The model a search scores with, live. */
    public static ExplosionModel<LivingEntity> model() {
        return ExplosionModel.<LivingEntity>builder()
                .measureFrom(Tracked::getPosition)
                .exposure(EXPOSURE)
                .falloff(FALLOFF)
                .mitigation(Mitigation.fromState(CAPTURE, DIFFICULTY, ARMOUR, RESISTANCE, PROTECTION))
                .build();
    }

    /** The same model over recorded {@link TargetState}s, for replaying test vectors. */
    public static ExplosionModel<TargetState> replayModel() {
        return ExplosionModel.<TargetState>builder()
                .measureFrom(Tracked::getPosition)
                .exposure(EXPOSURE)
                .falloff(FALLOFF)
                .mitigation(Mitigation.ofState(DIFFICULTY, ARMOUR, RESISTANCE, PROTECTION))
                .build();
    }

    // ------------------------------------------------------------ internals

    /** Resistance's level (amplifier + 1), or 0 without it. */
    private static int resistanceLevel(LivingEntity entity) {
        StatusEffectInstance resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
        return resistance == null ? 0 : resistance.getAmplifier() + 1;
    }

    /**
     * Protection points against an explosion, summed over the armour worn:
     * Protection gives one a level and Blast Protection two.
     *
     * <p>The game asks {@code EnchantmentHelper.getProtectionAmount}, which needs a
     * server world; the enchantments themselves are on the items, which the server
     * sends with every player's equipment.
     */
    private static int protectionPoints(LivingEntity entity) {
        int points = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.hasEnchantments()) {
                continue;
            }
            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry
                    : stack.getEnchantments().getEnchantmentEntries()) {
                RegistryEntry<Enchantment> enchantment = entry.getKey();
                if (enchantment.matchesKey(Enchantments.PROTECTION)) {
                    points += entry.getIntValue();
                } else if (enchantment.matchesKey(Enchantments.BLAST_PROTECTION)) {
                    points += 2 * entry.getIntValue();
                }
            }
        }
        return points;
    }

    /** The world's difficulty as 0 (peaceful) to 3 (hard). */
    private static int difficulty() {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world == null ? 2 : world.getDifficulty().getId();
    }
}
