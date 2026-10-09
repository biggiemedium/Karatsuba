package dev.px.karatsuba.client.module.render;

import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.animation.PresenceTracker;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.projectile.ProjectileKit;
import dev.px.karatsuba.adapter.projectile.ProjectileType;
import dev.px.karatsuba.adapter.projectile.Projectiles;
import dev.px.karatsuba.adapter.projectile.Shot;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.render.ThemeColors;
import dev.px.projectile.flight.Hit;
import dev.px.projectile.flight.Trajectory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Arrows, pearls and potions in the air, and the one in your hand.
 *
 * <ul>
 *   <li><b>Trail.</b> Where each projectile has been, drawn as a ribbon facing the
 *       camera, a real width in the world, tapering and fading toward its tail,
 *       with a glow, a bright core line that stays visible however far away it
 *       is, and a dot at its head. The head moves between ticks, at the frame rate.</li>
 *   <li><b>Landing.</b> The projectile library flies each one on from its own
 *       velocity, as the game will, and the face it will hit is highlighted:
 *       filled, outlined, corner brackets that snap in, and a cross on the exact
 *       point. It glides when the prediction moves. Headed for an entity, that
 *       entity's box is highlighted instead. When it lands, the trail draws itself
 *       into the impact and the brackets flare out.</li>
 *   <li><b>Held.</b> Holding a pearl, a bow, a potion or anything else that
 *       launches, its whole path and landing, before you use it, as a line
 *       coming out of the hand holding it. Worked out every frame from where you
 *       look, so it holds still on screen; a bow not yet drawn shows a full draw.</li>
 * </ul>
 *
 * <p>The prediction is exact for the projectile's motion; it does not know where
 * entities will move while it flies.
 */
@ModuleInfo(name = "Projectiles", description = "Trails behind projectiles, and where they and yours will land",
        category = "Render")
public final class ProjectilesModule extends ClientModule {

    /** How long a trail takes to draw itself into the landing once its projectile is gone. */
    private static final long RETRACT_MILLIS = 420L;

    /** Lifts the face highlight off the face, so the block does not flicker through it. */
    private static final double LIFT = 0.015d;

    /** Half a block face. */
    private static final double HALF = 0.5d;

    /** Length of the corner brackets along each edge, in blocks. */
    private static final double BRACKET = 0.22d;

    /** Length of one dash plus its gap along a projectile's predicted path, in blocks. */
    private static final double DASH_PERIOD = 0.9d;

    /** How fast the dashes flow toward the landing, in blocks a second. */
    private static final double DASH_SPEED = 3.2d;

    /**
     * Over how many blocks of path the held preview bends from your hand into the
     * projectile's real path, which leaves from the middle of your eyes.
     */
    private static final double HAND_BLEND = 3d;

    private final MultiEnumSetting<Kind> kinds = multi("Projectiles", Kind.class,
            Kind.ARROWS, Kind.TRIDENTS, Kind.PEARLS, Kind.POTIONS)
            .describe("Which projectiles in the air to show");

    private final BooleanSetting own = bool("Own", true)
            .describe("Your own projectiles too, once thrown");

    private final NumberSetting<Integer> range = integer("Range", 64, 8, 128)
            .describe("Blocks from you within which projectiles are shown");

    private final BooleanSetting held = bool("Held", true)
            .describe("Where what you are holding would land, before you use it");

    private final BooleanSetting trail = bool("Trail", true)
            .describe("Where each projectile has been");

    private final NumberSetting<Integer> trailLength = integer("Trail Length", 20, 4, 60)
            .describe("How many ticks of flight the trail keeps")
            .visibleWhen(trail);

    private final BooleanSetting landing = bool("Landing", true)
            .describe("Where each projectile in the air is predicted to land");

    private final BooleanSetting path = bool("Path", true)
            .describe("The predicted path to the landing, flowing toward it")
            .visibleWhen(landing);

    private final BooleanSetting countdown = bool("Countdown", true)
            .describe("The landing's face fills from its middle as the projectile closes in")
            .visibleWhen(landing);

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw in the client theme's colours instead of the ones set here");

