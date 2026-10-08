package dev.px.karatsuba.client.module.utility;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.input.Bind;
import dev.px.core.math.MathUtil;
import dev.px.core.math.Vec2;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.event.game.combat.ExplosionEvent;
import dev.px.karatsuba.adapter.event.game.combat.TotemPopEvent;
import dev.px.karatsuba.adapter.event.game.player.AttackEvent;
import dev.px.karatsuba.adapter.game.entity.FakePlayers;
import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.ItemSpec;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.utility.fakeplayer.ArmorSet;
import dev.px.karatsuba.client.module.utility.fakeplayer.HeldItem;
import dev.px.karatsuba.client.module.utility.fakeplayer.MotionRecording;

import java.util.Locale;

/**
 * A target dummy for crystal PvP practice: a player only this client can see,
 * that takes real damage and pops real totems, and can run a path you recorded.
 *
 * <h2>Damage, as the game deals it</h2>
 * <ul>
 *   <li><b>Swords</b>: your hit is kept from the server (which has never heard of
 *       the dummy) and dealt here the way the game would: your weapon scaled by
 *       the attack cooldown, Sharpness, a critical hit's half again, then the
 *       dummy's armour and Protection.</li>
 *   <li><b>Crystals</b>, beds and anchors: every real explosion near it, yours or
 *       anyone's, hurts it through the same 1.21.4 explosion maths the auras
 *       score with, armour and Blast Protection included. So a crystal aura
 *       pointed at it does exactly the damage it would to a player in that kit.</li>
 *   <li>Like a player, it is only hurt again within half a second of a hit by
 *       more than that hit did, and only by the difference: two crystals in one
 *       tick do not stack.</li>
 * </ul>
 *
 * <h2>Totems</h2>
 * It always has totems, a full stack in the off hand that never runs out. With
 * {@code Pop Totems} on, a lethal hit pops one as the game does: down to half a
 * heart, four golden hearts of absorption for five seconds, Regeneration II for
 * 45 and Fire Resistance for 40, with the particles, the sound and a pop that
 * Combat Info counts. Off, a lethal hit simply puts it back to full health, for
 * testing damage without the pops.
 *
 * <h2>Movement</h2>
 * Press {@code Record Bind} to start recording your movement, and again to stop:
 * the dummy then runs that path on a loop, restarting or going back and forth,
 * walking, jumping and crouching where you did. It moves the way the game moves
 * any other player, so the auras' prediction and Predict ESP see an ordinary
 * moving target. With no recording it stands where it was spawned, optionally
 * turning to face the way you face.
 */
@ModuleInfo(name = "Fake Player", description = "A dummy that takes crystal and sword damage, pops totems and runs your path",
        category = "Utility")
public final class FakePlayerModule extends Module {

    /** What a totem leaves its holder on: half a heart. */
    private static final float TOTEM_HEALTH = 1f;

    /** Absorption II: four golden hearts. */
    private static final float TOTEM_ABSORPTION = 8f;

    private static final int ABSORPTION_TICKS = 5 * 20;
    private static final int REGENERATION_TICKS = 45 * 20;
    private static final int FIRE_RESISTANCE_TICKS = 40 * 20;

    /** Regeneration II heals one point every this many ticks. */
    private static final int REGENERATION_INTERVAL = 25;

    /** After a hit, only a harder hit lands, for this long: half a player's invulnerability. */
    private static final int HURT_WINDOW_TICKS = 10;

    /** The smoothing per tick at Rotation Speed 1: a tenth of the remaining turn. */
    private static final float TURN_PER_SPEED = 0.1f;

    private final StringSetting name = text("Name", "Barnaby")
            .describe("Takes effect the next time it is spawned");

    // ------------------------------------------------------------------ kit

    private final EnumSetting<ArmorSet> armor = enumOf("Armor", ArmorSet.NETHERITE)
            .onChange(ignored -> dress());
    private final EnumSetting<HeldItem> heldItem = enumOf("Held Item", HeldItem.ENCHANTED_GAPPLE)
            .onChange(ignored -> dress());

    private final GroupSetting kit = group("Kit", true)
            .containing(armor, heldItem);

    // --------------------------------------------------------------- damage

    private final BooleanSetting takeDamage = bool("Take Damage", true)
            .describe("Lose health to hits and explosions; off, it only flinches");

    private final BooleanSetting explosions = bool("Explosions", true)
            .describe("Crystals, beds and anchors going off near it hurt it")
            .visibleWhen(takeDamage);

    private final BooleanSetting popTotems = bool("Pop Totems", true)
            .describe("A lethal hit pops a totem (it never runs out); off, it just goes back to full health")
            .visibleWhen(takeDamage);

    private final NumberSetting<Integer> healAfter = integer("Heal After", 0, 0, 30)
            .describe("Seconds without a hit before it heals to full; 0 never")
            .visibleWhen(takeDamage);

