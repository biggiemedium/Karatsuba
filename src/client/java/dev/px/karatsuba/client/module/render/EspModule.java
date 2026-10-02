package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.math.Vec3;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.render.ColorUtil;
import dev.px.karatsuba.adapter.event.game.render.WorldOverlayEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.mode.ModalModule;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.render.esp.Box2DEsp;
import dev.px.karatsuba.client.module.render.esp.BoxEsp;
import dev.px.karatsuba.client.module.render.esp.EspMode;
import dev.px.karatsuba.client.module.render.esp.FilledEsp;
import dev.px.karatsuba.client.module.render.esp.R6Esp;
import dev.px.karatsuba.client.module.render.esp.WireframeEsp;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows entities through walls.
 *
 * <p>Ported from Leapfrog's {@code ESP}: its four modes, its tracers, its
 * per-type filters and colours, and its range. Karatsuba's own Filled shape is kept
 * as another mode, and 2D Box draws in screen space over the HUD.
 *
 * <p>The worked example for <b>{@code Render3D}</b>, the half of the render facade
 * that needs a camera, and for what owning a frame means. This class decides
 * <em>which</em> entities and <em>what colour</em>; a mode decides only how one
 * looks. In between it turns depth testing off &mdash; which is the entire
 * difference between an outline that shows through walls and one that hides
 * behind them &mdash; and turns it back on afterwards, so nothing drawn after ESP
 * in the same frame inherits it.
 *
 * <p>The adapter seam supplies the entities. {@link GameEntity} is a snapshot in
 * Core's own types, so nothing below is tied to a game version.
 */
@ModuleInfo(name = "ESP", description = "Shows entities through walls", category = "Render")
public final class EspModule extends ModalModule<EspModule.Mode> {

    public enum Mode {
        BOX,
        BOX_2D,
        R6,
        WIREFRAME,
        FILLED
    }

    // ---------------------------------------------------------------- modes

    private final BoxEsp box = attach(new BoxEsp());
    private final GroupSetting boxPanel = panelFor(box);

    private final Box2DEsp box2D = attach(new Box2DEsp());
    private final GroupSetting box2DPanel = panelFor(box2D);

    private final R6Esp r6 = attach(new R6Esp());
    private final GroupSetting r6Panel = panelFor(r6);

    private final WireframeEsp wireframe = attach(new WireframeEsp());
    private final GroupSetting wireframePanel = panelFor(wireframe);

    private final FilledEsp filled = attach(new FilledEsp());
    private final GroupSetting filledPanel = panelFor(filled);

    // -------------------------------------------------------------- tracers

    private final BooleanSetting tracers = bool("Tracers", false)
            .describe("A line from the crosshair to each entity");
    private final NumberSetting<Float> tracerWidth = number("Tracer Width", 1.5f, 0.5f, 4f).step(0.1f)
            .visibleWhen(tracers);
    private final ColorSetting tracerColor = color("Tracer Color", Color.WHITE)
            .visibleWhen(tracers);
    // R6 draws its tracers to the feet in each entity's own colour, as Leapfrog's
    // did, so the target choice means nothing there and is hidden.
    private final EnumSetting<TracerTarget> tracerTarget = enumOf("Tracer Target", TracerTarget.FEET)
            .visibleWhen(() -> tracers.isOn() && getMode() != Mode.R6);

    private final GroupSetting tracerGroup = group("Tracers", true)
            .containing(tracers, tracerWidth, tracerColor, tracerTarget);

    // -------------------------------------------------------------- targets

    private final BooleanSetting players = bool("Players", true);
    private final BooleanSetting monsters = bool("Monsters", false);
    private final BooleanSetting neutrals = bool("Neutrals", false)
            .describe("Everything alive that is neither a monster nor an animal: fish, bats, villagers, golems");
    private final BooleanSetting animals = bool("Animals", false);
    private final BooleanSetting invisibles = bool("Invisible", true)
            .describe("Include entities that are invisible");
    private final BooleanSetting friends = bool("Friends", false)
            .describe("Include friends, in their own colour. Off leaves them out entirely");

    private final GroupSetting targetGroup = group("Targets", true)
            .containing(players, monsters, neutrals, animals, invisibles, friends);

    // --------------------------------------------------------------- colors

    private final ColorSetting playerColor = color("Player Color", Color.rgb(0x00FF00))
            .visibleWhen(players);
    private final ColorSetting friendColor = color("Friend Color", Color.rgb(0x00B7FF))
            .visibleWhen(() -> players.isOn() && friends.isOn());
    private final ColorSetting monsterColor = color("Monster Color", Color.rgb(0xFF5555))
            .visibleWhen(monsters);
    private final ColorSetting animalColor = color("Animal Color", Color.rgb(0x55FF55))
            .visibleWhen(animals);
    private final ColorSetting neutralColor = color("Neutral Color", Color.rgb(0xFFFF55))
            .visibleWhen(neutrals);
    private final BooleanSetting healthTint = bool("Health Tint", false)
            .describe("Colour by health, green to red, instead of by type. Friends keep their colour");

