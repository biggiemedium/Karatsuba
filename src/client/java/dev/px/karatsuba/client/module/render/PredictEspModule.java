package dev.px.karatsuba.client.module.render;

import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.prediction.Future;
import dev.px.core.movement.prediction.Prediction;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.client.render.ThemeColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the players near you are about to be.
 *
 * <p>Draws, for each, the hitbox where Core's prediction puts them
 * {@code Ticks} ticks from now and the path they take to get there. It is the
 * same prediction the crystal and bed auras score with when their Predict
 * setting is on, so this is what the auras see: a crystal placed for where
 * someone will be rather than where they are. Watching it is how you learn to
 * trust that setting, or turn it off.
 *
 * <p>The prediction plays out several ways the player could move (keep going,
 * stop, jump, and the rest) and weighs each by how well it has matched them so
 * far. The likeliest is drawn solid. With {@code Alternatives}, the others are
 * drawn as faint paths, fainter the less likely. The likeliest box is in
 * {@code Reliable} colour when the prediction has been matching the player well,
 * and {@code Unsure} when it has not: a player who keeps changing direction is
 * hard to predict, and the colour says so.
 *
 * <p>Worked out once a tick for the nearest {@code Max Players}, drawn every frame.
 */
@ModuleInfo(name = "Predict ESP", description = "Shows where nearby players will be, as the auras predict it",
        category = "Render")
public final class PredictEspModule extends Module {

    private final NumberSetting<Integer> ticks = integer("Ticks", 6, 1, 20)
            .describe("How far ahead to predict; the auras' Predict looks a few ticks ahead too");

    private final NumberSetting<Integer> range = integer("Range", 24, 4, 64)
            .describe("Blocks from you within which players are predicted");

    private final NumberSetting<Integer> maxPlayers = integer("Max Players", 4, 1, 16)
            .describe("Predict at most this many, nearest first; each costs a little every tick");

    private final BooleanSetting friends = bool("Friends", false)
            .describe("Predict friends too");

    private final BooleanSetting path = bool("Path", true)
            .describe("The path to the predicted position");

    private final BooleanSetting alternatives = bool("Alternatives", false)
            .describe("The less likely ways they might move, faint");

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw in the client theme's colours instead of the ones set here");

    private final ColorSetting reliableColor = color("Reliable", Color.of(80, 200, 255, 220))
            .describe("The predicted box while the prediction has been matching them")
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting unsureColor = color("Unsure", Color.of(255, 170, 60, 220))
            .describe("The predicted box while it has not")
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting pathColor = color("Path Colour", Color.of(255, 255, 255, 160))
            .visibleWhen(() -> path.isOn() && !themeColours.isOn());

    private final BooleanSetting fill = bool("Fill", true);

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.15f, 0.05f, 1f).step(0.05f)
            .visibleWhen(fill);

    private final NumberSetting<Float> thickness = number("Thickness", 1.2f, 0.5f, 4f).step(0.1f);

    private final BooleanSetting throughWalls = bool("Through Walls", true);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(themeColours, reliableColor, unsureColor, pathColor, fill, fillOpacity, thickness,
                    throughWalls);

    /** What the last tick predicted, drawn until the next. */
    private List<Shown> shown = new ArrayList<>();

    @Override
    protected void onDisable() {
        shown = new ArrayList<>();
    }

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        shown = predict(Combat.kit());
    }

    private <E> List<Shown> predict(CombatKit<E> kit) {
        Tracked<E> self = Core.entities().getSelf();
        if (self == null) {
            return new ArrayList<>();
        }
        double reach = range.getInt();
        List<Tracked<E>> near = new ArrayList<>();
        for (Tracked<E> player : kit.players().getAll()) {
            if (player.get() == self.get() || player.distanceTo(self) > reach
                    || (!friends.isOn() && kit.isFriend(player.get()))) {
                continue;
            }
            near.add(player);
        }
        near.sort(Comparator.comparingDouble(player -> player.distanceTo(self)));

        int ahead = ticks.getInt();
        List<Shown> result = new ArrayList<>();
        for (Tracked<E> player : near.subList(0, Math.min(near.size(), maxPlayers.getInt()))) {
            Prediction prediction = Core.prediction().predict(player, ahead);
            List<Future> futures = prediction.getFutures();
            Future likeliest = prediction.likeliest();
            List<Trail> trails = new ArrayList<>();
            for (Future future : futures) {
                if (future == likeliest && !path.isOn()) {
                    continue;
                }
                if (future != likeliest && !alternatives.isOn()) {
                    continue;
                }
                trails.add(new Trail(trace(player.getPosition(), future, ahead), future.getWeight(),
                        future == likeliest));
            }
            Vec3 at = prediction.positionAt(ahead);
            Box box = Box.around(at, player.getWidth(), player.getHeight());
            result.add(new Shown(box, trails, prediction.isReliable()));
        }
        return result;
    }

    /** The future's positions, tick by tick, starting where the player is now. */
    private static List<Vec3> trace(Vec3 start, Future future, int ahead) {
        List<Vec3> points = new ArrayList<>(ahead + 1);
        points.add(start);
        for (int tick = 1; tick <= ahead; tick++) {
            points.add(future.positionAt(tick));
        }
        return points;
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (shown.isEmpty()) {
            return;
        }
        boolean themed = themeColours.isOn();
        Render.setDepthTest(!throughWalls.isOn());
        try {
            for (Shown player : shown) {
                for (Trail trail : player.trails()) {
                    Color base = ThemeColors.of(pathColor, themed, 1f);
                    Color color = trail.likeliest() ? base
                            : base.fade((float) Math.max(0.15d, Math.min(0.6d, trail.weight())));
                    float width = trail.likeliest() ? thickness.getFloat() : thickness.getFloat() * 0.6f;
                    List<Vec3> points = trail.points();
                    for (int i = 1; i < points.size(); i++) {
                        Render.line3D(points.get(i - 1), points.get(i), width, color);
                    }
                }
                Color boxColor = player.reliable()
                        ? ThemeColors.of(reliableColor, themed, 0f)
                        : ThemeColors.of(unsureColor, themed, 0.5f);
                if (fill.isOn()) {
                    Render.box(player.box(), boxColor.fade(fillOpacity.getFloat()));
                }
                Render.boxOutline(player.box(), thickness.getFloat(), boxColor);
            }
        } finally {
            Render.setDepthTest(true);
        }
    }

    @Override
    public String getDisplayInfo() {
        return ticks.getInt() + "t";
    }

    private record Trail(List<Vec3> points, double weight, boolean likeliest) {
    }

    private record Shown(Box box, List<Trail> trails, boolean reliable) {
    }
}
