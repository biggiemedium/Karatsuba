package dev.px.karatsuba.client.module.inventory;

import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.input.Bind;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.combat.SelfBlast;
import dev.px.karatsuba.adapter.game.item.Armor;
import dev.px.karatsuba.adapter.game.item.ArmorStats;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.inventory.manager.ArmorPlanner;
import dev.px.karatsuba.client.module.movement.TickKey;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.inventory.InventoryClick;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

/**
 * One key between chestplate and elytra, a fresh elytra before the worn one gives
 * out, and a chestplate on before a crystal can catch you in an elytra.
 *
 * <h2>The swap</h2>
 * {@code Swap Bind} puts on whichever of the two you are not wearing: the elytra
 * that can glide longest, or your best chestplate. With {@code Take Off}, swapping
 * to the elytra on the ground also jumps and opens it, and in the air it opens it
 * straight away. The chest slot is never empty along the way: a piece in the
 * hotbar swaps on in one click, and one in the main inventory is brought to the
 * hotbar first and that hotbar slot put back as it was afterwards, so the hotbar
 * ends up as you left it.
 *
 * <h2>Fresh elytra</h2>
 * {@code Auto Replace}: while wearing an elytra with less than {@code Replace At}
 * durability left, the freshest one carried goes on instead, in the air without
 * landing, since the slot never empties.
 *
 * <h2>Crystal Guard</h2>
 * An elytra protects nothing. While you wear one, every crystal near you is
 * checked against where you will be once a chestplate could be on: the swap
 * lands a tick or two from now, so the question is whether you will have flown
 * out of reach by then. Each crystal is scored at your position over the next
 * {@code Look Ahead} ticks after the swap could land, with the same 1.21.4
 * explosion maths the crystal aura uses ({@link SelfBlast}). If any of those
 * would do {@code Guard Damage} or more (or would kill you), and your
 * chestplate would take some of it off, the chestplate goes on, Blast Protection
 * ranked first. A crystal only counts if it can go off: with
 * {@code Enemy Near Crystal}, an enemy has to be within reach of it.
 *
 * <p>Swapping out of an elytra in the air ends the glide. With {@code Re-Equip},
 * once nothing has threatened you for half a second and you are still falling,
 * the elytra goes back on and opens again.
 *
 * <h2>Grim</h2>
 * Every click goes through {@code Services.inventory()}, marked to stop you for a
 * tick if moving is all that holds it back (MultiActionsC): in the air that costs
 * nothing, since an elytra ignores the movement keys. Take-off and opening are
 * the real jump key, one tick at a time, so Grim sees exactly what vanilla sends.
 *
 * <p>Set Inventory Manager's {@code Keep Elytra} on (the default), or its armour
 * will take the elytra back off.
 */
@ModuleInfo(name = "Elytra Swap", description = "One key between chestplate and elytra; a chestplate on before a crystal can catch you",
        category = "Inventory")
public final class ElytraSwapModule extends ClientModule {

    private static final int CHEST = Armor.Piece.CHESTPLATE.inventorySlot();

    /** How far a crystal can be and still be worth scoring: past 12 blocks, one cannot hurt. */
    private static final double CRYSTAL_RANGE = 14d;

    /** Ticks without a threat before the elytra goes back on. */
    private static final int SAFE_TICKS = 10;

    /** Give up putting a borrowed hotbar slot back after this long. */
    private static final int RESTORE_TIMEOUT = 40;

    /**
     * Above Inventory Manager's armour ({@link ClaimPriority#HIGH}): a tie goes to
     * whoever asked first, so at equal priority a waiting armour upgrade would hold
     * the click slot and the swap you pressed for would never get it.
     */
    private static final int SWAP_PRIORITY = ClaimPriority.HIGH + 15;

    private final BindSetting swapBind = bind("Swap Bind")
            .describe("Put on whichever of chestplate and elytra you are not wearing");

    private final BooleanSetting takeOff = bool("Take Off", true)
            .describe("Swapping to the elytra also jumps and opens it");

    private final BooleanSetting autoReplace = bool("Auto Replace", true)
            .describe("Swap in a fresher elytra before the worn one gives out");

    private final NumberSetting<Integer> replaceAt = integer("Replace At", 5, 1, 50)
            .describe("Percent durability left at which the elytra is replaced")
            .visibleWhen(autoReplace);

    private final BooleanSetting guard = bool("Crystal Guard", true)
            .describe("Put the chestplate on when a crystal could go off before you are clear of it");

