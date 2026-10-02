package dev.px.karatsuba.adapter.world;

import net.minecraft.client.world.ClientWorld;

/**
 * The client's own idea of the time and the weather.
 *
 * <p>Both are the client's copy, not the server's. Changing them changes what is
 * drawn &mdash; the sky, the light, the rain &mdash; and nothing the server
 * knows about: mobs still spawn at night, crops still need light. The server also
 * keeps correcting them (time about once a second, weather whenever it changes),
 * so a module holding either fixed has to suppress those corrections too, through
 * {@code WorldTimeEvent} and {@code WeatherEvent}.
 *
 * <p>Everything is borrowed state, the same as an option in
 * {@code adapter.game.Options}: read it before overriding, hand it back after.
 *
 * <p>Every reader answers safely out of a world, and every writer is a no-op
 * there. <b>Game thread only.</b>
 */
public final class Environment {

    private Environment() {
    }

    /** @return whether there is a world to ask about */
    public static boolean present() {
        return Voxels.world() != null;
    }

    // ----------------------------------------------------------------- time

    /** @return the time of day in ticks (0 sunrise, 6000 noon, 18000 midnight), or 0 out of a world */
    public static long timeOfDay() {
        ClientWorld world = Voxels.world();
        return world == null ? 0L : world.getLevelProperties().getTimeOfDay();
    }

    /**
     * Moves the client's clock.
     *
     * <p>The client advances it by one each tick on its own, so a module holding
     * a fixed time sets it every tick rather than once.
     */
    public static void timeOfDay(long ticks) {
        ClientWorld world = Voxels.world();
        if (world != null) {
            world.getLevelProperties().setTimeOfDay(ticks);
        }
    }

    // -------------------------------------------------------------- weather

    /** @return whether it is raining (or snowing, by biome), or false out of a world */
    public static boolean raining() {
        ClientWorld world = Voxels.world();
        return world != null && world.getLevelProperties().isRaining();
    }

    /** @return how heavy the rain is, 0 to 1, or 0 out of a world */
    public static float rainLevel() {
        ClientWorld world = Voxels.world();
        return world == null ? 0f : world.getRainGradient(1f);
    }

    /** @return how strong the thunderstorm is, 0 to 1, or 0 out of a world */
    public static float thunderLevel() {
        ClientWorld world = Voxels.world();
        return world == null ? 0f : world.getThunderGradient(1f);
    }

    /**
     * Sets the weather the client draws.
     *
     * <p>The levels are what is actually rendered; {@code raining} is the flag the
     * game checks for things like rain sounds and whether lightning can appear.
     * Thunder only shows on top of rain, so a thunder level with no rain level
     * draws a dark sky and nothing falling.
     *
     * @param raining the rain flag
     * @param rain    rain level, clamped to 0..1
     * @param thunder thunder level, clamped to 0..1
     */
    public static void weather(boolean raining, float rain, float thunder) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return;
        }
        world.getLevelProperties().setRaining(raining);
        // Both setters write the previous-tick value as well, so the change shows
        // this frame instead of being interpolated in over the next one.
        world.setRainGradient(clamp(rain));
        world.setThunderGradient(clamp(thunder));
    }

    private static float clamp(float level) {
        return Math.max(0f, Math.min(1f, level));
    }
}
