package dev.px.karatsuba.adapter.projectile;

import dev.px.core.Core;
import dev.px.core.entity.EntityService;
import dev.px.core.entity.EntityTracker;
import dev.px.core.entity.Tracked;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.prediction.Lookahead;
import dev.px.core.world.BlockView;
import dev.px.karatsuba.adapter.game.entity.ProjectileTracker;
import dev.px.karatsuba.adapter.world.Voxels;
import dev.px.projectile.LaunchOrigin;
import dev.px.projectile.LaunchRules;
import dev.px.projectile.Shooter;
import dev.px.projectile.ShooterVelocity;
import dev.px.projectile.aim.AimSolver;
import dev.px.projectile.flight.Flight;
import dev.px.projectile.flight.Trajectory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.LingeringPotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * {@link ProjectileKit} for 1.21.4. Every launch number here was read from the
 * item's own {@code use} or {@code onStoppedUsing}, and every flight number from
 * the entity's {@code tick}; see {@link ProjectileType} and {@link Shot.Item}.
 *
 * <h2>Launches</h2>
 * <ul>
 *   <li><b>Spread</b> is {@code 0.0172275} per axis (a divergence of 1 for every
 *       item), nudged before the speed scales it ({@code ProjectileEntity.calculateVelocity}).</li>
 *   <li><b>Where it appears</b> is your eyes less {@code (double) 0.1f}, for arrows,
 *       tridents and thrown items alike.</li>
 *   <li><b>Your motion</b> is added by {@code setVelocity(Entity shooter, ...)}:
 *       {@code getMovement()}, which a client player answers with its velocity,
 *       less its Y on the ground. A crossbow skips that overload, so its arrows
 *       keep none of it.</li>
 *   <li><b>A bow's power</b> follows the draw through the game's own
 *       {@code BowItem.getPullProgress}. Not drawing, it is a full draw, so a
 *       Trajectories module shows where a full shot would land.</li>
 *   <li><b>A crossbow</b> is only a shot while charged with arrows; its multishot
 *       side arrows (&plusmn;10&deg;) are not modelled, only the middle one.</li>
 * </ul>
 *
 * <h2>What can be hit</h2>
 * Whatever the game's {@code Entity.canBeHitByProjectile()} accepts: living
 * entities but spectators, boats, minecarts, crystals, item frames, armour stands
 * that are not markers. You are included; a flight you launch never hits you, as
 * the game ignores its owner until the projectile clears them. Arrows and tridents
 * fly through endermen, which teleport away instead (a teleport that fails all 64
 * tries is not foreseen).
 */
final class MinecraftProjectileKit implements ProjectileKit {

    /** {@code ProjectileEntity.calculateVelocity}: the per-axis nudge at a divergence of 1. */
    private static final double SPREAD = 0.0172275d;

    /** {@code ThrownItemEntity} and {@code PersistentProjectileEntity}: eye height less {@code (double) 0.1f}. */
    private static final LaunchOrigin ORIGIN = LaunchOrigin.below(0.1f);

    /**
     * Where a held item sits in first person, from the eyes along the view: ahead,
     * to its side, and down. A drawing position only; the real launch leaves from
     * the middle of your eyes.
     */
    private static final double HAND_FORWARD = 0.6d;
    private static final double HAND_SIDE = 0.32d;
    private static final double HAND_DOWN = 0.28d;

    /** {@code BowItem.onStoppedUsing}: a draw under this fires nothing. */
    private static final float MIN_PULL = 0.1f;

    private final BlockView blocks = ProjectileBlocks.INSTANCE;
    private final EntityTracker<?> projectiles;
    private final Lookahead<Object> lookahead;

    private final Map<ProjectileType, Flight> flights = new EnumMap<>(ProjectileType.class);
    /** As {@link #flights}, never hitting you: for following your own projectiles. */
    private final Map<ProjectileType, Flight> ownFlights = new EnumMap<>(ProjectileType.class);
    private final Map<Shot.Item, Shot> shots = new EnumMap<>(Shot.Item.class);

