package dev.px.karatsuba.client.module.combat;

import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.input.Bind;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.event.game.combat.TotemPopEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

/**
 * Keeps a totem of undying in your offhand &mdash; or, in Smart mode, your fighting
 * item while it is safe to hold one, and a totem the moment it is not.
 *
 * <h2>Two modes, one key between them</h2>
 * <ul>
 *   <li><b>{@link Mode#TOTEM}</b>: always a totem. For exploring, building, anything
 *       where the offhand should just be insurance.</li>
 *   <li><b>{@link Mode#SMART}</b>: your {@code Smart Item} (crystals or gapples) while
 *       an enemy is within {@code Combat Range} and nothing threatens to kill you;
 *       a totem otherwise &mdash; with nobody to fight there is no reason to hold
 *       anything else.</li>
 * </ul>
 * {@code Mode Bind} flips between the two, with a notification, so going from a
 * fight back to exploring is one key.
 *
 * <h2>What counts as danger</h2>
 * In Smart mode, a totem goes in whenever any of these is true, whatever else is
 * going on:
 * <ul>
 *   <li>health and absorption at or below {@code Safe Health};</li>
 *   <li>a crystal already in the world that would leave you within a heart
 *       ({@code Crystal Check}): the same explosion maths the crystal aura scores with;</li>
 *   <li>a fall that would (fall distance less three, before armour);</li>
 *   <li>gliding on an elytra, where one wall is the end.</li>
 * </ul>
 *
 * <h2>Getting it there</h2>
 * Through {@code Services.actions()}, ahead of everything else, and from the main
 * inventory first: the hotbar is used only when the main inventory has none, so
 * your hotbar stays as you laid it out. A main-inventory item is one click, which
 * with the Interaction checks (Client Settings) on waits until you stand still
 * &mdash; Grim flags an inventory click while moving. A hotbar item is the
 * swap-hands key, with no click. A totem you need now is the exception: in danger,
 * when the click cannot go this tick, a hotbar totem goes in instead, and with
 * {@code Stop In Danger} a totem only in the main inventory stops your movement
 * keys for one tick so the click goes out at the start of the next. A swap never
 * shares a tick with an attack or a click.
 *
 * <h2>Not fighting the server</h2>
 * <ul>
 *   <li>One swap at a time: the inventory service sends nothing more until the
 *       server has answered the last one (see {@code InventoryService#toOffhand}).
 *       The swap-hands key is applied by the server, not the client, so asking
 *       again before the answer swaps the item straight back out.</li>
 *   <li>Its choices hold: a totem stays in for a second after the last sign of
 *       danger, the fighting item for a moment after it went in, and an enemy has
 *       to step a little past {@code Combat Range} before the fighting item goes.
 *       Health hovering at {@code Safe Health}, or an enemy at the edge of range,
 *       swaps once rather than every tick. A totem for danger, and an apple for a
 *       right click, never wait.</li>
 * </ul>
 */
@ModuleInfo(name = "Auto Totem", description = "Keeps a totem in your offhand, or your fighting item while it is safe",
        category = "Combat")
public final class AutoTotemModule extends ClientModule {

    private static final String TOTEM = "totem_of_undying";
    private static final String CRYSTAL = "end_crystal";
    private static final String GAPPLE = "enchanted_golden_apple";
    private static final String GOLDEN_APPLE = "golden_apple";

    /** How far a crystal can be and still be worth checking: past this none can hurt. */
    private static final double CRYSTAL_RANGE = 12d;

    /** Ticks a totem stays in after the last sign of danger. */
    private static final long DANGER_HOLD = 20L;

    /** Ticks the fighting item stays before anything but a totem or an apple for a right click replaces it. */
    private static final long CHOICE_HOLD = 10L;

