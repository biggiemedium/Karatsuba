package dev.px.karatsuba.client.module.mining;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec3;
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
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.module.mining.smartmine.MineQueue;
import dev.px.karatsuba.client.module.mining.render.MiningBox;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;
import dev.px.karatsuba.client.render.CellBoxes;
import dev.px.karatsuba.client.render.ThemeColors;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.dig.DigRequest;
import dev.px.karatsuba.client.service.dig.Sightline;
import dev.px.karatsuba.client.settings.ClientSettings;
import dev.px.karatsuba.client.settings.interaction.InteractionSettings;

import java.util.List;
import java.util.function.Predicate;

/**
 * Mines a queue of blocks you mark, one at a time, while you get on with something
 * else.
 *
 * <h2>Marking</h2>
 * While it is on, a left click on a block queues it instead of starting to break
 * it; with {@code Vein} the whole vein it belongs to is queued. A right click on a
 * queued block, the one being mined included, takes it out of the queue. Neither
 * click reaches the server: no dig is started, nothing is placed or opened. With
 * {@code Cancel Animation} on the arm does not swing either, so nobody watching
 * sees a click at all; off, it swings as a click would. Holding the attack key
 * never breaks a block by hand while it is mining.
 *
 * <p>Whenever a queued block is in reach and can be seen, the head turns to it
 * with a silent rotation, the best tool comes out, and it is mined. Walk more than
 * {@code Dissolve Range} from a queued block and it leaves the queue.
 *
 * <h2>Stepping aside</h2>
 * The moment you do something yourself, it lets go of everything: pick a hotbar
 * slot, hit an entity, use an item, place or open something with a right click
 * that is not an unmark. The hotbar is yours (the slot you picked stays picked),
 * the head is yours, and every click does what it normally does, left click on a
 * block breaking it by hand. It carries on with the queue once you have done none
 * of that for {@code Resume After} seconds. So in a fight, crystals, gapples, pearls
 * and swords all work as if it were off, and the queue waits.
 *
 * <h2>Dug by the dig service</h2>
 * This module decides <em>which</em> block; {@code Services.digging()} digs it, the
 * same way it digs for anything else: the game's own breaking, the tool held
 * openly, the head on the block when Grim reads it, and Client Settings &gt; Mining
 * (Quiet Mining, Early Break) applied. See {@code DigService} for how, and why each
 * part is there. Walking under the silent rotation needs the Move Fix, and without
 * it the ArrayList says so.
 *
 * <h2>Giving way</h2>
 * Besides stepping aside for you: a container, an item in use, or a module with a
 * higher claim on the head or the hotbar makes the queue wait.
 *
 * <h2>Drawn</h2>
 * Queued blocks pop in when marked and fade out once mined, unmarked or left behind
 * ({@link CellBoxes}). The block being mined grows out of its middle as the dig
 * goes, from the Start colour to the Finish colour ({@link MiningBox}).
 */
@ModuleInfo(name = "Smart Mine", description = "Mines blocks you queue up, one at a time, at the game's own speed",
        category = "Mining")
public final class SmartMineModule extends ClientModule {

    public enum Select {
        /** Just the block under the crosshair. */
        BLOCK,
        /** The block and every block of its kind touching it. */
        VEIN
    }

    public enum Order {
        /** The nearest marked block that can be mined from here. */
        NEAREST,
        /** The first marked block that can be mined from here. */
        MARKED
    }

    private static final int TICKS_PER_SECOND = 20;

    private final BooleanSetting cancelAnimation = bool("Cancel Animation", true)
            .describe("Don't swing your arm when you mark or unmark a block, so nobody sees the click");

    private final EnumSetting<Select> select = enumOf("Select", Select.BLOCK)
            .describe("BLOCK marks one block; VEIN marks every touching block of the same kind too");

    private final NumberSetting<Integer> veinLimit = integer("Vein Limit", 32, 2, 128)
            .describe("The most blocks one vein mark adds")
            .visibleWhen(() -> select.get() == Select.VEIN);

    private final EnumSetting<Order> order = enumOf("Order", Order.NEAREST)
            .describe("NEAREST mines the closest marked block first; MARKED goes in the order you marked them");

    private final NumberSetting<Double> dissolveRange = decimal("Dissolve Range", 8d, 4d, 16d)
            .step(0.5d)
            .describe("A queued block this far from you leaves the queue");

