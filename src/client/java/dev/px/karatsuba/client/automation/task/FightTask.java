package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Condition;
import dev.px.core.flow.Flow;
import dev.px.core.flow.Step;
import dev.px.core.navigation.Goal;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.karatsuba.client.automation.step.Eat;
import dev.px.karatsuba.client.automation.step.Equip;
import dev.px.karatsuba.client.automation.step.Melee;
import dev.px.karatsuba.client.automation.step.Sighting;

import java.util.List;

/**
 * Fights another player hand to hand until they go down or get away.
 *
 * <pre>
 * .task fight             the nearest player who is not a friend
 * .task fight Steve
 * </pre>
 *
 * <p>Put together the way a player fights, from steps that each do one thing:
 *
 * <ol>
 *   <li><b>A totem</b> in the offhand first, if there is one.</li>
 *   <li><b>Closing in:</b> a run at them, facing where it goes, sprinting.</li>
 *   <li><b>Engaging</b> once close: {@link Melee} has the head on them and swings
 *       on every recharge, while a walk beside it keeps within striking distance
 *       holding only the keys. They are ahead, so the keys stay forward: no
 *       walking backwards at them. If they get away, back to closing in.</li>
 *   <li><b>Healing:</b> low on health with a golden apple in the hotbar, the fight
 *       pauses for one and picks up where it was.</li>
 * </ol>
 *
 * <p>Done when they go down; failed when they are out of sight.
 */
public final class FightTask implements Task {

    public static final TaskType TYPE = TaskType.of("fight", "[player]",
            "Fights a player hand to hand, or the nearest one who is not a friend", FightTask::parse)
            .completes(index -> index == 0 ? PlayerList.names() : List.of());

    /** How far to look for the nearest enemy, in blocks. */
    private static final double SEARCH = 24d;

    /** Blocks: close in to this, then engage. */
    private static final double CLOSE = 3.5d;
    /** Blocks: while engaged, keep this near. */
    private static final double STRIKE = 2.5d;
    /** Blocks: further than this while engaged, and they have got away; close in again. */
    private static final double BROKE_AWAY = 7d;

    /** Health, in half hearts, at or below which a golden apple is worth the pause. */
    private static final float LOW_HEALTH = 8f;
    /** Ticks between apples, so one apple's absorption is given time to show. */
    private static final long BETWEEN_APPLES = 100L;

    private static final String TOTEM = "totem_of_undying";
    private static final String GAPPLE = "golden_apple";
    private static final String ENCHANTED_GAPPLE = "enchanted_golden_apple";

    private final String name;

    private FightTask(String name) {
        this.name = name;
    }

    /** @return a task that fights {@code name}, or the nearest enemy when null */
    public static FightTask player(String name) {
        return new FightTask(name);
    }

    private static Task parse(TaskArgs args) {
        String name = args.get(0, null);
        Sighting sighting = name == null ? Sighting.nearestEnemy(SEARCH) : Sighting.player(name);
        if (!sighting.look()) {
            args.fail(name == null ? "No one to fight within " + (int) SEARCH + " blocks" : name + " is not in sight");
        }
        return player(name);
    }

    @Override
    public String getName() {
        return "Fight";
    }

    @Override
    public String describe() {
        return name != null ? name : "the nearest enemy";
    }

    @Override
    public Step build(Navigation navigation) {
        Sighting enemy = name == null ? Sighting.nearestEnemy(SEARCH) : Sighting.player(name);
        enemy.look();

        Step closeIn = navigation.to(Goal.near(enemy::position, CLOSE)).named("close in");
        Step engage = Flow.race(
                        new Melee(enemy),
                        Flow.loop(navigation.alongside(Goal.near(enemy::position, STRIKE))).named("keep close"))
                .until(c -> enemy.distance() > BROKE_AWAY)
                .named("engage");
        Step fight = Flow.race(enemy.watch(), Flow.loop(Flow.sequence(closeIn, engage)).named("fight"))
                .interrupt(lowOnHealth(), heal())
                .named("fight " + enemy.describe());

        return Flow.sequence(totem(), fight);
    }

    /** A totem in the offhand if there is one; nothing if there is not. */
    private static Step totem() {
        return Flow.firstOf(Equip.offhand(TOTEM), Flow.action("no totem", c -> { }));
    }

    /** The best apple there is: enchanted first. */
    private static Step heal() {
        return Flow.firstOf(Eat.item(ENCHANTED_GAPPLE), Eat.item(GAPPLE)).named("heal");
    }

    private static Condition lowOnHealth() {
        long[] lastApple = {Long.MIN_VALUE / 2};
        return c -> {
            if (Players.health() > LOW_HEALTH || c.tick() - lastApple[0] < BETWEEN_APPLES
                    || (!Inventories.hotbarHas(GAPPLE) && !Inventories.hotbarHas(ENCHANTED_GAPPLE))) {
                return false;
            }
            lastApple[0] = c.tick();
            return true;
        };
    }
}
