package dev.px.karatsuba.adapter.game.item;

import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;

import java.util.EnumMap;
import java.util.Map;

/**
 * What another entity is holding and wearing.
 *
 * <p>{@link Armor} and {@link Inventories} answer for the local player, whose
 * whole inventory is known. For anyone else the client only ever sees the six
 * visible slots, which is exactly what this is: a snapshot of those six, by
 * entity id, in the same {@link GameItem} values.
 *
 * <p><b>Game thread only.</b>
 */
public final class Equipment {

    /** The six slots anyone can see, in the game's own order. */
    public enum Slot {
        MAIN_HAND(EquipmentSlot.MAINHAND),
        OFF_HAND(EquipmentSlot.OFFHAND),
        HEAD(EquipmentSlot.HEAD),
        CHEST(EquipmentSlot.CHEST),
        LEGS(EquipmentSlot.LEGS),
        FEET(EquipmentSlot.FEET);

        private final EquipmentSlot game;

        Slot(EquipmentSlot game) {
            this.game = game;
        }
    }

    private static final Equipment NONE = new Equipment(new EnumMap<>(Slot.class));

    private final Map<Slot, GameItem> items;

    private Equipment(Map<Slot, GameItem> items) {
        this.items = items;
    }

    /**
     * @param entityId the entity, as a {@code GameEntity} carries it
     * @return its equipment, or an all-empty snapshot for anything that cannot
     *         hold items or is no longer in the world
     */
    public static Equipment of(int entityId) {
        ClientWorld world = Voxels.world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        if (!(entity instanceof LivingEntity living)) {
            return NONE;
        }
        Map<Slot, GameItem> items = new EnumMap<>(Slot.class);
        for (Slot slot : Slot.values()) {
            items.put(slot, GameItem.of(living.getEquippedStack(slot.game)));
        }
        return new Equipment(items);
    }

    /**
     * Puts an item in an entity's slot, client-side.
     *
     * <p>Only what this client draws changes. On a real entity the server's next
     * equipment update puts things back, so this is for entities the client owns
     * itself, such as a fake player.
     *
     * @param spec the item to make; {@link ItemSpec#NONE} empties the slot
     */
    public static void equip(int entityId, Slot slot, ItemSpec spec) {
        ClientWorld world = Voxels.world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        if (entity instanceof LivingEntity living) {
            living.equipStack(slot.game, spec.toStack());
        }
    }

    /** @return what is in a slot; {@link GameItem#EMPTY}, never null, when nothing is */
    public GameItem get(Slot slot) {
        return items.getOrDefault(slot, GameItem.EMPTY);
    }
}
