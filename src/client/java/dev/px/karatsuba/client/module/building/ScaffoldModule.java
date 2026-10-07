package dev.px.karatsuba.client.module.building;

import dev.px.combat.place.Click;
import dev.px.combat.place.Plan;
import dev.px.combat.place.PlacementPlanner;
import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Direction;
import dev.px.core.math.MathUtil;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.render.PlacementBox;
import dev.px.karatsuba.client.module.movement.Ledges;
import dev.px.karatsuba.client.module.movement.TickKey;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.settings.ClientSettings;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places blocks under the player as they walk, bridge or jump.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>Normal</b> faces the block it is building against and stays turned, so
 *       the player walks the bridge backwards as far as the server can tell. Slow
 *       (no sprint while the keys say backwards) and as safe as scaffolding gets.</li>
 *   <li><b>Telly</b> sprint-jumps facing forward and turns to place only once in
 *       the air, then turns back before landing, so every take-off is a forward
 *       sprint jump. Builds at the take-off height, a block under the feet.</li>
 *   <li><b>Tower</b>, in either mode, while the player holds jump: builds straight
 *       up, placing under the feet once a jump clears the next block.</li>
 * </ul>
 *
 * <p>Normal mode's Edge Sneak crouches at the ledge until the block is down.
 * Without it the click lands a tick or two after the player starts to fall, and
 * Grim only accepts a side face once the eyes are past it, which walking speed
 * barely allows before the feet leave the edge.
 *
 * <h2>Grim, checked against 2.3.73's own bytecode</h2>
 * <ul>
 *   <li><b>RotationPlace</b> casts the look of the movement packet <em>after</em>
 *       the click (and the one before, its {@code lastYaw}/{@code lastPitch}) from
 *       the player's new position, and flags a click it does not hit. The click
 *       goes out at the start of the tick after the turn, through
 *       {@code Services.actions()}, which sends it only if the rotation the server
 *       has hits that face. A look is only chosen if it hits from where the eyes
 *       will be after this tick's move and after the next.</li>
 *   <li><b>MultiPlace</b> flags a second, different click before the next tick
 *       packet, so one block a tick, never a burst.</li>
 *   <li><b>AimModulo360</b> flags a yaw jump of over 320&deg;; the Rotations
 *       adapter writes every yaw unwrapped next to the last one sent.</li>
 *   <li><b>DuplicateRotPlace</b> flags two clicks whose preceding turns were the
 *       same size (over 2&deg;, to 0.0001&deg;): what a fixed turn speed onto a
 *       fixed point produces, and every telly flip is 180&deg;. A turn that would
 *       repeat the last click's stops a degree short and finishes next tick.</li>
 *   <li><b>PositionPlace</b> flags clicking a face the eyes are behind. Clicks are
 *       planned with the strict style, which only uses faces turned toward the eyes.</li>
 *   <li><b>Simulation</b>: the jump is the real jump key, and the turns are silent
 *       rotations under the client's Move Fix, which rewrites the keys to match the
 *       yaw the move is made along. With Move Fix off, the walk under a silent
 *       rotation is a move the reported keys do not explain; the module says so.</li>
 * </ul>
 * The rest (reach, one click a tick, holding the block, ghost blocks) is the
 * action service's, the same as for every placing module.
 *
 * <p>Rotations are always silent and always this module's own, whatever the
 * client's Rotate setting says: a scaffold that does not turn cannot place where
 * a strict server will accept it, and a camera that turns makes the keys walk the
 * wrong way.
 */
@ModuleInfo(name = "Scaffold", description = "Places blocks under you as you walk or bridge", category = "Building")
public final class ScaffoldModule extends Module {

    public enum Mode {
        NORMAL,
        TELLY
    }

    /** How far from the middle of a face the aim may wander, each way: the inner half of it. */
    private static final double AIM_SPREAD = 0.25d;

