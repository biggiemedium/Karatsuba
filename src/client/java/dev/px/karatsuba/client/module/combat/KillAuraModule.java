package dev.px.karatsuba.client.module.combat;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.List;

/**
 * Attacks whatever is in reach.
 *
 * <p>The worked example for <b>three arbiters at once</b>, and the reason they
 * exist. This module wants the player's head, the hotbar and the swing, and it
 * is far from the only thing that will. In a client without arbitration the
 * three lines that take them would be direct writes, and every module added
 * afterwards would need to know this one exists.
 *
 * <p>Here it asks:
 *
 * <ul>
 *   <li>{@code Core.rotations()} for the head, {@link RotationPriority#HIGH},
 *       stepped so the turn traces out instead of snapping.</li>
 *   <li>{@code Services.inventory()} for the weapon, {@link ClaimPriority#HIGH} so
 *       it beats an auto-tool asking at NORMAL &mdash; a fight is worth more
 *       than a faster pickaxe.</li>
 *   <li>{@code Services.actions()} for the swing, so two combat modules
 *       cannot both hit the same tick, and nothing is sent that the server's
 *       view of the player could not have hit.</li>
 * </ul>
 *
 * <p><b>It does not name another module anywhere</b>, and it has no
 * {@code onDisable}. Every claim lasts one tick; switching the module off stops
 * the asking, and the head, the hotbar and the swing return to the player on
 * their own.
 *
 * <h2>Why it waits for the cooldown</h2>
 * Swinging as fast as possible deals less damage than swinging on the beat: an
 * attack before the weapon has recharged is scaled down hard. Waiting is both
 * the correct play and the one that looks like a person.
 */
@ModuleInfo(name = "Kill Aura", description = "Attacks nearby entities", category = "Combat")
public final class KillAuraModule extends ClientModule {

    /** Full damage lands at 1; anything below is a scaled-down hit. */
    private static final float COOLDOWN_READY = 0.95f;

    private final MultiEnumSetting<Target> targets =
            multi("Targets", Target.class, Target.PLAYERS, Target.HOSTILES)
                    .describe("Which entities are worth hitting");

    private final NumberSetting<Double> range = decimal("Range", 4.2d, 2d, 6d)
            .step(0.1d)
            .describe("Blocks from the eyes to the target's centre");

    private final EnumSetting<Sort> sort = enumOf("Priority", Sort.CLOSEST)
            .describe("Which target to pick when several are in reach");

    private final BooleanSetting rotate = bool("Rotate", true)
            .describe("Aim at the target before hitting it");

    private final EnumSetting<RotationMode> rotationMode = enumOf("Rotation Mode", RotationMode.SILENT)
            .describe("SILENT moves only what the server sees; CLIENT turns the camera")
            .visibleWhen(rotate);

    private final NumberSetting<Float> turnSpeed = number("Turn Speed", 40f, 5f, 180f)
            .step(5f)
            .describe("Degrees per tick; 180 snaps")
            .visibleWhen(rotate);

    private final BooleanSetting autoWeapon = bool("Auto Weapon", true)
            .describe("Hold the hardest-hitting hotbar item while fighting");

    /** The target this tick, kept only for the ArrayList readout. */
    private String targetName = "";

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        GameEntity target = pickTarget();
        if (target == null) {
            targetName = "";
            return;
        }
        targetName = target.getName();

        if (autoWeapon.isOn()) {
            int weapon = Inventories.bestWeaponSlot();
            if (weapon != Inventories.NOT_FOUND) {
                Services.inventory().request(this, weapon, ClaimPriority.HIGH);
            }
        }

        if (rotate.isOn()) {
            aimAt(target);
        }

        // The cooldown gate is last, so the head and the hotbar keep tracking
        // between swings rather than only on the tick one lands.
        // Turning is this module's own (its rotation settings); the service still
        // waits until the rotation the server has looks at the target.
        if (Players.attackCooldown() >= COOLDOWN_READY) {
            Services.actions().submit(this, Action.attack(target.getId())
                    .rotate(false)
                    .priority(ClaimPriority.HIGH));
        }
    }

    /**
     * Files the rotation claim.
     *
     * <p>Aimed at the eyes rather than the feet: it is the part of a target that
     * is hardest to miss and the part a person would look at.
     */
    private void aimAt(GameEntity target) {
        Vec3 eye = Players.eyePosition();

        Core.rotations().request(this, RotationRequest.at(eye.rotationTo(target.getEyePosition()))
                .priority(RotationPriority.HIGH)
                .step(turnSpeed.getFloat())
                .mode(rotationMode.get()));
    }

    /**
     * @return the entity to hit this tick, or null
     *
     * <p>One pass over the nearby list. {@code Entities.nearby} already filters
     * by distance from the eyes, so the work here is the wanted-kind test and the
     * ordering.
     */
    private GameEntity pickTarget() {
        if (!Players.present()) {
            return null;
        }
        Vec3 eye = Players.eyePosition();
        List<GameEntity> nearby = Entities.nearby(1f, range.getDouble());

        GameEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (GameEntity candidate : nearby) {
            if (!wanted(candidate)) {
                continue;
            }
            double score = switch (sort.get()) {
                case CLOSEST -> candidate.distanceTo(eye);
                case WEAKEST -> candidate.getHealth();
                case STRONGEST -> -candidate.getHealth();
            };
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * @return whether this entity is one of the kinds asked for
     *
     * <p>Friends are never hit, regardless of the target list. Core owns the
     * friends list, so this is the same answer ESP colours by and the same one a
     * future module will get without any of them sharing state.
     */
    private boolean wanted(GameEntity entity) {
        if (entity.isFriend() || entity.getMaxHealth() <= 0f) {
            return false;
        }
        if (entity.isPlayer()) {
            return targets.has(Target.PLAYERS);
        }
        return targets.has(entity.isHostile() ? Target.HOSTILES : Target.PASSIVES);
    }

    @Override
    protected void onDisable() {
        // Nothing to release. Every claim expires on its own; this is only the
        // ArrayList readout.
        targetName = "";
    }

    @Override
    public String getDisplayInfo() {
        return targetName;
    }

    public enum Target {
        PLAYERS,
        HOSTILES,
        PASSIVES
    }

    public enum Sort {
        CLOSEST,
        WEAKEST,
        STRONGEST
    }
}
