package dev.px.karatsuba.adapter.projectile;

import dev.px.projectile.DragRule;
import dev.px.projectile.ProjectileRules;
import dev.px.projectile.StepOrder;
import lombok.Getter;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;

/**
 * How each projectile this version flies, read from 1.21.4's bytecode rather than
 * the wiki (the two disagree in places, marked below).
 *
 * <ul>
 *   <li><b>Gravity</b> is each class's {@code getGravity()} (Mojang's
 *       {@code getDefaultGravity}).</li>
 *   <li><b>Drag</b> constants are floats the game widens to double, so they are
 *       written as floats here: {@code 0.99f} is not quite 0.99.</li>
 *   <li><b>Water</b> is {@code isTouchingWater()}, which the game updates after
 *       each move ({@code Entity.baseTick}), so it describes where the projectile
 *       was when the tick began: what {@code DragRule} is asked about.</li>
 *   <li><b>Entity margin</b> is {@code ProjectileUtil.DEFAULT_MARGIN}, 0.3, for
 *       arrows and thrown items alike. The wiki says arrows use none; the
 *       bytecode says otherwise ({@code PersistentProjectileEntity.getEntityCollision}).</li>
 * </ul>
 */
@Getter
public enum ProjectileType {

    /**
     * Arrows and spectral arrows ({@code PersistentProjectileEntity}): gravity
     * 0.05; drag 0.99, or {@code getDragInWater()} 0.6 in water; the move uses
     * the velocity from before the drag and gravity of that tick.
     */
    ARROW(ProjectileRules.builder("arrow")
            .gravity(0.05d)
            .drag(DragRule.inside(ProjectileBlocks::isWater, 0.6f, 0.99f))
            .order(StepOrder.POSITION_DRAG_ACCELERATION)
            .entityMargin(0.3f)
            .build()),

    /** As an arrow, but {@code TridentEntity.getDragInWater()} is 0.99: water does not slow it. */
    TRIDENT(ProjectileRules.builder("trident")
            .gravity(0.05d)
            .drag(0.99f)
            .order(StepOrder.POSITION_DRAG_ACCELERATION)
            .entityMargin(0.3f)
            .build()),

    /**
     * Snowballs ({@code ThrownEntity}): gravity 0.03; drag 0.99, or 0.8 in water
     * (not on the wiki); gravity, then drag, then the move.
     */
    SNOWBALL(thrown("snowball", 0.03d)),

    /** Eggs: as a snowball. */
    EGG(thrown("egg", 0.03d)),

    /** Ender pearls: as a snowball. Separate so a module can pick pearls out. */
    ENDER_PEARL(thrown("ender pearl", 0.03d)),

    /** Splash and lingering potions, one {@code PotionEntity}: gravity 0.05, otherwise thrown. */
    POTION(thrown("potion", 0.05d)),

    /** Experience bottles: gravity 0.07, otherwise thrown. */
    EXPERIENCE_BOTTLE(thrown("experience bottle", 0.07d)),

    /**
     * Llama spit: gravity 0.06, drag the double 0.99 (not a float here), moved
     * before either. Water does not slow it: it is discarded there.
     */
    LLAMA_SPIT(ProjectileRules.builder("llama spit")
            .gravity(0.06d)
            .drag(0.99d)
            .order(StepOrder.POSITION_DRAG_ACCELERATION)
            .entityMargin(0.3f)
            .build());

    private final ProjectileRules rules;

    ProjectileType(ProjectileRules rules) {
        this.rules = rules;
    }

    /**
     * @return the type of a game projectile, or null for one with no rules here:
     *         fireballs, wind charges and shulker bullets steer or accelerate, and
     *         a crossbow's firework flies straight
     */
    public static ProjectileType of(Object entity) {
        if (entity instanceof ArrowEntity || entity instanceof SpectralArrowEntity) {
            return ARROW;
        }
        if (entity instanceof TridentEntity) {
            return TRIDENT;
        }
        if (entity instanceof SnowballEntity) {
            return SNOWBALL;
        }
        if (entity instanceof EggEntity) {
            return EGG;
        }
        if (entity instanceof EnderPearlEntity) {
            return ENDER_PEARL;
        }
        if (entity instanceof PotionEntity) {
            return POTION;
        }
        if (entity instanceof ExperienceBottleEntity) {
            return EXPERIENCE_BOTTLE;
        }
        if (entity instanceof LlamaSpitEntity) {
            return LLAMA_SPIT;
        }
        return null;
    }

    /** Whether it flies through endermen, as arrows and tridents do, rather than being used up on them. */
    public boolean passesEndermen() {
        return this == ARROW || this == TRIDENT;
    }

    private static ProjectileRules thrown(String name, double gravity) {
        return ProjectileRules.builder(name)
                .gravity(gravity)
                .drag(DragRule.inside(ProjectileBlocks::isWater, 0.8f, 0.99f))
                .order(StepOrder.ACCELERATION_DRAG_POSITION)
                .entityMargin(0.3f)
                .build();
    }
}