    private final GroupSetting colorGroup = group("Colors", false)
            .containing(playerColor, friendColor, monsterColor, animalColor, neutralColor, healthTint);

    private final NumberSetting<Integer> distance = integer("Distance", 75, 0, 300)
            .describe("How far out to look, in blocks");

    public EspModule() {
        super(Mode.BOX);
    }

    /**
     * Draws one frame.
     *
     * <p>Synchronous, and it has to be: the camera and the projection are only
     * valid inside the game's world-render callback, which is what posts this
     * event. Queueing the work for later would draw it against the wrong matrices.
     */
    @Subscribe
    private void onRender3D(Render3DEvent event) {
        float partialTicks = event.getPartialTicks();
        List<GameEntity> targets = targets(partialTicks);
        if (targets.isEmpty()) {
            return;
        }
        SubMode<Mode> active = getActiveMode();

        Render.setDepthTest(false);
        try {
            if (active instanceof EspMode mode) {
                mode.render(this, targets, partialTicks);
            }
            if (tracers.isOn()) {
                drawTracers(targets);
            }
        } finally {
            Render.setDepthTest(true);
        }
    }

    /**
     * The screen-space half, for modes that draw over the HUD.
     *
     * <p>On the world overlay layer rather than Core's top 2D layer, so an open
     * screen covers the boxes the way it covers the world behind it. Projection
     * uses the camera from this frame's world pass, so the boxes line up with
     * where the entities were just rendered.
     */
    @Subscribe
    private void onWorldOverlay(WorldOverlayEvent event) {
        if (!(getActiveMode() instanceof EspMode mode)) {
            return;
        }
        float partialTicks = event.getPartialTicks();
        List<GameEntity> targets = targets(partialTicks);
        if (!targets.isEmpty()) {
            mode.render2D(this, targets, partialTicks);
        }
    }

    private void drawTracers(List<GameEntity> targets) {
        boolean r6Style = getMode() == Mode.R6;
        for (GameEntity entity : targets) {
            Vec3 end = r6Style ? entity.getPosition() : tracerEnd(entity);
            Color color = r6Style ? colorFor(entity) : tracerColor.resolve();
            Render.tracer(end, tracerWidth.getFloat(), color);
        }
    }

    private Vec3 tracerEnd(GameEntity entity) {
        return switch (tracerTarget.get()) {
            case FEET -> entity.getPosition();
            case TORSO -> entity.getPosition().add(0d, entity.getHeight() / 2d, 0d);
            case HEAD -> entity.getEyePosition();
        };
    }

    /** @return the entities in range that the filters let through */
    private List<GameEntity> targets(float partialTicks) {
        List<GameEntity> nearby = Entities.nearby(partialTicks, distance.getInt());
        List<GameEntity> wanted = new ArrayList<>(nearby.size());
        for (GameEntity entity : nearby) {
            if (shouldRender(entity)) {
                wanted.add(entity);
            }
        }
        return wanted;
    }

    /** @return whether the filters let this entity through */
    private boolean shouldRender(GameEntity entity) {
        if (entity.isInvisible() && !invisibles.isOn()) {
            return false;
        }
        if (entity.isFriend() && !friends.isOn()) {
            return false;
        }
        return switch (entity.getKind()) {
            case PLAYER -> players.isOn();
            case MONSTER -> monsters.isOn();
            case ANIMAL -> animals.isOn();
            case NEUTRAL -> neutrals.isOn();
        };
    }

    /**
     * The colour to draw an entity in, for whichever mode is running.
     *
     * <p>Friend beats everything: who someone is matters more than what they are or
     * how hurt they are, and a friend fading to red reads as a warning about the
     * wrong thing.
     */
    public Color colorFor(GameEntity entity) {
        if (entity.isFriend()) {
            return friendColor.resolve();
        }
        if (healthTint.isOn() && entity.getMaxHealth() > 0f) {
            return ColorUtil.health(entity.getHealth(), entity.getMaxHealth());
        }
        return switch (entity.getKind()) {
            case PLAYER -> playerColor.resolve();
            case MONSTER -> monsterColor.resolve();
            case ANIMAL -> animalColor.resolve();
            case NEUTRAL -> neutralColor.resolve();
        };
    }

    public enum TracerTarget {
        FEET,
        TORSO,
        HEAD
    }
}
