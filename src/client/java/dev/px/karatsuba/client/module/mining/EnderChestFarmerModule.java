package dev.px.karatsuba.client.module.mining;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.flow.FlowHandle;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec3i;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.ClickEvent;
import dev.px.karatsuba.adapter.event.game.player.HotbarPickEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Digging;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.automation.Automation;
import dev.px.karatsuba.client.automation.plan.EnderChestFarm;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;
import dev.px.karatsuba.client.render.ThemeColors;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Turns ender chests into obsidian: places one in front of you, breaks it with a
 * pickaxe that has no Silk Touch (eight obsidian each), and places the next, until
 * it runs out of something.
 *
 * <p>Lambda's Ender Chest Farmer is the model (one cell, place or break whichever
 * it holds, stop on a full inventory, no chests or no pickaxe); the hands are
 * Karatsuba's own, so it is Grim-safe the same way every placing and mining module is.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Assist</b>: you stand still beside the cell and it farms. Walking out of
 *       reach ends it.</li>
 *   <li><b>Autonomous</b>: it also walks. Knocked off the spot, it goes back; obsidian
 *       that bounced out of pickup reach is fetched, and it returns to farm on.</li>
 * </ul>
 * Both run the same plan, {@link EnderChestFarm}, as a Core flow: this module only
 * picks the cell, starts it, steps aside for you, and draws. The plan is made of the
 * automation's steps and digs through {@code Services.digging()}, so Client Settings
 * &gt; Mining (Quiet Mining, Early Break) applies here as everywhere.
 *
 * <h2>Where</h2>
 * The cell in front of your feet, the way you face when you switch it on. It must
 * be empty (or already an ender chest), with something to place against.
 *
 * <h2>When it stops</h2>
 * No ender chests left beyond {@code Keep}, no pickaxe without Silk Touch, no room
 * for more obsidian, {@code Stop At} reached, the cell blocked, or (Assist) you out
 * of reach of it. Each says why, and switching off reports how much obsidian it
 * made. Pick a hotbar slot or click anything yourself (or, Autonomous, walk) and it
 * waits until you have stopped.
 */
@ModuleInfo(name = "Ender Chest Farmer", description = "Places and breaks ender chests in front of you for obsidian",
        category = "Mining")
public final class EnderChestFarmerModule extends ClientModule {

    public enum Mode {
        /** You stand there; it farms. */
        ASSIST,
        /** It also walks: back to the spot, and for stray drops. */
        AUTONOMOUS
    }

    /** Ticks to wait after you take over before carrying on. */
    private static final int YIELD_TICKS = 60;

