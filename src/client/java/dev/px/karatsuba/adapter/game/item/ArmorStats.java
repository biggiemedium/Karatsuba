package dev.px.karatsuba.adapter.game.item;

import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/**
 * What a piece of armour is worth, in plain numbers.
 *
 * <p>Everything an auto-armour needs to rank two pieces, read once from the stack:
 * the worn slot, the armour and toughness it adds, and the enchantments that
 * change what gets through. The ranking itself is the module's; this only
 * reports. Read by enchantment key rather than name, so it does not change with
 * the language.
 *
 * <p><b>Game thread only</b>, like the rest of this package.
 */
@Getter
public final class ArmorStats {

    /** The slot it is worn in. */
    private final Armor.Piece piece;

    /** Armour points it adds, as the bar counts them. */
    private final double armor;

    private final double toughness;

    /** Knockback resistance, 0 to 1; netherite's 0.1 a piece. */
    private final double knockbackResistance;

    private final int protection;
    private final int blastProtection;
    private final int fireProtection;
    private final int projectileProtection;

    /** Curse of Binding: once on, it cannot come off. */
    private final boolean binding;

    private final boolean mending;

    /** An elytra: worn in the chest slot, protects nothing. */
    private final boolean elytra;

    /** How much use is left, 1 new and 0 about to break; 1 for anything that cannot wear out. */
    private final float durability;

    /** For an elytra: whether it can still glide. One durability left, and it cannot. */
    private final boolean glides;

    private ArmorStats(ItemStack stack, Armor.Piece piece) {
        this.piece = piece;
        EquipmentSlot slot = piece.slot();
        double[] values = new double[3];
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            modifiers.applyModifiers(slot, (attribute, modifier) -> {
                if (attribute == EntityAttributes.ARMOR) {
                    values[0] += modifier.value();
                } else if (attribute == EntityAttributes.ARMOR_TOUGHNESS) {
                    values[1] += modifier.value();
                } else if (attribute == EntityAttributes.KNOCKBACK_RESISTANCE) {
                    values[2] += modifier.value();
                }
            });
        }
        this.armor = values[0];
        this.toughness = values[1];
        this.knockbackResistance = values[2];

        this.protection = level(stack, Enchantments.PROTECTION);
        this.blastProtection = level(stack, Enchantments.BLAST_PROTECTION);
        this.fireProtection = level(stack, Enchantments.FIRE_PROTECTION);
        this.projectileProtection = level(stack, Enchantments.PROJECTILE_PROTECTION);
        this.binding = level(stack, Enchantments.BINDING_CURSE) > 0;
        this.mending = level(stack, Enchantments.MENDING) > 0;
        this.elytra = stack.contains(DataComponentTypes.GLIDER);
        this.glides = elytra && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST);
        this.durability = !stack.isDamageable() || stack.getMaxDamage() <= 0 ? 1f
                : Math.max(0f, 1f - (float) stack.getDamage() / stack.getMaxDamage());
    }

    /**
     * @param slot any slot in {@link Inventories}' numbering, armour slots included
     * @return what the piece there is worth, or null if nothing wearable as armour
     *         is in it (a carved pumpkin or a head is not armour)
     */
    public static ArmorStats at(int slot) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || slot < 0 || slot >= Inventories.SLOT_COUNT) {
            return null;
        }
        return of(player.getInventory().getStack(slot));
    }

    private static ArmorStats of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        Armor.Piece piece = equippable == null ? null : Armor.Piece.of(equippable.slot());
        if (piece == null) {
            return null;
        }
        ArmorStats stats = new ArmorStats(stack, piece);
        return stats.armor > 0d || stats.elytra ? stats : null;
    }

    private static int level(ItemStack stack, RegistryKey<Enchantment> key) {
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry
                : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
