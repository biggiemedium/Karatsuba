package dev.px.karatsuba.adapter.combat;

import dev.px.combat.anchor.AnchorPlacement;
import dev.px.combat.anchor.AnchorRules;
import dev.px.combat.bed.BedPart;
import dev.px.combat.bed.BedPlacement;
import dev.px.combat.bed.BedRules;
import dev.px.combat.crystal.CrystalBody;
import dev.px.combat.crystal.CrystalRules;
import dev.px.combat.crystal.Placement;
import dev.px.combat.explosion.ExplosionModel;
import dev.px.combat.explosion.state.StateCapture;
import dev.px.combat.hole.HoleFill;
import dev.px.combat.hole.HoleFinder;
import dev.px.combat.hole.HoleRules;
import dev.px.combat.hole.HoleWatch;
import dev.px.combat.monitor.DamageMonitor;
import dev.px.combat.monitor.Vitals;
import dev.px.combat.place.Clicks;
import dev.px.combat.place.PlacementPlanner;
import dev.px.combat.place.PlacementStyle;
import dev.px.combat.search.AnchorSearch;
import dev.px.combat.search.BedSearch;
import dev.px.combat.search.CrystalSearch;
import dev.px.core.movement.prediction.Lookahead;
import dev.px.combat.search.rule.Reach;
import dev.px.combat.search.timing.AttackLog;
import dev.px.combat.trap.TrapSearch;
import dev.px.combat.vector.capture.VectorRecorder;
import dev.px.core.Core;
import dev.px.core.entity.EntityService;
import dev.px.core.entity.EntityTracker;
import dev.px.core.world.BlockView;
import dev.px.core.world.Obstructions;
import dev.px.karatsuba.adapter.game.entity.CrystalTracker;
import dev.px.karatsuba.adapter.game.entity.LivingTracker;
import dev.px.karatsuba.adapter.game.entity.PlayerTracker;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * The {@link CombatKit} for 1.21.4, typed by {@code LivingEntity}.
 *
 * <p>Every rule here was checked against the game's own code for this version;
 * the numbers that are not obvious say where they came from. Built once by
 * {@link Combat#install}; the rules hold no per-tick state, so one of each serves
 * every module.
 */
@Getter
@Accessors(fluent = true)
final class MinecraftCombatKit implements CombatKit<LivingEntity> {

    /** The player's reach for blocks when there is no player to ask. */
    private static final double DEFAULT_BLOCK_REACH = 4.5d;

    /**
     * How tall a bed's collision box is: what must hold no entity at the foot.
     * The head is placed without an entity check ({@code BedBlock.getPlacementState}).
     */
    private static final double BED_HEIGHT = 0.5625d;

    @Getter(AccessLevel.NONE)
    private final EntityService entities;
    private final EntityTracker<LivingEntity> living;
    private final EntityTracker<LivingEntity> players;
    private final EntityTracker<?> crystals;

    private final Obstructions everything;
    private final Obstructions blocking;
    private final Obstructions playerBodies;

    private final BlockView blocks = GameBlocks.INSTANCE;
    private final ExplosionModel<LivingEntity> explosions = ExplosionProfile.model();
    private final StateCapture<LivingEntity> state = ExplosionProfile.CAPTURE;
    private final Vitals<LivingEntity> vitals = new LivingVitals();

    private final CrystalRules<LivingEntity> crystalRules;
    private final BedRules<LivingEntity> bedRules;
    private final AnchorRules<LivingEntity> anchorRules;
    private final HoleRules holeRules;
    private final HoleFinder holeFinder;

    private final AttackLog log;
    private final Lookahead<LivingEntity> lookahead;
    private final Reach blockReach;

    private final DamageMonitor<LivingEntity> monitor;
    private final VectorRecorder<LivingEntity> vectors;

    @Getter(AccessLevel.NONE)
    private BooleanSupplier healthTrusted = () -> true;

    MinecraftCombatKit(Core core) {
        this.entities = core.getEntityService();
        this.living = entities.require(LivingTracker.class);
        this.players = entities.require(PlayerTracker.class);
        this.crystals = entities.require(CrystalTracker.class);

        // Two trackers of our own for the two kinds of "in the way". A crystal is
        // refused over any entity but spectators (EndCrystalItem asks
        // getOtherEntities); a block only over entities the game checks
        // collisions for (World.doesNotIntersectEntities).
        EntityTracker<Entity> anyEntity = entities.register(
                EntityTracker.of(Entity.class, entity -> !entity.isRemoved() && !entity.isSpectator()));
        EntityTracker<Entity> solidEntity = entities.register(
                EntityTracker.of(Entity.class, entity -> !entity.isRemoved() && entity.intersectionChecked));
        this.everything = Obstructions.of(entities, anyEntity);
        this.blocking = Obstructions.of(entities, solidEntity);
        this.playerBodies = Obstructions.of(entities, players);

        this.crystalRules = CrystalRules.<LivingEntity>builder()
                // One block of air above the base, and no entity in the two
                // blocks above it (EndCrystalItem.useOnBlock): not the wiki's two.
                .placement(Placement.clearance(GameBlocks::isCrystalBase, 1, GameBlocks::isAir, 2d))
                // Centre of the block above, 2 x 2 (EntityType.END_CRYSTAL),
                // exploding from where it stands.
                .body(CrystalBody.at(0.5d, 1d, 0.5d).size(2d, 2d).explodingAt(0d, 0d, 0d))
                .explosive(ExplosionProfile.END_CRYSTAL)
                .model(explosions)
                .blocks(blocks)
                .obstructions(everything)
                .build();

        this.bedRules = BedRules.<LivingEntity>builder()
                .placement(BedPlacement.clearance(GameBlocks::isReplaceable, BED_HEIGHT, 0d))
                // From the centre of the head, whichever half was used (BedBlock.onUse).
                .explodingAt(0.5d, 0.5d, 0.5d)
                .usableFrom(BedPart.FOOT, BedPart.HEAD)
                .explodesWhen(() -> {
                    ClientWorld world = MinecraftClient.getInstance().world;
                    return world != null && !world.getDimension().bedWorks();
                })
                .lookup(GameBlocks::bedHeadAt)
                .explosive(ExplosionProfile.BED)
                .model(explosions)
                .blocks(blocks)
                .obstructions(blocking)
                .build();

        this.anchorRules = AnchorRules.<LivingEntity>builder()
                .placement(AnchorPlacement.clearance(GameBlocks::isReplaceable))
                // From the centre of the block (RespawnAnchorBlock.explode).
                .explodingAt(0.5d, 0.5d, 0.5d)
                .explodesWhen(() -> {
                    ClientWorld world = MinecraftClient.getInstance().world;
                    return world != null && !world.getDimension().respawnAnchorWorks();
                })
                .lookup(GameBlocks::anchorChargesAt)
                .explosive(ExplosionProfile.RESPAWN_ANCHOR)
                .model(explosions)
                .blocks(blocks)
                .obstructions(blocking)
                .build();

        this.holeRules = HoleRules.builder()
                .walls(GameBlocks::isBlastProof)
                .open(GameBlocks::isOpen)
                .headroom(2)
                .safe(GameBlocks::isUnbreakable)
                .build();
        this.holeFinder = new HoleFinder(holeRules);

        this.log = AttackLog.ticking(core.getBus());
        this.lookahead = Lookahead.predicted(core.getPredictionService());
        this.blockReach = Reach.of(MinecraftCombatKit::blockRange, MinecraftCombatKit::blockRange);

        this.vectors = VectorRecorder.<LivingEntity>builder()
                .state(state)
                .blocks(blocks)
                .build();
        this.monitor = DamageMonitor.<LivingEntity>builder()
                .model(explosions)
                .blocks(blocks)
                .vitals(vitals)
                .targets(entities, living)
                .recorder(vectors)
                .bus(core.getBus())
                .build();
    }

    // ------------------------------------------------------------- entities

    @Override
    public int id(Object entity) {
        return entity instanceof Entity game ? game.getId() : -1;
    }

    @Override
    public String name(Object entity) {
        return entity instanceof Entity game ? game.getName().getString() : "";
    }

    @Override
    public boolean isFriend(LivingEntity entity) {
        return entity instanceof PlayerEntity && Core.social().isFriend(entity.getName().getString());
    }

    @Override
    public void trustHealth(BooleanSupplier trusted) {
        this.healthTrusted = trusted == null ? () -> true : trusted;
    }

    @Override
    public double armourWear(LivingEntity entity) {
        double lowest = Double.NaN;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }
            double left = 100d * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage();
            if (Double.isNaN(lowest) || left < lowest) {
                lowest = left;
            }
        }
        return lowest;
    }

    // ------------------------------------------------------------ searching

    @Override
    public CrystalSearch.Builder<LivingEntity> crystalSearch() {
        return CrystalSearch.<LivingEntity>builder()
                .rules(crystalRules)
                .crystals(crystals)
                .entities(entities)
                .vitals(vitals)
                .log(log)
                .lookahead(lookahead);
    }

    @Override
    public BedSearch.Builder<LivingEntity> bedSearch() {
        return BedSearch.<LivingEntity>builder()
                .rules(bedRules)
                .entities(entities)
                .vitals(vitals)
                .log(log)
                .lookahead(lookahead);
    }

    @Override
    public AnchorSearch.Builder<LivingEntity> anchorSearch() {
        return AnchorSearch.<LivingEntity>builder()
                .rules(anchorRules)
                .entities(entities)
                .vitals(vitals)
                .log(log)
                .lookahead(lookahead);
    }

    @Override
    public HoleWatch.Builder holeWatch() {
        return HoleWatch.builder()
                .finder(holeFinder)
                .prediction(Core.prediction())
                .obstructions(playerBodies);
    }

    @Override
    public HoleFill.Builder<LivingEntity> holeFill() {
        return HoleFill.<LivingEntity>builder()
                .finder(holeFinder)
                .prediction(Core.prediction())
                .entities(entities)
                .blocks(blocks)
                .obstructions(playerBodies)
                .log(log);
    }

    // -------------------------------------------------------------- placing

    @Override
    public PlacementStyle vanillaStyle() {
        return PlacementStyle.vanilla(blockReach, blocks);
    }

    @Override
    public PlacementStyle strictStyle() {
        return PlacementStyle.strict(blockReach, blocks);
    }

    @Override
    public Clicks clicks(PlacementStyle style) {
        return style.clicks(GameBlocks::isSupport, GameBlocks::isReplaceable);
    }

    @Override
    public PlacementPlanner.Builder planner(PlacementStyle style) {
        return style.planner(clicks(style), blocking);
    }

    @Override
    public TrapSearch.Builder<LivingEntity> trapSearch() {
        return TrapSearch.<LivingEntity>builder()
                .prediction(Core.prediction())
                .entities(entities)
                .log(log);
    }

    /** The player's block interaction range: 4.5 unless a server changed the attribute. */
    private static double blockRange() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? DEFAULT_BLOCK_REACH : player.getBlockInteractionRange();
    }

    /** Health as the explosion maths needs it. */
    private final class LivingVitals implements Vitals<LivingEntity> {

        @Override
        public double pool(LivingEntity entity) {
            return entity.getHealth() + entity.getAbsorptionAmount();
        }

        @Override
        public boolean isTrusted(LivingEntity entity) {
            if (entity == MinecraftClient.getInstance().player) {
                return true;
            }
            float health = entity.getHealth();
            return health > 0f && !Float.isNaN(health) && healthTrusted.getAsBoolean();
        }

        @Override
        public boolean isRecentlyHurt(LivingEntity entity) {
            return entity.hurtTime > 0;
        }
    }
}