    /** Blocks past {@code Combat Range} an enemy may step before the fighting item goes back. */
    private static final double RANGE_SLACK = 1.5d;

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.SMART)
            .describe("TOTEM always holds a totem; SMART holds your fighting item while it is safe");

    private final BindSetting modeBind = bind("Mode Bind")
            .describe("Flip between Totem and Smart");

    private final EnumSetting<SmartItem> smartItem = enumOf("Smart Item", SmartItem.CRYSTAL)
            .describe("What Smart mode holds while it is safe")
            .visibleWhen(() -> mode.get() == Mode.SMART);

    private final NumberSetting<Double> combatRange = decimal("Combat Range", 8d, 2d, 20d)
            .step(0.5d)
            .describe("Hold the fighting item only while an enemy is this close")
            .visibleWhen(() -> mode.get() == Mode.SMART);

    private final NumberSetting<Double> safeHealth = decimal("Safe Health", 14d, 1d, 36d)
            .step(0.5d)
            .describe("Totem at or below this much health and absorption")
            .visibleWhen(() -> mode.get() == Mode.SMART);

    private final BooleanSetting crystalCheck = bool("Crystal Check", true)
            .describe("Totem when a crystal nearby would leave you within a heart")
            .visibleWhen(() -> mode.get() == Mode.SMART);

    private final BooleanSetting fallCheck = bool("Fall Check", true)
            .describe("Totem when the fall you are in would kill you")
            .visibleWhen(() -> mode.get() == Mode.SMART);

    private final BooleanSetting elytraCheck = bool("Elytra Check", true)
            .describe("Totem while gliding")
            .visibleWhen(() -> mode.get() == Mode.SMART);

    private final GroupSetting smart = group("Smart", true)
            .containing(smartItem, combatRange, safeHealth, crystalCheck, fallCheck, elytraCheck);

    private final BooleanSetting gappleOnUse = bool("Gapple On Use", true)
            .describe("While safe, a golden apple when you right-click with a weapon");

    private final BooleanSetting stopInDanger = bool("Stop In Danger", true)
            .describe("In danger, stop for one tick to pull a totem from the main inventory, rather than wait for you to stand still");

    private String display = "";

    private long ticks;
    private long dangerUntil = Long.MIN_VALUE / 2;
    /** The item last chosen, and the tick it was. */
    private String chosen;
    private long chosenAt = Long.MIN_VALUE / 2;
    /** Set by {@link #wanted} when its choice is an apple for a right click, which never waits. */
    private boolean forUse;

    @Override
    protected void onEnable() {
        dangerUntil = Long.MIN_VALUE / 2;
        chosen = null;
        chosenAt = Long.MIN_VALUE / 2;
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            display = "";
            return;
        }
        int totems = Inventories.count(TOTEM);
        display = (mode.get() == Mode.TOTEM ? "Totem " : "Smart ") + totems;

        ticks++;
        boolean danger = mode.get() == Mode.SMART && inDanger();
        if (danger) {
            dangerUntil = ticks + DANGER_HOLD;
        }
        boolean threatened = ticks < dangerUntil;
        String wanted = steady(wanted(threatened));
        if (wanted != null && !Inventories.offhandHolds(wanted)) {
            Action action = Action.offhand(wanted).priority(ClaimPriority.HIGHEST).fromInventoryFirst();
            if (TOTEM.equals(wanted) && danger) {
                action.urgent();
                if (stopInDanger.isOn()) {
                    action.stopping();
                }
            }
            Services.actions().submit(this, action);
        }
    }

    /**
     * Holds the fighting item a moment once it is in, so a choice on the edge of
     * a threshold swaps once rather than every tick. A totem and an apple for a
     * right click always go straight in.
     */
    private String steady(String wanted) {
        if (wanted == null || wanted.equals(chosen)) {
            return wanted;
        }
        boolean immediate = TOTEM.equals(wanted) || forUse;
        if (!immediate && chosen != null && !TOTEM.equals(chosen) && ticks - chosenAt < CHOICE_HOLD && has(chosen)) {
            return chosen;
        }
        chosen = wanted;
        chosenAt = ticks;
        return wanted;
    }

    /**
     * A totem just saved us: the next goes in on the very next tick, not a tick
     * later. Having just popped one is danger by definition, so it may stop us.
     */
    @Subscribe
    private void onPop(TotemPopEvent event) {
        if (event.isSelf() && Inventories.count(TOTEM) > 0) {
            dangerUntil = ticks + DANGER_HOLD;
            Action action = Action.offhand(TOTEM).priority(ClaimPriority.HIGHEST).fromInventoryFirst().urgent();
            if (stopInDanger.isOn()) {
                action.stopping();
            }
            Services.actions().submit(this, action);
        }
    }

    @Subscribe
    private void onKey(KeyEvent event) {
        Bind bind = modeBind.get();
        if (event.isPressed() && !bind.isMouse() && bind.isBound()
                && bind.matches(event.getKey(), event.getModifiers())) {
            flipMode();
        }
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        Bind bind = modeBind.get();
        if (event.isPressed() && bind.isMouse() && bind.matches(event.getButton(), event.getModifiers())) {
            flipMode();
        }
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    // --------------------------------------------------------------- choosing

    /**
     * @param threatened in danger now, or within {@link #DANGER_HOLD} of it
     * @return the item the offhand should hold now; null when none of it is to hand
     */
    private String wanted(boolean threatened) {
        forUse = false;
        String totem = has(TOTEM) ? TOTEM : null;
        if (mode.get() == Mode.TOTEM || threatened) {
            return totem;
        }
        if (gappleOnUse.isOn() && Inputs.pressed(Inputs.Control.USE) && holdingWeapon()) {
            String apple = has(GAPPLE) ? GAPPLE : has(GOLDEN_APPLE) ? GOLDEN_APPLE : null;
            if (apple != null) {
                forUse = true;
                return apple;
            }
        }
        if (enemyNear(Combat.kit())) {
            String item = smartItem.get() == SmartItem.CRYSTAL ? CRYSTAL
                    : has(GAPPLE) ? GAPPLE : GOLDEN_APPLE;
            if (has(item)) {
                return item;
            }
        }
        return totem;
    }

    /** @return whether something could kill us soon enough that only a totem will do */
    private boolean inDanger() {
        float pool = Players.healthPool();
        if (pool <= safeHealth.getDouble()) {
            return true;
        }
        if (elytraCheck.isOn() && Players.gliding()) {
            return true;
        }
        if (fallCheck.isOn() && Math.ceil(Players.fallDistance() - 3d) >= pool - 1d) {
            return true;
        }
        return crystalCheck.isOn() && lethalCrystal(Combat.kit(), pool);
    }

    /** @return whether a crystal already in the world would leave us within a heart */
    private static <E> boolean lethalCrystal(CombatKit<E> kit, float pool) {
        Tracked<E> self = Core.entities().getSelf();
        if (self == null) {
            return false;
        }
        boolean[] lethal = {false};
        kit.crystals().forEachWithin(self.getPosition(), CRYSTAL_RANGE, crystal -> {
            if (!lethal[0] && kit.crystalRules().damage(crystal, self) >= pool - 2d) {
                lethal[0] = true;
            }
        });
        return lethal[0];
    }

    /** @return whether a player who is not a friend is within {@code Combat Range} */
    private <E> boolean enemyNear(CombatKit<E> kit) {
        Tracked<E> self = Core.entities().getSelf();
        if (self == null) {
            return false;
        }
        // A little further while the fighting item is in, so an enemy on the edge does not swap it every tick.
        boolean holdingSmart = !Inventories.offhandItem().isEmpty() && !Inventories.offhandHolds(TOTEM);
        double range = combatRange.getDouble() + (holdingSmart ? RANGE_SLACK : 0d);
        for (Tracked<E> player : kit.players().getAll()) {
            if (!kit.isFriend(player.get()) && player.distanceTo(self) <= range) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdingWeapon() {
        String held = Inventories.heldItem();
        return held.endsWith("_sword") || held.endsWith("_axe") || held.endsWith("trident") || held.endsWith("mace");
    }

    private static boolean has(String item) {
        return Inventories.count(item) > 0;
    }

    private void flipMode() {
        mode.set(mode.get() == Mode.TOTEM ? Mode.SMART : Mode.TOTEM);
        Notices.info(Source.MODULE_MESSAGES, "Auto Totem", mode.get() == Mode.TOTEM ? "Totem" : "Smart");
    }

    public enum Mode {
        /** Always a totem. */
        TOTEM,
        /** The fighting item while it is safe, a totem when it is not. */
        SMART
    }

    public enum SmartItem {
        CRYSTAL,
        GAPPLE
    }
}
