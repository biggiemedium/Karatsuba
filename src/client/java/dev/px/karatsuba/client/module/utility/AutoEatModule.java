package dev.px.karatsuba.client.module.utility;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.flow.Flow;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.FlowHandle;
import dev.px.core.flow.FlowService;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec3;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.step.Eat;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Eats a golden apple when health is low and food when hunger is.
 *
 * <h2>Built on flows, not on a tick handler</h2>
 * The prototype for a module that is smarter by being made of flow steps. It
 * registers two of Core's <b>reflexes</b> while on and cancels them when off:
 *
 * <ul>
 *   <li><b>Golden apple</b>: health (absorption not counted) at or below
 *       {@code Apple Health}, fight or not. Enchanted or plain, whichever the
 *       hotbar has, in the order set.</li>
 *   <li><b>Meal</b>: hunger at or below {@code Hunger Below}, or hurt, below the
 *       hunger natural regeneration needs and out of a fight. Never started, and
 *       given up, with an enemy in melee range or an apple needed instead.</li>
 * </ul>
 *
 * <p>The eating itself is the automation's {@link Eat} step, so it is done the
 * way a player does it: the slot really switched to, the use held until the
 * server says the item was eaten. A reflex pauses only the flows that need the use button, so a
 * task walking somewhere keeps walking while this eats, and one that was using
 * the button picks up where it left off.
 *
 * <h2>Silent Swap</h2>
 * The hotbar and the hand keep showing the player's own slot while the food is
 * held. Only the drawing changes: a swap that only the server sees cannot last
 * the 32 ticks of a bite, because Grim tracks item use and its slowdown from the
 * slot and use packets (see {@code anticheat/Grim.md}, Simulation).
 *
 * <h2>When it counts as a fight</h2>
 * A player who is not a friend within {@code Enemy Range}, health lost in the
 * last three seconds, or burning. A meal is never <em>started</em> with an enemy
 * in melee range, fight or not: the bite takes over a second and a half.
 *
 * <h2>Against the combat modules</h2>
 * The hotbar is claimed like any module's. Kill Aura holds its weapon at
 * {@code HIGH} and equal claims stay with whoever asked first, so with
 * {@code Over Combat} on the apple is claimed just above that, and off just
 * below, which waits for the aura to stop. Meals claim at {@code NORMAL}, beneath
 * every combat module. Nothing swings while eating: the action service refuses an
 * attack while an item is in use, as Grim does.
 */
@ModuleInfo(name = "Auto Eat", description = "Eats golden apples when health is low and food when hungry",
        category = "Utility")
public final class AutoEatModule extends ClientModule {

    private static final String GAPPLE = "golden_apple";
    private static final String ENCHANTED_GAPPLE = "enchanted_golden_apple";

    /** Hunger at and above which health regenerates by itself. */
    private static final int REGEN_HUNGER = 18;
    /** Blocks: an enemy this near is in melee range, too close to start a meal. */
    private static final double MELEE = 4d;
    /** Ticks after losing health that still count as being in a fight. */
    private static final long HURT_MEMORY = 60L;
    /** Ticks to leave meals alone after one could not be eaten. */
    private static final long BACK_OFF = 40L;

    private final BooleanSetting silentSwap = bool("Silent Swap", true)
            .describe("Keep your hotbar and hand showing your own slot while eating; the server still sees the food held");

    private final BooleanSetting apples = bool("Golden Apples", true)
            .describe("Eat a golden apple whenever health is low");

    private final NumberSetting<Float> appleHealth = number("Apple Health", 10f, 1f, 20f)
            .step(0.5f)
            .describe("Health at or below which an apple is eaten; absorption is not counted")
            .visibleWhen(apples);

    private final BooleanSetting enchantedFirst = bool("Enchanted First", false)
            .describe("Eat an enchanted apple before a plain one")
            .visibleWhen(apples);

    private final NumberSetting<Integer> appleGap = integer("Apple Gap", 20, 0, 100)
            .describe("Ticks between apples, so regeneration has a moment to heal first")
            .visibleWhen(apples);

    private final BooleanSetting overCombat = bool("Over Combat", true)
            .describe("Take the hotbar from combat modules for an apple")
            .visibleWhen(apples);

    private final GroupSetting appleGroup = group("Apples", true)
            .containing(apples, appleHealth, enchantedFirst, appleGap, overCombat);

    private final NumberSetting<Integer> hungerBelow = integer("Hunger Below", 14, 1, 19)
            .describe("Eat food at or below this hunger");

    private final BooleanSetting keepRegen = bool("Keep Regen", true)
            .describe("Also eat while hurt and below 18 hunger, so health keeps regenerating");

    private final NumberSetting<Float> enemyRange = number("Enemy Range", 8f, 3f, 24f)
            .step(0.5f)
            .describe("A player this near who is not a friend means a fight");

    private FlowHandle apple;
    private FlowHandle meal;

    private float lastPool;
    private long lastHurt = Long.MIN_VALUE / 2;
    private long lastApple = Long.MIN_VALUE / 2;
    private long mealsFrom;