    private final NumberSetting<Integer> resumeAfter = integer("Resume After", 3, 1, 30)
            .describe("Seconds without you switching slots, hitting or using anything before it mines again");

    private final BooleanSetting autoTool = bool("Auto Tool", true)
            .describe("Hold the fastest tool in the hotbar for each block");

    private final BooleanSetting keepQueue = bool("Keep Queue", true)
            .describe("Keep the marked blocks when the module is switched off; off clears them");

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the marked blocks, and the one being mined filling up");

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw in the client theme's colours instead of the ones set here")
            .visibleWhen(render);

    private final ColorSetting queuedColor = color("Queued", Color.of(85, 170, 255, 160))
            .visibleWhen(() -> render.isOn() && !themeColours.isOn());

    private final ColorSetting startColor = color("Start", Color.of(255, 70, 70, 220))
            .describe("The block being mined, just started")
            .visibleWhen(() -> render.isOn() && !themeColours.isOn());

    private final ColorSetting finishColor = color("Finish", Color.of(70, 255, 110, 220))
            .describe("The block being mined, about to break")
            .visibleWhen(() -> render.isOn() && !themeColours.isOn());

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.3f, 0.05f, 1f)
            .step(0.05f)
            .describe("The mining box's fill opacity, as a share of the colour's")
            .visibleWhen(render);

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(render);

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop blocks in when marked, fade them out when mined, fade the mining box in and out")
            .visibleWhen(render);

    private final BooleanSetting throughWalls = bool("Through Walls", true)
            .visibleWhen(render);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, themeColours, queuedColor, startColor, finishColor, fillOpacity, thickness,
                    animations, throughWalls);

    // ------------------------------------------------------------------ state

    private final MineQueue queue = new MineQueue();
    private final Sightline sightline = new Sightline();

    /** The marked blocks waiting their turn, popped in and faded out. */
    private final CellBoxes queuedBoxes = new CellBoxes();

    /** The block being mined, growing as the dig goes. */
    private final MiningBox miningBox = new MiningBox();

    /** The block being worked on, kept until it is mined or can no longer be. */
    private Vec3i target;

    /** Counts this module's ticks, for the pause after you take over. */
    private long tick;

    /** While {@link #tick} is below this, you have the controls and the queue waits. */
    private long pausedUntil;

    private String display = "";

    @Override
    protected void onEnable() {
        target = null;
        sightline.clear();
        tick = 0L;
        pausedUntil = 0L;
    }

    @Override
    protected void onDisable() {
        stop();
        if (!keepQueue.isOn()) {
            queue.clear();
        }
        queuedBoxes.clear();
        miningBox.clear();
    }

    /** Lets go of everything; the game cancels a dig in progress at its next input handling. */
    private void stop() {
        target = null;
        sightline.clear();
        Services.digging().release(this);
        Core.rotations().release(this);
        Services.inventory().release(this);
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        queue.clear();
        stop();
        queuedBoxes.clear();
        miningBox.clear();
    }

    // ------------------------------------------------------------------ queue

    /**
     * Left click on a block queues it, right click on a queued block unqueues it;
     * neither reaches the game. Any other click of yours means you are doing
     * something else, and it steps aside.
     */
    @Subscribe
    private void onClick(ClickEvent event) {
        if (paused()) {
            // Yours, as normal; and still busy, so the pause runs on.
            if (event.isByPlayer()) {
                pause();
            }
            return;
        }
        Vec3i block = event.getPosition();
        if (event.isBlock() && event.getButton() == ClickEvent.Button.ATTACK) {
            // Every left click on a block is a mark while mining, even one on a
            // block that cannot be queued: the player never mines by hand here.
            mark(block);
        } else if (event.isBlock() && queue.remove(block)) {
            if (block.equals(target)) {
                stop();
            }
        } else {
            // Not ours: the click hits, places or uses as normal, and you have the controls.
            if (event.isByPlayer()) {
                pause();
            }
            return;
        }
        event.cancel();
        if (!cancelAnimation.isOn()) {
            Interactions.swing(Interactions.Hand.MAIN, Interactions.Swing.CLIENT);
        }
    }

    /** You picked a slot yourself: it is yours, and so is everything else for a while. */
    @Subscribe
    private void onHotbarPick(HotbarPickEvent event) {
        pause();
    }

    /**
     * Lets go of the hotbar, the head and the dig at once, and waits. Releasing the
     * hotbar now, before the tick's end, is what keeps the slot you just picked:
     * there is no claim left to select the tool again.
     */
    private void pause() {
        pausedUntil = tick + (long) resumeAfter.getInt() * TICKS_PER_SECOND;
        stop();
    }

    private boolean paused() {
        return tick < pausedUntil;
    }

    private void mark(Vec3i block) {
        if (queue.contains(block)) {
            return;
        }
        if (select.get() == Select.VEIN) {
            Vec3 eyes = Players.eyePosition();
            double range = dissolveRange.getDouble();
            int added = queue.addVein(block, veinLimit.getInt(),
                    cell -> cell.center().squaredDistanceTo(eyes) <= range * range);
            if (added > 1) {
                Notices.info(Source.MODULE_MESSAGES, "Smart Mine", "Queued a vein of " + added);
            }
        } else {
            queue.add(block);
        }
    }

    // ------------------------------------------------------------------- mine

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        if (!Players.present()) {
            return;
        }
        queue.prune(Players.eyePosition(), dissolveRange.getDouble());
        if (paused()) {
            // Mining or eating by hand is still you being busy.
            if (Inputs.playerHolds(Inputs.Control.ATTACK) || Inputs.playerHolds(Inputs.Control.USE)) {
                pause();
            }
            display = queue.isEmpty() ? "" : "Paused";
            return;
        }
        // Holding the attack key after a mark must not go on to break the block by hand.
        Digging.holdOffHand();
        InteractionSettings rules = ClientSettings.interaction();
        display = queue.isEmpty() ? "" : rules.moveFix() ? String.valueOf(queue.size()) : "Move Fix off";

        // A container or an item in use leaves the game's breaking to the game this tick.
        if (queue.isEmpty() || Players.inContainer() || Players.usingItem()) {
            stop();
            return;
        }

        Vec3 eyes = Players.eyePosition();
        double reach = Players.blockReach();
        if (target == null || !queue.contains(target) || sightline.find(target, eyes, reach) == null) {
            sightline.clear();
            target = pick(eyes, reach);
        }
        if (target == null) {
            stop();
            return;
        }
        // The fastest tool in the hotbar, or the hand when nothing beats it.
        int tool = autoTool.isOn() ? Inventories.bestToolSlotFor(Blocks.idAt(target)) : Inventories.NOT_FOUND;
        Services.digging().dig(this, DigRequest.of(target).tool(tool).priority(ClaimPriority.NORMAL));
    }

    /** @return the next block to mine: in reach, in sight, by the chosen order; null for none */
    private Vec3i pick(Vec3 eyes, double reach) {
        Sightline probe = new Sightline();
        // The distance first: it is cheap, and rules out most of a long queue
        // before a single ray is cast.
        Predicate<Vec3i> usable = block -> Blocks.outline(block).distanceTo(eyes) <= reach
                && probe.find(block, eyes, reach) != null;
        return order.get() == Order.NEAREST ? queue.nearest(eyes, usable) : queue.first(usable);
    }

    // ----------------------------------------------------------------- render

    /**
     * The tick's half of the drawing, once this tick's queue and target are settled:
     * which blocks wait, and which is being mined. The block being mined leaves the
     * queued boxes, so it is drawn once, in its own box.
     */
    @Subscribe(stage = Stage.POST)
    private void onTickEnd(TickEvent event) {
        boolean animated = animations.isOn();
        boolean shown = render.isOn() && Players.present();
        // A block that broke this tick is no longer being mined: its box leaves whole.
        Vec3i mining = shown && target != null && !Blocks.isAir(target) ? target : null;
        List<Vec3i> waiting = shown
                ? queue.all().stream().filter(block -> !block.equals(mining)).toList()
                : List.of();
        queuedBoxes.update(waiting, animated);
        float progress = mining != null ? Math.max(0f, Digging.progress(mining)) : 0f;
        miningBox.update(mining, progress, animated);
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (!render.isOn()) {
            return;
        }
        boolean themed = themeColours.isOn();
        Render.setDepthTest(!throughWalls.isOn());
        try {
            queuedBoxes.draw(ThemeColors.of(queuedColor, themed, 0f), false, 0f, thickness.getFloat());
            miningBox.draw(event.getPartialTicks(),
                    progress -> ThemeColors.between(startColor, finishColor, themed, progress),
                    fillOpacity.getFloat(), thickness.getFloat());
        } finally {
            Render.setDepthTest(true);
        }
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }
}
