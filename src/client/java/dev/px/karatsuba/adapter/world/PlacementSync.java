package dev.px.karatsuba.adapter.world;

import dev.px.core.Core;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.PacketEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.network.PlacementResultEvent;
import dev.px.karatsuba.adapter.mixin.network.ClientWorldAccessor;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Follows every block the client places ahead of the server until the server
 * settles it, so a ghost block is found and taken back rather than left standing.
 *
 * <h2>What the game already does</h2>
 * A block click goes out numbered (1.21.4, {@code sendSequencedPacket}): the
 * client shows the block at once and books it in the world's
 * {@code PendingUpdateManager}. The server answers every click with the real state
 * of the block clicked and the one beside it, then acknowledges the number
 * ({@code PlayerActionResponseS2CPacket}). Server block updates for booked cells
 * are held until then, and the acknowledgement puts the cell to whatever the
 * server said. A refused placement corrects itself within a round trip.
 *
 * <h2>What this adds</h2>
 * <ul>
 *   <li><b>An answer per placement.</b> The acknowledgement says only "up to number
 *       N"; this checks each booked cell after it and says whether the block is
 *       there: {@link PlacementResult#CONFIRMED} or {@link PlacementResult#REJECTED}.</li>
 *   <li><b>No answer at all.</b> A click the server never saw &mdash; dropped by
 *       a plugin, cancelled on the way out &mdash; is never acknowledged, and the
 *       game would show the block until something else touched that cell. After
 *       a timeout this acknowledges it locally ({@code handlePlayerActionResponse}),
 *       which takes the prediction back exactly as a server answer would, and
 *       reports {@link PlacementResult#TIMED_OUT}. There is no packet that asks the
 *       server to resend one block; taking the guess back is the honest fallback.</li>
 * </ul>
 *
 * <p>Answers are read at the start of the tick after the acknowledgement was
 * applied, so the cell holds the server's state by then. Each is told to the
 * caller's callback and posted as {@link PlacementResultEvent}.
 *
 * <p><b>Game thread only.</b>
 */
public final class PlacementSync {

    /** The least wait for an answer, in ticks, however low the ping. */
    private static final int MIN_TIMEOUT_TICKS = 10;

    private static final List<Pending> PENDING = new ArrayList<>();

    /** The highest number the server has acknowledged in this world. */
    private static int acknowledged = -1;
    private static long tick;

    private PlacementSync() {
    }

    /** Listens for acknowledgements and settles placements once a tick. From {@code MinecraftAdapter.install}. */
    public static void install(Core core) {
        core.getBus().on(PacketEvent.class, event -> {
            if (event.getPhase() == PacketEvent.Phase.APPLIED
                    && event.getPacket() instanceof PlayerActionResponseS2CPacket response) {
                acknowledged = Math.max(acknowledged, response.sequence());
            }
        });
        // Ahead of every module, so a placement answered last tick is settled
        // before anything plans this tick's.
        core.getBus().on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                tick++;
                settle();
            }
        });
        core.getBus().on(WorldEvent.class, event -> {
            // Numbers start again in a new world, and nothing booked in the old one is answered.
            PENDING.clear();
            acknowledged = -1;
        });
    }

    /**
     * Books a placement the client just predicted. Called by {@code Interactions.click}
     * straight after the click, while the number it went out with is still the latest.
     *
     * @param world    the world it was predicted in
     * @param cell     where the block now shows
     * @param placed   the block that shows there
     * @param then     told how it settles; may be null
     */
    public static void track(ClientWorld world, BlockPos cell, Block placed, Consumer<PlacementResult> then) {
        int sequence = ((ClientWorldAccessor) world).karatsuba$getPendingUpdateManager().getSequence();
        PENDING.add(new Pending(Voxels.toVec3i(cell), cell.toImmutable(), placed, sequence, tick, then));
    }

    /** @return placements waiting on the server */
    public static int pendingCount() {
        return PENDING.size();
    }

    /** @return whether a block the client placed in this cell is still waiting on the server */
    public static boolean isPending(Vec3i cell) {
        for (Pending pending : PENDING) {
            if (pending.cell.equals(cell)) {
                return true;
            }
        }
        return false;
    }

    private static void settle() {
        if (PENDING.isEmpty()) {
            return;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            PENDING.clear();
            return;
        }
        long timeout = timeoutTicks();
        Iterator<Pending> each = PENDING.iterator();
        List<Pending> settled = new ArrayList<>();
        List<PlacementResult> results = new ArrayList<>();
        int expire = -1;
        while (each.hasNext()) {
            Pending pending = each.next();
            if (pending.sequence <= acknowledged) {
                boolean there = world.getBlockState(pending.pos).isOf(pending.placed);
                settled.add(pending);
                results.add(there ? PlacementResult.CONFIRMED : PlacementResult.REJECTED);
                each.remove();
            } else if (tick - pending.sentAt > timeout) {
                expire = Math.max(expire, pending.sequence);
                settled.add(pending);
                results.add(PlacementResult.TIMED_OUT);
                each.remove();
            }
        }
        if (expire >= 0) {
            // Takes back every prediction up to it, as the server's own answer would.
            world.handlePlayerActionResponse(expire);
            // Anything still waiting with a lower number was taken back with it.
            Iterator<Pending> rest = PENDING.iterator();
            while (rest.hasNext()) {
                Pending pending = rest.next();
                if (pending.sequence <= expire) {
                    settled.add(pending);
                    results.add(PlacementResult.TIMED_OUT);
                    rest.remove();
                }
            }
        }
        for (int i = 0; i < settled.size(); i++) {
            report(settled.get(i), results.get(i));
        }
    }

    private static void report(Pending pending, PlacementResult result) {
        if (pending.then != null) {
            try {
                pending.then.accept(result);
            } catch (RuntimeException failure) {
                Core.get().getLogger().error("A placement callback threw", failure);
            }
        }
        Events.post(new PlacementResultEvent(pending.cell, result));
    }

    /** A round trip and then some: twice the ping in ticks, plus ten, never under {@link #MIN_TIMEOUT_TICKS}. */
    private static long timeoutTicks() {
        return Math.max(MIN_TIMEOUT_TICKS, 2L * (long) Math.ceil(Core.lag().getPing() / 50d) + 10L);
    }

    private record Pending(Vec3i cell, BlockPos pos, Block placed, int sequence, long sentAt,
                           Consumer<PlacementResult> then) {
    }
}
