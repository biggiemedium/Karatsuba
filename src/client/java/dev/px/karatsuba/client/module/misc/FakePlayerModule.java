package dev.px.karatsuba.client.module.misc;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.MathUtil;
import dev.px.core.math.Vec2;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.event.game.player.AttackEvent;
import dev.px.karatsuba.adapter.game.entity.FakePlayers;
import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.ItemSpec;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.misc.fakeplayer.ArmorSet;
import dev.px.karatsuba.client.module.misc.fakeplayer.HeldItem;

/**
 * A target dummy: a player only this client can see, standing where you were.
 *
 * <p>For testing everything that looks at players &mdash; ESP, name tags, aim,
 * combat timing &mdash; without a second account. It is a real player entity on
 * the client, so every module finds it the ordinary way.
 *
 * <p>Hitting it works as a dummy should. The attack is kept from the server
 * (which would ignore an entity it has never heard of anyway), the dummy flinches
 * and makes the hurt sound, and, unless it is invincible, loses the health your
 * hit would deal: your weapon's damage scaled by the attack cooldown, as the game
 * scales it. A lethal hit pops its totem if it has one, and kills it if not.
 *
 * <p>Ported from Leapfrog's {@code FakePlayer}, keeping the kit, the totems and
 * the rotation mirroring. Left behind: damage taken from <em>your</em>
 * knockback, a scripted gapple-eating sequence, and copying your position every
 * tick, none of which tested anything real.
 */
@ModuleInfo(name = "Fake Player", description = "Spawns a client-side dummy to test against", category = "Misc")
public final class FakePlayerModule extends Module {

    /** What a totem leaves its holder on, as in vanilla: half a heart. */
    private static final float TOTEM_HEALTH = 1f;

    /** The smoothing per tick at Rotation Speed 1: a tenth of the remaining turn, as Leapfrog turned. */
    private static final float TURN_PER_SPEED = 0.1f;

    private final StringSetting name = text("Name", "Barnaby")
            .describe("Takes effect the next time it is spawned");

    // ---------------------------------------------------------------- kit

    private final EnumSetting<ArmorSet> armor = enumOf("Armor", ArmorSet.NETHERITE)
            .onChange(ignored -> dress());
    private final EnumSetting<HeldItem> heldItem = enumOf("Held Item", HeldItem.ENCHANTED_GAPPLE)
            .onChange(ignored -> dress());
    private final BooleanSetting totem = bool("Totem", true)
            .describe("A totem of undying in the off hand, which saves it from one lethal hit")
            .onChange(ignored -> freshTotem());
    private final BooleanSetting infiniteTotems = bool("Infinite Totems", true)
            .describe("A fresh totem after every pop, so it can never die")
            .visibleWhen(totem)
            .onChange(ignored -> freshTotem());

    private final GroupSetting kit = group("Kit", true)
            .containing(armor, heldItem, totem, infiniteTotems);

    // ------------------------------------------------------------ behaviour

    private final BooleanSetting invincible = bool("Invincible", true)
            .describe("Flinches when hit but never loses health");
    private final BooleanSetting copyRotation = bool("Copy Rotation", false)
            .describe("Turns to face the way you are facing");
    private final NumberSetting<Float> rotationSpeed = number("Rotation Speed", 1f, 0.1f, 5f).step(0.1f)
            .visibleWhen(copyRotation);

    private final GroupSetting behaviour = group("Behaviour", true)
            .containing(invincible, copyRotation, rotationSpeed);

    /** The dummy's entity id, or {@link FakePlayers#NO_ENTITY} when there is none. */
    private int dummy = FakePlayers.NO_ENTITY;

    /** Its health, kept here: the dummy has no server to keep it. */
    private float health;

    /** Whether its one totem has been used. Only meaningful without infinite totems. */
    private boolean totemSpent;

    private Vec2 facing = Vec2.rotation(0f, 0f);

    @Override
    protected void onEnable() {
        if (!Players.present()) {
            // Leaves the module on: the tick check below turns it off again, which
            // is simpler than disabling from inside the enable that is running.
            return;
        }
        facing = Players.rotation();
        dummy = FakePlayers.spawn(name.get(), Players.position(), facing);
        health = FakePlayers.maxHealth(dummy);
        totemSpent = false;

        FakePlayers.setHealth(dummy, health);
        dress();
    }

