package dev.px.karatsuba.client.module.render;

import dev.px.core.Core;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Texture;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.shader.Uniforms;
import dev.px.karatsuba.adapter.event.game.render.HandRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.SkyRenderEvent;
import dev.px.karatsuba.adapter.render.shader.ShaderPasses;
import dev.px.karatsuba.adapter.render.shader.Silhouettes;
import dev.px.karatsuba.adapter.render.shader.Silhouettes.Target;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.render.ShaderCatalogue;
import dev.px.karatsuba.client.render.ThemeColors;

import java.util.EnumSet;
import java.util.Set;

/**
 * Shader effects over the world: entities and your hands outlined, filled or
 * glowing in one colour, and the sky replaced with a shader of its own.
 *
 * <h2>Entities and hands</h2>
 * Each frame the chosen entities are drawn again, by the game's own renderers,
 * into an offscreen mask ({@code Silhouettes}), and {@code effect/silhouette.fsh}
 * turns the mask into a fill over each shape and an edge around it: a hard
 * {@code Outline}, or a {@code Glow} that fades out. Shapes are exact, cut-outs
 * included, and an edge follows the model rather than a box. The hands are the
 * same, straight after the game draws them.
 *
 * <p>{@code Through Walls} on shows the whole of every target; off, the world
 * stays in front of them and only what you could see is shaded.
 *
 * <h2>Skybox</h2>
 * The vanilla overworld sky (dome, sunrise, sun, moon and stars) is replaced by
 * {@code effect/sky.fsh}: a gradient from the horizon colour to the zenith colour,
 * with optional twinkling stars. Clouds, weather and the world draw over it as
 * usual. If the shader fails to compile the vanilla sky stays.
 *
 * <h2>Theme Colours</h2>
 * On, the shader colour is a slow wave between the theme's two accents, and the
 * sky runs from the secondary accent at the horizon to the primary overhead.
 */
@ModuleInfo(name = "Shaders", description = "Outlines and glows entities and your hands, and can replace the sky",
        category = "Render")
public final class ShadersModule extends ClientModule {

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw in the client theme's colours instead of the ones set here");

    // ---------------------------------------------------------------- shader

    private final EnumSetting<Style> style = enumOf("Style", Style.OUTLINE)
            .describe("A hard edge, or a glow that fades out");

    private final NumberSetting<Float> width = number("Width", 1.5f, 0.5f, 5f)
            .step(0.1f)
            .describe("Edge width in pixels at 1080p")
            .visibleWhen(() -> style.get() == Style.OUTLINE);

