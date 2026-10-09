package dev.px.karatsuba.client.module.combat;

import dev.px.combat.hole.FillOption;
import dev.px.combat.hole.HoleFill;
import dev.px.combat.hole.Hole;
import dev.px.combat.place.Clicks;
import dev.px.combat.place.PlacementPlanner;
import dev.px.combat.place.PlacementStyle;
import dev.px.combat.place.Plan;
import dev.px.combat.search.rule.Reach;
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
import dev.px.karatsuba.client.module.combat.render.CellBoxes;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.target.TargetSelector;
import dev.px.core.world.Obstructions;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.PlacementResult;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fills holes before an enemy can stand in them, with blocks or with cobwebs.
 *
 * <h2>Two ways to choose holes</h2>
 * <ul>
 *   <li><b>{@link Mode#PREDICT}</b> fills the holes an enemy is about to get into.
 *       The combat library's {@code HoleFill} predicts each enemy with
 *       {@code Core.prediction()}, weighs a "heads for it" future per hole against
 *       how they have actually been moving, and offers the holes they are likely
 *       to reach &mdash; timed against how long your fill takes to land: SAFE when
 *       it lands before they could possibly get there, RACE before they likely
 *       do, LATE after. Holes you are standing in or heading for, holes a friend
 *       is heading for, and holes near you (Self Radius) are kept.</li>
 *   <li><b>{@link Mode#ALL}</b> fills every hole in reach that nobody is standing
 *       in, nearest first, leaving the ones within Self Radius of you alone. With
 *       Near Enemies on, only holes close to an enemy.</li>
 * </ul>
 *
 * <h2>Blocks or webs</h2>
 * Blocks go into the hole's floor cells. Webs too, and with {@code Stack} a
 * second web over each: an enemy who drops in is held by both. A web has no
 * collision box, so it may go where a player already stands; only you are
 * never webbed.
 *
 * <h2>Sending</h2>
 * The placement planner picks each click and any support under it, and
 * {@code Services.actions()} sends one a tick &mdash; turned to, swapped to and
 * checked against the server's view of your head, as every module's are.
 */
@ModuleInfo(name = "Hole Fill", description = "Fills holes enemies are about to use, with blocks or webs",
        category = "Combat")
public final class HoleFillModule extends ClientModule {

    private static final String[] BLOCKS = {"obsidian", "crying_obsidian", "ender_chest"};
    private static final String WEB = "cobweb";

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.PREDICT)
            .describe("PREDICT fills holes an enemy is heading for; ALL fills every hole in reach");

    private final EnumSetting<Fill> fillWith = enumOf("Fill With", Fill.BLOCKS)
            .describe("BLOCKS uses obsidian, then crying obsidian, then ender chests; WEBS uses cobwebs");

    private final BooleanSetting stack = bool("Stack", true)
            .describe("Place a second web over each, two high")
            .visibleWhen(() -> fillWith.get() == Fill.WEBS);

    private final BooleanSetting strict = bool("Strict", true)
            .describe("Only faces turned toward you and in sight; off allows any face, as vanilla does");

    private final NumberSetting<Double> placeRange = decimal("Place Range", 4.5d, 1d, 6d)
            .step(0.1d)
            .describe("To the click; the server's block reach caps it");

    private final NumberSetting<Double> selfRadius = decimal("Self Radius", 2d, 0d, 6d)
            .step(0.5d)
            .describe("Never fill a hole this close to you, so you keep somewhere to go");

    // ---------------------------------------------------------------- predict

    private final NumberSetting<Double> targetRange = decimal("Target Range", 10d, 4d, 16d)
            .step(0.5d)
            .describe("How far away an enemy is watched");

    private final NumberSetting<Integer> horizon = integer("Horizon", 15, 3, 25)
            .describe("Ticks ahead an enemy is predicted");

    private final NumberSetting<Double> minChance = decimal("Min Chance", 0.3d, 0.05d, 1d)
            .step(0.05d)
            .describe("How likely an enemy must be to reach a hole before it is filled");

    private final BooleanSetting late = bool("Fill Late", false)
            .describe("Fill holes even when the enemy will probably be in first");

    private final GroupSetting prediction = group("Prediction", true)
            .containing(targetRange, horizon, minChance, late);

    // -------------------------------------------------------------------- all

    private final BooleanSetting nearEnemies = bool("Near Enemies", true)
            .describe("Only holes within Enemy Range of an enemy")
            .visibleWhen(() -> mode.get() == Mode.ALL);

    private final NumberSetting<Double> enemyRange = decimal("Enemy Range", 5d, 1d, 12d)
            .step(0.5d)
            .visibleWhen(() -> mode.get() == Mode.ALL && nearEnemies.isOn());

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the cells being filled");

    private final ColorSetting renderColor = color("Render Colour", Color.of(255, 170, 0, 200))
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
            .describe("Pop cells in when they are chosen and fade them out once filled")
            .visibleWhen(render);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, animations);

    // ------------------------------------------------------------------ state

    private CombatKit<?> kit;
    private HoleFill<?> search;
    private PlacementPlanner blockStrict;
    private PlacementPlanner blockVanilla;
    private PlacementPlanner webStrict;
    private PlacementPlanner webVanilla;

    /** Holes this module has started and not finished: a stack of webs half placed. */
    private final Set<Hole> started = new LinkedHashSet<>();

    /** Which cells a placement can go into. */
    private Clicks replaceable;

    /** Cells being filled, appearing, shown or fading. */
    private final CellBoxes shown = new CellBoxes();

    private String display = "";

    @Override
    protected void onEnable() {
        kit = Combat.kit();
        search = build(kit);
        blockStrict = kit.planner(kit.strictStyle()).build();
        blockVanilla = kit.planner(kit.vanillaStyle()).build();
        // A web goes into a player: only you are in its way.
        Obstructions onlyYou = Obstructions.of(Core.entities());
        webStrict = webPlanner(kit, kit.strictStyle(), onlyYou);
        webVanilla = webPlanner(kit, kit.vanillaStyle(), onlyYou);
        replaceable = kit.clicks(kit.vanillaStyle());
        started.clear();
        shown.clear();
        display = "";
    }

    @Override
    protected void onDisable() {
        started.clear();
        if (search != null) {
            search.close();
        }
        search = null;
        shown.clear();
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        if (search == null || !Players.present()) {
            display = "";
            return;
        }
        String item = item();
        if (item == null) {
            display = fillWith.get() == Fill.WEBS ? "No webs" : "No blocks";
            show(List.of());
            return;
        }

        // The holes to fill this tick, most urgent first, and what to do once one is done.
        // Holes already started come first: once a web is in, the hole no longer
        // counts as a hole, and the web over it would otherwise never be placed.
        List<Target> targets = new ArrayList<>();
        started.removeIf(hole -> openCells(hole) == 0 || !inReach(hole));
        for (Hole hole : started) {
            targets.add(new Target(hole, null));
        }
        for (Target found : mode.get() == Mode.PREDICT ? predicted() : everyHole()) {
            if (!started.contains(found.hole())) {
                targets.add(found);
            }
        }
        List<Vec3i> cells = new ArrayList<>();
        for (Target target : targets) {
            cells.addAll(cellsOf(target.hole()));
        }
        show(cells);
        if (targets.isEmpty()) {
            display = "";
            return;
        }
        display = targets.size() + (targets.size() == 1 ? " hole" : " holes");

        boolean webs = fillWith.get() == Fill.WEBS;
        PlacementPlanner planner = webs
                ? (strict.isOn() ? webStrict : webVanilla)
                : (strict.isOn() ? blockStrict : blockVanilla);
        Plan plan = planner.plan(Players.eyePosition(), cells, Players.serverRotation());
        if (plan.getSteps().isEmpty()) {
            return;
        }
        Plan.Step step = plan.getSteps().get(0);
        Target owner = ownerOf(targets, step.getCell());
        Services.actions().submit(this, Action.click(step.getClick())
                .holding(item)
                .priority(ClaimPriority.NORMAL)
                .then(outcome -> {
                    if (outcome != Outcome.SENT || owner == null || step.isSupport()) {
                        return;
                    }
                    // The client shows the placement at once, so this counts it already.
                    if (openCells(owner.hole()) > 0) {
                        started.add(owner.hole());
                    } else if (owner.fill() != null) {
                        // Pending, so the search does not offer it again while the server
                        // catches up; the crystal aura shares the log and keeps out too.
                        search.filled(owner.fill());
                    }
                })
                .settled(result -> {
                    // Refused or never answered: the block was taken back, and the
                    // search may still hold the hole as pending. Finish it from here.
                    if (result != PlacementResult.CONFIRMED && owner != null && search != null) {
                        started.add(owner.hole());
                    }
                }));
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        shown.clear();
        started.clear();
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        shown.draw(renderColor.resolve(), fill.isOn(), fillOpacity.getFloat(), thickness.getFloat());
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    // --------------------------------------------------------------- choosing

    /** A hole to fill, and the search's option for it when it came from the search. */
    private record Target(Hole hole, FillOption<?> fill) {
    }

    /** {@link Mode#PREDICT}: the holes the search says an enemy is heading for, most urgent first. */
    private List<Target> predicted() {
        List<Target> targets = new ArrayList<>();
        for (FillOption<?> option : search.findFills(4)) {
            targets.add(new Target(option.getHole(), option));
        }
        return targets;
    }

    /** {@link Mode#ALL}: every empty hole in reach, nearest first, away from you and near an enemy. */
    private List<Target> everyHole() {
        Vec3 position = Players.position();
        double reach = Math.min(placeRange.getDouble(), Players.blockReach());
        List<Target> targets = new ArrayList<>();
        for (Hole hole : kit.holeFinder().around(Players.eyePosition(), reach + 1d)) {
            if (horizontal(hole.getCentre(), position) < selfRadius.getDouble()
                    || kit.playerBodies().any(standingRoom(hole))
                    || (nearEnemies.isOn() && !nearEnemy(kit, hole, enemyRange.getDouble()))) {
                continue;
            }
            targets.add(new Target(hole, null));
        }
        return targets;
    }

    /** @return whether a non-friend player stands within {@code range} of the hole */
    private <E> boolean nearEnemy(CombatKit<E> kit, Hole hole, double range) {
        double squared = range * range;
        for (Tracked<E> player : kit.players().getAll()) {
            if (!kit.isFriend(player.get())
                    && player.getPosition().squaredDistanceTo(hole.getCentre()) <= squared) {
                return true;
            }
        }
        return false;
    }

    /** The cells to place into: the floor cells, and with stacked webs the cell over each. */
    private List<Vec3i> cellsOf(Hole hole) {
        if (fillWith.get() != Fill.WEBS || !stack.isOn()) {
            return hole.getCells();
        }
        Set<Vec3i> cells = new LinkedHashSet<>(hole.getCells());
        for (Vec3i cell : hole.getCells()) {
            cells.add(cell.up());
        }
        return new ArrayList<>(cells);
    }

    /** @return how many of the hole's cells (with the stack) are still open */
    private int openCells(Hole hole) {
        int open = 0;
        for (Vec3i cell : cellsOf(hole)) {
            if (replaceable.isReplaceable(cell)) {
                open++;
            }
        }
        return open;
    }

    private static Target ownerOf(List<Target> targets, Vec3i cell) {
        for (Target target : targets) {
            if (target.hole().getCells().contains(cell) || target.hole().getCells().contains(cell.down())) {
                return target;
            }
        }
        return null;
    }

    /** @return whether the nearest of the hole's cells is within place reach */
    private boolean inReach(Hole hole) {
        double reach = Math.min(placeRange.getDouble(), Players.blockReach());
        Vec3 eye = Players.eyePosition();
        for (Vec3i cell : hole.getCells()) {
            if (cell.center().distanceTo(eye) <= reach + 0.5d) {
                return true;
            }
        }
        return false;
    }

    /** The room a player stands in over a hole: its cells, two high. */
    private static Box standingRoom(Hole hole) {
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3i cell : hole.getCells()) {
            minX = Math.min(minX, cell.getX());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX() + 1d);
            maxZ = Math.max(maxZ, cell.getZ() + 1d);
        }
        return Box.of(minX, hole.getY(), minZ, maxX, hole.getY() + 2d, maxZ);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- search

    /** The predicting search, in the kit's entity type. */
    private <E> HoleFill<E> build(CombatKit<E> kit) {
        TargetSelector<E> enemies = TargetSelector.from(kit.players())
                .range(targetRange::getDouble)
                .where(entity -> !kit.isFriend(entity))
                .build();
        TargetSelector<E> friends = TargetSelector.from(kit.players())
                .range(targetRange::getDouble)
                .where(kit::isFriend)
                .build();
        return kit.holeFill()
                .targets(Core.targets(), enemies)
                .protect(friends)
                .placeReach(Reach.of(() -> Math.min(placeRange.getDouble(), Players.blockReach()),
                        () -> Math.min(placeRange.getDouble(), Players.blockReach())))
                .horizon(horizon::getInt)
                .fillDelay(() -> pingTicks() + 1)
                .minChance(minChance::getDouble)
                .protectOwn(() -> true)
                .escapeRadius(selfRadius::getDouble)
                .filter(option -> late.isOn() || option.getTiming() != FillOption.Timing.LATE)
                .build();
    }

    /** A planner for webs: placed like blocks, but only you are in a web's way. */
    private static PlacementPlanner webPlanner(CombatKit<?> kit, PlacementStyle style, Obstructions onlyYou) {
        return PlacementPlanner.builder()
                .clicks(kit.clicks(style))
                .obstructions(onlyYou)
                .perTick(style.getPerTick())
                .supports(style.getSupports())
                .build();
    }

    /** One-way latency in ticks, rounded up: how long until the server places what is sent now. */
    private static int pingTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 2d / 50d);
    }

    // ----------------------------------------------------------------- items

    /** @return what to fill with: a web, or the first block to hand; null when there is none */
    private String item() {
        if (fillWith.get() == Fill.WEBS) {
            return has(WEB) ? WEB : null;
        }
        for (String block : BLOCKS) {
            if (has(block)) {
                return block;
            }
        }
        return null;
    }

    private static boolean has(String item) {
        return Inventories.hotbarHas(item) || Inventories.offhandHolds(item);
    }

    // ---------------------------------------------------------------- render

    /** Shows the cells still open; filled ones fade out. */
    private void show(List<Vec3i> cells) {
        List<Vec3i> open = render.isOn()
                ? cells.stream().filter(replaceable::isReplaceable).toList()
                : List.of();
        shown.update(open, animations.isOn());
    }

    public enum Mode {
        /** Holes an enemy is predicted to reach. */
        PREDICT,
        /** Every empty hole in reach. */
        ALL
    }

    public enum Fill {
        BLOCKS,
        WEBS
    }
}