    private final GroupSetting damage = group("Damage", true)
            .containing(takeDamage, explosions, popTotems, healAfter);

    // ------------------------------------------------------------- movement

    private final BindSetting recordBind = bind("Record Bind")
            .describe("Start recording your movement; press again to stop and have it run the path");

    private final EnumSetting<MotionRecording.LoopMode> loopMode = enumOf("Loop", MotionRecording.LoopMode.PING_PONG)
            .describe("RESTART jumps back to the start; PING_PONG runs the path back and forth");

    private final NumberSetting<Integer> maxLength = integer("Max Length", 60, 5, 300)
            .describe("Seconds after which a recording stops on its own");

    private final BooleanSetting copyRotation = bool("Copy Rotation", false)
            .describe("With no path to run, turn to face the way you are facing");

    private final NumberSetting<Float> rotationSpeed = number("Rotation Speed", 1f, 0.1f, 5f).step(0.1f)
            .visibleWhen(copyRotation);

    private final GroupSetting movement = group("Movement", true)
            .containing(recordBind, loopMode, maxLength, copyRotation, rotationSpeed);

    /** The dummy's entity id, or {@link FakePlayers#NO_ENTITY} when there is none. */
    private int dummy = FakePlayers.NO_ENTITY;

    /** Its health, kept here: the dummy has no server to keep it. */
    private float health;

    private final MotionRecording path = new MotionRecording();
    private boolean recording;

    private Vec2 facing = Vec2.rotation(0f, 0f);

    // What a totem pop left running, counted down here: the client does not tick a fake's effects.
    private int absorptionTicks;
    private int regenerationTicks;
    private int fireResistanceTicks;

