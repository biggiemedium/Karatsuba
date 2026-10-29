package dev.px.karatsuba.client.module.combat;

import dev.px.combat.place.Clicks;
import dev.px.combat.place.Plan;
import dev.px.combat.place.PlacementPlanner;
import dev.px.combat.place.Shapes;
import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.karatsuba.client.render.CellBoxes;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.PlacementSync;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walls in the cells around your feet with blast-proof blocks, so crystals and
 * beds beside you cannot reach your feet.
 *
 * <h2>Who decides what</h2>
 * <ul>
 *   <li><b>{@code Shapes.around}</b> says which cells: the ring beside every
 *       cell your feet are in, so standing on a block's edge walls two columns
 *       and on a corner four.</li>
 *   <li><b>The placement planner</b> ({@code kit.planner(...)}) says how: which
 *       block to click and which face, most important cell first, with a block
 *       placed underneath first when a cell has nothing beside it to click.
 *       Cells already filled or with someone standing in them are left out.</li>
 *   <li><b>{@code Services.actions()}</b> sends each one: turns to it, swaps to
 *       the block, waits for the server's view of your head to look at it.</li>
 * </ul>
 *
 * <h2>One a tick</h2>
 * The action service sends one right-click a tick, and a strict server checks
 * each against one rotation, so a full surround goes up a block a tick, the
 * planner choosing the next each time. The client shows each block as soon as
 * it is placed, so the next tick's plan already counts it.
 *
 * <h2>Crystals in the way</h2>
 * A crystal touching a surround cell stops the block going in. With
 * {@code Break Crystals} on, one in your reach is broken &mdash; unless the
 * explosion would do more to you than {@code Max Self Damage} or leave you within
 * a heart &mdash; and its cell's block goes out behind the attack in the same
 * tick: the server handles the attack first, so the cell is already free there
 * ({@code CombatKit.asIfGone}, {@code Action.afterAttack}). The attack aims at
 * the nearest point of the crystal, or at the click when one look meets both well
 * inside reach. A crystal hit is inhibited in the kit's log, so it is never hit
 * twice and no longer blocks a cell; a block the client refused only because it
 * still shows that crystal is waited for, not clicked again. See
 * {@code documentation/modules/SURROUND.md}.
 *
 * <h2>Only from the ground</h2>
 * Nothing is placed while you are in the air: mid-jump, the cells beside your
 * feet are a block too high.
 */
@ModuleInfo(name = "Surround", description = "Surrounds your feet with obsidian", category = "Combat")
public final class SurroundModule extends ClientModule {

    private static final String OBSIDIAN = "obsidian";
    private static final String ENDER_CHEST = "ender_chest";

    /** Ticks past the round trip a block that went out unseen is waited for, for jitter. */
    private static final int PENDING_SLACK = 3;

    /**
     * Blocks inside the reach a look shared by an attack and a click must meet the
     * crystal by: room for the server's look to differ a little from the one aimed.
     * As Crystal Aura's; see documentation/knowledge/AIMING.md.
     */
    private static final double AIM_MARGIN = 0.25d;

    private final EnumSetting<Block> block = enumOf("Block", Block.OBSIDIAN)
            .describe("What to surround with");

    private final BooleanSetting fallback = bool("Fallback", true)
            .describe("Use the other block when this one runs out");

    private final BooleanSetting strict = bool("Strict", true)
            .describe("Only faces turned toward you and in sight; off allows any face, as vanilla does");

    private final EnumSetting<Disable> disable = enumOf("Disable", Disable.LEAVE)
            .describe("LEAVE turns off when you walk out of the cells you started in; DONE when it is complete");

    private final BooleanSetting breakCrystals = bool("Break Crystals", true)
            .describe("Break a crystal in a surround cell so the block can go in");

    private final NumberSetting<Double> maxSelf = decimal("Max Self Damage", 6d, 0d, 36d)
            .step(0.5d)
            .describe("Most damage breaking a crystal may do to you")
            .visibleWhen(breakCrystals);

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the cells still to be filled");

    private final ColorSetting renderColor = color("Render Colour", Color.of(255, 85, 85, 200))
            .visibleWhen(render);

