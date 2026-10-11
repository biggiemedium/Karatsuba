package dev.px.karatsuba.client.module.automation;

import dev.px.combat.hole.Hole;
import dev.px.combat.hole.HoleFinder;
import dev.px.combat.hole.HoleShape;
import dev.px.core.Core;
import dev.px.core.flow.Flow;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.FlowHandle;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.navigation.Goal;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.Automation;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.settings.ClientSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fights the way a player would, a piece at a time. For now, the first piece:
 * getting into a hole.
 *
 * <h2>Holes</h2>
 * Out of a hole with a fight on, it finds the holes around you with the combat
 * library's {@link HoleFinder} and walks into one. It does not pick the nearest
 * by distance and hope: every candidate goes to the planner as one goal
 * ({@link Goal#anyOf}), and the planner, playing each move through Core's
 * simulation, takes the one that is cheapest to actually reach. A hole two
 * blocks away behind a wall loses to one four away down a step.
 *
 * <p>Holes are left out if they are taken by another player, are bigger than
 * {@code Double Holes} allows, or are not bedrock with {@code Bedrock Only}. A
 * hole the planner could not reach is left alone for a few seconds, so the next
 * try goes for another rather than the same one again. The walk is given up as
 * soon as you are in a hole, the holes it was heading for stop being holes, or
 * the fight is over.
 *
 * <h2>Built from task flow</h2>
 * The walk is the automation's travel step, run as a Core <b>reflex</b> while the
 * module is on. That makes it a good neighbour to tasks: when it fires it pauses
 * only the flows that need the keys or the head, a {@code .task goto} for one,
 * and resumes them once you are in, so the trip carries on afterwards. It moves
 * under Client Settings &gt; Automation like any task, falls, hazards and sprint
 * included, and hands the controls back the moment you touch a movement key, when
 * those settings say so.
 */
@ModuleInfo(name = "Auto Combat", description = "Gets you into a hole when a fight is on",
        category = "Automation")
public final class AutoCombatModule extends ClientModule {

    /** Above a task at the default priority, so a fight takes the keys from a trip and gives them back. */
    private static final int PRIORITY = RotationPriority.NORMAL + 10;

    /** The nearest this many holes are offered to the planner; past that, they only slow the search. */
    private static final int CANDIDATES = 8;
    /** Ticks to leave a hole alone after the planner could not reach it. */
    private static final long UNREACHABLE_FOR = 100L;
    /** Ticks to stay out of the way after you took the controls. */
    private static final long YIELD_FOR = 40L;
    /** Ticks between hole searches while none is found: a search reads a few thousand blocks. */
    private static final long SEARCH_EVERY = 5L;

    private final NumberSetting<Float> range = number("Hole Range", 6f, 2f, 12f)
            .step(0.5f)
            .describe("How far to look for a hole");

    private final BooleanSetting bedrockOnly = bool("Bedrock Only", false)
            .describe("Only holes walled and floored with blocks that cannot be broken");

    private final BooleanSetting doubles = bool("Double Holes", true)
            .describe("Also use two- and four-block holes, not only singles");

    private final BooleanSetting onlyInFights = bool("Only In Fights", true)
            .describe("Only go for a hole while an enemy is near; off goes for one whenever out of one");

    private final NumberSetting<Float> enemyRange = number("Enemy Range", 10f, 3f, 24f)
            .step(0.5f)
            .describe("A player this near who is not a friend means a fight")
            .visibleWhen(onlyInFights);

    private FlowHandle reflex;

    /** The holes the walk under way is heading for. */
    private List<Hole> targets = List.of();
    /** Holes the planner could not reach, until the tick they may be tried again. */
    private final Map<Hole, Long> unreachable = new HashMap<>();
    private long quietUntil;
    private long nextSearch;

    /** Whether the feet were in a hole, and the tick that was asked: asked at most once a tick. */
    private boolean inHoleNow;
    private long inHoleAsked = -1L;

    @Override
    protected void onEnable() {
        targets = List.of();
        unreachable.clear();
        quietUntil = 0L;
        nextSearch = 0L;
        inHoleAsked = -1L;
        reflex = Core.flows().reflex("Auto Combat: hole", this::wantsHole, PRIORITY, toHole());
    }

    @Override
    protected void onDisable() {
        if (reflex != null) {
            reflex.cancel();
            reflex = null;
        }
        targets = List.of();
        unreachable.clear();
    }

    // ---------------------------------------------------------------- flow

    /**
     * Into the cheapest hole to reach, given up the moment it stops making sense;
     * then a note of how it went, so the next try is a better one.
     */
    private Step toHole() {
        Step walk = Automation.get().getNavigation()
                .to(c -> Goal.anyOf(targets.stream().map(AutoCombatModule::inside).toArray(Goal[]::new)))
                .until(c -> inHole() || yielding() || !targetsStand() || !fightOn());
        Step unreached = Flow.action("no way in", c -> {
            for (Hole hole : targets) {
                unreachable.put(hole, c.tick() + UNREACHABLE_FOR);
            }
        });
        Step settle = Flow.action("settle", c -> {
            if (yielding()) {
                quietUntil = c.tick() + YIELD_FOR;
            }
            targets = List.of();
        });
        return walk.orElse(unreached).then(settle).named("into a hole");
    }

    /** The reflex's question, which also picks the holes to offer when the answer is yes. */
    private boolean wantsHole(FlowContext c) {
        if (c.tick() < quietUntil || !Players.present() || yielding() || !fightOn() || inHole()) {
            return false;
        }
        unreachable.values().removeIf(until -> until <= c.tick());
        if (c.tick() < nextSearch) {
            return false;
        }
        List<Hole> found = candidates();
        if (found.isEmpty()) {
            nextSearch = c.tick() + SEARCH_EVERY;
            return false;
        }
        targets = found;
        return true;
    }

    // --------------------------------------------------------------- holes

    /** @return the nearest usable holes, at most {@link #CANDIDATES} */
    private List<Hole> candidates() {
        List<GameEntity> others = otherPlayers(range.getFloat() + 2d);
        List<Hole> usable = new ArrayList<>(CANDIDATES);
        for (Hole hole : finder().around(Players.position(), range.getFloat())) {
            if (usable.size() == CANDIDATES) {
                break;
            }
            if ((!doubles.isOn() && hole.getShape() != HoleShape.SINGLE)
                    || (bedrockOnly.isOn() && !hole.isSafe())
                    || unreachable.containsKey(hole)
                    || takenBy(hole, others)) {
                continue;
            }
            usable.add(hole);
        }
        return usable;
    }

    /** @return whether the player's feet are in a hole now */
    private boolean inHole() {
        long tick = Core.flows().getTick();
        if (tick != inHoleAsked) {
            inHoleAsked = tick;
            inHoleNow = findInHole();
        }
        return inHoleNow;
    }

    private boolean findInHole() {
        Vec3i feet = Players.blockPosition();
        return finder().at(feet.getX(), feet.getY(), feet.getZ()) != null;
    }

    /** @return whether at least one hole being walked to is still a hole: a block placed in it, and it is not */
    private boolean targetsStand() {
        for (Hole hole : targets) {
            Vec3i cell = hole.getCells().get(0);
            if (hole.equals(finder().at(cell.getX(), cell.getY(), cell.getZ()))) {
                return true;
            }
        }
        return false;
    }

    /** @return met with the feet in any of the hole's cells */
    private static Goal inside(Hole hole) {
        return Goal.anyOf(hole.getCells().stream().map(Goal::block).toArray(Goal[]::new));
    }

    private static boolean takenBy(Hole hole, List<GameEntity> others) {
        for (GameEntity other : others) {
            Vec3 feet = other.getPosition();
            Vec3i cell = Vec3i.of((int) Math.floor(feet.getX()), (int) Math.floor(feet.getY()),
                    (int) Math.floor(feet.getZ()));
            if (hole.getCells().contains(cell)) {
                return true;
            }
        }
        return false;
    }

    private static HoleFinder finder() {
        return Combat.kit().holeFinder();
    }

    // --------------------------------------------------------------- fight

    private boolean fightOn() {
        if (!onlyInFights.isOn()) {
            return true;
        }
        for (GameEntity other : otherPlayers(enemyRange.getFloat())) {
            if (!other.isFriend()) {
                return true;
            }
        }
        return false;
    }

    /** @return every player within {@code radius} but you */
    private static List<GameEntity> otherPlayers(double radius) {
        List<GameEntity> players = new ArrayList<>();
        for (GameEntity entity : Entities.nearby(1f, radius)) {
            if (entity.isPlayer() && entity.getId() != Players.entityId()) {
                players.add(entity);
            }
        }
        return players;
    }

    /** @return whether you have the controls, and the automation settings say to give them to you */
    private static boolean yielding() {
        return ClientSettings.automation().yieldToPlayer() && Inputs.playerMoving();
    }

    @Override
    public String getDisplayInfo() {
        if (reflex != null && reflex.getState() == FlowHandle.State.RUNNING) {
            return "Moving";
        }
        return Players.present() && inHole() ? "Hole" : "";
    }
}
