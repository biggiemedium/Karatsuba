package dev.px.karatsuba.adapter.combat;

import dev.px.combat.explosion.state.TargetState;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.game.item.Armor;
import dev.px.karatsuba.adapter.game.item.ArmorStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;

import java.util.Map;

/**
 * What an explosion would do to the local player somewhere they are not yet,
 * wearing a chest piece they do not have on yet.
 *
 * <p>The question an elytra guard asks: "if that crystal goes off once I have
 * swapped, where I will be by then, how much does it hurt with the elytra on,
 * and how much with the chestplate?" Neither the position nor the armour is the
 * player's real one, so the combat library's live model, which reads both off
 * the entity, cannot answer it. This runs the very same 1.21.4 rules from
 * {@link ExplosionProfile} (exposure, falloff, difficulty, armour, resistance,
 * protection) over a {@link TargetState} with the chest slot swapped, at a box
 * moved to the given feet position.
 *
 * <p><b>Game thread only.</b>
 */
public final class SelfBlast {

    private SelfBlast() {
    }

    /**
     * @param origin the explosion's centre: where the crystal stands
     * @param feet   where the player's feet would be
     * @param chest  the chest piece to count instead of the worn one; null counts a
     *               bare chest, which is what an elytra is worth
     * @return the damage an end crystal there would deal, after armour; 0 out of range
     */
    public static double crystal(Vec3 origin, Vec3 feet, ArmorStats chest) {
        return damage(origin, ExplosionProfile.END_CRYSTAL.getPower(), feet, chest);
    }

    /** As {@link #crystal}, with the chest piece actually worn. */
    public static double crystalAsWorn(Vec3 origin, Vec3 feet) {
        return damage(origin, ExplosionProfile.END_CRYSTAL.getPower(), feet, ArmorStats.at(chestSlot()));
    }

    private static double damage(Vec3 origin, double power, Vec3 feet, ArmorStats chest) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || origin == null || feet == null) {
            return 0d;
        }
        double distance = feet.distanceTo(origin);
        if (distance > ExplosionProfile.FALLOFF.range(power)) {
            return 0d;
        }
        Box box = Box.around(feet, player.getWidth(), player.getHeight());
        double exposure = ExplosionProfile.EXPOSURE.of(origin, box, GameBlocks.INSTANCE);
        double damage = ExplosionProfile.FALLOFF.damage(distance, exposure, power);

        TargetState state = withChest(player, chest);
        damage = ExplosionProfile.DIFFICULTY.apply(damage, state);
        damage = ExplosionProfile.ARMOUR.apply(damage, state);
        damage = ExplosionProfile.RESISTANCE.apply(damage, state);
        return ExplosionProfile.PROTECTION.apply(damage, state);
    }

    /** The player's captured state, with the worn chest piece's share swapped for {@code chest}'s. */
    private static TargetState withChest(ClientPlayerEntity player, ArmorStats chest) {
        TargetState captured = ExplosionProfile.CAPTURE.capture(player);
        ArmorStats worn = ArmorStats.at(chestSlot());

        double armor = player.getAttributeValue(EntityAttributes.ARMOR) - armorOf(worn) + armorOf(chest);
        double toughness = player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)
                - toughnessOf(worn) + toughnessOf(chest);
        double protection = captured.get(ExplosionProfile.PROTECTION_POINTS)
                - protectionOf(worn) + protectionOf(chest);

        TargetState.Builder builder = TargetState.builder();
        for (Map.Entry<String, Object> entry : captured.asMap().entrySet()) {
            builder.putAny(entry.getKey(), entry.getValue());
        }
        // The capture floors armour, as getArmor() does; so does this.
        return builder.put(ExplosionProfile.ARMOR, Math.floor(Math.max(0d, armor)))
                .put(ExplosionProfile.TOUGHNESS, Math.max(0d, toughness))
                .put(ExplosionProfile.PROTECTION_POINTS, Math.max(0d, protection))
                .build();
    }

    private static int chestSlot() {
        return Armor.Piece.CHESTPLATE.inventorySlot();
    }

    private static double armorOf(ArmorStats stats) {
        return stats == null ? 0d : stats.getArmor();
    }

    private static double toughnessOf(ArmorStats stats) {
        return stats == null ? 0d : stats.getToughness();
    }

    /** Protection points against an explosion: one a level of Protection, two of Blast Protection. */
    private static double protectionOf(ArmorStats stats) {
        return stats == null ? 0d : stats.getProtection() + 2d * stats.getBlastProtection();
    }
}