    private static final double SURFACE_OFFSET = 0.002d;

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.ASSIST)
            .describe("ASSIST farms while you stand there; AUTONOMOUS also walks back to the spot and fetches stray drops");

    private final NumberSetting<Integer> keep = integer("Keep", 1, 0, 64)
            .describe("Ender chests to keep: it stops before placing these");

    private final NumberSetting<Integer> stopAt = integer("Stop At", 0, 0, 2304)
            .step(8)
            .describe("Stop once you carry this much obsidian; 0 farms until something runs out");

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Outline the farm cell, and the chest growing as it is broken");

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw in the client theme's colours instead of the ones set here")
            .visibleWhen(render);

    private final ColorSetting cellColor = color("Farm Colour", Color.of(155, 0, 255, 200))
            .describe("The farm cell's outline")
            .visibleWhen(() -> render.isOn() && !themeColours.isOn());

    private final ColorSetting startColor = color("Start", Color.of(255, 70, 70, 220))
            .describe("The chest being broken, just started")
            .visibleWhen(() -> render.isOn() && !themeColours.isOn());

    private final ColorSetting finishColor = color("Finish", Color.of(70, 255, 110, 220))
            .describe("The chest being broken, about to go")
            .visibleWhen(() -> render.isOn() && !themeColours.isOn());

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.3f, 0.05f, 1f)
            .step(0.05f)
            .describe("The breaking box's fill opacity, as a share of the colour's")
            .visibleWhen(render);

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(render);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, themeColours, cellColor, startColor, finishColor, fillOpacity, thickness);

    // ------------------------------------------------------------------ state

    private final MiningBox breakingBox = new MiningBox();

    /** The farm running, or null while off. */
    private FlowHandle farm;

    /** The farm cell, or null while off. */
    private Vec3i cell;

    /** Obsidian carried when it started, for the count. */
    private int startObsidian;

    private long tick;
    private long yieldUntil;

    private String display = "";

    @Override
    protected void onEnable() {
        cell = null;
        farm = null;
        tick = 0L;
        yieldUntil = 0L;
        display = "";
        if (!Players.present()) {
            stopWith("Join a world first");
            return;
        }
        Vec3i stand = Players.blockPosition();
        Vec3i ahead = stand.offset(Direction.fromYaw(Players.rotation().getYaw()));
        if (!ENDER_CHEST_OR_EMPTY.test(ahead)) {
            stopWith("The block in front of you must be empty or an ender chest");
            return;
        }
        if (EnderChestFarm.pickaxe() == Inventories.NOT_FOUND) {
            stopWith("You need a pickaxe without Silk Touch");
            return;
        }
        cell = ahead;
        startObsidian = Inventories.count(EnderChestFarm.OBSIDIAN);
        EnderChestFarm.Options options = new EnderChestFarm.Options(ahead, stand, keep::getInt, stopAt::getInt,
                () -> mode.get() == Mode.AUTONOMOUS);
        // Below the combat modules, as Auto Eat's meals are: a fight takes the head and
        // the hotbar, and the farm waits.
        farm = Core.flows().start("Ender Chest Farmer",
                EnderChestFarm.build(Automation.get().getNavigation(), options), ClaimPriority.NORMAL);
    }

    @Override
    protected void onDisable() {
        if (farm != null) {
            farm.cancel();
            farm = null;
        }
        if (cell != null) {
            int made = Inventories.count(EnderChestFarm.OBSIDIAN) - startObsidian;
            Notices.info(Source.MODULE_MESSAGES, "Ender Chest Farmer", "Made " + Math.max(0, made) + " obsidian");
        }
        cell = null;
        breakingBox.clear();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        disable();
    }

    // ---------------------------------------------------------------- yielding

    /** You did something yourself: wait for you. */
    @Subscribe
    private void onClick(ClickEvent event) {
        if (event.isByPlayer()) {
            yieldToPlayer();
        }
    }

    @Subscribe
    private void onHotbarPick(HotbarPickEvent event) {
        yieldToPlayer();
    }

    /** Pauses the farm, which lets go of the head, hotbar, dig and keys; it resumes once you are done. */
    private void yieldToPlayer() {
        yieldUntil = tick + YIELD_TICKS;
        if (farm != null && farm.getState() == FlowHandle.State.RUNNING) {
            farm.pause();
        }
    }

    // ------------------------------------------------------------------- farm

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        if (farm == null || !Players.present()) {
            return;
        }
        if (farm.isDone()) {
            // It only ends by failing, with the reason it stopped.
            stopWith(EnderChestFarm.explain(farm.getReason()));
            return;
        }
        // A screen open is you busy; and Autonomous walks, so your hand on the
        // movement keys is you taking over too.
        if (Players.inContainer() || (mode.get() == Mode.AUTONOMOUS && Inputs.playerMoving())) {
            yieldToPlayer();
        }
        if (tick < yieldUntil) {
            display = "Paused";
            return;
        }
        if (farm.getState() == FlowHandle.State.PAUSED) {
            farm.resume();
        }
        int made = Inventories.count(EnderChestFarm.OBSIDIAN) - startObsidian;
        display = "+" + Math.max(0, made);
    }

    /** Says why and switches off. */
    private void stopWith(String reason) {
        Notices.warn(Source.MODULE_MESSAGES, "Ender Chest Farmer", reason);
        disable();
    }

    private static final java.util.function.Predicate<Vec3i> ENDER_CHEST_OR_EMPTY =
            at -> EnderChestFarm.ENDER_CHEST.equals(Blocks.idAt(at)) || Blocks.isReplaceable(at);

    // ----------------------------------------------------------------- render

    @Subscribe(stage = Stage.POST)
    private void onTickEnd(TickEvent event) {
        Vec3i breaking = cell != null && EnderChestFarm.ENDER_CHEST.equals(Blocks.idAt(cell)) ? cell : null;
        float progress = breaking != null ? Math.max(0f, Digging.progress(breaking)) : 0f;
        breakingBox.update(render.isOn() ? breaking : null, progress, true);
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (!render.isOn() || cell == null) {
            return;
        }
        boolean themed = themeColours.isOn();
        Render.boxOutline(cell.toBox().expand(SURFACE_OFFSET), thickness.getFloat(),
                ThemeColors.of(cellColor, themed, 0f));
        breakingBox.draw(event.getPartialTicks(),
                progress -> ThemeColors.between(startColor, finishColor, themed, progress),
                fillOpacity.getFloat(), thickness.getFloat());
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }
}
