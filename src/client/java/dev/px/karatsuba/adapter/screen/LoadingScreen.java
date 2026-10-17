package dev.px.karatsuba.adapter.screen;

/**
 * What the client draws in place of the game's loading screen: the Mojang red
 * with its logo and bar, shown while resources load at start-up and on every
 * reload after (a resource pack change, F3+T).
 *
 * <p>A seam for the same reason as {@link Overlay}. The adapter owns <em>when</em>
 * the game is loading, how far it has got and how far through its fades it is,
 * because only the game knows; what it looks like is the client's. The game's own
 * timing is kept whole: it still decides when loading is done, fades out over the
 * title screen and hands over, and only its drawing is replaced.
 *
 * <p>One slot, not a list: there is one loading screen. Empty until the client
 * {@linkplain #install installs} one, and the game's own draws as usual while it
 * is, so nothing changes before the client has booted.
 */
public interface LoadingScreen {

    /**
     * Draws the loading screen, inside a 2D frame of its own and over whatever
     * the game drew under it (the screen being faded in or out over).
     *
     * <p>At full opacity it must cover the whole screen: at start-up the game has
     * nothing under it, and its own clear to red is one of the draws replaced.
     *
     * @param screenWidth  scaled screen width, the space Core's {@code Render} draws in
     * @param screenHeight scaled screen height
     * @param progress     how much has loaded, 0 to 1, eased as the game eases its bar
     * @param opacity      the whole screen's opacity, 0 to 1: rising as a reload
     *                     fades in over a screen, falling as it fades out once done
     * @param finished     0 while loading; then 0 to 1 over the second after it
     *                     finishes, before the fade out starts, for the bar to go
     */
    void render(float screenWidth, float screenHeight, float progress, float opacity, float finished);

    /** Makes {@code screen} the loading screen, or puts the game's back with null. */
    static void install(LoadingScreen screen) {
        Slot.current = screen;
    }

    /** @return the installed loading screen, or null to let the game draw its own */
    static LoadingScreen installed() {
        return Slot.current;
    }

    /** Holds the one installed screen; an interface cannot have a field that changes. */
    final class Slot {

        private static volatile LoadingScreen current;

        private Slot() {
        }
    }
}