    private final NumberSetting<Double> guardDamage = decimal("Guard Damage", 10d, 1d, 36d)
            .step(0.5d)
            .describe("Damage, in half hearts, a crystal would have to do through the elytra; a lethal hit always counts")
            .visibleWhen(guard);

    private final NumberSetting<Integer> lookAhead = integer("Look Ahead", 10, 0, 40)
            .describe("Ticks past the swap to keep checking where you will be")
            .visibleWhen(guard);

    private final BooleanSetting enemyNear = bool("Enemy Near Crystal", true)
            .describe("Count a crystal only while an enemy is close enough to set it off")
            .visibleWhen(guard);

    private final NumberSetting<Double> enemyReach = decimal("Enemy Reach", 6d, 3d, 10d)
            .step(0.5d)
            .describe("How close to a crystal an enemy has to be")
            .visibleWhen(() -> guard.isOn() && enemyNear.isOn());

    private final BooleanSetting reEquip = bool("Re-Equip", true)
            .describe("Once safe again and still falling, put the elytra back on and open it")
            .visibleWhen(guard);

    private final GroupSetting guardGroup = group("Guard Options", true)
            .containing(guardDamage, lookAhead, enemyNear, enemyReach, reEquip)
            .visibleWhen(guard);

    private final TickKey jump = new TickKey(Inputs.Control.JUMP);

    /** What the chest slot should end up holding. */
    private Target target = Target.NONE;

    /** Whether the current swap is the guard's, which outranks everything and earns Re-Equip. */
    private boolean guarding;

    /** Whether to say so when the swap lands: it was asked for with the bind. */
    private boolean announce;

    /** Whether the guard took the elytra off and may put it back. */
    private boolean guardedOff;

    private int safeTicks;

    /** Take-off: open the elytra once the slot holds one. */
    private boolean openAfter;

    /** Ticks since the take-off jump, or -1 for none in progress. */
    private int sinceJump = -1;

    /** A hotbar slot lent to a swap: put back once the swap is done. */
    private int borrowedSlot = Inventories.NOT_FOUND;
    private int borrowedTo = Inventories.NOT_FOUND;
    private GameItem borrowedItem;
    private int borrowAge;

