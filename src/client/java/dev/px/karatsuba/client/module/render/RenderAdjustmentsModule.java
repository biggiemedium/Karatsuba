package dev.px.karatsuba.client.module.render;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.network.WeatherEvent;
import dev.px.karatsuba.adapter.event.game.network.WorldTimeEvent;
import dev.px.karatsuba.adapter.event.game.render.CameraDistanceEvent;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.GammaEvent;
import dev.px.karatsuba.adapter.event.game.render.ParticleSpawnEvent;
import dev.px.karatsuba.adapter.world.Environment;

/**
 * A grab bag of visual tweaks: brightness, the effects the game layers over the
 * view, the third-person camera, and the sky.
 *
 * <p>Ported setting for setting from Leapfrog's {@code RenderAdjustments}, with
 * the settings grouped so the module is four headings rather than seventeen rows.
 *
 * <p>Everything except the sky is a veto on an adapter event and holds no state:
 * turning a setting off simply stops the veto, and the next frame is vanilla.
 * The sky is different. Time and weather are the client's copy of world state,
 * so overriding them is a borrow: the real values are remembered (and kept
 * current from the server's corrections, which are suppressed while the override
 * holds) and handed back when the override ends.
 */
@ModuleInfo(name = "Render Adjustments", description = "Removes visual clutter and fixes the sky", category = "Render")
public final class RenderAdjustmentsModule extends Module {

    private final BooleanSetting fullBright = bool("Full Bright", true)
            .describe("Lights everything as if it were day, without touching your brightness setting");

    /**
     * The gamma Full Bright draws with. Values above 1 go beyond the vanilla
     * slider, which is the point; 10 is bright enough that a cave at light level
     * 0 reads clearly. Was the separate Fullbright module's one setting.
     */
    private final NumberSetting<Double> brightness = decimal("Brightness", 10d, 1d, 20d)
            .describe("How bright Full Bright draws; above 1 is beyond the vanilla slider")
            .visibleWhen(fullBright);

    // ------------------------------------------------------------ no render

    private final BooleanSetting darkness = bool("Darkness", true)
            .describe("The pulsing gloom and fog from the Darkness effect");
    private final BooleanSetting fire = bool("Fire", true)
            .describe("The flames across the screen while burning");
    private final BooleanSetting hurtCam = bool("Hurt Cam", true)
            .describe("The camera roll when taking damage");
    private final BooleanSetting explosions = bool("Explosions", true)
            .describe("Explosion smoke particles");
    private final BooleanSetting fireworks = bool("Fireworks", false)
            .describe("Firework trails and bursts");
    private final BooleanSetting potionIcons = bool("Potion Icons", false)
            .describe("The status effect icons in the top right of the HUD");
    private final BooleanSetting portals = bool("Portals", true)
            .describe("The purple swirl while standing in a nether portal");
    private final BooleanSetting nausea = bool("Nausea", true)
            .describe("The green swirl and world warp from Nausea; also stills the warp inside a portal");
    private final BooleanSetting fog = bool("Fog", false)
            .describe("Distance fog, and the fog under water and in lava");

    private final GroupSetting noRender = group("No Render", true)
            .describe("Effects the game draws over the view, each removed when ticked")
            .containing(darkness, fire, hurtCam, explosions, fireworks, potionIcons, portals, nausea, fog);

    // --------------------------------------------------------------- camera

    private final BooleanSetting viewClip = bool("View Clip", true)
            .describe("Lets the third-person camera pass through walls instead of being pushed in");
    private final NumberSetting<Double> clipDistance = decimal("Clip Distance", 3.5d, 1d, 20d)
            .step(0.1d)
            .describe("How far behind you the third-person camera sits")
            .visibleWhen(viewClip);

    private final GroupSetting camera = group("Camera", true)
            .containing(viewClip, clipDistance);

    // ---------------------------------------------------------- environment

    private final BooleanSetting daylightCycle = bool("Daylight Cycle", true)
            .describe("Holds the sky at a fixed time of day. Visual only: the server's clock is unchanged");
    private final EnumSetting<TimeOfDay> daylightMode = enumOf("Daylight Mode", TimeOfDay.DAY)
            .visibleWhen(daylightCycle);
    private final BooleanSetting weatherCycle = bool("Weather Cycle", true)
            .describe("Holds the weather fixed. Visual only: the server's weather is unchanged");
    private final EnumSetting<Weather> weatherMode = enumOf("Weather Mode", Weather.CLEAR)
            .visibleWhen(weatherCycle);

    private final GroupSetting environment = group("Environment", true)
            .containing(daylightCycle, daylightMode, weatherCycle, weatherMode);

    /** The real time of day while the clock is held. Null when it is not. */
    private Long borrowedTime;

    /** The real weather while it is held. Null when it is not. */
    private BorrowedWeather borrowedWeather;

    // -------------------------------------------------------------- visuals

    @Subscribe
    private void onGamma(GammaEvent event) {
        if (fullBright.isOn()) {
            event.setGamma(brightness.getFloat());
        }
    }

    @Subscribe
    private void onEffect(EffectRenderEvent event) {
        if (settingFor(event.getEffect()).isOn()) {
            event.cancel();
        }
    }

