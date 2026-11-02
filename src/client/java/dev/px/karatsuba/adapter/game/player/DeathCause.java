package dev.px.karatsuba.adapter.game.player;

import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.GameItem;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who killed the player, and with what: a snapshot taken when the death screen is
 * about to show.
 *
 * <p>Two sources, both what the server sent rather than words to parse:
 * <ul>
 *   <li><b>The last hit</b> ({@link #noteDamage}, from {@code LivingEntityMixin}).
 *       Since 1.20 every hit arrives with its damage type, the entity that
 *       caused it (the attacker) and the one that landed it: the end crystal,
 *       the arrow, the TNT.</li>
 *   <li><b>The death message.</b> The game gives each entity named in it a hover
 *       card with its UUID and type, and the weapon ("using [Diamond Sword]") one
 *       holding the item. That names a killer the last hit doesn't, such as "doomed
 *       to fall by", and the exact weapon.</li>
 * </ul>
 *
 * <p>With no killer, or a death the player caused, the "killer" is the player
 * themselves ({@link #isSelf()}), so a death screen always has someone to show.
 */
@Getter
public final class DeathCause {

    /** A hit older than this when the player died is not what killed them. */
    private static final long HIT_WINDOW_MS = 2_000L;

    /** What landed the hit, by entity type, for kills an item didn't do in hand. */
    private static final Map<String, String> PROJECTILES = Map.ofEntries(
            Map.entry("minecraft:end_crystal", "minecraft:end_crystal"),
            Map.entry("minecraft:tnt", "minecraft:tnt"),
            Map.entry("minecraft:tnt_minecart", "minecraft:tnt_minecart"),
            Map.entry("minecraft:trident", "minecraft:trident"),
            Map.entry("minecraft:fireball", "minecraft:fire_charge"),
            Map.entry("minecraft:small_fireball", "minecraft:fire_charge"),
            Map.entry("minecraft:dragon_fireball", "minecraft:dragon_breath"),
            Map.entry("minecraft:firework_rocket", "minecraft:firework_rocket"),
            Map.entry("minecraft:wither_skull", "minecraft:wither_skeleton_skull"),
            Map.entry("minecraft:potion", "minecraft:splash_potion"),
            Map.entry("minecraft:wind_charge", "minecraft:wind_charge"),
            Map.entry("minecraft:shulker_bullet", "minecraft:shulker_shell"),
            Map.entry("minecraft:snowball", "minecraft:snowball"),
            Map.entry("minecraft:egg", "minecraft:egg"),
            Map.entry("minecraft:arrow", "minecraft:bow"),
            Map.entry("minecraft:spectral_arrow", "minecraft:bow"));

    /** Deaths nothing held: the item that stands for each, by damage type. */
    private static final Map<String, String> CAUSES = Map.ofEntries(
            Map.entry("fall", "minecraft:feather"),
            Map.entry("ender_pearl", "minecraft:ender_pearl"),
            Map.entry("lava", "minecraft:lava_bucket"),
            Map.entry("in_fire", "minecraft:flint_and_steel"),
            Map.entry("on_fire", "minecraft:blaze_powder"),
            Map.entry("campfire", "minecraft:campfire"),
            Map.entry("hot_floor", "minecraft:magma_block"),
            Map.entry("drown", "minecraft:water_bucket"),
            Map.entry("freeze", "minecraft:powder_snow_bucket"),
            Map.entry("starve", "minecraft:rotten_flesh"),
            Map.entry("cactus", "minecraft:cactus"),
            Map.entry("sweet_berry_bush", "minecraft:sweet_berries"),
            Map.entry("out_of_world", "minecraft:ender_eye"),
            Map.entry("outside_border", "minecraft:barrier"),
            Map.entry("in_wall", "minecraft:gravel"),
            Map.entry("lightning_bolt", "minecraft:lightning_rod"),
            Map.entry("falling_anvil", "minecraft:anvil"),
            Map.entry("falling_stalactite", "minecraft:pointed_dripstone"),
            Map.entry("stalagmite", "minecraft:pointed_dripstone"),
            Map.entry("magic", "minecraft:splash_potion"),
            Map.entry("indirect_magic", "minecraft:splash_potion"),
            Map.entry("wither", "minecraft:wither_rose"),
            Map.entry("dragon_breath", "minecraft:dragon_breath"),
            Map.entry("sonic_boom", "minecraft:sculk_shrieker"),
            Map.entry("fly_into_wall", "minecraft:elytra"),
            Map.entry("generic_kill", "minecraft:command_block"));

    /** What the card calls a death nothing held, where the damage type's own name reads badly. */
    private static final Map<String, String> CAUSE_LABELS = Map.of(
            "out_of_world", "The Void",
            "in_wall", "Suffocation",
            "generic_kill", "/kill",
            "fly_into_wall", "Kinetic Energy",
            "hot_floor", "Magma",
            "in_fire", "Fire",
            "on_fire", "Burning");

    // -------------------------------------------------------------- last hit

    private static String lastType = "";
    private static UUID lastAttacker;
    private static String lastDirectType = "";
    private static ItemStack lastAttackerHand = ItemStack.EMPTY;
    private static long lastHitAt;

    /** Called by {@code LivingEntityMixin} for every hit the local player takes. */
    public static void noteDamage(DamageSource source) {
        lastType = source.getTypeRegistryEntry().getKey().map(key -> key.getValue().getPath()).orElse("");
        Entity attacker = source.getAttacker();
        lastAttacker = attacker == null ? null : attacker.getUuid();
        lastAttackerHand = attacker instanceof LivingEntity living ? living.getMainHandStack().copy() : ItemStack.EMPTY;
        Entity direct = source.getSource();
        lastDirectType = direct == null ? "" : Registries.ENTITY_TYPE.getId(direct.getType()).toString();
        lastHitAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------ the snapshot

    /** The killer, or the player if nobody killed them. */
    private final boolean self;

    /** The killer's name: a player's, a mob's ("Zombie") or its name tag. */
    private final String name;

    /** What the killer is: "Player", "Zombie". */
    private final String kind;

    /** The killer's UUID as text, what {@code PlayerFaces} takes. */
    private final String uuid;

    /** The killer's entity id, to follow them while they are loaded. */
    private final int entityId;

    private final boolean player;

    /** What did it: the weapon, the end crystal, the bow, or for a death nothing held, an item that stands for it. */
    private final GameItem used;

    /** What to call {@link #used}: the item's name, or the cause ("Fall", "The Void"). */
    private final String usedLabel;

    /** What the killer held and wore at the time; all empty for one not loaded. */
    private final Equipment equipment;

    /** Blocks from the player to the killer at the time, or {@code NaN} if they aren't loaded. */
    private final double distance;

    private DeathCause(boolean self, String name, String kind, String uuid, int entityId, boolean player,
                       GameItem used, String usedLabel, Equipment equipment, double distance) {
        this.self = self;
        this.name = name;
        this.kind = kind;
        this.uuid = uuid;
        this.entityId = entityId;
        this.player = player;
        this.used = used;
        this.usedLabel = usedLabel;
        this.equipment = equipment;
        this.distance = distance;
    }

    /** @return whether {@link #getDistance()} is known */
    public boolean hasDistance() {
        return !Double.isNaN(distance);
    }

    /**
     * @param message the death message, or null if the game gave none
     * @return the cause; null only with no player
     */
    public static DeathCause of(Text message) {
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (self == null) {
            return null;
        }
        boolean recent = System.currentTimeMillis() - lastHitAt <= HIT_WINDOW_MS;
        String type = recent ? lastType : "";

        HoverEvent.EntityContent named = null;
        ItemStack weapon = ItemStack.EMPTY;
        if (message != null) {
            HoverEvent.EntityContent[] entity = {null};
            ItemStack[] item = {ItemStack.EMPTY};
            message.visit((style, segment) -> {
                HoverEvent hover = style.getHoverEvent();
                if (hover != null) {
                    HoverEvent.EntityContent card = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
                    if (entity[0] == null && card != null && !card.uuid.equals(self.getUuid())) {
                        entity[0] = card;
                    }
                    HoverEvent.ItemStackContent held = hover.getValue(HoverEvent.Action.SHOW_ITEM);
                    if (item[0].isEmpty() && held != null) {
                        item[0] = held.asStack();
                    }
                }
                return Optional.empty();
            }, Style.EMPTY);
            named = entity[0];
            weapon = item[0];
        }

        // The killer: the last hit's attacker, else whoever the message names.
        UUID killerId = recent && lastAttacker != null ? lastAttacker : named == null ? null : named.uuid;
        Entity killer = killerId == null ? null : find(self, killerId);
        boolean bySelf = killerId == null || killerId.equals(self.getUuid());

        GameItem used = GameItem.of(usedStack(type, recent, weapon, self.getWorld()));
        String usedLabel = used.isEmpty() ? label(type) : used.getName();

        if (bySelf) {
            return new DeathCause(true, self.getName().getString(), kindOf(EntityType.PLAYER), self.getUuidAsString(),
                    self.getId(), true, used, usedLabel, Equipment.of(self.getId()), 0d);
        }
        String name = killer != null ? killer.getName().getString()
                : named != null ? named.name.map(Text::getString).orElse("?") : "?";
        EntityType<?> killerType = killer != null ? killer.getType() : named != null ? named.entityType : EntityType.PLAYER;
        int entityId = killer == null ? -1 : killer.getId();
        return new DeathCause(false, name, kindOf(killerType), killerId.toString(), entityId,
                killerType == EntityType.PLAYER, used, usedLabel, Equipment.of(entityId),
                killer == null ? Double.NaN : self.distanceTo(killer));
    }

    /**
     * What did it, in order: the weapon the message names; what landed the hit
     * (crystal, TNT, arrow from the bow in hand); the stand-in for a cause nothing
     * held (a fall, lava); the attacker's hand; nothing (a fist).
     */
    private static ItemStack usedStack(String type, boolean recent, ItemStack weapon, World world) {
        if (!weapon.isEmpty()) {
            return weapon;
        }
        if (recent && PROJECTILES.containsKey(lastDirectType)) {
            boolean arrow = lastDirectType.endsWith("arrow");
            if (arrow && (lastAttackerHand.getItem() instanceof BowItem
                    || lastAttackerHand.getItem() instanceof CrossbowItem)) {
                return lastAttackerHand;
            }
            return stackOf(PROJECTILES.get(lastDirectType));
        }
        if ("bad_respawn_point".equals(type)) {
            // An anchor blows up outside the Nether, a bed inside it.
            return stackOf(world.getRegistryKey() == World.NETHER ? "minecraft:red_bed" : "minecraft:respawn_anchor");
        }
        if (CAUSES.containsKey(type)) {
            return stackOf(CAUSES.get(type));
        }
        return recent ? lastAttackerHand : ItemStack.EMPTY;
    }

    private static String label(String type) {
        if (type.isEmpty()) {
            return "Fist";
        }
        if (CAUSE_LABELS.containsKey(type)) {
            return CAUSE_LABELS.get(type);
        }
        String spaced = type.replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    private static String kindOf(EntityType<?> type) {
        return type.getName().getString();
    }

    private static ItemStack stackOf(String itemId) {
        return new ItemStack(Registries.ITEM.get(Identifier.of(itemId)));
    }

    private static Entity find(ClientPlayerEntity self, UUID id) {
        if (self.clientWorld == null) {
            return null;
        }
        for (Entity entity : self.clientWorld.getEntities()) {
            if (entity.getUuid().equals(id)) {
                return entity;
            }
        }
        return null;
    }
}
