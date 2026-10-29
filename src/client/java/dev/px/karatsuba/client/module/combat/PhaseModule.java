package dev.px.karatsuba.client.module.combat;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.flow.Control;
import dev.px.core.flow.Flow;
import dev.px.core.flow.FlowHandle;
import dev.px.core.flow.Span;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.math.Box;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.ServerTeleportEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.projectile.ProjectileKit;
import dev.px.karatsuba.adapter.projectile.Projectiles;
import dev.px.karatsuba.adapter.projectile.Shot;
import dev.px.karatsuba.adapter.movement.BlockPushOut;
import dev.px.karatsuba.adapter.world.BlastResistance;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.automation.step.Throw;
import dev.px.karatsuba.client.automation.step.ToHotbar;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.projectile.Shooter;
import dev.px.projectile.flight.Hit;
import dev.px.projectile.flight.Trajectory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pearl phase: an ender pearl thrown so the server's teleport leaves you clipped
 * into the wall beside you, and with {@link Mode#WALK}, walked on through it.
 *
 * <h2>Where a pearl puts you</h2>
 * The teleport does not check that a player fits where it lands, which is the
 * whole trick. Where it puts you depends on the server:
 * <ul>
 *   <li><b>1.21.2 and later</b> ({@link Server#MODERN}): where the pearl was at
 *       the <em>start</em> of the tick it hit, not where it hit
 *       ({@code EnderPearlEntity.onCollision} → {@code getLastRenderPos()}). A
 *       pearl thrown at your feet hits in its first tick and puts you back where
 *       it left your hand, which is why aiming at the corner, as Alien does for
 *       older servers, does nothing here. So the throw is chosen by flying it: of
 *       the rotations tried, the one whose pearl is in the corner, just above the
 *       floor, at the start of the tick it lands.</li>
 *   <li><b>1.21.1 and older</b> ({@link Server#LEGACY}): where it hit. The throw
 *       that lands it on the corner.</li>
 * </ul>
 * Either way the throw is checked against the projectile library's flight
 * through the world, and the module checks after the teleport that you really are
 * inside a block, saying so if not.
 *
 * <h2>Walking through ({@link Mode#WALK})</h2>
 * Vanilla collision ignores a block the moving box's leading face is already in:
 * only the cells past that face can stop it. Once the pearl leaves you overlapping
 * a one-thick wall, walking on carries you through it and out the open side. Grim's
 * Phase check flags only a block the box meets that it did not meet the tick
 * before, so this is not flagged; Grim simulates the same collision. The walk is
 * real keys through Core's controls, and stops rather than step into any solid
 * block the box is not already in. Vanilla's push out of blocks works against you
 * up to the middle of the wall and with you after, and Grim simulates it too. See
 * {@code anticheat/Grim.md}, "Being inside blocks".
 *
 * <h2>Built from the automation's steps</h2>
 * A flow, started once you stand on the ground: a pearl to the hotbar if the
 * hotbar has none, crystals, item frames and paintings in the way hit, the throw
 * ({@link Throw#pearlAlong}), the teleport awaited and checked, and for Walk, the
 * walk. The module turns itself off when the flow ends.
 *
 * <p>Alien's packet-break of scaffolding (a start and a stop with no digging) is
 * left out: it is a fabricated dig.
 */
@ModuleInfo(name = "Phase", description = "Pearls you into the wall beside you, and can walk you through it",
        category = "Combat")
public final class PhaseModule extends ClientModule {

    private static final String PEARL = "ender_pearl";

    /** Ticks to clear what is in the way before giving up: a crystal can respawn as fast as it breaks. */
    private static final int CLEAR_PATIENCE = 20;
    /** Ticks to wait for the server's teleport after the throw. */
    private static final long TELEPORT_PATIENCE = 40L;
    /** Ticks to walk through a wall before giving up. */
    private static final int WALK_PATIENCE = 80;

    private static final double HALF_WIDTH = 0.3d;
    private static final double HEIGHT = 1.8d;

    /** Degrees: the shallowest throw tried. */
    private static final float MIN_PITCH = 20f;

    /** Blocks: how far the landing may be from the spot aimed for, across the floor. */
    private static final double LANDING_TOLERANCE = 0.12d;
    /** Blocks above the floor the landing may be: higher, and you come down on top of the wall. */
    private static final double LANDING_HEIGHT = 0.9d;
    /** Blocks the box may move in a tick of walking, for the look ahead. */
    private static final double STEP = 0.35d;

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.PEARL)
            .describe("PEARL clips you into the corner; WALK then walks you through a one-thick wall");

    private final EnumSetting<Server> server = enumOf("Server", Server.MODERN)
            .describe("MODERN for 1.21.2+ servers, where a pearl puts you where it was the tick before it landed; LEGACY for older ones, where it hit");

    private final BooleanSetting onlyGround = bool("Only Ground", true)
            .describe("Wait until you stand on the ground or a ladder: the throw is worked out from standing still");

    private final BooleanSetting onlyHard = bool("Only Hard", false)
            .describe("Only phase into obsidian, bedrock or ender chests")
            .visibleWhen(() -> mode.get() == Mode.PEARL);

    private final NumberSetting<Float> depth = number("Depth", 0.4f, 0.25f, 0.45f)
            .step(0.05f)
            .describe("How far from your block's middle toward the wall you land; more is deeper in, and harder to throw");

    private final BooleanSetting clear = bool("Clear", true)
            .describe("First hit any crystal, item frame or painting where the pearl flies or you land");

    private final BooleanSetting stay = bool("Stay", true)
            .describe("Stay in the blocks: no push back out, which Grim allows. Leave by walking out or turning Phase off");

    private FlowHandle flow;

    /** Landed and staying in the blocks, with the push out held off, until you leave them. */
    private boolean sitting;

    /** The throw last worked out, and where you stood for it: worked out again only when you move. */
    private Vec3 thrownFrom;
    private Vec2 throwRotation;

    public PhaseModule() {
        // Held off from the throw on (it also helps Walk through the wall), and for as long as you sit in the blocks.
        BlockPushOut.skipWhen(() -> isEnabled() && stay.isOn() && (sitting || flow != null));
    }

    @Override
    protected void onEnable() {
        flow = null;
        sitting = false;
        thrownFrom = null;
        if (!Players.present() || Inventories.count(PEARL) == 0) {
            Notices.info(Source.MODULE_MESSAGES, "Phase", "No ender pearls");
            setEnabled(false);
        }
    }

    @Override
    protected void onDisable() {
        if (flow != null) {
            flow.cancel();
            flow = null;
        }
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            return;
        }
        if (sitting) {
            // Walked out, or pushed by something else: nothing left to hold.
            if (!insideSolid(body())) {
                setEnabled(false);
            }
            return;
        }
        if (flow != null) {
            if (flow.isDone()) {
                boolean landed = flow.getState() == FlowHandle.State.DONE;
                if (!landed) {
                    Notices.info(Source.MODULE_MESSAGES, "Phase", "Could not phase: " + flow.getReason());
                }
                flow = null;
                if (landed && mode.get() == Mode.PEARL && stay.isOn()) {
                    sitting = true;
                    return;
                }
                setEnabled(false);
            }
            return;
        }
        if (onlyGround.isOn() && !Players.onGround() && !Players.climbing()) {
            return;
        }
        Plan plan = mode.get() == Mode.WALK ? wallToWalk() : corner();
        if (plan == null) {
            Notices.info(Source.MODULE_MESSAGES, "Phase", mode.get() == Mode.WALK
                    ? "No one-thick wall beside you with open space behind it"
                    : onlyHard.isOn() ? "No hard wall beside you" : "No wall beside you");
            setEnabled(false);
            return;
        }
        flow = Core.flows().start("Phase", phase(plan), RotationPriority.HIGH);
    }

    @Override
    public String getDisplayInfo() {
        if (sitting) {
            return "Inside";
        }
        return flow != null ? (mode.get() == Mode.WALK ? "Walk" : "Pearl") : "";
    }

    // ---------------------------------------------------------------- the flow

    private Step phase(Plan plan) {
        Step pearlInHotbar = Flow.firstOf(
                Flow.check("a pearl in the hotbar", c -> Inventories.hotbarHas(PEARL)),
                new ToHotbar("an ender pearl", () -> Inventories.findInInventory(PEARL)));
        Step landed = Flow.sequence(
                Flow.awaitEvent(ServerTeleportEvent.class).timeout(Span.ticks(TELEPORT_PATIENCE)),
                Flow.check("inside the wall", c -> insideSolid(body())));
        Step pearl = Flow.sequence(pearlInHotbar, clearWay(plan.target),
                Throw.pearlAlong(() -> throwFor(plan.target)), landed);
        return (plan.through == null ? pearl : pearl.then(walkThrough(plan))).named("phase");
    }

    /** Hits what would stop the pearl or fill the landing, one a tick, until none is left. */
    private Step clearWay(Vec3 target) {
        int[] waited = {0};
        return Flow.step("clear the way")
                .start(c -> waited[0] = 0)
                .tick(c -> {
                    if (!clear.isOn()) {
                        return Status.DONE;
                    }
                    List<GameEntity> inTheWay = Entities.fixturesIn(path(target));
                    if (inTheWay.isEmpty()) {
                        return Status.DONE;
                    }
                    if (++waited[0] > CLEAR_PATIENCE) {
                        return c.fail("could not clear " + inTheWay.get(0).getName());
                    }
                    Services.actions().submit(this, Action.attack(inTheWay.get(0).getId()).priority(ClaimPriority.HIGH));
                    return Status.RUNNING;
                })
                .stop((c, why) -> Services.actions().release(this));
    }

    /**
     * Walks on through the wall with real keys until the box is out the far side,
     * never into a solid block it is not already in.
     */
    private Step walkThrough(Plan plan) {
        int[] walked = {0};
        Vec3i way = plan.through;
        float yaw = (float) Math.toDegrees(Math.atan2(-way.getX(), way.getZ()));
        return Flow.step("walk through").uses(Control.MOVEMENT)
                .start(c -> walked[0] = 0)
                .tick(c -> {
                    Box box = body();
                    if (pastFace(box, plan)) {
                        return Status.DONE;
                    }
                    if (++walked[0] > WALK_PATIENCE) {
                        return c.fail("stuck in the wall");
                    }
                    Box next = box.offset(way.getX() * STEP, 0d, way.getZ() * STEP);
                    if (entersNewSolid(box, next)) {
                        return c.fail("the way through is blocked");
                    }
                    c.move(MovementInput.forward(yaw).withSprint(true));
                    return Status.RUNNING;
                });
    }

    /** @return whether the box is wholly past the wall's far face */
    private static boolean pastFace(Box box, Plan plan) {
        Vec3i way = plan.through;
        if (way.getX() != 0) {
            return way.getX() > 0 ? box.getMin().getX() >= plan.farFace : box.getMax().getX() <= plan.farFace;
        }
        return way.getZ() > 0 ? box.getMin().getZ() >= plan.farFace : box.getMax().getZ() <= plan.farFace;
    }

    // --------------------------------------------------------------- the throw

    /**
     * @return a rotation whose pearl puts you on {@code target}, as this server
     *         teleports; null when none does. Worked out again only when you move
     */
    private Vec2 throwFor(Vec3 target) {
        Vec3 at = Players.position();
        if (thrownFrom != null && thrownFrom.squaredDistanceTo(at) < 1e-6) {
            return throwRotation;
        }
        thrownFrom = at;
        throwRotation = searchThrow(target);
        return throwRotation;
    }

    /** A coarse sweep round the target, then a fine one round the best of it. */
    private Vec2 searchThrow(Vec3 target) {
        ProjectileKit kit = Projectiles.kit();
        Shot shot = kit.shot(Shot.Item.ENDER_PEARL);
        Shooter you = kit.you();
        float towardYaw = Players.eyePosition().rotationTo(target).getYaw();

        Vec2 best = null;
        double bestMiss = Double.POSITIVE_INFINITY;
        // Downward only: a landing beside your feet is a throw down, and one thrown up flies for seconds.
        for (float pitch = MIN_PITCH; pitch <= 90f; pitch += 2f) {
            for (float yaw = towardYaw - 60f; yaw <= towardYaw + 60f; yaw += 3f) {
                double miss = miss(shot.trajectory(you, Vec2.of(yaw, pitch)), target);
                if (miss < bestMiss) {
                    bestMiss = miss;
                    best = Vec2.of(yaw, pitch);
                }
            }
        }
        if (best == null) {
            return null;
        }
        Vec2 coarse = best;
        for (float pitch = coarse.getPitch() - 2f; pitch <= coarse.getPitch() + 2f; pitch += 0.25f) {
            for (float yaw = coarse.getYaw() - 3f; yaw <= coarse.getYaw() + 3f; yaw += 0.25f) {
                if (pitch < MIN_PITCH || pitch > 90f) {
                    continue;
                }
                double miss = miss(shot.trajectory(you, Vec2.of(yaw, pitch)), target);
                if (miss < bestMiss) {
                    bestMiss = miss;
                    best = Vec2.of(yaw, pitch);
                }
            }
        }
        return bestMiss <= LANDING_TOLERANCE ? best : null;
    }

    /**
     * @return how far across the floor from {@code target} this flight's
     *         teleport puts you; infinity when it lands on nothing, on an entity,
     *         or too high to stay in the wall
     */
    private double miss(Trajectory flight, Vec3 target) {
        Hit hit = flight.getHit();
        if (hit == null || hit.getType() != Hit.Type.BLOCK) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 landing;
        if (server.get() == Server.MODERN) {
            // The start of the tick it hit in; a first-tick hit starts from your hand.
            if (hit.getTick() < 2) {
                return Double.POSITIVE_INFINITY;
            }
            landing = flight.at(hit.getTick() - 1);
        } else {
            landing = hit.getPoint();
        }
        double above = landing.getY() - target.getY();
        if (above < -0.01d || above > LANDING_HEIGHT) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = landing.getX() - target.getX();
        double dz = landing.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** @return the space from your body to the landing: where the pearl flies and where you arrive */
    private static Box path(Vec3 target) {
        Vec3 at = Players.position();
        return Box.of(Math.min(at.getX(), target.getX()) - HALF_WIDTH, at.getY(),
                Math.min(at.getZ(), target.getZ()) - HALF_WIDTH,
                Math.max(at.getX(), target.getX()) + HALF_WIDTH, at.getY() + HEIGHT,
                Math.max(at.getZ(), target.getZ()) + HALF_WIDTH);
    }

    // ------------------------------------------------------------ where to go

    /**
     * Pearl: of your block's four corners with a wall beside them, the nearest,
     * {@code Depth} from the middle on both axes.
     */
    private Plan corner() {
        Vec3i feet = Players.blockPosition();
        Vec3 at = Players.position();
        double reach = depth.getFloat();
        Plan best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                if (!walled(feet.add(x, 0, 0)) && !walled(feet.add(0, 0, z)) && !walled(feet.add(x, 0, z))) {
                    continue;
                }
                Vec3 target = Vec3.of(feet.getX() + 0.5d + x * reach, at.getY(), feet.getZ() + 0.5d + z * reach);
                double distance = horizontalSquared(target, at);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Plan(target, null, 0d);
                }
            }
        }
        return best;
    }

    /**
     * Walk: a side of your block walled one thick, at your feet and head, with
     * open space behind it at both; the nearest. The landing is {@code Depth}
     * toward it, centred on the side, so the box is in that wall and nothing else.
     */
    private Plan wallToWalk() {
        Vec3i feet = Players.blockPosition();
        Vec3 at = Players.position();
        double reach = depth.getFloat();
        Plan best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Vec3i way : new Vec3i[] {Vec3i.of(1, 0, 0), Vec3i.of(-1, 0, 0), Vec3i.of(0, 0, 1), Vec3i.of(0, 0, -1)}) {
            Vec3i wall = feet.add(way);
            Vec3i beyond = wall.add(way);
            if (!Blocks.isSolid(wall) || !Blocks.isSolid(wall.up()) || !Blocks.isOpen(beyond) || !Blocks.isOpen(beyond.up())) {
                continue;
            }
            Vec3 target = Vec3.of(feet.getX() + 0.5d + way.getX() * reach, at.getY(),
                    feet.getZ() + 0.5d + way.getZ() * reach);
            double farFace = way.getX() != 0
                    ? (way.getX() > 0 ? wall.getX() + 1 : wall.getX())
                    : (way.getZ() > 0 ? wall.getZ() + 1 : wall.getZ());
            double distance = horizontalSquared(target, at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Plan(target, way, farFace);
            }
        }
        return best;
    }

    private boolean walled(Vec3i cell) {
        return onlyHard.isOn() ? Blocks.blastResistance(cell) != BlastResistance.NONE : Blocks.isSolid(cell);
    }

    // ------------------------------------------------------------- the body

    private static Box body() {
        return Box.around(Players.position(), HALF_WIDTH * 2, HEIGHT);
    }

    /** @return whether the box is in any solid block */
    private static boolean insideSolid(Box box) {
        return !solidCells(box).isEmpty();
    }

    /** @return whether {@code next} would be in a solid block {@code now} is not in: Grim's Phase flag */
    private static boolean entersNewSolid(Box now, Box next) {
        Set<Vec3i> before = solidCells(now);
        for (Vec3i cell : solidCells(next)) {
            if (!before.contains(cell)) {
                return true;
            }
        }
        return false;
    }

    /** @return the cells with something solid in them that the box reaches into */
    private static Set<Vec3i> solidCells(Box box) {
        Set<Vec3i> cells = new HashSet<>();
        Vec3 min = box.getMin();
        Vec3 max = box.getMax();
        for (int x = (int) Math.floor(min.getX() + 1e-7); x <= (int) Math.floor(max.getX() - 1e-7); x++) {
            for (int y = (int) Math.floor(min.getY() + 1e-7); y <= (int) Math.floor(max.getY() - 1e-7); y++) {
                for (int z = (int) Math.floor(min.getZ() + 1e-7); z <= (int) Math.floor(max.getZ() - 1e-7); z++) {
                    Vec3i cell = Vec3i.of(x, y, z);
                    if (Blocks.isSolid(cell)) {
                        cells.add(cell);
                    }
                }
            }
        }
        return cells;
    }

    private static double horizontalSquared(Vec3 a, Vec3 b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Where the pearl should put you; for Walk, the way through the wall and the
     * coordinate of its far face on that axis.
     */
    private record Plan(Vec3 target, Vec3i through, double farFace) {
    }

    public enum Mode {
        /** Clip into the corner. */
        PEARL,
        /** Clip into a one-thick wall, then walk out the other side. */
        WALK
    }

    public enum Server {
        /** 1.21.2 and later: the pearl's position at the start of the tick it hit. */
        MODERN,
        /** 1.21.1 and older: where the pearl hit. */
        LEGACY
    }
}
