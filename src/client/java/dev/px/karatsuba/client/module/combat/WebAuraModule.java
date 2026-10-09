package dev.px.karatsuba.client.module.combat;

import dev.px.combat.place.Occupancy;
import dev.px.combat.place.PlacementPlanner;
import dev.px.combat.place.PlacementStyle;
import dev.px.combat.place.Plan;
import dev.px.combat.place.Shapes;
import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
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
import dev.px.karatsuba.client.module.combat.render.CellBoxes;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Drops cobwebs on an enemy: at their feet, and with {@code Head} over them too,
 * so they are held where they stand or where they are going.
 *
 * <h2>Where</h2>
 * <ul>
 *   <li><b>{@link Mode#NOW}</b>: the cells they are in now. Best against someone
 *       standing still, sitting in a hole above all.</li>
 *   <li><b>{@link Mode#PREDICT}</b>: the cells they will be in when the web lands
 *       ({@code kit.lookahead()}: Core's likeliest future for them while it is
 *       reliable, where they are otherwise).</li>
 *   <li><b>{@link Mode#LIKELY}</b>: every cell they are likely to pass through
 *       around the time it lands, across all their predicted futures
 *       ({@code Occupancy}), soonest first. For a target whose way is less
 *       certain.</li>
 * </ul>
 *
 * <h2>Into a player</h2>
 * A web has no collision box, so the server lets it go into a player: the planner
 * is told only you are in its way. It never webs you, and never a friend. Their
 * feet web is clicked against the floor; their head web against the feet web
 * just planned, so both go up together.
 *
 * <h2>Sending</h2>
 * One web a tick through {@code Services.actions()}, turned to, swapped to and
 * checked against the server's view of your head like every placement.
 */
@ModuleInfo(name = "Web Aura", description = "Places cobwebs on enemies, where they are or will be",
        category = "Combat")
public final class WebAuraModule extends ClientModule {

    private static final String WEB = "cobweb";

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.PREDICT)
            .describe("NOW webs where they are; PREDICT where they will be; LIKELY wherever they likely pass");

    private final BooleanSetting head = bool("Head", true)
            .describe("A second web at their head, two high");

    private final NumberSetting<Double> targetRange = decimal("Target Range", 6d, 2d, 10d)
            .step(0.5d)
            .describe("How far away a player can be and still be webbed");

    private final NumberSetting<Double> minChance = decimal("Min Chance", 0.3d, 0.05d, 1d)
            .step(0.05d)
            .describe("How likely they must be in a cell to web it")
            .visibleWhen(() -> mode.get() == Mode.LIKELY);

    private final NumberSetting<Integer> spread = integer("Spread", 3, 0, 10)
            .describe("Ticks after the web lands still counted")
            .visibleWhen(() -> mode.get() == Mode.LIKELY);

    private final BooleanSetting inHoleOnly = bool("In Hole Only", false)
            .describe("Only web someone standing in a hole");

    private final BooleanSetting strict = bool("Strict", true)
            .describe("Only faces turned toward you and in sight; off allows any face, as vanilla does");

    private final NumberSetting<Integer> delay = integer("Delay", 0, 0, 10)
            .describe("Ticks between webs");

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the cells being webbed");

    private final ColorSetting renderColor = color("Render Colour", Color.of(220, 220, 220, 200))
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
            .describe("Pop cells in when they are chosen and fade them out once webbed")
            .visibleWhen(render);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, animations);

    // ------------------------------------------------------------------ state

    private Targeting<?> targeting;
    private PlacementPlanner strictPlanner;
    private PlacementPlanner vanillaPlanner;

    private final CellBoxes shown = new CellBoxes();

    private long tick;
    private long lastWeb = Long.MIN_VALUE / 2;

    private String display = "";

    /** The kit and its enemy selector, kept together so both are in one entity type. */
    private record Targeting<E>(CombatKit<E> kit, TargetSelector<E> enemies) {
    }

    @Override
    protected void onEnable() {
        CombatKit<?> kit = Combat.kit();
        targeting = targeting(kit);
        // A web goes into a player: only you are in its way.
        Obstructions onlyYou = Obstructions.of(Core.entities());
        strictPlanner = planner(kit, kit.strictStyle(), onlyYou);
        vanillaPlanner = planner(kit, kit.vanillaStyle(), onlyYou);
        reset();
    }

    @Override
    protected void onDisable() {
        targeting = null;
        reset();
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        if (targeting == null || !Players.present()) {
            display = "";
            shown.update(List.of(), animations.isOn());
            return;
        }
        boolean hasWebs = Inventories.hotbarHas(WEB) || Inventories.offhandHolds(WEB);
        Webbing webbing = hasWebs ? choose(targeting) : null;
        if (webbing == null) {
            display = hasWebs ? "" : "No webs";
            shown.update(List.of(), animations.isOn());
            return;
        }

        PlacementPlanner planner = strict.isOn() ? strictPlanner : vanillaPlanner;
        Plan plan = planner.plan(Players.eyePosition(), webbing.cells(), Players.serverRotation());
        List<Vec3i> open = webbing.cells().stream()
                .filter(cell -> plan.getSkipped().get(cell) != Plan.Skip.FILLED)
                .toList();
        shown.update(render.isOn() ? open : List.of(), animations.isOn());
        display = open.isEmpty() ? "" : webbing.name();

        if (plan.getSteps().isEmpty() || tick - lastWeb < delay.getInt()) {
            return;
        }
        Services.actions().submit(this, Action.click(plan.getSteps().get(0).getClick())
                .holding(WEB)
                .priority(ClaimPriority.NORMAL)
                .then(outcome -> {
                    if (outcome == Outcome.SENT) {
                        lastWeb = tick;
                    }
                }));
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        reset();
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

    /** Who to web, by name, and the cells to put webs in, most important first. */
    private record Webbing(String name, List<Vec3i> cells) {
    }

    /** @return the nearest enemy in range worth webbing, and where; null for none */
    private <E> Webbing choose(Targeting<E> targeting) {
        CombatKit<E> kit = targeting.kit();
        for (Tracked<E> enemy : Core.targets().all(targeting.enemies(), 4)) {
            if (inHoleOnly.isOn() && !inHole(kit, enemy)) {
                continue;
            }
            List<Vec3i> cells = cellsFor(kit, enemy);
            if (!cells.isEmpty()) {
                return new Webbing(kit.name(enemy.get()), cells);
            }
        }
        return null;
    }

    private <E> List<Vec3i> cellsFor(CombatKit<E> kit, Tracked<E> enemy) {
        int lands = pingTicks() + 1;
        switch (mode.get()) {
            case NOW:
                return feetAndHead(enemy.getBox());
            case PREDICT:
                return feetAndHead(kit.lookahead().at(enemy, lands).getBox());
            default: {
                Occupancy likely = Occupancy.of(Core.prediction().predict(enemy, lands + spread.getInt()),
                        lands, lands + spread.getInt());
                List<Vec3i> cells = likely.likelyCells(minChance.getDouble());
                return head.isOn() ? cells : lowest(cells);
            }
        }
    }

    /** The cells their feet are in, and with {@code Head} the cells their head is in, feet first. */
    private List<Vec3i> feetAndHead(Box box) {
        if (!head.isOn()) {
            return Shapes.feet(box);
        }
        return Shapes.union(Shapes.feet(box), Shapes.head(box));
    }

    /** Only the bottom cell of each column: the feet, wherever they are. */
    private static List<Vec3i> lowest(List<Vec3i> cells) {
        Set<Vec3i> all = new LinkedHashSet<>(cells);
        List<Vec3i> bottom = new ArrayList<>();
        for (Vec3i cell : cells) {
            if (!all.contains(cell.down())) {
                bottom.add(cell);
            }
        }
        return bottom;
    }

    /** @return whether the enemy's feet are in a hole */
    private static <E> boolean inHole(CombatKit<E> kit, Tracked<E> enemy) {
        for (Vec3i cell : Shapes.feet(enemy.getBox())) {
            if (kit.holeFinder().at(cell.getX(), cell.getY(), cell.getZ()) != null) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- set up

    /** Enemies in range, nearest first; never a friend. In the kit's entity type. */
    private <E> Targeting<E> targeting(CombatKit<E> kit) {
        TargetSelector<E> enemies = TargetSelector.from(kit.players())
                .range(targetRange::getDouble)
                .where(entity -> !kit.isFriend(entity))
                .build();
        return new Targeting<>(kit, enemies);
    }

    /** A planner for webs: placed like blocks, but only you are in a web's way. */
    private static PlacementPlanner planner(CombatKit<?> kit, PlacementStyle style, Obstructions onlyYou) {
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

    private void reset() {
        tick = 0;
        lastWeb = Long.MIN_VALUE / 2;
        shown.clear();
        display = "";
    }

    public enum Mode {
        /** Where they are now. */
        NOW,
        /** Where they will be when the web lands. */
        PREDICT,
        /** Wherever they are likely to pass around then. */
        LIKELY
    }
}
