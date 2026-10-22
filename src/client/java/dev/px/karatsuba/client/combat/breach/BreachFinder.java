package dev.px.karatsuba.client.combat.breach;

import dev.px.combat.place.Shapes;
import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.combat.GameBlocks;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Digging;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which block to break so a crystal can reach a target: our "city", by its own name.
 *
 * <h2>The one-block hole</h2>
 * Since 1.13 a crystal needs one block of air over obsidian or bedrock, and no entity
 * in the two cells above that base (the kit's crystal rules). So a wall block beside
 * their feet is a breach when the block under it is obsidian or bedrock and nothing
 * stands where the crystal would: break it, and a crystal fits the hole it leaves,
 * right beside their feet. No damage sums: this is geometry and the time to mine.
 *
 * <h2>What it recognises</h2>
 * <ul>
 *   <li><b>Burrowed:</b> a block inside their feet cell. Out first; it is all that is
 *       offered.</li>
 *   <li><b>Open:</b> a hole a crystal fits already beside their feet. Nothing to break.</li>
 *   <li><b>Surrounded:</b> feet-ring blocks on an obsidian or bedrock floor.</li>
 *   <li><b>Trapped:</b> head ring and roof up, as an auto-trap builds them. Head-ring
 *       blocks standing on a ring block of obsidian or bedrock are offered too: break one
 *       and the crystal goes on the wall, at their face.</li>
 *   <li><b>Sealed:</b> no feet-ring block will do (bedrock walls, or the wrong floor),
 *       so only face blocks remain, if there are any.</li>
 * </ul>
 * Never offered: anything unbreakable, anything a player could not break here, and any
 * block of your own surround or in your own feet.
 *
 * <h2>Ranked</h2>
 * In reach first, then feet before face (a hole at the feet is the bigger hit), then
 * the quickest to mine with the best tool in the hotbar, then the nearest.
 *
 * <p>Pure reading: it changes nothing, so any module, task or flow may ask. Game thread only.
 */
public final class BreachFinder {

    private BreachFinder() {
    }

    /** @return how to get a crystal onto {@code target} */
    public static <E> Assessment assess(CombatKit<E> kit, Tracked<E> target) {
        Box box = target.getBox();
        Set<Vec3i> own = ownCells(kit);
        Vec3 eyes = Players.eyePosition();
        double reach = Players.blockReach();

        List<Vec3i> feet = Shapes.feet(box);
        List<Vec3i> ring = Shapes.around(box);
        boolean trapped = trapped(ring, Shapes.above(box));

        List<Breach> burrows = new ArrayList<>();
        for (Vec3i cell : feet) {
            if (!GameBlocks.isOpen(cell.getX(), cell.getY(), cell.getZ()) && Digging.breakable(cell)
                    && !own.contains(cell)) {
                burrows.add(candidate(cell, null, Breach.Level.BURROW, eyes, reach));
            }
        }
        if (!burrows.isEmpty()) {
            return new Assessment(Scenario.BURROWED, trapped, ranked(burrows, eyes));
        }

        for (Vec3i wall : ring) {
            if (kit.crystalRules().canPlace(wall.getX(), wall.getY() - 1, wall.getZ())) {
                return new Assessment(Scenario.OPEN, trapped, List.of());
            }
        }

        List<Breach> found = new ArrayList<>();
        for (Vec3i wall : ring) {
            // The hole it leaves, on the block below.
            Vec3i base = wall.down();
            if (opens(kit, wall, base, own)) {
                found.add(candidate(wall, base, Breach.Level.FEET, eyes, reach));
            }
        }
        boolean feetFound = !found.isEmpty();
        if (trapped || !feetFound) {
            for (Vec3i wall : ring) {
                // The head-ring block on top of it: the wall itself is the base.
                Vec3i face = wall.up();
                if (opens(kit, face, wall, own)) {
                    found.add(candidate(face, wall, Breach.Level.FACE, eyes, reach));
                }
            }
        }
        Scenario scenario = feetFound ? Scenario.SURROUNDED : Scenario.SEALED;
        return new Assessment(scenario, trapped, ranked(found, eyes));
    }

    /**
     * @return whether breaking {@code block} leaves a hole a crystal fits on {@code base}:
     *         something there to break, breakable, the base obsidian or bedrock, no entity
     *         where the crystal would stand, and none of it yours
     */
    private static <E> boolean opens(CombatKit<E> kit, Vec3i block, Vec3i base, Set<Vec3i> own) {
        if (own.contains(block) || GameBlocks.isReplaceable(block.getX(), block.getY(), block.getZ())
                || !Digging.breakable(block)
                || !GameBlocks.isCrystalBase(base.getX(), base.getY(), base.getZ())) {
            return false;
        }
        // The kit's own clearance: no entity in the two cells above the base.
        Box room = Box.of(base.getX(), base.getY() + 1, base.getZ(), base.getX() + 1d, base.getY() + 3d, base.getZ() + 1d);
        return !kit.everything().any(room);
    }

    /** @return whether every head-ring cell and every roof cell is filled: no stepping or jumping out */
    private static boolean trapped(List<Vec3i> ring, List<Vec3i> roof) {
        for (Vec3i wall : ring) {
            Vec3i head = wall.up();
            if (GameBlocks.isOpen(head.getX(), head.getY(), head.getZ())) {
                return false;
            }
        }
        for (Vec3i cell : roof) {
            if (GameBlocks.isOpen(cell.getX(), cell.getY(), cell.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** Your feet and your surround: never broken for an attack. */
    private static <E> Set<Vec3i> ownCells(CombatKit<E> kit) {
        Set<Vec3i> own = new HashSet<>();
        Tracked<E> self = Core.entities().getSelf();
        if (self != null) {
            own.addAll(Shapes.feet(self.getBox()));
            own.addAll(Shapes.around(self.getBox()));
        }
        return own;
    }

    private static Breach candidate(Vec3i block, Vec3i base, Breach.Level level, Vec3 eyes, double reach) {
        int slot = Inventories.bestToolSlotFor(Blocks.idAt(block));
        float perTick = Digging.progressPerTick(block, slot);
        int ticks = perTick <= 0f ? Integer.MAX_VALUE : (int) Math.ceil(1d / perTick);
        boolean inReach = Blocks.outline(block).distanceTo(eyes) <= reach;
        return new Breach(block, base, level, ticks, inReach);
    }

    private static List<Breach> ranked(List<Breach> breaches, Vec3 eyes) {
        List<Breach> sorted = new ArrayList<>(breaches);
        sorted.sort(Comparator.comparing((Breach breach) -> !breach.inReach())
                .thenComparing(Breach::level)
                .thenComparingInt(Breach::mineTicks)
                .thenComparingDouble(breach -> breach.block().center().squaredDistanceTo(eyes)));
        return List.copyOf(sorted);
    }
}