    /** Ticks since it was last hurt, and how hard: the hurt window. */
    private int sinceHurt = Integer.MAX_VALUE / 2;
    private float lastHurt;

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
        FakePlayers.setHealth(dummy, health);
        absorptionTicks = regenerationTicks = fireResistanceTicks = 0;
        sinceHurt = Integer.MAX_VALUE / 2;
        recording = false;
        path.rewind();
        dress();
    }

    @Override
    protected void onDisable() {
        FakePlayers.remove(dummy);
        dummy = FakePlayers.NO_ENTITY;
        recording = false;
    }

    /**
     * Records, replays, turns and heals, and notices when the dummy has gone.
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
        if (recording) {
            path.add(new MotionRecording.Frame(Players.position(), Players.rotation(), Players.sneaking()));
            if (path.seconds() >= maxLength.getInt()) {
                stopRecording();
            }
        } else if (!path.isEmpty()) {
            boolean jump = path.atStart();
            MotionRecording.Frame frame = path.next(loopMode.get());
            if (jump) {
                FakePlayers.teleport(dummy, frame.position(), frame.rotation());
            } else {
                FakePlayers.moveTo(dummy, frame.position(), frame.rotation(), frame.sneaking());
            }
        } else if (copyRotation.isOn()) {
            facing = turnTowards(facing, Players.rotation());
            FakePlayers.setRotation(dummy, facing);
        }
        tickRecovery();
    }

    // -------------------------------------------------------------- damage

    /** A hit on the dummy, which the server must never see. */
    @Subscribe
    private void onAttack(AttackEvent event) {
        if (event.getTargetId() != dummy) {
            return;
        }
        event.cancel();
        // Worked out before the cooldown restarts: the hit is as strong as the charge it was swung with.
        float dealt = FakePlayers.meleeDamage(dummy);
        // The game never processed the hit, so the cooldown has to be restarted by
        // hand; otherwise every swing would land at full strength.
        Players.resetAttackCooldown();
        hurt(dealt);
    }

    @Subscribe
    private void onExplosion(ExplosionEvent event) {
        if (!explosions.isOn() || !FakePlayers.exists(dummy)) {
            return;
        }
        float dealt = FakePlayers.explosionDamage(dummy, event.getOrigin(), event.getPower());
        if (dealt > 0f) {
            hurt(dealt);
        }
    }

    /**
     * One hit, through the hurt window: within it, only the part of a hit beyond
     * the last one lands, as with a player.
     */
    private void hurt(float dealt) {
        if (!takeDamage.isOn()) {
            FakePlayers.hurt(dummy);
            return;
        }
        float landed;
        if (sinceHurt < HURT_WINDOW_TICKS) {
            if (dealt <= lastHurt) {
                return;
            }
            landed = dealt - lastHurt;
        } else {
            landed = dealt;
            sinceHurt = 0;
            FakePlayers.hurt(dummy);
        }
        lastHurt = Math.max(lastHurt, dealt);
        if (landed > 0f) {
            takeDamage(landed);
        }
    }

    /** Absorption first, then health; a lethal hit pops a totem, or with pops off, heals it. */
    private void takeDamage(float amount) {
        float absorption = FakePlayers.absorption(dummy);
        float throughAbsorption = Math.max(0f, amount - absorption);
        FakePlayers.setAbsorption(dummy, Math.max(0f, absorption - amount));
        health -= throughAbsorption;
        if (health > 0f) {
            FakePlayers.setHealth(dummy, health);
            return;
        }
        if (popTotems.isOn()) {
            popTotem();
        } else {
            health = FakePlayers.maxHealth(dummy);
            FakePlayers.setHealth(dummy, health);
        }
    }

    /** What a totem of undying does, effects and all, and a pop the rest of the client hears. */
    private void popTotem() {
        health = TOTEM_HEALTH;
        FakePlayers.setHealth(dummy, health);
        FakePlayers.setAbsorption(dummy, TOTEM_ABSORPTION);
        absorptionTicks = ABSORPTION_TICKS;
        regenerationTicks = REGENERATION_TICKS;
        fireResistanceTicks = FIRE_RESISTANCE_TICKS;
        FakePlayers.setEffect(dummy, "minecraft:absorption", 2, absorptionTicks);
        FakePlayers.setEffect(dummy, "minecraft:regeneration", 2, regenerationTicks);
        FakePlayers.setEffect(dummy, "minecraft:fire_resistance", 1, fireResistanceTicks);
        FakePlayers.popTotem(dummy);
        Core.bus().post(new TotemPopEvent(dummy, name.get(), FakePlayers.position(dummy), false));
    }

    /** Counts down what a pop left, heals with regeneration, and heals fully after a quiet spell. */
    private void tickRecovery() {
        sinceHurt++;
        if (sinceHurt >= HURT_WINDOW_TICKS) {
            lastHurt = 0f;
        }
        // The effects themselves count down on the client; only their ends and the
        // healing are done here. Re-applying them each tick would not do: applying
        // Absorption refills the golden hearts, so they would never drain.
        if (regenerationTicks > 0) {
            regenerationTicks--;
            if (regenerationTicks % REGENERATION_INTERVAL == 0) {
                heal(1f);
            }
            if (regenerationTicks == 0) {
                FakePlayers.setEffect(dummy, "minecraft:regeneration", 2, 0);
            }
        }
        if (absorptionTicks > 0 && --absorptionTicks == 0) {
            // The golden hearts go when the effect does.
            FakePlayers.setEffect(dummy, "minecraft:absorption", 2, 0);
            FakePlayers.setAbsorption(dummy, 0f);
        }
        if (fireResistanceTicks > 0 && --fireResistanceTicks == 0) {
            FakePlayers.setEffect(dummy, "minecraft:fire_resistance", 1, 0);
        }
        if (healAfter.getInt() > 0 && sinceHurt == healAfter.getInt() * 20) {
            heal(FakePlayers.maxHealth(dummy));
        }
    }

    private void heal(float amount) {
        health = Math.min(FakePlayers.maxHealth(dummy), health + amount);
        FakePlayers.setHealth(dummy, health);
    }

    // ------------------------------------------------------------ recording

    @Subscribe
    private void onKey(KeyEvent event) {
        Bind bind = recordBind.get();
        if (event.isPressed() && !bind.isMouse() && bind.isBound()
                && bind.matches(event.getKey(), event.getModifiers())) {
            toggleRecording();
        }
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        Bind bind = recordBind.get();
        if (event.isPressed() && bind.isMouse() && bind.matches(event.getButton(), event.getModifiers())) {
            toggleRecording();
        }
    }

    private void toggleRecording() {
        if (recording) {
            stopRecording();
            return;
        }
        path.clear();
        recording = true;
        Core.notifications().info(getName(), "Recording your movement");
    }

    private void stopRecording() {
        recording = false;
        path.rewind();
        if (path.size() < 2) {
            path.clear();
            Core.notifications().info(getName(), "Recording too short");
            return;
        }
        Core.notifications().info(getName(), String.format(Locale.ROOT, "Running a %.1fs path", path.seconds()));
    }

    // ------------------------------------------------------------------ kit

    /** Puts the whole kit on: armour, held item and a full stack of totems, and makes the armour count. */
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
        Equipment.equip(dummy, Equipment.Slot.OFF_HAND, ItemSpec.of("totem_of_undying").count(64));
        FakePlayers.refreshArmor(dummy);
    }

    /** Eases a fraction of the remaining turn each tick, the shorter way round. */
    private Vec2 turnTowards(Vec2 current, Vec2 target) {
        float share = Math.min(1f, rotationSpeed.getFloat() * TURN_PER_SPEED);
        float yaw = current.getYaw() + MathUtil.wrapDegrees(target.getYaw() - current.getYaw()) * share;
        float pitch = current.getPitch() + (target.getPitch() - current.getPitch()) * share;
        return Vec2.rotation(yaw, RotationMath.clampPitch(pitch));
    }

    @Override
    public String getDisplayInfo() {
        if (recording) {
            return String.format(Locale.ROOT, "Rec %.0fs", path.seconds());
        }
        return path.isEmpty() ? "" : "Path";
    }
}