    private final NumberSetting<Float> radius = number("Radius", 8f, 2f, 16f)
            .step(0.5f)
            .describe("How far the glow reaches, in pixels at 1080p")
            .visibleWhen(() -> style.get() == Style.GLOW);

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.25f, 0f, 1f)
            .step(0.05f)
            .describe("How strongly each shape is filled; 0 for an edge only");

    private final NumberSetting<Float> edgeOpacity = number("Edge Opacity", 0.9f, 0f, 1f)
            .step(0.05f)
            .describe("How strong the outline or glow is");

    private final ColorSetting color = color("Colour", Color.of(255, 110, 190, 255))
            .visibleWhen(() -> !themeColours.isOn());

    private final GroupSetting shaderGroup = group("Shader", true)
            .containing(style, width, radius, fillOpacity, edgeOpacity, color);

    // --------------------------------------------------------------- targets

    private final BooleanSetting hands = bool("Hands", true)
            .describe("Your hands and what they hold");

    private final BooleanSetting self = bool("Self", true)
            .describe("You, in third person");

    private final BooleanSetting players = bool("Players", true);

    private final BooleanSetting hostiles = bool("Hostiles", true);

    private final BooleanSetting passives = bool("Passives", true)
            .describe("Animals");

    private final BooleanSetting neutrals = bool("Neutrals", false)
            .describe("Everything else alive: villagers, golems, fish, bats");

    private final BooleanSetting crystals = bool("Crystals", true);

    private final BooleanSetting items = bool("Items", true)
            .describe("Dropped items");

    private final BooleanSetting pearls = bool("Pearls", true)
            .describe("Thrown ender pearls");

    private final BooleanSetting experience = bool("Experience", true)
            .describe("Thrown experience bottles and orbs");

    private final BooleanSetting projectiles = bool("Projectiles", false)
            .describe("Arrows, tridents and anything else in flight");

    private final NumberSetting<Integer> range = integer("Range", 64, 8, 256)
            .describe("Blocks from you within which entities are shaded");

    private final BooleanSetting throughWalls = bool("Through Walls", true)
            .describe("Shade the whole of every target, even behind blocks");

    private final GroupSetting targetGroup = group("Targets", true)
            .containing(hands, self, players, hostiles, passives, neutrals, crystals, items, pearls,
                    experience, projectiles, range, throughWalls);

    // ------------------------------------------------------------------- sky

    private final BooleanSetting skybox = bool("Skybox", false)
            .describe("Replace the overworld sky with a shader sky");

    private final ColorSetting zenith = color("Zenith", Color.of(40, 30, 90, 255))
            .describe("The sky straight overhead")
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting horizon = color("Horizon", Color.of(230, 110, 160, 255))
            .describe("The sky at the horizon")
            .visibleWhen(() -> !themeColours.isOn());

    private final BooleanSetting stars = bool("Stars", true)
            .describe("Twinkling stars across the upper sky");

    private final GroupSetting skyGroup = group("Skybox Options", true)
            .containing(zenith, horizon, stars)
            .visibleWhen(skybox);

    /** Refilled each frame rather than allocated. */
    private final Set<Target> targets = EnumSet.noneOf(Target.class);

    // ---------------------------------------------------------------- events

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        collectTargets();
        if (targets.isEmpty()) {
            return;
        }
        Texture mask = Silhouettes.entities(targets, range.get(), throughWalls.isOn());
        if (mask != null) {
            ShaderPasses.overlay(ShaderCatalogue.SILHOUETTE, u -> silhouette(u, mask));
        }
    }

    @Subscribe
    private void onHands(HandRenderEvent event) {
        if (!hands.isOn()) {
            return;
        }
        Texture mask = Silhouettes.hands();
        if (mask != null) {
            ShaderPasses.overlay(ShaderCatalogue.SILHOUETTE, u -> silhouette(u, mask));
        }
    }

    @Subscribe
    private void onSky(SkyRenderEvent event) {
        if (!skybox.isOn()) {
            return;
        }
        boolean themed = themeColours.isOn();
        Color top = ThemeColors.of(zenith, themed, 0f);
        Color bottom = ThemeColors.of(horizon, themed, 1f);
        boolean drew = ShaderPasses.sky(ShaderCatalogue.SKY, u -> u
                .set("uZenith", top)
                .set("uHorizon", bottom)
                .set("uStars", stars.isOn() ? 1f : 0f));
        // Only once ours is up: a sky shader that failed leaves the vanilla sky.
        if (drew) {
            event.cancel();
        }
    }

    // -------------------------------------------------------------- internal

    private void silhouette(Uniforms uniforms, Texture mask) {
        boolean themed = themeColours.isOn();
        Color primary = themed ? Core.themes().accent(0f) : color.resolve();
        Color secondary = themed ? Core.themes().accent(1f) : primary;
        boolean glow = style.get() == Style.GLOW;
        uniforms.sampler("uMask", mask, 0)
                .set("uColorA", primary)
                .set("uColorB", secondary)
                .set("uGradient", themed ? 1f : 0f)
                .set("uMode", glow ? 1 : 0)
                .set("uWidth", glow ? radius.get() : width.get())
                .set("uFillOpacity", fillOpacity.get())
                .set("uEdgeOpacity", edgeOpacity.get());
    }

    private void collectTargets() {
        targets.clear();
        add(self, Target.SELF);
        add(players, Target.PLAYERS);
        add(hostiles, Target.HOSTILES);
        add(passives, Target.PASSIVES);
        add(neutrals, Target.NEUTRALS);
        add(crystals, Target.CRYSTALS);
        add(items, Target.ITEMS);
        add(pearls, Target.PEARLS);
        add(experience, Target.EXPERIENCE);
        add(projectiles, Target.PROJECTILES);
    }

    private void add(BooleanSetting setting, Target target) {
        if (setting.isOn()) {
            targets.add(target);
        }
    }

    public enum Style {
        OUTLINE,
        GLOW
    }
}
