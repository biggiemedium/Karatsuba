package dev.px.karatsuba.client.module.mining.smartmine;

import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.player.Digging;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The blocks marked for mining, in the order they were marked.
 *
 * <p>Each entry remembers what kind of block it was marked as. It leaves the queue
 * when that is no longer what stands there: mined (air), washed out, or replaced by
 * something else. A block the server puts back after a mine it refused is the same
 * kind again, so it stays queued and is mined again.
 *
 * <p>Kinds compare with the deepslate variant folded in, so a vein of iron that runs
 * from stone into deepslate is one vein.
 */
public final class MineQueue {

    /** Every neighbour sharing a face, an edge or a corner: ore veins touch diagonally. */
    private static final int REACH = 1;

    private final Map<Vec3i, String> blocks = new LinkedHashMap<>();

    /** @return the queued blocks, in the order they were marked */
    public Collection<Vec3i> all() {
        return Collections.unmodifiableSet(blocks.keySet());
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public boolean contains(Vec3i block) {
        return blocks.containsKey(block);
    }

    /** @return whether it was added: something breakable, not queued already */
    public boolean add(Vec3i block) {
        if (blocks.containsKey(block) || !Digging.breakable(block)) {
            return false;
        }
        blocks.put(block, kindAt(block));
        return true;
    }

    /**
     * Adds the block and every block of its kind joined to it, nearest first, up to
     * {@code limit} in all. The flood stops at blocks {@code allowed} refuses (too
     * far away to keep), so a vein never runs out past them.
     *
     * @return how many were added
     */
    public int addVein(Vec3i origin, int limit, Predicate<Vec3i> allowed) {
        String kind = kindAt(origin);
        if (kind.isEmpty()) {
            return 0;
        }
        int added = 0;
        Deque<Vec3i> open = new ArrayDeque<>();
        Set<Vec3i> seen = new HashSet<>();
        open.add(origin);
        seen.add(origin);
        while (!open.isEmpty() && added < limit) {
            Vec3i block = open.poll();
            if (add(block)) {
                added++;
            }
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dy = -REACH; dy <= REACH; dy++) {
                    for (int dz = -REACH; dz <= REACH; dz++) {
                        Vec3i next = block.add(dx, dy, dz);
                        if (seen.add(next) && allowed.test(next) && kind.equals(kindAt(next))) {
                            open.add(next);
                        }
                    }
                }
            }
        }
        return added;
    }

    public boolean remove(Vec3i block) {
        return blocks.remove(block) != null;
    }

    public void clear() {
        blocks.clear();
    }

    /**
     * Drops every block that is no longer what was marked (mined, or replaced), and
     * every block more than {@code range} from the eyes: walk away and the queue goes.
     */
    public void prune(Vec3 eyes, double range) {
        double limit = range * range;
        Iterator<Map.Entry<Vec3i, String>> entries = blocks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Vec3i, String> entry = entries.next();
            Vec3i block = entry.getKey();
            if (block.center().squaredDistanceTo(eyes) > limit
                    || !entry.getValue().equals(kindAt(block)) || !Digging.breakable(block)) {
                entries.remove();
            }
        }
    }

    /** @return the queued block nearest the eyes, or null for none */
    public Vec3i nearest(Vec3 eyes, Predicate<Vec3i> usable) {
        Vec3i best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vec3i block : blocks.keySet()) {
            double distance = block.center().squaredDistanceTo(eyes);
            if (distance < bestDistance && usable.test(block)) {
                best = block;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** @return the first block marked that is usable now, or null for none */
    public Vec3i first(Predicate<Vec3i> usable) {
        for (Vec3i block : blocks.keySet()) {
            if (usable.test(block)) {
                return block;
            }
        }
        return null;
    }

    /** The block's kind: its id, with deepslate variants folded into the plain one. Empty for air. */
    private static String kindAt(Vec3i block) {
        if (Blocks.isAir(block)) {
            return "";
        }
        return Blocks.idAt(block).replace("deepslate_", "");
    }
}
