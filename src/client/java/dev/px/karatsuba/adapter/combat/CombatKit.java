package dev.px.karatsuba.adapter.combat;

import dev.px.combat.anchor.AnchorRules;
import dev.px.combat.bed.BedRules;
import dev.px.combat.crystal.CrystalRules;
import dev.px.combat.explosion.ExplosionModel;
import dev.px.combat.explosion.state.StateCapture;
import dev.px.combat.hole.HoleFill;
import dev.px.combat.hole.HoleFinder;
import dev.px.combat.hole.HoleRules;
import dev.px.combat.hole.HoleWatch;
import dev.px.combat.monitor.DamageMonitor;
import dev.px.combat.monitor.Vitals;
import dev.px.combat.place.Clicks;
import dev.px.combat.place.PlacementPlanner;
import dev.px.combat.place.PlacementStyle;
import dev.px.combat.search.AnchorSearch;
import dev.px.combat.search.BedSearch;
import dev.px.combat.search.CrystalSearch;
import dev.px.combat.search.rule.Lookahead;
import dev.px.combat.search.rule.Reach;
import dev.px.combat.search.timing.AttackLog;
import dev.px.combat.trap.TrapSearch;
import dev.px.combat.vector.capture.VectorRecorder;
import dev.px.core.entity.EntityTracker;
import dev.px.core.world.BlockView;
import dev.px.core.world.Obstructions;

import java.util.function.BooleanSupplier;

/**
 * The combat library, wired to this game: every rule its README asks a client
 * to supply, built once for 1.21.4.
 *
 * <p>The library is typed by the game's entity class and the {@code client}
 * package may never name it, so the kit is typed by a variable instead. A module
 * gets the kit as {@code CombatKit<?>} from {@link Combat#kit()} and builds its
 * searches in a generic method, where {@code E} stands for the game's class
 * without ever being spelled:
 *
 * <pre>{@code
 * private CrystalSearch<?> search;
 *
 * @Override
 * protected void onEnable() {
 *     search = build(Combat.kit());
 * }
 *
 * private <E> CrystalSearch<E> build(CombatKit<E> kit) {
 *     TargetSelector<E> enemies = TargetSelector.from(kit.players())
 *             .range(targetRange::getDouble)
 *             .where(e -> !kit.isFriend(e))
 *             .build();
 *     return kit.crystalSearch()
 *             .targets(Core.targets(), enemies)
 *             .placeReach(Reach.of(placeRange::getDouble, wallRange::getDouble))
 *             .breakReach(Reach.of(breakRange::getDouble, wallRange::getDouble))
 *             .thresholds(Thresholds.<E>builder().minDamage(minDamage::getDouble).build())
 *             .build();
 * }
 * }</pre>
 *
 * <p>Everything the {@code search} then returns is the library's own types:
 * {@code PlaceOption<?>}, {@code Tracked<?>}, {@code Click}. To act on one, attack
 * by {@link #id} and click with {@code Interactions.click}.
 *
 * <h2>What is wired, by README section</h2>
 * <table>
 *   <caption>The combat README, and where each part lives</caption>
 *   <tr><th>Section</th><th>Here</th></tr>
 *   <tr><td>1. Your world</td><td>{@link #blocks()} ({@link GameBlocks}), the cell tests on
 *       {@link GameBlocks}, {@link #living()}, {@link #players()}, {@link #crystals()}, the three
 *       obstructions, and Core's entity source and prediction physics underneath</td></tr>
 *   <tr><td>2. Explosions</td><td>{@link #explosions()}, {@link #state()} ({@link ExplosionProfile})</td></tr>
 *   <tr><td>3. Crystals</td><td>{@link #crystalRules()}</td></tr>
 *   <tr><td>4. Searching</td><td>{@link #crystalSearch()}, {@link #vitals()}, {@link #armourWear},
 *       {@link #lookahead()}, {@link #log()}, and crystals tracked on spawn
 *       ({@code CrystalSpawnEvent})</td></tr>
 *   <tr><td>5. Beds and anchors</td><td>{@link #bedRules()}, {@link #anchorRules()},
 *       {@link #bedSearch()}, {@link #anchorSearch()}</td></tr>
 *   <tr><td>6. Holes</td><td>{@link #holeRules()}, {@link #holeFinder()}, {@link #holeWatch()},
 *       {@link #holeFill()}</td></tr>
 *   <tr><td>7. Placing blocks</td><td>{@link #blockReach()}, {@link #vanillaStyle()},
 *       {@link #strictStyle()}, {@link #clicks}, {@link #planner}, {@link #trapSearch()}, and
 *       {@code Interactions.click} to send a {@code Click}</td></tr>
 *   <tr><td>8. The damage monitor</td><td>{@link #monitor()}, fed every explosion and totem pop</td></tr>
 *   <tr><td>9. Test vectors</td><td>{@link #vectors()}, recording from the monitor;
 *       {@code ExplosionProfile.replayModel()} to replay them</td></tr>
 * </table>
 *
 * <h2>Starting points, not finished searches</h2>
 * The {@code ...Search()} methods return builders with the game's half already
 * given &mdash; rules, entities, vitals, the shared log, the lookahead &mdash; and
 * the aura's half left to it: who to target, how far, what is worth doing. Each
 * call is a new builder, so two modules never share one by accident.
 *
 * <p><b>Game thread only</b>, like everything the searches read.
 *
 * @param <E> the game's living-entity class, never named outside the adapter
 */