    private final BooleanSetting fill = bool("Fill", true)
            .visibleWhen(render);

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.25f, 0.05f, 1f)
            .step(0.05f)
            .describe("The fill's opacity, as a share of the colour's")
            .visibleWhen(() -> render.isOn() && fill.isOn());

    private final NumberSetting<Float> thickness = number("Thickness", 1.2f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(render);

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop cells in when they need filling and fade them out once filled")
            .visibleWhen(render);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, animations);

    // ------------------------------------------------------------------ state

    private CombatKit<?> kit;
    private PlacementPlanner strictPlanner;
    private PlacementPlanner vanillaPlanner;
    /** For asking whether a cell still takes a block. */
    private Clicks clicks;

    private long tick;

    /** Cells whose block went out though the client refused it, and the tick: see {@link #place}. */
    private final Map<Vec3i, Long> sent = new HashMap<>();

    /** The cells your feet were in when it was switched on. */
    private Set<Vec3i> start = new HashSet<>();

    /** Cells still to fill, appearing, shown or fading. */
    private final CellBoxes missing = new CellBoxes();

    private String display = "";

    @Override
    protected void onEnable() {
        kit = Combat.kit();
        strictPlanner = kit.planner(kit.strictStyle()).build();
        vanillaPlanner = kit.planner(kit.vanillaStyle()).build();
        clicks = kit.clicks(kit.strictStyle());
        tick = 0;
        sent.clear();
        start = new HashSet<>();
        Tracked<?> self = Core.entities().getSelf();
        if (self != null) {
            start.addAll(Shapes.feet(self.getBox()));
        }
        missing.clear();
        display = "";
    }

    @Override
    protected void onDisable() {
        missing.clear();
        start.clear();
        sent.clear();
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        if (clicks != null) {
            forgetSent();
        }
        Tracked<?> self = Core.entities().getSelf();
        if (kit == null || self == null || !Players.present()) {
            display = "";
            return;
        }
        Box box = self.getBox();
        List<Vec3i> feet = Shapes.feet(box);
        if (start.isEmpty() && Players.onGround()) {
            // Switched on before there was a world: start from the first cells stood in.
            start.addAll(feet);
        }

        if (disable.get() == Disable.LEAVE && Players.onGround() && !start.isEmpty() && !start.containsAll(feet)) {
            setEnabled(false);
            return;
        }
        if (!Players.onGround()) {
            // The ring beside your feet is a block too high while you are in the air.
            showMissing(List.of());
            return;
        }

        List<Vec3i> cells = Shapes.around(box);
        PlacementPlanner planner = strict.isOn() ? strictPlanner : vanillaPlanner;
        Vec3 eye = Players.eyePosition();
        // A cell whose block went out unseen is the server's until it answers.
        List<Vec3i> plannable = cells.stream().filter(cell -> !sent.containsKey(cell)).toList();
        Plan seen = planner.plan(eye, plannable, Players.serverRotation());

        List<Vec3i> open = cells.stream()
                .filter(cell -> seen.getSkipped().get(cell) != Plan.Skip.FILLED)
                .toList();
        showMissing(open);

        if (open.isEmpty()) {
            display = "";
            // Done once the server has confirmed it, not when the client first shows it:
            // a block refused after switching off would leave a gap.
            if (disable.get() == Disable.DONE && cells.stream().noneMatch(PlacementSync::isPending)) {
                setEnabled(false);
            }
            return;
        }
        display = open.size() + " left";

        // A crystal in a cell is broken, and the cell planned as if it were gone, so
        // its block can go out behind the attack in the same tick.
        Tracked<?> crystal = breakCrystals.isOn() ? crystalIn(seen.getSkipped(), eye) : null;
        Plan plan = crystal == null ? seen
                : kit.asIfGone(crystal.get(), () -> planner.plan(eye, plannable, Players.serverRotation()));

        String item = item();
        if (plan.getSteps().isEmpty() || item == null) {
            if (item == null) {
                display = "No " + (block.get() == Block.OBSIDIAN ? "obsidian" : "ender chests");
            }
            if (crystal != null) {
                breakCrystal(crystal, null);
            }
            return;
        }
        Plan.Step step = plan.getSteps().get(0);
        boolean behind = crystal != null && Box.block(step.getCell().getX(), step.getCell().getY(),
                step.getCell().getZ()).intersects(crystal.getBox());
        if (crystal != null) {
            breakCrystal(crystal, behind ? sharedLook(crystal.getBox(), step.getClick().getHit()) : null);
        }
        place(step, item, behind);
    }

    /**
     * Places one block. Behind an attack on a crystal in its cell, it goes only
     * once that attack has.
     *
     * <p>The client still shows that crystal when the click goes, so it refuses
     * the block and shows nothing, though the packet went and the server, having
     * handled the attack first, places it. Such a cell is left alone until the
     * server's block arrives, rather than clicked again every tick.
     */
    private void place(Plan.Step step, String item, boolean behind) {
        Vec3i cell = step.getCell();
        Action click = Action.click(step.getClick())
                .holding(item)
                .priority(ClaimPriority.HIGHEST);
        if (behind) {
            click.afterAttack();
        }
        Services.actions().submit(this, click.then(outcome -> {
            // Attack callbacks run first: a crystal hit this tick no longer counts as blocking.
            if (outcome == Outcome.REFUSED && !kit.blocking().any(Box.block(cell.getX(), cell.getY(), cell.getZ()))) {
                sent.put(cell, tick);
            }
        }));
    }

    /** Forgets cells the server has answered for, or never did. */
    private void forgetSent() {
        int due = pendingTicks();
        sent.entrySet().removeIf(entry -> tick - entry.getValue() > due || !clicks.isReplaceable(entry.getKey()));
    }

    /** One round trip, rounded up, and a little for jitter: how long the server's answer is waited for. */
    private static int pendingTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 50d) + PENDING_SLACK;
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        setEnabled(false);
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        missing.draw(renderColor.resolve(), fill.isOn(), fillOpacity.getFloat(), thickness.getFloat());
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    // ------------------------------------------------------------- crystals

    /**
     * @return a crystal standing in a cell the planner had to leave out for
     *         someone being in it, within your reach and safe to break; null when
     *         none is. One already hit no longer blocks a cell, so it is never
     *         offered twice
     */
    private Tracked<?> crystalIn(Map<Vec3i, Plan.Skip> skipped, Vec3 eye) {
        double reach = Players.entityReach();
        for (Map.Entry<Vec3i, Plan.Skip> entry : skipped.entrySet()) {
            if (entry.getValue() != Plan.Skip.OCCUPIED) {
                continue;
            }
            Vec3i cell = entry.getKey();
            Box room = Box.block(cell.getX(), cell.getY(), cell.getZ());
            List<Tracked<?>> inside = new ArrayList<>(2);
            kit.crystals().forEachWithin(room.getCenter(), 2d, crystal -> {
                if (crystal.getBox().intersects(room)) {
                    inside.add(crystal);
                }
            });
            for (Tracked<?> crystal : inside) {
                if (crystal.getBox().distanceTo(eye) <= reach && safeToBreak(kit, crystal)) {
                    return crystal;
                }
            }
        }
        return null;
    }

    /**
     * Attacks a crystal: at {@code look} when given, one look shared with the
     * click behind it; otherwise at the point of its box nearest the eyes, which
     * the action service aims at. Never its centre, which at the edge of reach a
     * look meets past the reach. Once sent it is inhibited in the kit's log, so
     * nothing hits it again while the server answers.
     */
    private void breakCrystal(Tracked<?> crystal, Vec3 look) {
        int id = kit.id(crystal.get());
        if (id < 0) {
            return;
        }
        Action attack = Action.attack(id).priority(ClaimPriority.HIGHEST);
        if (look != null) {
            attack.aimAt(look);
        }
        Services.actions().submit(this, attack.then(outcome -> {
            if (outcome == Outcome.SENT) {
                kit.log().attacked(crystal.get(), pendingTicks());
            }
        }));
    }

    /**
     * @return {@code click} when a look at it meets {@code crystal} at least
     *         {@link #AIM_MARGIN} inside your reach, so one look serves the attack
     *         and the click; null when it does not
     */
    private static Vec3 sharedLook(Box crystal, Vec3 click) {
        // From where the server will hold the look: the eyes after this tick's move.
        Vec3 eye = Players.eyePosition().add(Players.velocity());
        double along = crystal.clip(eye, click);
        return !Double.isNaN(along) && along * eye.distanceTo(click) <= Players.entityReach() - AIM_MARGIN
                ? click : null;
    }

    /** @return whether the crystal's explosion leaves you under the cap and above a heart */
    private <E> boolean safeToBreak(CombatKit<E> kit, Tracked<?> crystal) {
        Tracked<E> self = Core.entities().getSelf();
        if (self == null) {
            return false;
        }
        double damage = kit.crystalRules().damage(crystal, self);
        return damage <= maxSelf.getDouble() && damage < kit.vitals().pool(self.get()) - 2d;
    }

    // ---------------------------------------------------------------- items

    /** @return the block to place, the chosen one first; null when there is none to hand */
    private String item() {
        String first = block.get() == Block.OBSIDIAN ? OBSIDIAN : ENDER_CHEST;
        String second = block.get() == Block.OBSIDIAN ? ENDER_CHEST : OBSIDIAN;
        if (has(first)) {
            return first;
        }
        return fallback.isOn() && has(second) ? second : null;
    }

    private static boolean has(String item) {
        return Inventories.hotbarHas(item) || Inventories.offhandHolds(item);
    }

    // --------------------------------------------------------------- render

    private void showMissing(List<Vec3i> cells) {
        missing.update(render.isOn() ? cells : List.of(), animations.isOn());
    }

    public enum Block {
        OBSIDIAN,
        ENDER_CHEST
    }

    public enum Disable {
        /** Stay on, and follow you wherever you stand. */
        NEVER,
        /** Turn off when you walk out of the cells you started in. */
        LEAVE,
        /** Turn off once every cell is filled. */
        DONE
    }
}
