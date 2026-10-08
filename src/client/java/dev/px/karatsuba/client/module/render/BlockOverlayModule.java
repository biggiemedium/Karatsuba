package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.MathUtil;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.render.BlockOutlineEvent;
import dev.px.karatsuba.adapter.event.game.render.WorldOverlayEvent;
import dev.px.karatsuba.adapter.event.game.world.BlockBreakingEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.render.ThemeColors;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Blocks worth looking at: the one under your crosshair, and every block someone
 * is breaking.
 *
 * <h2>Crosshair</h2>
 * Replaces the black wireframe on the block you are looking at with an outline,
 * a fill or both, sliding between blocks with {@code Smoothing}.
 * {@link BlockOutlineEvent} is cancelled to hide the vanilla one; the drawing is
 * done in {@link Render3DEvent}, the pass set up for it. The target is read from
 * {@code Targets} each frame, so the overlay works even with the vanilla outline
 * turned off in the game's settings.
 *
 * <h2>Breaking</h2>
 * Every block being mined, yours and everyone else's, from the same progress the
 * game draws cracks with ({@link BlockBreakingEvent}). The box fills from its
 * centre as the block nears breaking, coloured from {@code Start} to
 * {@code Finish}, with who is mining it, how far along, and when it should go.
 *
 * <p>The server sends another player's progress only when their crack grows, ten
 * steps in all, so the fill is smoothed between steps at the rate they have been
 * arriving, and the time left is that rate times the steps to go. A block next to
 * your feet (your surround) is drawn in {@code Surround Colour}: that is the block
 * someone mines before they crystal you.
 *
 * <h2>Theme Colours</h2>
 * On, every colour here comes from the client theme's accent gradient instead of
 * its own setting: the crosshair at the primary end, breaking progress running
 * from primary to secondary. Opacity still comes from each setting.
 */
@ModuleInfo(name = "Block Overlay", description = "Highlights the block under the crosshair, and every block being broken",
        category = "Render")
public final class BlockOverlayModule extends Module {

    /** How close to the target the smoothing has to get before it stops mattering. */
    private static final double SNAP_DISTANCE = 0.01d;

    /** Lifted off the block face, so the overlay does not fight it for depth. */
    private static final double SURFACE_OFFSET = 0.002d;

    /** Vanilla forgets a crack after this long without news; so does this. */
    private static final long FORGET_MILLIS = 20_000L;

    private static final int STAGES = 10;

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw everything in the client theme's colours instead of the ones set here");

    // -------------------------------------------------------------- crosshair