    private final ColorSetting trailColor = color("Trail Colour", Color.of(255, 215, 80, 255))
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting pearlColor = color("Pearl Colour", Color.of(200, 110, 255, 255))
            .describe("Ender pearls' trail, so they stand out from arrows")
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting landingColor = color("Landing Colour", Color.of(90, 200, 255, 255))
            .describe("The landing highlight and predicted paths")
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting heldColor = color("Held Colour", Color.of(255, 255, 255, 235))
            .describe("The preview of what you are holding")
            .visibleWhen(() -> held.isOn() && !themeColours.isOn());

    private final NumberSetting<Float> trailWidth = number("Trail Width", 0.09f, 0.02f, 0.3f).step(0.01f)
            .describe("How wide the trail is at its head, in blocks");

    private final NumberSetting<Float> thickness = number("Thickness", 2f, 1f, 5f).step(0.1f)
            .describe("Lines: the outline, brackets and the trail's core");

    private final BooleanSetting glow = bool("Glow", true)
            .describe("A soft halo around the trail and the paths");

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Snap, glide, flow and retract; off, everything appears and goes at once");

    private final BooleanSetting throughWalls = bool("Through Walls", true);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(themeColours, trailColor, pearlColor, landingColor, heldColor, trailWidth, thickness,
                    glow, animations, throughWalls);

    /** Each projectile's presence: in when first seen, retracting once gone. */
    private final PresenceTracker<Object, Flying> shown =
            new PresenceTracker<>(PresenceTracker.POP_IN_MILLIS, Easing.BACK_OUT, RETRACT_MILLIS, Easing.QUAD_OUT);

    /** The state behind each entry, kept across ticks; keyed like {@link #shown}. */
    private final Map<Object, Flying> flying = new HashMap<>();

    /** The held preview's landing, and whether it is showing. */
    private final Marker heldMarker = new Marker();
    private final Animation heldPresence = Animation.of(0f, PresenceTracker.POP_IN_MILLIS, Easing.BACK_OUT);

    /** When the last frame was drawn, for easing at the frame rate. */
    private long lastFrameNanos;

    @Override
    protected void onDisable() {
        clear();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        clear();
    }

    private void clear() {
        shown.clear();
        flying.clear();
        heldMarker.reset();
        heldPresence.snapTo(0f);
    }