public interface CombatKit<E> {

    // ------------------------------------------------------------- entities

    /** Every living entity but you. */
    EntityTracker<E> living();

    /** Other players, alive and not spectating. Same type as {@link #living()}. */
    EntityTracker<E> players();

    /** End crystals; also tracked the moment one spawns, before the tick. */
    EntityTracker<?> crystals();

    /**
     * Any entity, and you: what stops an end crystal being placed. The game
     * refuses a crystal over any entity at all, dropped items included.
     */
    Obstructions everything();

    /** Entities a placed block may not go into, and you: what stops a block, bed or anchor. */
    Obstructions blocking();

    /** Other players and you: who can stand in a hole and fill it. */
    Obstructions playerBodies();

    /** @return a game entity's id, for {@code Action.attack} through {@code Services.actions()}; -1 for anything else */
    int id(Object entity);

    /** @return a game entity's display name; empty for anything else */
    String name(Object entity);

    /** @return whether this entity is a player on Core's friends list */
    boolean isFriend(E entity);

    // ---------------------------------------------------------------- world

    /** The shapes that stop explosion rays and lines of sight. See {@link GameBlocks}. */
    BlockView blocks();

    // ----------------------------------------------------------- explosions

    /** How an explosion hurts a living entity in this version. See {@link ExplosionProfile}. */
    ExplosionModel<E> explosions();

    /** The values mitigation reads off an entity, for recording and replaying. */
    StateCapture<E> state();

    /**
     * Health as the library reads it: health plus absorption; trusted for you,
     * and for others while {@link #trustHealth} says so; recently hurt while an
     * entity is still flashing red.
     */
    Vitals<E> vitals();

    /**
     * Whether other entities' health is believed. On by default; turn it off on
     * a server that hides or fakes health, so nothing is ever called lethal from
     * a number the server made up.
     */
    void trustHealth(BooleanSupplier trusted);

    /**
     * How worn the most worn armour piece is, as percent durability left (0 to
     * 100); NaN when nothing worn can wear. For {@code Thresholds.armourBreak(kit::armourWear, ...)}.
     */
    double armourWear(E entity);

    // ---------------------------------------------------------------- rules

    /** End crystals: obsidian or bedrock, one block of air above, no entity in the two above it. */
    CrystalRules<E> crystalRules();

    /** Beds: both halves replaceable, no entity at the foot, explode where beds do not work. */
    BedRules<E> bedRules();

    /** Respawn anchors: a replaceable cell with no entity in it, explode where anchors do not work. */
    AnchorRules<E> anchorRules();

    /** Holes: walled with blast-proof blocks, two clear cells high, safe when all bedrock. */
    HoleRules holeRules();

    HoleFinder holeFinder();

    // ------------------------------------------------------------- searching

    /**
     * One log for every search and fill, ticked once: what has gone off this tick,
     * what is inhibited, what was placed and not yet shown.
     */
    AttackLog log();

    /** Where a target will be: Core's prediction while it is reliable, where they are otherwise. */
    Lookahead<E> lookahead();

    /**
     * A crystal search with rules, entities, crystals, vitals, the shared log and
     * the lookahead given. Still needs: {@code targets}, {@code placeReach},
     * {@code breakReach}, {@code thresholds}.
     */
    CrystalSearch.Builder<E> crystalSearch();

    /** As {@link #crystalSearch()}, for beds. Still needs: targets, placeReach, useReach, thresholds. */
    BedSearch.Builder<E> bedSearch();

    /** As {@link #crystalSearch()}, for respawn anchors. Still needs: targets, placeReach, useReach, thresholds. */
    AnchorSearch.Builder<E> anchorSearch();

    // ---------------------------------------------------------------- holes

    /** A hole watch with the finder, prediction and obstructions given. Still needs: horizon, radius. */
    HoleWatch.Builder holeWatch();

    /**
     * An auto-fill search with the finder, prediction, entities, blocks,
     * obstructions and shared log given. Still needs: targets, placeReach,
     * horizon, fillDelay, minChance.
     */
    HoleFill.Builder<E> holeFill();

    // -------------------------------------------------------------- placing

    /** The player's block interaction range, read live, with no wall rule of its own. */
    Reach blockReach();

    /** Vanilla's rules: any face in {@link #blockReach()}, as many a tick as you like. */
    PlacementStyle vanillaStyle();

    /** An anticheat's: faces toward you, seen, in reach, never mid air, one a tick. */
    PlacementStyle strictStyle();

    /**
     * How the server takes a click in this style, against this world: placed
     * against blocks that do not open when clicked, into cells that are replaceable.
     */
    Clicks clicks(PlacementStyle style);

    /** A placement planner in this style, keeping blocks out of anyone standing in the way. */
    PlacementPlanner.Builder planner(PlacementStyle style);

    /** A trap search with prediction, entities and the shared log given. Still needs: targets, planner. */
    TrapSearch.Builder<E> trapSearch();

    // -------------------------------------------------------------- monitor

    /**
     * Checks {@link #explosions()} against every explosion that happens near you,
     * and posts {@code DamageDriftEvent} when it stops matching the game.
     */
    DamageMonitor<E> monitor();

    /** Every sample the monitor keeps, as a test vector. Save with {@code VectorJson.write}. */
    VectorRecorder<E> vectors();
}
