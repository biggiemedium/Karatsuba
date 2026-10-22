package dev.px.karatsuba.client.module.inventory;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.input.Bind;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.item.ArmorStats;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.inventory.manager.ArmorPlanner;
import dev.px.karatsuba.client.module.inventory.manager.RefillPlanner;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.inventory.InventoryClick;
import dev.px.karatsuba.client.setting.ItemSetting;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

/**
 * Looks after the inventory while you play: the best armour on, the hotbar
 * stocked, the junk thrown out.
 *
 * <h2>Three jobs, one module</h2>
 * They are one module because they decide together. The cleaner must not throw
 * what the refill would bring to the hotbar, and the refill must not bring junk
 * to the hotbar for the cleaner to throw; armour the cleaner would call junk in
 * whitelist mode is armour this module is about to put on. Each has its own
 * switch, and the cleaner its own bind, for looting with it off.
 *
 * <ul>
 *   <li><b>Armour:</b> wears the best pieces carried. See {@link ArmorPlanner}.</li>
 *   <li><b>Refill:</b> tops up hotbar stacks, and refills a slot whose item was
 *       used up, with no kit to set up. See {@link RefillPlanner}.</li>
 *   <li><b>Cleaner:</b> throws out the items on its list (blacklist), or everything
 *       not on it (whitelist).</li>
 * </ul>
 *
 * <h2>How it clicks</h2>
 * One click a tick at most, through {@code Services.inventory()}, which sends it
 * only on a tick the server believes you are standing still. Armour asks first,
 * at a higher priority than refill, and the cleaner last and lowest, so Auto
 * Totem and anything else outranks a clean-up. Only armour ever stops you to
 * click, and only as {@code Stop To Equip} says: by default, when a slot is bare.
 */
@ModuleInfo(name = "Inventory Manager", description = "Wears your best armour, keeps the hotbar stocked, throws out junk",
        category = "Inventory")
public final class InventoryManagerModule extends ClientModule {

    // ---------------------------------------------------------------- armour

    private final BooleanSetting armour = bool("Armour", true)
            .describe("Wear the best armour you carry");

    private final EnumSetting<ArmorPlanner.Preference> prefer = enumOf("Prefer", ArmorPlanner.Preference.PROTECTION)
            .describe("PROTECTION for all damage; BLAST for crystal and bed fights")
            .visibleWhen(armour);

    private final NumberSetting<Integer> replaceBelow = integer("Replace Below", 5, 0, 50)
            .describe("Swap out a piece with less than this percent durability left; 0 never")
            .visibleWhen(armour);

    private final BooleanSetting keepElytra = bool("Keep Elytra", true)
            .describe("Leave a worn elytra on instead of putting a chestplate on")
            .visibleWhen(armour);

    private final EnumSetting<StopMode> stopToEquip = enumOf("Stop To Equip", StopMode.BARE_SLOT)
            .describe("When to stop you for a tick to put armour on: never, only into a bare slot, or always")
            .visibleWhen(armour);

    private final GroupSetting armourGroup = group("Armour Options", true)
            .containing(prefer, replaceBelow, keepElytra, stopToEquip)
            .visibleWhen(armour);

    // ---------------------------------------------------------------- refill

    private final BooleanSetting refill = bool("Refill", true)
            .describe("Keep hotbar stacks topped up from the rest of the inventory");

    private final NumberSetting<Integer> refillBelow = integer("Refill Below", 50, 5, 95)
            .describe("Top a stack up once it is below this percent full")
            .visibleWhen(refill);

    private final BooleanSetting refillEmptied = bool("Refill Used Up", true)
            .describe("When you use the last of a hotbar stack, put another of it in the same slot")
            .visibleWhen(refill);

    private final GroupSetting refillGroup = group("Refill Options", true)
            .containing(refillBelow, refillEmptied)
            .visibleWhen(refill);

    // --------------------------------------------------------------- cleaner

    private final BooleanSetting cleaner = bool("Cleaner", true)
            .describe("Throw out junk");

    private final EnumSetting<CleanMode> cleanMode = enumOf("Clean Mode", CleanMode.BLACKLIST)
            .describe("BLACKLIST throws what is listed; WHITELIST throws everything else outside the hotbar")
            .visibleWhen(cleaner);

    private final ItemSetting cleanItems = ItemSetting.items("Clean Items",
                    "minecraft:rotten_flesh", "minecraft:poisonous_potato", "minecraft:spider_eye",
                    "minecraft:bone_meal")
            .describe("The blacklist, or in whitelist mode, what to keep")
            .visibleWhen(cleaner);

    private final BooleanSetting cleanHotbar = bool("Clean Hotbar", true)
            .describe("Blacklist mode also throws listed items from the hotbar")
            .visibleWhen(() -> cleaner.isOn() && cleanMode.get() == CleanMode.BLACKLIST);