    @Subscribe
    private void onParticle(ParticleSpawnEvent event) {
        BooleanSetting setting = switch (event.getKind()) {
            case EXPLOSION -> explosions;
            case FIREWORK -> fireworks;
        };
        if (setting.isOn()) {
            event.cancel();
        }
    }

    @Subscribe
    private void onCameraDistance(CameraDistanceEvent event) {
        // FreeCam owns the camera outright while it is on; a third-person offset
        // applied on top would move the view away from where it was flown to.
        if (viewClip.isOn() && !Core.modules().isEnabled(FreeCamModule.class)) {
            event.setClipping(false);
            event.setDistance(clipDistance.getFloat());
        }
    }

    /**
     * The setting that vetoes each effect.
     *
     * <p>A switch with no default, so a new {@link EffectRenderEvent.Effect} is a
     * compile error here until it is given a setting, rather than a silent no-op.
     */
    private BooleanSetting settingFor(EffectRenderEvent.Effect effect) {
        return switch (effect) {
            case FIRE_OVERLAY -> fire;
            case HURT_TILT -> hurtCam;
            case PORTAL_OVERLAY -> portals;
            case NAUSEA -> nausea;
            case STATUS_EFFECT_ICONS -> potionIcons;
            case DARKNESS -> darkness;
            case FOG -> fog;
        };
    }

    // ---------------------------------------------------------- environment

    /**
     * The server correcting the clock, which would undo the held time within a
     * second. Suppressed while the time is held, and remembered so the real time
     * can be handed back.
     */
    @Subscribe
    private void onWorldTime(WorldTimeEvent event) {
        if (daylightCycle.isOn()) {
            borrowedTime = event.getTimeOfDay();
            event.cancel();
        }
    }

    /** The same for the weather: suppressed while held, and tracked for the hand-back. */
    @Subscribe
    private void onWeather(WeatherEvent event) {
        if (!weatherCycle.isOn()) {
            return;
        }
        BorrowedWeather real = borrowWeather();
        switch (event.getChange()) {
            case RAIN_STARTED -> real.raining = true;
            case RAIN_STOPPED -> real.raining = false;
            case RAIN_LEVEL -> real.rain = event.getValue();
            case THUNDER_LEVEL -> real.thunder = event.getValue();
        }
        event.cancel();
    }

    /**
     * Applies the held time and weather, or hands them back once a setting is
     * turned off.
     *
     * <p>Every tick rather than once, because the client advances its own clock
     * each tick, and checked here rather than in a setting listener so the
     * hand-back happens on the game thread with a world present.
     */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Environment.present()) {
            return;
        }

        if (daylightCycle.isOn()) {
            if (borrowedTime == null) {
                borrowedTime = Environment.timeOfDay();
            }
            Environment.timeOfDay(daylightMode.get().ticks);
        } else {
            restoreTime();
        }

        if (weatherCycle.isOn()) {
            borrowWeather();
            Weather held = weatherMode.get();
            Environment.weather(held.raining, held.rain, held.thunder);
        } else {
            restoreWeather();
        }
    }

    /**
     * Forgets the borrowed sky when the world goes away.
     *
     * <p>There is nothing to hand it back to, and the next world's real values
     * arrive from its server as the usual corrections, which are recorded then.
     */
    @Subscribe
    private void onWorld(WorldEvent event) {
        if (event.isUnloaded()) {
            borrowedTime = null;
            borrowedWeather = null;
        }
    }

    @Override
    protected void onDisable() {
        restoreTime();
        restoreWeather();
    }

    /** @return the remembered real weather, taken from the world now if nothing is remembered yet */
    private BorrowedWeather borrowWeather() {
        if (borrowedWeather == null) {
            borrowedWeather = new BorrowedWeather(
                    Environment.raining(), Environment.rainLevel(), Environment.thunderLevel());
        }
        return borrowedWeather;
    }

    private void restoreTime() {
        if (borrowedTime != null) {
            Environment.timeOfDay(borrowedTime);
            borrowedTime = null;
        }
    }

    private void restoreWeather() {
        if (borrowedWeather != null) {
            Environment.weather(borrowedWeather.raining, borrowedWeather.rain, borrowedWeather.thunder);
            borrowedWeather = null;
        }
    }

    /** The times Daylight Cycle can hold, in ticks of the day. */
    public enum TimeOfDay {
        DAY(1000L),
        NIGHT(18000L),
        EVENING(13000L);

        private final long ticks;

        TimeOfDay(long ticks) {
            this.ticks = ticks;
        }
    }

    /** The weather Weather Cycle can hold. */
    public enum Weather {
        CLEAR(false, 0f, 0f),
        RAIN(true, 1f, 0f),
        THUNDER(true, 1f, 1f);

        private final boolean raining;
        private final float rain;
        private final float thunder;

        Weather(boolean raining, float rain, float thunder) {
            this.raining = raining;
            this.rain = rain;
            this.thunder = thunder;
        }
    }

    /** The server's weather, kept up to date while the client draws something else. */
    private static final class BorrowedWeather {

        private boolean raining;
        private float rain;
        private float thunder;

        private BorrowedWeather(boolean raining, float rain, float thunder) {
            this.raining = raining;
            this.rain = rain;
            this.thunder = thunder;
        }
    }
}
