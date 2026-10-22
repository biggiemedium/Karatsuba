package dev.px.karatsuba.client.automation.plan;

import dev.px.core.flow.Condition;
import dev.px.core.flow.Flow;
import dev.px.core.flow.Span;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec3i;
import dev.px.core.navigation.Goal;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.karatsuba.client.automation.step.Dig;
import dev.px.karatsuba.client.automation.step.PickUpDrops;
import dev.px.karatsuba.client.automation.step.PlaceBlock;
import dev.px.karatsuba.client.automation.step.ToHotbar;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Ender chests into obsidian, as a flow: place one in the farm cell, dig it with a
 * pickaxe that has no Silk Touch (eight obsidian), again, until something runs out.
 *
 * <p>One plan for every front end: Ender Chest Farmer runs it, in either mode, and
 * a task will. It names no module; it is built from the automation's steps, and
 * digs through the dig service, so Client Settings &gt; Mining applies.
 *
 * <h2>Each round</h2>
 * <ol>
 *   <li><b>Can it go on?</b> Fails, ending the farm with the reason, when the cell
 *       is blocked, there is no pickaxe, no room for obsidian, {@code Stop At} is
 *       reached, or (Assist) the cell is out of reach.</li>
 *   <li><b>Autonomous only:</b> back onto the stand cell if pushed off it, and
 *       fetch obsidian that landed out of pickup reach, then back again.</li>
 *   <li><b>The pickaxe</b> brought to the hotbar if it is only in the inventory.</li>
 *   <li><b>Dig</b> the chest if there is one; otherwise <b>place</b> one, bringing
 *       a chest to the hotbar first, unless only the ones to keep are left.</li>
 * </ol>
 */
public final class EnderChestFarm {

    public static final String ENDER_CHEST = "minecraft:ender_chest";
    public static final String OBSIDIAN = "minecraft:obsidian";

    /** How far from the farm cell a stray drop is fetched from. */
    private static final double COLLECT_RADIUS = 6d;

    /** Ticks a drop lies before it counts as stray: one beside the player is picked up well before. */
    private static final int STRAY_AGE = 30;

    /** The name of the step that checks whether the farm can go on: a failure there is a stop, not a fault. */
    private static final String CAN_GO_ON = "can it go on";

    /** Longer than an ender chest takes with any pickaxe. */
    private static final Span DIG_LIMIT = Span.seconds(20);

    /**
     * What the farm is told.
     *
     * @param cell       where the chests go
     * @param stand      where the player stands to farm it
     * @param keep       ender chests never to place, read live
     * @param stopAt     obsidian to stop at, 0 for no limit, read live
     * @param autonomous whether to walk: back to the stand, and for stray drops
     */
    public record Options(Vec3i cell, Vec3i stand, IntSupplier keep, IntSupplier stopAt, BooleanSupplier autonomous) {
    }

    private EnderChestFarm() {
    }

    /** @return the farm, as one step that runs until it fails with the reason it stopped */
    public static Step build(Navigation navigation, Options options) {
        Vec3i cell = options.cell();
        Step canGoOn = Flow.step(CAN_GO_ON).tick(c -> {
            String why = whyStop(options);
            return why == null ? Status.DONE : c.fail(why);
        });

        Step backToStand = optional(c -> options.autonomous().getAsBoolean() && !onStand(options),
                navigation.to(Goal.block(options.stand())).named("back to the farm"));

        Step collect = optional(c -> options.autonomous().getAsBoolean()
                        && PickUpDrops.any(OBSIDIAN, cell.center(), COLLECT_RADIUS, STRAY_AGE),
                Flow.sequence(
                        PickUpDrops.of(navigation, OBSIDIAN, cell::center, COLLECT_RADIUS, STRAY_AGE),
                        navigation.to(Goal.block(options.stand())).named("back to the farm")));

        Step pickaxe = new ToHotbar("a pickaxe", EnderChestFarm::pickaxe, ENDER_CHEST);

        Step dig = Dig.block(cell, EnderChestFarm::pickaxe)
                .when(c -> ENDER_CHEST.equals(Blocks.idAt(cell)))
                .timeout(DIG_LIMIT);

        Step place = Flow.sequence(
                        new ToHotbar("an ender chest", EnderChestFarm::enderChest, "_pickaxe"),
                        new PlaceBlock(cell, ENDER_CHEST))
                .when(c -> Blocks.isReplaceable(cell))
                .retry(2);

        return Flow.loop(Flow.sequence(canGoOn, backToStand, collect, pickaxe, Flow.firstOf(dig, place)))
                .named("farm ender chests");
    }

    /**
     * @return the farm flow's failure reason, put for the player: a stop the plan
     *         chose ("Inventory full") as it is, anything else as the flow says it
     */
    public static String explain(String reason) {
        if (reason == null || reason.isEmpty()) {
            return "Stopped";
        }
        int at = reason.lastIndexOf(CAN_GO_ON + ": ");
        return at < 0 ? reason : reason.substring(at + CAN_GO_ON.length() + 2);
    }

    /** @return why the farm cannot go on, or null while it can */
    public static String whyStop(Options options) {
        Vec3i cell = options.cell();
        boolean chest = ENDER_CHEST.equals(Blocks.idAt(cell));
        if (!chest && !Blocks.isReplaceable(cell)) {
            return "The farm cell is blocked";
        }
        if (pickaxe() == Inventories.NOT_FOUND) {
            return "No pickaxe without Silk Touch";
        }
        if (!Inventories.hasRoomFor(OBSIDIAN)) {
            return "Inventory full";
        }
        int stopAt = options.stopAt().getAsInt();
        if (stopAt > 0 && Inventories.count(OBSIDIAN) >= stopAt) {
            return "Reached " + stopAt + " obsidian";
        }
        if (!chest && Inventories.count(ENDER_CHEST) <= options.keep().getAsInt()) {
            return options.keep().getAsInt() == 0 ? "Out of ender chests" : "Only the ender chests to keep are left";
        }
        if (!options.autonomous().getAsBoolean()
                && Blocks.outline(cell).distanceTo(Players.eyePosition()) > Players.blockReach()) {
            return "Out of reach of the farm cell";
        }
        return null;
    }

    /** @return the fastest carried tool that gets an ender chest's obsidian (no Silk Touch), or NOT_FOUND */
    public static int pickaxe() {
        return Inventories.bestHarvestingTool(ENDER_CHEST, false);
    }

    /** @return where an ender chest is: the hotbar if there is one there, else the inventory; NOT_FOUND for none */
    private static int enderChest() {
        int hotbar = Inventories.findInHotbar(ENDER_CHEST);
        return hotbar != Inventories.NOT_FOUND ? hotbar : Inventories.findInInventory(ENDER_CHEST);
    }

    private static boolean onStand(Options options) {
        return Players.blockPosition().equals(options.stand());
    }

    /** @return {@code step} when {@code condition} holds as it would start, and nothing otherwise, never failing for it */
    private static Step optional(Condition condition, Step step) {
        return Flow.firstOf(step.when(condition), Flow.action("nothing to do", c -> { }));
    }
}