    MinecraftProjectileKit(Core core) {
        EntityService entities = core.getEntityService();
        this.projectiles = entities.require(ProjectileTracker.class);
        this.lookahead = Lookahead.predicted(core.getPredictionService());

        // What a projectile can hit (ProjectileEntity.canHit): canBeHitByProjectile,
        // which is alive && canHit(), and players exclude spectators themselves.
        EntityTracker<Entity> hittable = entities.register(
                EntityTracker.of(Entity.class, entity -> !entity.isRemoved() && entity.canBeHitByProjectile()));

        for (ProjectileType type : ProjectileType.values()) {
            flights.put(type, flight(type, entities, hittable, false));
            ownFlights.put(type, flight(type, entities, hittable, true));
        }

        shots.put(Shot.Item.BOW, shot(Shot.Item.BOW, ProjectileType.ARROW,
                launch(ShooterVelocity.ALL_BUT_VERTICAL_ON_GROUND).power(MinecraftProjectileKit::bowPower),
                () -> bowPull() >= MIN_PULL));
        shots.put(Shot.Item.CROSSBOW, shot(Shot.Item.CROSSBOW, ProjectileType.ARROW,
                launch(ShooterVelocity.NONE).power(3.15f), () -> true));
        shots.put(Shot.Item.TRIDENT, shot(Shot.Item.TRIDENT, ProjectileType.TRIDENT,
                launch(ShooterVelocity.ALL_BUT_VERTICAL_ON_GROUND).power(2.5f),
                MinecraftProjectileKit::tridentReady));
        thrown(Shot.Item.SNOWBALL, ProjectileType.SNOWBALL, 1.5f, 0f);
        thrown(Shot.Item.EGG, ProjectileType.EGG, 1.5f, 0f);
        thrown(Shot.Item.ENDER_PEARL, ProjectileType.ENDER_PEARL, 1.5f, 0f);
        thrown(Shot.Item.SPLASH_POTION, ProjectileType.POTION, 0.5f, -20f);
        thrown(Shot.Item.LINGERING_POTION, ProjectileType.POTION, 0.5f, -20f);
        thrown(Shot.Item.EXPERIENCE_BOTTLE, ProjectileType.EXPERIENCE_BOTTLE, 0.7f, -20f);
    }

    @Override
    public BlockView blocks() {
        return blocks;
    }

    @Override
    public EntityTracker<?> projectiles() {
        return projectiles;
    }

    @Override
    public Lookahead<Object> lookahead() {
        return lookahead;
    }

    // ------------------------------------------------------------------ you