    private String display = "";

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        jump.release();
        reset();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        reset();
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            reset();
            return;
        }
        ArmorStats chest = ArmorStats.at(CHEST);
        boolean wearingElytra = chest != null && chest.isElytra();

        if (guard.isOn()) {
            watchCrystals(wearingElytra);
        }
        if (autoReplace.isOn() && wearingElytra && target == Target.NONE
                && chest.getDurability() * 100f < replaceAt.getInt() && bestElytra(chest.getDurability()) >= 0) {
            target = Target.ELYTRA;
        }

        if (target != Target.NONE) {
            pursue(chest);
        } else if (openAfter) {
            // Before putting the hotbar back: a click that stood us still would
            // swallow the jump that opens the elytra, and falling, opening matters more.
            takeOff(wearingElytra);
        } else if (borrowedSlot != Inventories.NOT_FOUND) {
            putBorrowedBack();
        }
        display = guardedOff ? "Guard" : wearingElytra ? "Elytra" : "";
    }

    // ------------------------------------------------------------------ swap

    private void pursue(ArmorStats chest) {
        boolean done = target == Target.ELYTRA
                ? chest != null && chest.isElytra() && chest.isGlides()
                        && !(autoReplace.isOn() && chest.getDurability() * 100f < replaceAt.getInt()
                        && bestElytra(chest.getDurability()) >= 0)
                : chest != null && !chest.isElytra();
        if (done) {
            if (announce) {
                Notices.info(Source.MODULE_MESSAGES, "Elytra Swap", target == Target.ELYTRA ? "Elytra on" : "Chestplate on");
            }
            target = Target.NONE;
            guarding = false;
            announce = false;
            return;
        }
        int source = target == Target.ELYTRA
                ? bestElytra(chest != null && chest.isElytra() ? chest.getDurability() : -1f)
                : bestChestplate();
        if (source < 0) {
            Notices.info(Source.MODULE_MESSAGES, "Elytra Swap", target == Target.ELYTRA ? "No elytra" : "No chestplate");
            target = Target.NONE;
            guarding = false;
            openAfter = false;
            return;
        }
        click(clickFor(source, chest == null));
    }

    /**
     * The next click toward wearing {@code source}. The chest slot never empties
     * on the way, so a glide in progress survives an elytra swap.
     */
    private InventoryClick clickFor(int source, boolean chestEmpty) {
        if (chestEmpty) {
            return InventoryClick.quickMove(source);
        }
        if (source < Inventories.HOTBAR_SIZE) {
            return InventoryClick.swap(CHEST, source);
        }
        // From the main inventory: lend it a hotbar slot first, and remember what
        // was there so it can be put back once the swap is done.
        int lent = lendableHotbarSlot();
        borrowedSlot = lent;
        borrowedTo = source;
        borrowedItem = Inventories.stackAt(lent);
        borrowAge = 0;
        return InventoryClick.swap(source, lent);
    }

    /**
     * Puts back the hotbar slot a swap borrowed: the item that was there is now
     * where the new piece came from, and the old piece is in the hotbar slot.
     */
    private void putBorrowedBack() {
        boolean back = borrowedItem == null || borrowedItem.isEmpty()
                ? Inventories.stackAt(borrowedSlot).isEmpty()
                : Inventories.sameItem(borrowedItem, borrowedSlot);
        if (back || ++borrowAge > RESTORE_TIMEOUT) {
            borrowedSlot = borrowedTo = Inventories.NOT_FOUND;
            borrowedItem = null;
            return;
        }
        click(InventoryClick.swap(borrowedTo, borrowedSlot));
    }

    private void click(InventoryClick click) {
        int priority = guarding ? ClaimPriority.HIGHEST : SWAP_PRIORITY;
        // Stop for it: the player asked for it, or the guard wants it now; in the
        // air the stop costs nothing.
        Services.inventory().click(this, click.stopping(), priority);
    }

    /** A hotbar slot that is not the one in hand. */
    private static int lendableHotbarSlot() {
        int selected = Inventories.selectedSlot();
        return selected == Inventories.HOTBAR_SIZE - 1 ? Inventories.HOTBAR_SIZE - 2 : Inventories.HOTBAR_SIZE - 1;
    }

    /** @return the carried elytra that can glide longest, if it beats {@code than}; or -1 */
    private static int bestElytra(float than) {
        int best = -1;
        float bestDurability = than;
        for (int slot = 0; slot < Inventories.ARMOR_START; slot++) {
            ArmorStats stats = ArmorStats.at(slot);
            if (stats != null && stats.isElytra() && stats.isGlides() && stats.getDurability() > bestDurability) {
                best = slot;
                bestDurability = stats.getDurability();
            }
        }
        return best;
    }

    /** @return the best carried chestplate, Blast Protection first while guarding; or -1 */
    private int bestChestplate() {
        ArmorPlanner.Preference preference = guarding
                ? ArmorPlanner.Preference.BLAST : ArmorPlanner.Preference.PROTECTION;
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < Inventories.ARMOR_START; slot++) {
            ArmorStats stats = ArmorStats.at(slot);
            if (stats == null || stats.getPiece() != Armor.Piece.CHESTPLATE || stats.isElytra() || stats.isBinding()) {
                continue;
            }
            double score = ArmorPlanner.score(stats, preference);
            if (score > bestScore) {
                best = slot;
                bestScore = score;
            }
        }
        return best;
    }

    // -------------------------------------------------------------- take-off

    /**
     * Opens the elytra with the real jump key: on the ground a jump first, then a
     * fresh press once airborne, which is what vanilla opens an elytra on.
     */
    private void takeOff(boolean wearingElytra) {
        if (!wearingElytra || Players.gliding()) {
            openAfter = false;
            sinceJump = -1;
            return;
        }
        if (Players.onGround()) {
            if (sinceJump < 0) {
                jump.press();
                sinceJump = 0;
            } else if (++sinceJump > 10) {
                // Never left the ground (a ceiling, say): give up.
                openAfter = false;
                sinceJump = -1;
            }
            return;
        }
        // Airborne: the press must be a new one, so leave a tick after the jump's.
        if (sinceJump >= 0 && ++sinceJump < 2) {
            return;
        }
        jump.press();
        openAfter = false;
        sinceJump = -1;
    }

    // ------------------------------------------------------------- the guard

    private void watchCrystals(boolean wearingElytra) {
        if (wearingElytra) {
            if (target != Target.CHEST && threatened(Combat.kit())) {
                target = Target.CHEST;
                guarding = true;
                guardedOff = true;
                openAfter = false;
                safeTicks = 0;
                Notices.info(Source.MODULE_MESSAGES, "Elytra Swap", "Crystal: chestplate on");
            }
            return;
        }
        if (!guardedOff) {
            return;
        }
        if (Players.onGround()) {
            // Landed in the chestplate: nothing to go back to.
            guardedOff = false;
            return;
        }
        if (threatened(Combat.kit())) {
            safeTicks = 0;
        } else if (++safeTicks >= SAFE_TICKS && reEquip.isOn() && Players.velocity().getY() < 0d
                && target == Target.NONE && bestElytra(-1f) >= 0) {
            guardedOff = false;
            target = Target.ELYTRA;
            openAfter = true;
        }
    }

    /**
     * @return whether a crystal that could go off would hurt too much in an
     *         elytra at some point from when a chestplate could be on until
     *         {@code Look Ahead} ticks after, while the chestplate would help
     *
     * <p>Asked the same way in and out of the elytra: out of it, it is what keeps
     * the elytra off until the danger has passed.
     */
    private <E> boolean threatened(CombatKit<E> kit) {
        int chestplate = bestChestplate();
        if (chestplate < 0) {
            return false;
        }
        ArmorStats plate = ArmorStats.at(chestplate);
        // One click if the plate is in the hotbar, two otherwise; one more tick for
        // the click to go out at the start of the next.
        int swapTicks = chestplate < Inventories.HOTBAR_SIZE ? 2 : 3;
        Vec3 feet = Players.position();
        Vec3 velocity = Players.velocity();
        double limit = Math.min(guardDamage.getDouble(), Players.healthPool() - 1d);

        boolean[] threat = {false};
        kit.crystals().forEachWithin(feet, CRYSTAL_RANGE, crystal -> {
            if (threat[0] || (enemyNear.isOn() && !enemyNear(kit, crystal.getPosition()))) {
                return;
            }
            Vec3 origin = crystal.getPosition();
            for (int tick = swapTicks; tick <= swapTicks + lookAhead.getInt(); tick++) {
                Vec3 at = feet.add(velocity.scale(tick));
                double bare = SelfBlast.crystal(origin, at, null);
                if (bare >= limit && SelfBlast.crystal(origin, at, plate) < bare) {
                    threat[0] = true;
                    return;
                }
            }
        });
        return threat[0];
    }

    /** @return whether a player who is not a friend is within reach of the point */
    private <E> boolean enemyNear(CombatKit<E> kit, Vec3 point) {
        double reach = enemyReach.getDouble();
        Tracked<E> self = Core.entities().getSelf();
        for (Tracked<E> player : kit.players().getAll()) {
            if (self != null && player.get() == self.get()) {
                continue;
            }
            if (!kit.isFriend(player.get()) && player.getEyePosition().distanceTo(point) <= reach) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ bind

    /**
     * Listens for {@code Swap Bind} whether or not the module is on: it is a
     * button you press, not a mode. A module's own subscriptions only run while it
     * is enabled, so these are on the bus for the module's whole life instead,
     * and pressing the bind with the module off turns it on to do the swap.
     */
    public ElytraSwapModule() {
        Core.bus().on(KeyEvent.class, event -> {
            Bind bind = swapBind.get();
            if (event.isPressed() && !bind.isMouse() && bind.isBound()
                    && bind.matches(event.getKey(), event.getModifiers())) {
                pressed();
            }
        });
        Core.bus().on(MouseEvent.class, event -> {
            Bind bind = swapBind.get();
            if (event.isPressed() && bind.isMouse() && bind.matches(event.getButton(), event.getModifiers())) {
                pressed();
            }
        });
    }

    private void pressed() {
        if (!Players.present()) {
            return;
        }
        if (!isEnabled()) {
            enable();
        }
        swap();
    }

    private void swap() {
        ArmorStats chest = ArmorStats.at(CHEST);
        boolean wearingElytra = chest != null && chest.isElytra();
        target = wearingElytra ? Target.CHEST : Target.ELYTRA;
        announce = true;
        guarding = false;
        guardedOff = false;
        openAfter = !wearingElytra && takeOff.isOn();
        sinceJump = -1;
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    private void reset() {
        target = Target.NONE;
        announce = false;
        guarding = false;
        guardedOff = false;
        openAfter = false;
        sinceJump = -1;
        safeTicks = 0;
        borrowedSlot = borrowedTo = Inventories.NOT_FOUND;
        borrowedItem = null;
        display = "";
    }

    private enum Target {
        NONE,
        ELYTRA,
        CHEST
    }
}