    private final BindSetting cleanerBind = bind("Cleaner Bind")
            .describe("Turn the cleaner on or off, for looting");

    private final GroupSetting cleanerGroup = group("Cleaner Options", true)
            .containing(cleanMode, cleanItems, cleanHotbar)
            .visibleWhen(cleaner);

    private final ArmorPlanner armourPlanner = new ArmorPlanner();
    private final RefillPlanner refillPlanner = new RefillPlanner();

    private String display = "";

    @Override
    protected void onEnable() {
        refillPlanner.reset();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        refillPlanner.reset();
    }

    /**
     * Plans at the end of the tick, from the inventory as this tick left it; the
     * service sends the click at the start of the next.
     */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            display = "";
            return;
        }
        refillPlanner.observe();

        if (armour.isOn()) {
            ArmorPlanner.Plan plan = armourPlanner.next(prefer.get(), replaceBelow.getInt() / 100f, keepElytra.isOn());
            if (plan != null) {
                InventoryClick click = plan.click();
                StopMode stop = stopToEquip.get();
                if (stop == StopMode.ALWAYS || (stop == StopMode.BARE_SLOT && plan.fillsEmptySlot())) {
                    click = click.stopping();
                }
                submit(click, ClaimPriority.HIGH, "Armour");
                return;
            }
        }
        if (refill.isOn()) {
            InventoryClick click = refillPlanner.next(refillBelow.getInt() / 100f, refillEmptied.isOn(), this::isJunk);
            if (click != null) {
                submit(click, ClaimPriority.NORMAL, "Refill");
                return;
            }
        }
        if (cleaner.isOn()) {
            int junk = firstJunk();
            if (junk != Inventories.NOT_FOUND) {
                submit(InventoryClick.drop(junk), ClaimPriority.LOW, "Clean");
                return;
            }
        }
        display = cleaner.isOn() ? "" : "No Clean";
    }

    private void submit(InventoryClick click, int priority, String job) {
        Services.inventory().click(this, click, priority);
        display = job;
    }

    // --------------------------------------------------------------- cleaner

    /** @return the first slot holding junk, main inventory before the hotbar, or NOT_FOUND */
    private int firstJunk() {
        for (int slot = Inventories.MAIN_START; slot < Inventories.ARMOR_START; slot++) {
            if (isJunk(slot)) {
                return slot;
            }
        }
        if (cleanMode.get() == CleanMode.BLACKLIST && cleanHotbar.isOn()) {
            for (int slot = 0; slot < Inventories.HOTBAR_SIZE; slot++) {
                if (isJunk(slot)) {
                    return slot;
                }
            }
        }
        return Inventories.NOT_FOUND;
    }

    private boolean isJunk(int slot) {
        GameItem item = Inventories.stackAt(slot);
        if (item.isEmpty() || !isJunk(item)) {
            return false;
        }
        // Whitelist mode keeps armour: putting on the best of it is this module's job.
        return cleanMode.get() == CleanMode.BLACKLIST || ArmorStats.at(slot) == null;
    }

    /** Whether the cleaner would throw this item, by its list alone. */
    private boolean isJunk(GameItem item) {
        if (!cleaner.isOn() || item.isEmpty()) {
            return false;
        }
        boolean listed = cleanItems.contains(item.getId());
        return cleanMode.get() == CleanMode.BLACKLIST ? listed : !listed;
    }

    // ------------------------------------------------------------------ bind

    @Subscribe
    private void onKey(KeyEvent event) {
        Bind bind = cleanerBind.get();
        if (event.isPressed() && !bind.isMouse() && bind.isBound()
                && bind.matches(event.getKey(), event.getModifiers())) {
            flipCleaner();
        }
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        Bind bind = cleanerBind.get();
        if (event.isPressed() && bind.isMouse() && bind.matches(event.getButton(), event.getModifiers())) {
            flipCleaner();
        }
    }

    private void flipCleaner() {
        cleaner.set(!cleaner.isOn());
        Notices.info(Source.MODULE_MESSAGES, "Inventory Manager", cleaner.isOn() ? "Cleaner on" : "Cleaner off");
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    /** When armour may stop the player for a tick to go on. */
    public enum StopMode {
        /** Wait for the player to stand still. */
        NEVER,
        /** Stop only to fill a bare slot: a piece just broke, or was never on. */
        BARE_SLOT,
        /** Stop for any upgrade. */
        ALWAYS
    }

    public enum CleanMode {
        /** Throw what is listed. */
        BLACKLIST,
        /** Throw everything not listed, outside the hotbar; armour is kept. */
        WHITELIST
    }
}