    @Override
    protected void onDisable() {
        FakePlayers.remove(dummy);
        dummy = FakePlayers.NO_ENTITY;
    }

    /**
     * Keeps the dummy turned, and notices when it has gone.
     *
     * <p>A world or dimension change drops every entity the client made, the
     * dummy included. Rather than respawning it somewhere unexpected, the module
     * turns itself off.
     */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!FakePlayers.exists(dummy)) {
            disable();
            return;
        }
        if (copyRotation.isOn()) {
            facing = turnTowards(facing, Players.rotation());
            FakePlayers.setRotation(dummy, facing);
        }
    }

    /** A hit on the dummy, which the server must never see. */
    @Subscribe
    private void onAttack(AttackEvent event) {
        if (event.getTargetId() != dummy) {
            return;
        }
        event.cancel();

        float damage = invincible.isOn() ? 0f : hitDamage();
        // The game never processed the hit, so the cooldown has to be restarted by
        // hand; otherwise every swing would land at full strength.
        Players.resetAttackCooldown();
        FakePlayers.hurt(dummy);

        if (damage > 0f) {
            takeDamage(damage);
        }
    }

    /** Vanilla's scaling: a fifth of the damage at no charge, rising with the square of the cooldown. */
    private static float hitDamage() {
        float charge = Players.attackCooldown();
        return Players.attackDamage() * (0.2f + charge * charge * 0.8f);
    }

    private void takeDamage(float damage) {
        health -= damage;
        if (health > 0f) {
            FakePlayers.setHealth(dummy, health);
            return;
        }
        if (hasTotem()) {
            popTotem();
            return;
        }
        Core.notifications().info(getName(), name.get() + " died");
        disable();
    }

    private void popTotem() {
        health = TOTEM_HEALTH;
        FakePlayers.setHealth(dummy, health);
        FakePlayers.popTotem(dummy);
        if (!infiniteTotems.isOn()) {
            totemSpent = true;
        }
        restockTotem();
    }

    // ------------------------------------------------------------------ kit

    /** Puts the whole kit on: armour, held item and totem. */
    private void dress() {
        if (!FakePlayers.exists(dummy)) {
            return;
        }
        ArmorSet set = armor.get();
        for (Equipment.Slot slot : new Equipment.Slot[]{
                Equipment.Slot.HEAD, Equipment.Slot.CHEST, Equipment.Slot.LEGS, Equipment.Slot.FEET}) {
            Equipment.equip(dummy, slot, set.pieceFor(slot));
        }
        Equipment.equip(dummy, Equipment.Slot.MAIN_HAND, heldItem.get().spec(set));
        restockTotem();
    }

    /**
     * Puts a totem in the off hand, or takes it away.
     *
     * <p>Infinite totems show a full stack, a single totem shows one, and a spent
     * one shows nothing &mdash; so a name tag reads the dummy's state at a glance.
     */
    private void restockTotem() {
        if (!FakePlayers.exists(dummy)) {
            return;
        }
        ItemSpec offHand = hasTotem()
                ? ItemSpec.of("totem_of_undying").count(infiniteTotems.isOn() ? 64 : 1)
                : ItemSpec.NONE;
        Equipment.equip(dummy, Equipment.Slot.OFF_HAND, offHand);
    }

    /** A changed totem setting is a new kit, so a spent totem is replaced. */
    private void freshTotem() {
        totemSpent = false;
        restockTotem();
    }

    private boolean hasTotem() {
        return totem.isOn() && (infiniteTotems.isOn() || !totemSpent);
    }

    /** Eases a fraction of the remaining turn each tick, the shorter way round. */
    private Vec2 turnTowards(Vec2 current, Vec2 target) {
        float share = Math.min(1f, rotationSpeed.getFloat() * TURN_PER_SPEED);
        float yaw = current.getYaw() + MathUtil.wrapDegrees(target.getYaw() - current.getYaw()) * share;
        float pitch = current.getPitch() + (target.getPitch() - current.getPitch()) * share;
        return Vec2.rotation(yaw, RotationMath.clampPitch(pitch));
    }
}
