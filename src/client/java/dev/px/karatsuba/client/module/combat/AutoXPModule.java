package dev.px.karatsuba.client.module.combat;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec2;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.item.Mending;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Throws experience bottles at your feet to mend your gear.
 *
 * <h2>Only what experience can fix</h2>
 * Mending repairs a damaged item with Mending in an armour slot or a hand, and
 * nothing else. So this throws while the most worn of <em>those</em> is below
 * {@code Start Below} and stops once it reaches {@code Repair To}. An item
 * without Mending is ignored, rather than kept throwing for forever. The gap
 * between the two thresholds stops it starting and stopping every time a point
 * of durability comes and goes.
 *
 * <h2>No wasted bottles</h2>
 * Experience still lying on the ground is mending that has not happened yet.
 * With {@code Max Orbs} set, it waits while that many orbs are around you rather
 * than throwing more on top of them.
 *
 * <h2>Through the action service</h2>
 * Each throw is an {@code Action}: the service swaps to a bottle (or uses one in
 * the offhand), turns you to look down &mdash; keeping your yaw, so only your
 * pitch moves &mdash; waits until the server has that rotation, and sends the
 * use with it. Since 1.21 the throw goes where the packet's rotation says, so the
 * bottle lands at your feet even with a silent rotation. Busy hands, open
 * containers and the profile's delays are the service's, as for every module.
 *
 * <p>By default it yields the right-click to combat modules: placing a crystal
 * or a surround block comes first, and a bottle goes out on any tick they leave
 * free. {@code Over Combat} turns that round.
 */
@ModuleInfo(name = "Auto XP", description = "Throws experience bottles to mend your gear", category = "Combat")
public final class AutoXPModule extends ClientModule {

    private static final String BOTTLE = "experience_bottle";

    private final NumberSetting<Integer> startBelow = integer("Start Below", 80, 1, 100)
            .describe("Start throwing when a Mending item drops to this percent durability");

    private final NumberSetting<Integer> repairTo = integer("Repair To", 100, 1, 100)
            .describe("Stop throwing once every Mending item is at least this percent");

    private final BooleanSetting hands = bool("Held Items", true)
            .describe("Count Mending items in your hands, not only your armour");

    private final NumberSetting<Integer> maxOrbs = integer("Max Orbs", 10, 0, 50)
            .describe("Wait while this many experience orbs are around you; 0 never waits");

    private final NumberSetting<Integer> pitch = integer("Pitch", 90, 45, 90)
            .describe("How far down to look when throwing; 90 is straight at your feet");

    private final NumberSetting<Integer> delay = integer("Delay", 0, 0, 10)
            .describe("Ticks between bottles");

    private final BooleanSetting overCombat = bool("Over Combat", false)
            .describe("Throw ahead of combat modules' right-clicks instead of after them");

    private final BooleanSetting autoDisable = bool("Auto Disable", false)
            .describe("Turn off once everything is repaired");

    /** Whether it is throwing: between reaching Start Below and reaching Repair To. */
    private boolean repairing;

    private long tick;
    private long lastThrow = Long.MIN_VALUE / 2;

    private String display = "";

    @Override
    protected void onEnable() {
        repairing = false;
        tick = 0;
        lastThrow = Long.MIN_VALUE / 2;
        display = "";
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        if (!Players.present()) {
            display = "";
            return;
        }
        int bottles = Inventories.count(BOTTLE);
        float lowest = Mending.lowest(hands.isOn());
        int percent = Math.round(lowest * 100f);
        display = bottles + " | " + percent + "%";

        if (!Mending.any(hands.isOn())) {
            repairing = false;
            display = "No Mending";
            return;
        }
        if (percent >= repairTo.getInt()) {
            if (repairing && autoDisable.isOn()) {
                setEnabled(false);
            }
            repairing = false;
            return;
        }
        if (percent <= startBelow.getInt()) {
            repairing = true;
        }
        if (!repairing || bottles == 0 || tick - lastThrow < delay.getInt()) {
            return;
        }
        if (maxOrbs.getInt() > 0 && Entities.experienceOrbsNear(3d) >= maxOrbs.getInt()) {
            // Mending still to be collected from the ground.
            return;
        }

        // Down at the feet, keeping the yaw the server already has: only the
        // pitch moves, which is all a throw at the feet needs.
        Vec2 down = Vec2.rotation(Players.serverRotation().getYaw(), pitch.getInt());
        Services.actions().submit(this, Action.use()
                .holding(BOTTLE)
                .look(down)
                .priority(overCombat.isOn() ? ClaimPriority.HIGHEST : ClaimPriority.LOW)
                .then(outcome -> {
                    if (outcome == Outcome.SENT) {
                        lastThrow = tick;
                    }
                }));
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }
}