    @Override
    public Shooter you() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return Shooter.still(Vec3.ZERO);
        }
        return Shooter.of(Voxels.toVec3(player.getEyePos()), Voxels.toVec3(player.getMovement()), player.isOnGround());
    }

    @Override
    public Shooter you(float partialTicks) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return Shooter.still(Vec3.ZERO);
        }
        return Shooter.of(Voxels.toVec3(player.getCameraPosVec(partialTicks)), Voxels.toVec3(player.getMovement()),
                player.isOnGround());
    }

    @Override
    public Vec2 view(float partialTicks) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null
                ? Vec2.rotation(0f, 0f)
                : Vec2.rotation(player.getYaw(partialTicks), player.getPitch(partialTicks));
    }

    // ---------------------------------------------------------------- shots

    @Override
    public Shot held() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Hand hand = heldHand(player);
        return hand == null ? null : shotFor(player, player.getStackInHand(hand));
    }

    @Override
    public Vec3 hand(float partialTicks) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Hand hand = heldHand(player);
        if (hand == null) {
            return null;
        }
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        double side = arm == Arm.RIGHT ? 1d : -1d;
        double yaw = Math.toRadians(player.getYaw(partialTicks));
        double pitch = Math.toRadians(player.getPitch(partialTicks));
        Vec3 forward = Vec3.of(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = Vec3.of(-Math.cos(yaw), 0d, -Math.sin(yaw));
        Vec3 down = forward.cross(right);
        return Voxels.toVec3(player.getCameraPosVec(partialTicks))
                .add(forward.scale(HAND_FORWARD))
                .add(right.scale(side * HAND_SIDE))
                .add(down.scale(HAND_DOWN));
    }

    /**
     * The hand whose item {@link #held()} launches: the one in use while an item is
     * being used, else the main hand if it launches something, else the off hand,
     * as the game tries them. Null when neither does.
     */
    private Hand heldHand(ClientPlayerEntity player) {
        if (player == null) {
            return null;
        }
        // An item in use is the only one that can launch until it is let go.
        if (player.isUsingItem()) {
            return shotFor(player, player.getActiveItem()) == null ? null : player.getActiveHand();
        }
        if (shotFor(player, player.getStackInHand(Hand.MAIN_HAND)) != null) {
            return Hand.MAIN_HAND;
        }
        return shotFor(player, player.getStackInHand(Hand.OFF_HAND)) != null ? Hand.OFF_HAND : null;
    }

    @Override
    public Shot shot(Shot.Item item) {
        return shots.get(item);
    }

    /** What using {@code stack} launches, or null. A bow needs ammunition; a crossbow, arrows loaded. */
    private Shot shotFor(ClientPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Shot.Item item;
        if (stack.getItem() instanceof BowItem) {
            item = player.getProjectileType(stack).isEmpty() ? null : Shot.Item.BOW;
        } else if (stack.getItem() instanceof CrossbowItem) {
            item = chargedWithArrows(stack) ? Shot.Item.CROSSBOW : null;
        } else if (stack.getItem() instanceof TridentItem) {
            item = Shot.Item.TRIDENT;
        } else if (stack.getItem() instanceof SnowballItem) {
            item = Shot.Item.SNOWBALL;
        } else if (stack.getItem() instanceof EggItem) {
            item = Shot.Item.EGG;
        } else if (stack.getItem() instanceof EnderPearlItem) {
            item = Shot.Item.ENDER_PEARL;
        } else if (stack.getItem() instanceof SplashPotionItem) {
            item = Shot.Item.SPLASH_POTION;
        } else if (stack.getItem() instanceof LingeringPotionItem) {
            item = Shot.Item.LINGERING_POTION;
        } else if (stack.getItem() instanceof ExperienceBottleItem) {
            item = Shot.Item.EXPERIENCE_BOTTLE;
        } else {
            item = null;
        }
        return item == null ? null : shots.get(item);
    }

    /** A crossbow fires its middle projectile as a flying arrow only when it is not a firework. */
    private static boolean chargedWithArrows(ItemStack crossbow) {
        ChargedProjectilesComponent charged = crossbow.get(DataComponentTypes.CHARGED_PROJECTILES);
        return charged != null && !charged.isEmpty() && !charged.contains(Items.FIREWORK_ROCKET);
    }

    // -------------------------------------------------------------- flights

    @Override
    public Flight flight(ProjectileType type) {
        return flights.get(type);
    }

    @Override
    public ProjectileType typeOf(Tracked<?> projectile) {
        return ProjectileType.of(projectile.get());
    }

    @Override
    public Vec3 velocityOf(Tracked<?> projectile) {
        return projectile.get() instanceof Entity entity ? Voxels.toVec3(entity.getVelocity()) : null;
    }

    @Override
    public boolean isYours(Tracked<?> projectile) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && projectile.get() instanceof ProjectileEntity thrown && thrown.getOwner() == player;
    }

    @Override
    public Trajectory follow(Tracked<?> projectile) {
        ProjectileType type = typeOf(projectile);
        if (type == null) {
            return null;
        }
        // The game ignores a projectile's owner until it has cleared them
        // (ProjectileEntity.canHit, leftOwner), and one you just threw starts inside
        // you: followed with the shared flight it would land on you at once.
        return (isYours(projectile) ? ownFlights : flights).get(type).from(projectile, velocityOf(projectile));
    }

    // --------------------------------------------------------------- aiming

    @Override
    public AimSolver.Builder aim(Shot shot) {
        return AimSolver.builder(shot.getFlight(), shot.getLaunch()).lookahead(lookahead);
    }

    // ------------------------------------------------------------- internal

    private Flight flight(ProjectileType type, EntityService entities, EntityTracker<Entity> hittable, boolean notYou) {
        Flight.Builder flight = Flight.builder(type.getRules())
                .blocks(blocks)
                .entities(entities, hittable);
        boolean passesEndermen = type.passesEndermen();
        if (passesEndermen || notYou) {
            flight.filter(entity -> {
                Object hit = entity.get();
                // PersistentProjectileEntity.onEntityHit returns without stopping
                // when an enderman takes the hit by teleporting.
                if (passesEndermen && hit instanceof EndermanEntity) {
                    return false;
                }
                return !notYou || hit != MinecraftClient.getInstance().player;
            });
        }
        return flight.build();
    }

    private static LaunchRules.Builder launch(ShooterVelocity carried) {
        return LaunchRules.builder()
                .spread(SPREAD)
                .shooterVelocity(carried)
                .origin(ORIGIN);
    }

    private Shot shot(Shot.Item item, ProjectileType type, LaunchRules.Builder launch, BooleanSupplier ready) {
        return new Shot(item, type, launch.build(), flights.get(type), ready);
    }

    /** {@code ThrowablePotionItem.use} and the rest: a fixed power, maybe pitched up, ready at once. */
    private void thrown(Shot.Item item, ProjectileType type, float power, float pitchOffset) {
        shots.put(item, shot(item, type,
                launch(ShooterVelocity.ALL_BUT_VERTICAL_ON_GROUND).power(power).pitchOffset(pitchOffset),
                () -> true));
    }

    /** The draw of a bow being used, 0 to 1; a full draw when no bow is drawn. */
    private static float bowPull() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.isUsingItem() || !(player.getActiveItem().getItem() instanceof BowItem)) {
            return 1f;
        }
        return BowItem.getPullProgress(player.getItemUseTime());
    }

    /** {@code BowItem.onStoppedUsing}: the draw times 3. */
    private static double bowPower() {
        return bowPull() * 3.0f;
    }

    /** {@code TridentItem.onStoppedUsing}: held long enough, and not a riptide trident that would launch you. */
    private static boolean tridentReady() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !player.isUsingItem() || !(player.getActiveItem().getItem() instanceof TridentItem)) {
            return false;
        }
        return player.getItemUseTime() >= TridentItem.MIN_DRAW_DURATION
                && EnchantmentHelper.getTridentSpinAttackStrength(player.getActiveItem(), player) == 0f;
    }
}