    /** The nearest enemy's distance, and the tick it was measured: each condition asks, so once a tick. */
    private double nearest = Double.POSITIVE_INFINITY;
    private long nearestAsked = -1L;

    @Override
    protected void onEnable() {
        lastPool = Players.healthPool();
        lastHurt = Long.MIN_VALUE / 2;
        lastApple = Long.MIN_VALUE / 2;
        mealsFrom = 0L;

        FlowService flows = Core.flows();
        // The apple first: reflexes are checked in the order they are registered,
        // so on a tick both would fire, the apple has the hotbar before the meal asks.
        apple = flows.reflex("Auto Eat: golden apple", this::wantsApple, RotationPriority.HIGH, appleStep());
        meal = flows.reflex("Auto Eat: meal", this::wantsMeal, RotationPriority.NORMAL + 1, mealStep());
    }

    @Override
    protected void onDisable() {
        if (apple != null) {
            apple.cancel();
            apple = null;
        }
        if (meal != null) {
            meal.cancel();
            meal = null;
        }
    }

    /** Notes when health was lost, which is half of knowing a fight is on. */
    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        float pool = Players.healthPool();
        if (pool < lastPool) {
            lastHurt = Core.flows().getTick();
        }
        lastPool = pool;
    }

    // --------------------------------------------------------------- steps

    /** The best apple there is, in the order set; the time noted either way, so a failure is not retried at once. */
    private Step appleStep() {
        Step eat = Flow.firstOf(
                Eat.item(ENCHANTED_GAPPLE).priority(this::appleClaim).silent(silentSwap::isOn)
                        .when(c -> enchantedFirst.isOn()),
                Eat.item(GAPPLE).priority(this::appleClaim).silent(silentSwap::isOn),
                Eat.item(ENCHANTED_GAPPLE).priority(this::appleClaim).silent(silentSwap::isOn));
        return eat.orElse(Flow.action("no apple to eat", c -> { }))
                .then(Flow.action("note the apple", c -> lastApple = c.tick()))
                .named("golden apple");
    }

    /**
     * The best food for hunger, given up if a fight closes in or an apple is
     * needed. Any meal the server did not finish &mdash; given up, cut short, or
     * never started &mdash; leaves meals alone for a moment, so a fight at the edge
     * of melee range does not start and drop one every tick.
     */
    private Step mealStep() {
        Eat eat = Eat.food().priority(ClaimPriority.NORMAL).silent(silentSwap::isOn);
        return eat.until(c -> nearestEnemy() <= MELEE || isRunning(apple))
                .orElse(Flow.action("not eaten", c -> { }))
                .then(Flow.action("back off unless eaten", c -> {
                    if (!eat.ate()) {
                        mealsFrom = c.tick() + BACK_OFF;
                    }
                }))
                .named("meal");
    }

    private int appleClaim() {
        return overCombat.isOn() ? ClaimPriority.HIGH + 1 : ClaimPriority.HIGH - 1;
    }

    // ---------------------------------------------------------- conditions

    private boolean wantsApple(FlowContext c) {
        if (!apples.isOn() || c.tick() - lastApple < appleGap.getInt()) {
            return false;
        }
        if (!Inventories.hotbarHas(GAPPLE) && !Inventories.hotbarHas(ENCHANTED_GAPPLE)) {
            return false;
        }
        // Health alone: absorption hearts do not hold an apple back, and neither does being out of a fight.
        return Players.health() <= appleHealth.getFloat();
    }

    private boolean wantsMeal(FlowContext c) {
        if (c.tick() < mealsFrom || Players.usingItem() || isRunning(apple)
                || Inventories.bestFoodSlot() == Inventories.NOT_FOUND || nearestEnemy() <= MELEE) {
            return false;
        }
        int food = Players.foodLevel();
        if (food <= hungerBelow.getInt()) {
            return true;
        }
        return keepRegen.isOn() && food < REGEN_HUNGER && Players.health() < Players.maxHealth() && !inFight(c);
    }

    private boolean inFight(FlowContext c) {
        return Players.onFire() || c.tick() - lastHurt < HURT_MEMORY || nearestEnemy() <= enemyRange.getFloat();
    }

    /** @return the distance to the nearest player who is not a friend, or infinity */
    private double nearestEnemy() {
        long tick = Core.flows().getTick();
        if (tick != nearestAsked) {
            nearestAsked = tick;
            nearest = findNearestEnemy();
        }
        return nearest;
    }

    private double findNearestEnemy() {
        Vec3 at = Players.position();
        double nearest = Double.POSITIVE_INFINITY;
        // Never closer than melee range, which is asked of this too.
        for (GameEntity entity : Entities.nearby(1f, Math.max(MELEE, enemyRange.getFloat()))) {
            if (entity.isPlayer() && entity.getId() != Players.entityId() && !entity.isFriend()) {
                nearest = Math.min(nearest, entity.distanceTo(at));
            }
        }
        return nearest;
    }

    private static boolean isRunning(FlowHandle reflex) {
        return reflex != null && reflex.getState() == FlowHandle.State.RUNNING;
    }

    @Override
    public String getDisplayInfo() {
        if (isRunning(apple)) {
            return "Apple";
        }
        return isRunning(meal) ? "Eating" : "";
    }
}