    private final BooleanSetting crosshair = bool("Crosshair", true)
            .describe("Highlight the block you are looking at");

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.BOTH);

    private final ColorSetting outlineColor = color("Outline", Color.of(255, 255, 255, 200))
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting fillColor = color("Fill", Color.of(255, 255, 255, 40))
            .visibleWhen(() -> mode.get() != Mode.OUTLINE && !themeColours.isOn());

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(() -> mode.get() != Mode.FILL);

    private final BooleanSetting hideVanilla = bool("Hide Vanilla", true)
            .describe("Suppress the game's own black wireframe");

    private final NumberSetting<Float> smoothing = number("Smoothing", 0.4f, 0f, 0.9f)
            .step(0.05f)
            .describe("How much the box slides between blocks; 0 snaps");

    private final GroupSetting crosshairGroup = group("Crosshair Options", true)
            .containing(mode, outlineColor, fillColor, thickness, hideVanilla, smoothing)
            .visibleWhen(crosshair);

    // --------------------------------------------------------------- breaking

    private final BooleanSetting breaking = bool("Breaking", true)
            .describe("Show every block being broken, how far along, by whom");

    private final BooleanSetting self = bool("Yours", true)
            .describe("Include the blocks you are breaking");

    private final BooleanSetting friends = bool("Friends", true)
            .describe("Include the blocks friends are breaking");

    private final NumberSetting<Integer> breakRange = integer("Range", 48, 8, 128)
            .describe("Blocks from you within which breaking is shown");

    private final BooleanSetting labels = bool("Labels", true)
            .describe("Who is breaking it and the time left");

    private final BooleanSetting percent = bool("Percent", false)
            .describe("Add how far along the break is to the label")
            .visibleWhen(labels);

    private final ColorSetting startColor = color("Start", Color.of(60, 220, 90, 200))
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting finishColor = color("Finish", Color.of(240, 60, 60, 200))
            .visibleWhen(() -> !themeColours.isOn());

    private final BooleanSetting surround = bool("Surround Alert", true)
            .describe("Mark a block next to your feet in its own colour: someone is mining your surround");

    private final ColorSetting surroundColor = color("Surround Colour", Color.of(255, 160, 0, 230))
            .visibleWhen(surround);

    private final NumberSetting<Float> breakFill = number("Fill Opacity", 0.3f, 0.05f, 1f).step(0.05f)
            .describe("The growing fill's opacity, as a share of the colour's");

    private final BooleanSetting throughWalls = bool("Through Walls", true);

    private final GroupSetting breakingGroup = group("Breaking Options", true)
            .containing(self, friends, breakRange, labels, percent, startColor, finishColor, surround, surroundColor,
                    breakFill, throughWalls)
            .visibleWhen(breaking);

    /**
     * The box being drawn, which trails the real target while smoothing is on.
     *
     * <p>Null when nothing is targeted, which is also what resets the smoothing:
     * sliding in from wherever the last block was would look like the overlay
     * flying across the world every time the crosshair leaves a wall.
     */
    private Box drawn;

    /** Every breaker's current block, by the breaker's entity id. */
    private final Map<Integer, Break> breaks = new HashMap<>();

    @Override
    protected void onDisable() {
        drawn = null;
        breaks.clear();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        breaks.clear();
    }

    // -------------------------------------------------------------- crosshair

    /**
     * Suppresses the vanilla wireframe.
     *
     * <p>Subscribed whether or not anything is drawn this frame, because the
     * decision is "should the game draw its outline", and the answer while the
     * crosshair overlay is on is no.
     */
    @Subscribe
    private void onBlockOutline(BlockOutlineEvent event) {
        if (crosshair.isOn() && hideVanilla.isOn()) {
            event.cancel();
        }
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (crosshair.isOn()) {
            drawCrosshair();
        } else {
            drawn = null;
        }
        if (breaking.isOn() && !breaks.isEmpty()) {
            drawBreaks();
        }
    }

    private void drawCrosshair() {
        Targets.Block target = Targets.block();
        if (target == null) {
            drawn = null;
            return;
        }

        drawn = approach(drawn, target.getShape(), smoothing.getFloat());
        Box box = drawn.expand(SURFACE_OFFSET);
        Color outline = ThemeColors.of(outlineColor, themeColours.isOn(), 0f);
        Color fill = ThemeColors.of(fillColor, themeColours.isOn(), 0f);

        switch (mode.get()) {
            case OUTLINE -> Render.boxOutline(box, thickness.getFloat(), outline);
            case FILL -> Render.box(box, fill);
            case BOTH -> Render.boxFilledOutline(box, fill, outline, thickness.getFloat());
        }
    }

    /**
     * Eases one box towards another, corner by corner.
     *
     * <p>Per corner rather than per centre, so an overlay moving from a slab to a
     * full block grows into it instead of jumping size at the end of the slide.
     *
     * @param from   where the overlay is now, or null to start at the target
     * @param to     where it should be
     * @param factor 0 snaps, approaching 1 trails further behind
     */
    private static Box approach(Box from, Box to, float factor) {
        if (from == null || factor <= 0f) {
            return to;
        }
        // Close enough that another frame of easing would move it by less than a
        // pixel. Snapping here is what stops the lerp creeping forever.
        if (from.getMin().distanceTo(to.getMin()) < SNAP_DISTANCE
                && from.getMax().distanceTo(to.getMax()) < SNAP_DISTANCE) {
            return to;
        }
        double step = 1d - factor;
        return Box.of(approach(from.getMin(), to.getMin(), step),
                approach(from.getMax(), to.getMax(), step));
    }

    private static Vec3 approach(Vec3 from, Vec3 to, double step) {
        return Vec3.of(
                MathUtil.lerp(from.getX(), to.getX(), step),
                MathUtil.lerp(from.getY(), to.getY(), step),
                MathUtil.lerp(from.getZ(), to.getZ(), step));
    }

    // --------------------------------------------------------------- breaking

    @Subscribe
    private void onBreaking(BlockBreakingEvent event) {
        int breaker = event.getBreakerId();
        if (event.isStopped()) {
            breaks.remove(breaker);
            return;
        }
        long now = System.currentTimeMillis();
        Break current = breaks.get(breaker);
        if (current == null || !current.position.equals(event.getPosition()) || event.getCrack() < current.crack) {
            breaks.put(breaker, new Break(event.getPosition(), event.getCrack(), now));
        } else if (event.getCrack() > current.crack) {
            current.advance(event.getCrack(), now);
        } else {
            current.heardAt = now;
        }
    }

    /** Drops cracks that went quiet, broke, or belong to no one shown. */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (breaks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Break>> entries = breaks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Break> entry = entries.next();
            Break crack = entry.getValue();
            if (now - crack.heardAt > FORGET_MILLIS || Blocks.isAir(crack.position)) {
                entries.remove();
                continue;
            }
            crack.name = Entities.nameOf(entry.getKey());
            crack.mine = entry.getKey() == Players.entityId();
            crack.friend = !crack.mine && Entities.isFriend(entry.getKey());
        }
    }

    private void drawBreaks() {
        Vec3 eyes = Players.eyePosition();
        double range = breakRange.getInt();
        long now = System.currentTimeMillis();
        Render.setDepthTest(!throughWalls.isOn());
        try {
            for (Break crack : breaks.values()) {
                if (!shows(crack) || crack.position.center().distanceTo(eyes) > range) {
                    continue;
                }
                float progress = crack.progress(now);
                Color color = colorOf(crack, progress);
                Box full = Blocks.outline(crack.position).expand(SURFACE_OFFSET);
                Render.boxOutline(full, 1.5f, color);
                Render.box(grown(full, progress), color.fade(breakFill.getFloat()));
            }
        } finally {
            Render.setDepthTest(true);
        }
    }

    /** Labels are text, so they are drawn in the 2D pass, at the block's projected top. */
    @Subscribe
    private void onWorldOverlay(WorldOverlayEvent event) {
        if (!breaking.isOn() || !labels.isOn() || breaks.isEmpty()) {
            return;
        }
        Vec3 eyes = Players.eyePosition();
        long now = System.currentTimeMillis();
        for (Break crack : breaks.values()) {
            if (!shows(crack) || crack.position.center().distanceTo(eyes) > breakRange.getInt()) {
                continue;
            }
            Vec2 at = Render.worldToScreen(crack.position.center().add(0d, 0.75d, 0d));
            if (at == null) {
                continue;
            }
            float progress = crack.progress(now);
            String text = label(crack, progress, now);
            float width = Render.textWidth(text);
            float height = Render.textHeight();
            float x = (float) at.getX() - width / 2f;
            float y = (float) at.getY() - height / 2f;
            Render.rect(x - 2f, y - 1f, width + 4f, height + 2f, Color.of(0, 0, 0, 120));
            Render.text(text, x, y, colorOf(crack, progress).withAlpha(255));
        }
    }

    private boolean shows(Break crack) {
        return crack.mine ? self.isOn() : !crack.friend || friends.isOn();
    }

    private Color colorOf(Break crack, float progress) {
        if (surround.isOn() && !crack.mine && isSurround(crack.position)) {
            return ThemeColors.of(surroundColor, false, 0f);
        }
        return ThemeColors.between(startColor, finishColor, themeColours.isOn(), progress);
    }

    /** @return whether the cell is beside the player's feet, where a surround block sits */
    private static boolean isSurround(Vec3i cell) {
        Vec3i feet = Players.blockPosition();
        int dx = Math.abs(cell.getX() - feet.getX());
        int dz = Math.abs(cell.getZ() - feet.getZ());
        return cell.getY() == feet.getY() && dx + dz == 1;
    }

    /** "Steve 1.2s", with the percentage between when {@code Percent} is on. */
    private String label(Break crack, float progress, long now) {
        StringBuilder text = new StringBuilder(crack.mine ? "You" : crack.name.isEmpty() ? "?" : crack.name);
        if (percent.isOn()) {
            text.append(' ').append(Math.round(progress * 100f)).append('%');
        }
        long left = crack.millisLeft(now);
        if (left >= 0L) {
            text.append(String.format(Locale.ROOT, " %.1fs", left / 1000d));
        }
        return text.toString();
    }

    /** The box shrunk to its centre by how far the break has to go: empty at the start, full when it goes. */
    private static Box grown(Box full, float progress) {
        Vec3 center = full.getMin().add(full.getMax()).scale(0.5d);
        double half = Math.max(0.02d, progress) / 2d;
        double sx = (full.getMax().getX() - full.getMin().getX()) * half;
        double sy = (full.getMax().getY() - full.getMin().getY()) * half;
        double sz = (full.getMax().getZ() - full.getMin().getZ()) * half;
        return Box.of(center.getX() - sx, center.getY() - sy, center.getZ() - sz,
                center.getX() + sx, center.getY() + sy, center.getZ() + sz);
    }

    @Override
    public String getDisplayInfo() {
        int shown = 0;
        for (Break crack : breaks.values()) {
            if (!crack.mine) {
                shown++;
            }
        }
        return shown == 0 ? mode.displayValue() : mode.displayValue() + " " + shown;
    }

    /**
     * One block being broken, and how fast its crack has been growing.
     *
     * <p>The pace is the average time a stage has taken so far, which is what
     * smooths the fill between the server's steps and estimates the time left.
     */
    private static final class Break {

        final Vec3i position;
        int crack;
        final long startedAt;
        final int startCrack;
        long crackAt;
        long heardAt;

        /** Average milliseconds a stage takes, or -1 until two stages have been seen. */
        double millisPerStage = -1d;

        String name = "";
        boolean mine;
        boolean friend;

        Break(Vec3i position, int crack, long now) {
            this.position = position;
            this.crack = crack;
            this.startedAt = now;
            this.startCrack = crack;
            this.crackAt = now;
            this.heardAt = now;
        }

        void advance(int next, long now) {
            crack = next;
            crackAt = now;
            heardAt = now;
            if (next > startCrack) {
                millisPerStage = (double) (now - startedAt) / (next - startCrack);
            }
        }

        /** @return 0 to 1: the crack's stage, plus the time into the next one at the pace so far */
        float progress(long now) {
            double within = millisPerStage <= 0d ? 0d : Math.min(1d, (now - crackAt) / millisPerStage);
            return (float) Math.min(1d, (crack + within) / STAGES);
        }

        /** @return milliseconds until it breaks at the pace so far, or -1 before there is a pace */
        long millisLeft(long now) {
            if (millisPerStage <= 0d) {
                return -1L;
            }
            double stagesLeft = STAGES - crack - Math.min(1d, (now - crackAt) / millisPerStage);
            return Math.max(0L, Math.round(stagesLeft * millisPerStage));
        }
    }

    public enum Mode {
        OUTLINE,
        FILL,
        BOTH
    }
}