    // ------------------------------------------------------------------ tick

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        ProjectileKit kit = Projectiles.kit();
        Tracked<?> self = Core.entities().getSelf();
        shown.setAnimated(animations.isOn());
        shown.begin();
        if (self != null && (trail.isOn() || landing.isOn())) {
            double reach = range.getInt();
            for (Tracked<?> projectile : kit.projectiles().getAll()) {
                ProjectileType type = kit.typeOf(projectile);
                if (type == null || !kinds.has(Kind.of(type))
                        || projectile.getPosition().distanceTo(self.getPosition()) > reach
                        || (!own.isOn() && kit.isYours(projectile))) {
                    continue;
                }
                Object key = projectile.get();
                Flying state = flying.computeIfAbsent(key, ignored -> new Flying(type, projectile, trailLength.getInt()));
                state.update(kit, projectile, trailLength.getInt(), landing.isOn());
                shown.report(key, true, state);
            }
        }
        shown.end();
        // An entry the tracker has let go of has finished animating out.
        flying.keySet().removeIf(key -> !shown.has(key));
    }

    // ---------------------------------------------------------------- render

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        long now = System.nanoTime();
        float frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        double seconds = now / 1.0e9d;
        boolean themed = themeColours.isOn();
        boolean animated = animations.isOn();
        Vec3 camera = Render.cameraPosition();

        Render.setDepthTest(!throughWalls.isOn());
        try {
            for (PresenceTracker.Entry<Flying> entry : shown.entries()) {
                Flying state = entry.value();
                Vec3 head = state.head(event.getPartialTicks());
                if (trail.isOn()) {
                    drawTrail(state, head, entry, camera, themed);
                }
                if (landing.isOn() && state.marker.isAimed()) {
                    state.marker.glide(frameSeconds, animated);
                    if (path.isOn() && entry.isWanted()) {
                        drawFlowingPath(state.path, head, state.marker.point, entry.alpha(), seconds, camera, themed);
                    }
                    double progress = countdown.isOn() && state.flightTicks > 0
                            ? 1d - state.ticksLeft / (double) state.flightTicks
                            : -1d;
                    drawMarker(state.marker, entry.presence(), entry.alpha(), !entry.isWanted(), progress,
                            seconds, ThemeColors.of(landingColor, themed, 1f));
                }
            }
            if (held.isOn()) {
                drawHeld(event.getPartialTicks(), frameSeconds, seconds, themed, animated);
            }
        } finally {
            Render.setDepthTest(true);
        }
    }

    /**
     * Tail to head: a ribbon narrowing and fading toward the tail, its glow, and
     * a core line. While retracting, only the newest part is left, so the trail
     * draws itself into where it ended.
     */
    private void drawTrail(Flying state, Vec3 head, PresenceTracker.Entry<Flying> entry, Vec3 camera,
                           boolean themed) {
        List<Vec3> points = new ArrayList<>(state.trail);
        if (entry.isWanted() && head != null) {
            points.set(points.size() - 1, head);
        }
        if (points.size() < 2) {
            return;
        }
        float presence = entry.alpha();
        int first = entry.isWanted() ? 0 : (int) Math.floor((points.size() - 1) * (1f - presence));
        ColorSetting setting = state.type == ProjectileType.ENDER_PEARL ? pearlColor : trailColor;
        double width = trailWidth.getFloat();
        int count = points.size() - 1;
        for (int i = Math.max(1, first + 1); i <= count; i++) {
            float along = i / (float) count;                       // 0 at the tail, 1 at the head
            float fade = (float) Math.pow(along, 0.8d) * presence;
            Color color = ThemeColors.of(setting, themed, 0.5f * (1f - along));
            double from = width * (0.2d + 0.8d * (i - 1) / count);
            double to = width * (0.2d + 0.8d * along);
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            if (glow.isOn()) {
                ribbon(a, b, from * 3d, to * 3d, camera, color.fade(fade * 0.2f));
            }
            ribbon(a, b, from, to, camera, color.fade(fade * 0.85f));
            Render.line3D(a, b, thickness.getFloat(), color.brighter(0.25f).fade(fade));
        }
        if (entry.isWanted()) {
            // The head: a small bright cube, so the projectile itself is easy to find.
            Vec3 tip = points.get(points.size() - 1);
            Color color = ThemeColors.of(setting, themed, 0f);
            double size = width * 0.9d;
            Box dot = Box.of(tip.getX() - size, tip.getY() - size, tip.getZ() - size,
                    tip.getX() + size, tip.getY() + size, tip.getZ() + size);
            Render.box(dot, color.fade(presence * 0.9f));
            if (glow.isOn()) {
                Box halo = Box.of(tip.getX() - size * 2.2d, tip.getY() - size * 2.2d, tip.getZ() - size * 2.2d,
                        tip.getX() + size * 2.2d, tip.getY() + size * 2.2d, tip.getZ() + size * 2.2d);
                Render.box(halo, color.fade(presence * 0.18f));
            }
        }
    }

    /** A projectile's predicted path from its head to its landing, as dashes flowing toward it. */
    private void drawFlowingPath(List<Vec3> predicted, Vec3 head, Vec3 end, float presence, double seconds,
                                 Vec3 camera, boolean themed) {
        if (predicted.size() < 2 || head == null) {
            return;
        }
        List<Vec3> points = new ArrayList<>(predicted);
        points.set(0, head);
        points.set(points.size() - 1, end);
        Color color = ThemeColors.of(landingColor, themed, 1f);
        double width = trailWidth.getFloat() * 0.55d;
        double dash = DASH_PERIOD * 0.55d;
        double travelled = -((seconds * (animations.isOn() ? DASH_SPEED : 0d)) % DASH_PERIOD);
        for (int i = 1; i < points.size(); i++) {
            Vec3 from = points.get(i - 1);
            Vec3 to = points.get(i);
            double length = from.distanceTo(to);
            if (length < 1.0e-6d) {
                continue;
            }
            double start = 0d;
            while (start < length) {
                double phase = ((travelled + start) % DASH_PERIOD + DASH_PERIOD) % DASH_PERIOD;
                boolean lit = phase < dash;
                double step = Math.max(1.0e-4d, Math.min(lit ? dash - phase : DASH_PERIOD - phase, length - start));
                if (lit) {
                    Vec3 a = from.lerp(to, start / length);
                    Vec3 b = from.lerp(to, (start + step) / length);
                    // Brighter nearer the landing, so the eye follows it there.
                    float near = (float) ((i - 1 + start / length) / (points.size() - 1));
                    float alpha = presence * (0.45f + 0.55f * near);
                    if (glow.isOn()) {
                        ribbon(a, b, width * 3d, width * 3d, camera, color.fade(alpha * 0.18f));
                    }
                    ribbon(a, b, width, width, camera, color.fade(alpha * 0.85f));
                }
                start += step;
            }
            travelled += length;
        }
    }

    /** What you are holding: its whole path, out of your hand, and where it lands. */
    private void drawHeld(float partialTicks, float frameSeconds, double seconds, boolean themed,
                          boolean animated) {
        ProjectileKit kit = Projectiles.kit();
        Shot shot = kit.held();
        Trajectory trajectory = shot == null ? null : shot.trajectory(kit.you(partialTicks), kit.view(partialTicks));
        Hit hit = trajectory == null ? null : trajectory.getHit();
        boolean showing = hit != null && hit.isHit();
        heldPresence.duration(showing ? PresenceTracker.POP_IN_MILLIS : PresenceTracker.FADE_OUT_MILLIS);
        heldPresence.target(showing);
        if (!animated) {
            heldPresence.snapTo(showing ? 1f : 0f);
        }
        float presence = heldPresence.get();
        float alpha = Math.max(0f, Math.min(1f, presence));
        if (alpha <= 0f) {
            if (!showing) {
                heldMarker.reset();
            }
            return;
        }
        Color color = ThemeColors.of(heldColor, themed, 0.25f);
        if (showing) {
            heldMarker.aim(hit, trajectory.velocityAt(Math.max(0, trajectory.getTicks() - 1)));
            heldMarker.glide(frameSeconds, animated);
            List<Vec3> points = new ArrayList<>(trajectory.getPoints());
            points.set(points.size() - 1, hit.getPoint());
            drawHeldLine(points, kit.hand(partialTicks), color, alpha);
        }
        if (heldMarker.isAimed()) {
            drawMarker(heldMarker, presence, alpha, false, -1d, seconds, ThemeColors.of(landingColor, themed, 1f));
        }
    }

    /**
     * The held path as a plain line, coming out of the hand holding the item:
     * the real path, shifted toward the hand at its start and eased back onto
     * itself over {@link #HAND_BLEND} blocks, so it leaves the item on screen
     * rather than the crosshair and still ends exactly where the shot lands.
     */
    private void drawHeldLine(List<Vec3> points, Vec3 hand, Color color, float alpha) {
        if (points.size() < 2) {
            return;
        }
        Vec3 offset = hand == null ? Vec3.ZERO : hand.subtract(points.get(0));
        Vec3 previous = points.get(0).add(offset);
        double travelled = 0d;
        for (int i = 1; i < points.size(); i++) {
            travelled += points.get(i - 1).distanceTo(points.get(i));
            double t = Math.min(1d, travelled / HAND_BLEND);
            double eased = t * t * (3d - 2d * t);
            Vec3 next = points.get(i).add(offset.scale(1d - eased));
            Render.line3D(previous, next, thickness.getFloat(), color.fade(alpha));
            previous = next;
        }
    }

    /**
     * The landing: on a block, its face filled and outlined, a bracket at each
     * corner that snaps in from outside when it appears and flares out when it
     * lands, and a cross on the exact point. On an entity, its box instead.
     *
     * @param progress how far through its flight, 0 to 1, to fill the face from the
     *                 middle; negative for no countdown
     */
    private void drawMarker(Marker marker, float presence, float alpha, boolean landed, double progress,
                            double seconds, Color color) {
        float lineWidth = thickness.getFloat();
        // Out from the corners: far while appearing (presence from 0, overshooting
        // past 1 for a snap), a gentle breath while shown, flaring out once landed.
        double breath = animations.isOn() ? 0.025d * Math.sin(seconds * 4d) : 0d;
        double spread = landed
                ? (1d - alpha) * 0.5d
                : (1d - Math.min(1.15d, presence)) * 0.6d + breath;

        if (marker.entityBox != null) {
            Box box = marker.entityBox;
            Render.box(box, color.fade(alpha * 0.14f));
            Render.boxOutline(box, lineWidth, color.fade(alpha));
            cross(marker.point, marker.normal, 0.12d, lineWidth, color.fade(alpha));
            return;
        }

        Vec3 normal = marker.normal;
        Vec3[] axes = faceAxes(normal);
        Vec3 centre = marker.centre.add(normal.scale(LIFT));
        Vec3 u = axes[0];
        Vec3 v = axes[1];

        // The face, faint, and the part of it the countdown has filled, stronger.
        quad(centre, u, v, HALF, color.fade(alpha * 0.16f));
        if (progress >= 0d) {
            quad(centre, u, v, HALF * Math.max(0d, Math.min(1d, progress)), color.fade(alpha * 0.22f));
        }
        outline(centre, u, v, HALF, lineWidth, color.fade(alpha * 0.55f));

        // Corner brackets.
        double reach = HALF + spread;
        Color bracket = color.brighter(0.2f).fade(alpha);
        for (int corner = 0; corner < 4; corner++) {
            double su = corner < 2 ? 1d : -1d;
            double sv = corner % 2 == 0 ? 1d : -1d;
            Vec3 tip = centre.add(u.scale(su * reach)).add(v.scale(sv * reach));
            Render.line3D(tip, tip.add(u.scale(-su * BRACKET)), lineWidth * 1.4f, bracket);
            Render.line3D(tip, tip.add(v.scale(-sv * BRACKET)), lineWidth * 1.4f, bracket);
        }

        cross(marker.point.add(normal.scale(LIFT * 2d)), normal, 0.1d, lineWidth, color.brighter(0.3f).fade(alpha));
    }

    // ---------------------------------------------------------------- shapes

    /**
     * One segment of a ribbon facing the camera, {@code fromWidth} wide at
     * {@code a} and {@code toWidth} at {@code b}: a real width in the world, so it
     * is as easy to see as the thing it follows, unlike a line a pixel or two wide.
     */
    private static void ribbon(Vec3 a, Vec3 b, double fromWidth, double toWidth, Vec3 camera, Color color) {
        Vec3 along = b.subtract(a);
        if (along.length() < 1.0e-6d) {
            return;
        }
        Vec3 sideA = side(along, a.subtract(camera), fromWidth);
        Vec3 sideB = side(along, b.subtract(camera), toWidth);
        if (sideA == null || sideB == null) {
            return;
        }
        Render.quad3D(a.add(sideA), a.subtract(sideA), b.subtract(sideB), b.add(sideB), color);
    }

    /** Half a ribbon's width, across it and across the view. Null when looking straight along it. */
    private static Vec3 side(Vec3 along, Vec3 toPoint, double width) {
        Vec3 across = along.cross(toPoint);
        double length = across.length();
        return length < 1.0e-9d ? null : across.scale(width / 2d / length);
    }

    /** A square in the plane of {@code u} and {@code v}, filled. */
    private static void quad(Vec3 centre, Vec3 u, Vec3 v, double half, Color color) {
        if (half <= 0d) {
            return;
        }
        Vec3 pu = u.scale(half);
        Vec3 pv = v.scale(half);
        Render.quad3D(centre.add(pu).add(pv), centre.add(pu).subtract(pv),
                centre.subtract(pu).subtract(pv), centre.subtract(pu).add(pv), color);
    }

    private static void outline(Vec3 centre, Vec3 u, Vec3 v, double half, float width, Color color) {
        Vec3 pu = u.scale(half);
        Vec3 pv = v.scale(half);
        Vec3 a = centre.add(pu).add(pv);
        Vec3 b = centre.add(pu).subtract(pv);
        Vec3 c = centre.subtract(pu).subtract(pv);
        Vec3 d = centre.subtract(pu).add(pv);
        Render.line3D(a, b, width, color);
        Render.line3D(b, c, width, color);
        Render.line3D(c, d, width, color);
        Render.line3D(d, a, width, color);
    }

    /** A small X lying on the plane with this normal. */
    private static void cross(Vec3 point, Vec3 normal, double size, float width, Color color) {
        Vec3[] axes = faceAxes(normal);
        Vec3 d1 = axes[0].add(axes[1]).scale(size / Math.sqrt(2d));
        Vec3 d2 = axes[0].subtract(axes[1]).scale(size / Math.sqrt(2d));
        Render.line3D(point.subtract(d1), point.add(d1), width, color);
        Render.line3D(point.subtract(d2), point.add(d2), width, color);
    }

    /** The two block axes lying in a face with this normal, so a highlight lines up with the blocks. */
    private static Vec3[] faceAxes(Vec3 normal) {
        double x = Math.abs(normal.getX());
        double y = Math.abs(normal.getY());
        double z = Math.abs(normal.getZ());
        if (y >= x && y >= z) {
            return new Vec3[]{Vec3.of(1d, 0d, 0d), Vec3.of(0d, 0d, 1d)};
        }
        if (x >= z) {
            return new Vec3[]{Vec3.of(0d, 1d, 0d), Vec3.of(0d, 0d, 1d)};
        }
        return new Vec3[]{Vec3.of(1d, 0d, 0d), Vec3.of(0d, 1d, 0d)};
    }

    @Override
    public String getDisplayInfo() {
        int count = 0;
        for (PresenceTracker.Entry<Flying> entry : shown.entries()) {
            if (entry.isWanted()) {
                count++;
            }
        }
        return Integer.toString(count);
    }

    // ----------------------------------------------------------------- state

    /**
     * Where something will land, as drawn: eased toward each new prediction
     * rather than jumping to it, and snapped when it moves to a different face.
     */
    private static final class Marker {

        /** The middle of the face it will hit, or the exact point on an entity. */
        private Vec3 centre;
        private Vec3 normal;
        /** The exact point it meets. */
        private Vec3 point;
        /** The entity it will hit, or null for a block. */
        private Box entityBox;

        private Vec3 centreTarget;
        private Vec3 pointTarget;
        private Direction face;

        boolean isAimed() {
            return centre != null;
        }

        void reset() {
            centre = null;
            centreTarget = null;
            face = null;
        }

        /** Takes a new prediction. {@code velocity} is the projectile's as it arrives, for an entity's facing side. */
        void aim(Hit hit, Vec3 velocity) {
            pointTarget = hit.getPoint();
            Direction newFace;
            if (hit.getType() == Hit.Type.BLOCK) {
                entityBox = null;
                newFace = hit.getFace();
                Vec3i cell = hit.getCell();
                Vec3 n = newFace.toVector();
                // The face's middle, on the plane the projectile meets (a slab's top,
                // not the cell's): the in-face axes from the cell, the other from the hit.
                centreTarget = Vec3.of(
                        Math.abs(n.getX()) > 0.5d ? pointTarget.getX() : cell.getX() + 0.5d,
                        Math.abs(n.getY()) > 0.5d ? pointTarget.getY() : cell.getY() + 0.5d,
                        Math.abs(n.getZ()) > 0.5d ? pointTarget.getZ() : cell.getZ() + 0.5d);
            } else {
                entityBox = hit.getEntity().getBox();
                newFace = velocity == null || velocity.length() < 1.0e-6d
                        ? Direction.UP
                        : Direction.nearest(velocity.scale(-1d));
                centreTarget = pointTarget;
            }
            if (centre == null || newFace != face || centre.distanceTo(centreTarget) > 6d) {
                // First prediction, another face, or too far to glide sensibly: snap.
                centre = centreTarget;
                point = pointTarget;
            }
            face = newFace;
            normal = newFace.toVector();
        }

        /** Eases toward the latest prediction, at the frame rate. */
        void glide(float frameSeconds, boolean animated) {
            if (centreTarget == null) {
                return;
            }
            if (!animated) {
                centre = centreTarget;
                point = pointTarget;
                return;
            }
            double ease = 1d - Math.exp(-frameSeconds * 16d);
            centre = centre.lerp(centreTarget, ease);
            point = point.lerp(pointTarget, ease);
        }
    }

    /** One projectile in the air: its trail so far, and where it is headed. */
    private static final class Flying {

        private final ProjectileType type;
        private final Deque<Vec3> trail = new ArrayDeque<>();
        private final Marker marker = new Marker();

        /** The predicted path, starting where it is now; empty until it has a landing. */
        private List<Vec3> path = List.of();

        /** The flight's length when the landing was first predicted, and what is left of it. */
        private int flightTicks;
        private int ticksLeft;

        private Flying(ProjectileType type, Tracked<?> projectile, int length) {
            this.type = type;
            // Seeded from what the tracker remembers, so a projectile first seen
            // mid-flight already has a trail behind it.
            for (int ago = Math.min(projectile.getHistorySize() - 1, length); ago >= 1; ago--) {
                Vec3 then = projectile.positionAgo(ago);
                if (then != null) {
                    trail.addLast(then);
                }
            }
        }

        private void update(ProjectileKit kit, Tracked<?> projectile, int length, boolean predict) {
            Vec3 now = projectile.getPosition();
            if (trail.isEmpty() || trail.peekLast().squaredDistanceTo(now) > 1.0e-8d) {
                trail.addLast(now);
            }
            while (trail.size() > length + 1) {
                trail.removeFirst();
            }
            if (!predict) {
                return;
            }
            Trajectory trajectory = kit.follow(projectile);
            Hit hit = trajectory == null ? null : trajectory.getHit();
            if (hit == null || !hit.isHit()) {
                // Nothing within reach of the flight: keep the last landing rather than
                // flicker it away for a tick.
                return;
            }
            path = trajectory.getPoints();
            ticksLeft = hit.getTick();
            flightTicks = Math.max(flightTicks, ticksLeft);
            marker.aim(hit, trajectory.velocityAt(Math.max(0, trajectory.getTicks() - 1)));
        }

        /** Between the last tick's position and this one's, at this frame. */
        private Vec3 head(float partialTicks) {
            if (trail.size() < 2) {
                return trail.peekLast();
            }
            Iterator<Vec3> newest = trail.descendingIterator();
            Vec3 current = newest.next();
            Vec3 previous = newest.next();
            return previous.lerp(current, partialTicks);
        }
    }

    /** What the Projectiles setting offers, one per kind a player tells apart. */
    public enum Kind {
        ARROWS,
        TRIDENTS,
        PEARLS,
        POTIONS,
        SNOWBALLS,
        EGGS,
        XP_BOTTLES,
        LLAMA_SPIT;

        static Kind of(ProjectileType type) {
            return switch (type) {
                case ARROW -> ARROWS;
                case TRIDENT -> TRIDENTS;
                case ENDER_PEARL -> PEARLS;
                case POTION -> POTIONS;
                case SNOWBALL -> SNOWBALLS;
                case EGG -> EGGS;
                case EXPERIENCE_BOTTLE -> XP_BOTTLES;
                case LLAMA_SPIT -> LLAMA_SPIT;
            };
        }
    }
}