    /** Below this many degrees from the camera, the head is facing forward. */
    private static final float FACING = 1f;

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.TELLY)
            .describe("NORMAL faces backwards the whole time; TELLY sprint-jumps forward and turns only in the air");

    private final NumberSetting<Integer> tellyTicks = integer("Telly Ticks", 1, 1, 5)
            .describe("Ticks in the air, still facing forward, before turning to place")
            .visibleWhen(() -> mode.get() == Mode.TELLY);

    private final BooleanSetting keepY = bool("Keep Y", false)
            .describe("Keep building at the height you started from when you jump; off builds up under you")
            .visibleWhen(() -> mode.get() == Mode.NORMAL);

    private final BooleanSetting edgeSneak = bool("Edge Sneak", true)
            .describe("Crouch at the edge until the next block is down, so you never walk off it")
            .visibleWhen(() -> mode.get() == Mode.NORMAL);

    private final BooleanSetting tower = bool("Tower", true)
            .describe("Holding jump builds straight up under you, in either mode");

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the block being placed");

    private final ColorSetting renderColor = color("Render Colour", Color.of(85, 170, 255, 200))
            .visibleWhen(render);

    private final BooleanSetting fill = bool("Fill", true)
            .visibleWhen(render);

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.3f, 0.05f, 1f)
            .step(0.05f)
            .describe("The fill's opacity, as a share of the colour's")
            .visibleWhen(() -> render.isOn() && fill.isOn());

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(render);

    private final NumberSetting<Integer> linger = integer("Linger", 10, 1, 40)
            .describe("Ticks the box stays after the last placement before fading out")
            .visibleWhen(render);

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop the box in when building starts and fade it out when it stops")
            .visibleWhen(render);

    private final BooleanSetting interpolate = bool("Interpolate", true)
            .describe("Slide the box from block to block instead of fading one out and another in")
            .visibleWhen(render);

    private final NumberSetting<Integer> moveTime = integer("Move Time", 150, 50, 500)
            .step(10)
            .describe("Milliseconds the slide to the next block takes")
            .visibleWhen(() -> render.isOn() && interpolate.isOn());

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, linger, animations, interpolate,
                    moveTime);

    // ------------------------------------------------------------------ state

    private final TickKey jump = new TickKey(Inputs.Control.JUMP);
    private final TickKey sneak = new TickKey(Inputs.Control.SNEAK);
    private final PlacementBox box = new PlacementBox();

    private PlacementPlanner planner;

    private long tick;

    /** The last block a click was sent for, and the tick it went: what the box shows. */
    private Vec3i placedOn;
    private long placedAt = Long.MIN_VALUE / 2;

    /** The height last stood at; the bridge goes one below it. Null until known. */
    private Integer baseY;

    /** Ticks since last on the ground. */
    private int airTicks;

    /** The face last aimed at, and the point on it, kept until the target changes. */
    private Vec3i aimBlock;
    private Direction aimFace;
    private Vec3 aimPoint;

    /** Normal mode: the last look placed with, held while walking so the head does not flick back and forth. */
    private Vec2 heldLook;

    /** The rotation the server had two ticks back, and the turn before the last click: for DuplicateRotPlace. */
    private Vec2 sentBefore = Vec2.ZERO;
    private float lastPlacedTurn = -1f;

    private String display = "";

    @Override
    protected void onEnable() {
        planner = build(Combat.kit());
        baseY = null;
        airTicks = 0;
        aimBlock = null;
        aimFace = null;
        aimPoint = null;
        heldLook = null;
        placedOn = null;
        display = "";
    }

    private static <E> PlacementPlanner build(CombatKit<E> kit) {
        return kit.planner(kit.strictStyle())
                .perTick(() -> 1)
                .supports(1)
                .build();
    }

    @Override
    protected void onDisable() {
        jump.release();
        sneak.release();
        Core.rotations().release(this);
        Services.actions().release(this);
        box.clear();
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        // After the action service has reported last tick's clicks, which read this as it was.
        Vec2 seen = Players.serverRotation();
        updateBox();
        if (planner == null || !Players.present()) {
            display = "";
            return;
        }
        sentBefore = seen;
        String item = Inventories.bestBuildingBlock();
        if (item == null) {
            display = "No blocks";
            faceCamera();
            return;
        }
        display = ClientSettings.interaction().moveFix() ? String.valueOf(Inventories.count(item)) : "Move Fix off";

        Vec3 position = Players.position();
        boolean ground = Players.onGround();
        int standing = (int) Math.floor(position.getY() + 1.0E-3d);
        if (ground || baseY == null) {
            baseY = standing;
        }
        airTicks = ground ? 0 : airTicks + 1;

        // Read before this module presses anything, so it is the player's own jump.
        boolean towering = tower.isOn() && Inputs.pressed(Inputs.Control.JUMP) && !jump.isHeld();
        boolean telly = mode.get() == Mode.TELLY;
        int floorY = !towering && (telly || keepY.isOn()) ? baseY - 1 : standing - 1;
        Plan.Step step = next(position, floorY);

        if (towering) {
            tower(step, item, position, standing);
        } else if (telly) {
            telly(ground, step, item);
        } else {
            normal(ground, step, item);
        }
    }

    // ------------------------------------------------------------------ modes

    /**
     * Faces the build and stays turned. With Edge Sneak, crouches whenever walking
     * on would leave the ground: vanilla clips the move at the ledge, which stops
     * the fall, holds the player still while the click goes out, and leaves their
     * eyes past the face they click &mdash; what Grim's PositionPlace and
     * RotationPlace want, and what shift-bridging by hand looks like.
     */
    private void normal(boolean ground, Plan.Step step, String item) {
        if (edgeSneak.isOn() && ground && Ledges.moving() && walksOff()) {
            sneak.press();
        }
        if (step != null) {
            place(step, item);
        } else if (heldLook != null && Ledges.moving()) {
            turnTo(heldLook, turnStep());
        }
    }

    /**
     * The player holds jump: build straight up. Looks down at the block stood on
     * between placements, so the turn is already made when the jump clears the
     * next block's height.
     */
    private void tower(Plan.Step step, String item, Vec3 position, int standing) {
        if (step != null) {
            place(step, item);
            return;
        }
        Vec3i under = Vec3i.of((int) Math.floor(position.getX()), standing - 1, (int) Math.floor(position.getZ()));
        turnToPlace(lookAt(under, Direction.UP));
    }

    /** @return whether walking on with the keys held leaves the ground within two ticks */
    private static boolean walksOff() {
        for (MotionState state : Ledges.ahead(Ledges.held(false, false), 2)) {
            if (!state.isOnGround()) {
                return true;
            }
        }
        return false;
    }

    /**
     * On the ground: face forward and jump. In the air: wait {@code Telly Ticks},
     * place while there is anything to place, and turn back in time to land facing
     * forward, so the next jump is a forward sprint jump again.
     */
    private void telly(boolean ground, Plan.Step step, String item) {
        if (ground) {
            boolean forward = faceForward();
            if (forward && canJump()) {
                jump.press();
            } else if (forward && step != null) {
                // Not jumping (sneaking, or standing still at an edge): place from here.
                place(step, item);
            }
            return;
        }
        if (airTicks < tellyTicks.getInt() || step == null || landingWithin(ticksToFaceForward() + 1)) {
            faceForward();
            return;
        }
        place(step, item);
    }

    private static boolean canJump() {
        return Inputs.pressed(Inputs.Control.FORWARD)
                && !Inputs.pressed(Inputs.Control.SNEAK)
                && !Players.inWater();
    }

    /** @return whether, keeping the keys held, the player is on the ground again within {@code ticks} */
    private static boolean landingWithin(int ticks) {
        for (MotionState state : Ledges.ahead(Ledges.held(false, false), ticks)) {
            if (state.isOnGround()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- placing

    /**
     * @return the next block to place: under the player now, or under where they
     *         will be in one or two ticks, at {@code floorY}; null when all are there
     */
    private Plan.Step next(Vec3 position, int floorY) {
        Vec3 velocity = Players.velocity();
        Set<Vec3i> cells = new LinkedHashSet<>();
        for (int ahead = 0; ahead <= 2; ahead++) {
            Vec3 at = position.add(velocity.scale(ahead));
            cells.add(Vec3i.of((int) Math.floor(at.getX()), floorY, (int) Math.floor(at.getZ())));
        }
        Plan plan = planner.plan(Players.eyePosition().add(velocity), cells, Players.serverRotation());
        return plan.getSteps().isEmpty() ? null : plan.getSteps().get(0);
    }

    private void place(Plan.Step step, String item) {
        Click click = step.getClick();
        Vec3i cell = step.getCell();
        Vec2 look = lookAt(click.getBlock(), click.getFace());
        heldLook = look;
        turnToPlace(look);
        Services.actions().submit(this, Action.click(click.getBlock(), click.getFace(), hitPoint(click, look))
                .holding(item)
                .rotate(false)
                .priority(ClaimPriority.HIGH)
                .then(outcome -> {
                    if (outcome == Outcome.SENT) {
                        placedOn = cell;
                        placedAt = tick;
                        lastPlacedTurn = Math.abs(MathUtil.wrapDegrees(
                                Players.serverRotation().getYaw() - sentBefore.getYaw()));
                    }
                }));
    }

    /**
     * The tick's half of the drawing: the last block placed, kept for {@code Linger}
     * ticks so the box slides along the bridge rather than flickering per block.
     */
    private void updateBox() {
        boolean showing = render.isOn() && placedOn != null && tick - placedAt <= linger.getInt();
        Box target = showing ? placedOn.toBox() : null;
        box.update(target, animations.isOn(), interpolate.isOn(), moveTime.getInt());
    }

    // -------------------------------------------------------------- the look

    /**
     * The rotation to click {@code face} of {@code block} with.
     *
     * <ol>
     *   <li><b>The one already held, if it hits.</b> No turn at all is the
     *       steadiest look there is, and a turn of nothing never trips
     *       DuplicateRotPlace.</li>
     *   <li><b>Otherwise a yaw straight behind the camera</b> (then 45&deg; either
     *       side, and so on round), with the steepest pitch from there that hits.
     *       A yaw exactly 180&deg; or a multiple of 45&deg; off the camera is one
     *       the Move Fix turns into keys exactly &mdash; W becomes S, not a
     *       diagonal &mdash; so the player walks straight down the bridge instead
     *       of drifting off it by up to 22.5&deg;.</li>
     *   <li><b>Otherwise</b>, straight at a point inside the face.</li>
     * </ol>
     *
     * A look only counts as hitting if it does from where the eyes will be after
     * this tick's move <em>and</em> the next: the server takes the rotation from
     * the first and Grim's RotationPlace casts it again from the second.
     */
    private Vec2 lookAt(Vec3i block, Direction face) {
        Vec3 eye = Players.eyePosition();
        Vec3 velocity = Players.velocity();
        Vec3 sent = eye.add(velocity);
        Vec3 checked = eye.add(velocity.scale(2d));
        double reach = Players.blockReach();

        Vec2 held = Players.serverRotation();
        if (hits(held, block, face, sent, checked, reach)) {
            return held;
        }
        float behind = Players.rotation().getYaw() + 180f;
        for (int turns : YAW_ORDER) {
            float yaw = behind + turns * 45f;
            for (float pitch = 90f; pitch > 30f; pitch -= pitch > 50f ? 1f : 10f) {
                Vec2 look = Vec2.rotation(yaw, pitch);
                if (hits(look, block, face, sent, checked, reach)) {
                    return look;
                }
            }
        }
        return sent.rotationTo(aimOn(block, face));
    }

    /** Behind the camera first, then working round both ways in eighths. */
    private static final int[] YAW_ORDER = {0, 1, -1, 2, -2, 3, -3, 4};

    private static boolean hits(Vec2 look, Vec3i block, Direction face, Vec3 sent, Vec3 checked, double reach) {
        return lands(Targets.raycast(sent, look, reach), block, face)
                && lands(Targets.raycast(checked, look, reach), block, face);
    }

    private static boolean lands(Targets.Hit hit, Vec3i block, Direction face) {
        return hit != null && hit.getBlock().equals(block) && hit.getFace() == face;
    }

    /**
     * Where the click says it hit: where the look lands, so the packet agrees with
     * the rotation even when the action service's own raycast is switched off.
     */
    private static Vec3 hitPoint(Click click, Vec2 look) {
        Targets.Hit hit = Targets.raycast(Players.eyePosition().add(Players.velocity()), look, Players.blockReach());
        return lands(hit, click.getBlock(), click.getFace()) ? hit.getPoint() : click.getHit();
    }

    /** A point well inside the face, chosen at random once per face: the fallback look's target. */
    private Vec3 aimOn(Vec3i block, Direction face) {
        if (block.equals(aimBlock) && face == aimFace && aimPoint != null) {
            return aimPoint;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 normal = face.toVector();
        double u = random.nextDouble(-AIM_SPREAD, AIM_SPREAD);
        double v = random.nextDouble(-AIM_SPREAD, AIM_SPREAD);
        Vec3 offset;
        if (normal.getX() != 0d) {
            offset = Vec3.of(0d, u, v);
        } else if (normal.getY() != 0d) {
            offset = Vec3.of(u, 0d, v);
        } else {
            offset = Vec3.of(u, v, 0d);
        }
        aimBlock = block;
        aimFace = face;
        aimPoint = block.center().add(normal.scale(0.5d)).add(offset);
        return aimPoint;
    }

    // -------------------------------------------------------------- rotations

    private void turnTo(Vec2 look, float step) {
        Core.rotations().request(this, RotationRequest.at(look)
                .priority(ClaimPriority.HIGH)
                .step(step)
                .mode(RotationMode.SILENT));
    }

    /**
     * Turns to place, never by the same amount as the turn before the last click.
     *
     * <p>Grim's DuplicateRotPlace flags a click whose preceding turn (over 2&deg;)
     * matches the previous click's to 0.0001&deg;. Snapped yaws make that likely
     * &mdash; every telly flip is 180&deg; &mdash; so a turn that would repeat it
     * stops a degree short, and finishes the next tick.
     */
    private void turnToPlace(Vec2 look) {
        float step = turnStep();
        float turn = Math.min(step, Math.abs(MathUtil.wrapDegrees(look.getYaw() - Players.serverRotation().getYaw())));
        if (turn > 2f && Math.abs(turn - lastPlacedTurn) < 0.5f) {
            step = turn - 1f;
        }
        turnTo(look, step);
    }

    /**
     * Telly's forward look: the camera's yaw, so the move and every jump go where
     * the player steers, with the pitch already down at the build. Pitch moves
     * nothing, so it is free to be ready: once in the air, the turn to place is
     * the yaw alone.
     *
     * @return whether the head faces forward for this tick's move
     */
    private boolean faceForward() {
        float yaw = Players.rotation().getYaw();
        float pitch = heldLook != null ? heldLook.getPitch() : READY_PITCH;
        float off = Math.abs(MathUtil.wrapDegrees(Players.serverRotation().getYaw() - yaw));
        float step = turnStep();
        turnTo(Vec2.rotation(yaw, pitch), step);
        return off <= step;
    }

    /** Where Telly holds the pitch before its first placement: about where a bridge block is. */
    private static final float READY_PITCH = 80f;

    /**
     * Turns the head back to the camera, if it is not there already.
     *
     * @return whether it faces the camera for this tick's move
     */
    private boolean faceCamera() {
        Vec2 camera = Players.rotation();
        float off = RotationMath.difference(Players.serverRotation(), camera);
        if (off <= FACING) {
            return true;
        }
        float step = turnStep();
        turnTo(camera, step);
        return off <= step;
    }

    /** @return ticks until the yaw is back on the camera's at the turn speed; at least one */
    private int ticksToFaceForward() {
        float off = Math.abs(MathUtil.wrapDegrees(Players.serverRotation().getYaw() - Players.rotation().getYaw()));
        return Math.max(1, (int) Math.ceil(off / turnStep()));
    }

    /** The client's turn speed, from the Interaction settings. */
    private static float turnStep() {
        return ClientSettings.interaction().turnSpeed();
    }

    // ------------------------------------------------------------------ misc

    @Subscribe
    private void onWorld(WorldEvent event) {
        setEnabled(false);
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        box.draw(renderColor.resolve(), fill.isOn(), fillOpacity.getFloat(), thickness.getFloat());
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }
}
