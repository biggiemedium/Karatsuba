package dev.px.karatsuba.client.hud.element;

import dev.px.core.render.Color;

/**
 * The colours of good, middling and bad, for elements that judge a connection.
 *
 * <p>Softer than the game's pure red and green, which vibrate against each other
 * and against a bright sky. Never the only cue: every element using these shows
 * the same state by shape too (bars lit), since red against green is exactly the
 * pair common colourblindness cannot tell apart.
 */
final class Health {

    static final Color GOOD = Color.of(90, 210, 125);
    static final Color FAIR = Color.of(240, 200, 85);
    static final Color BAD = Color.of(240, 95, 95);

    private Health() {
    }

    /** @param level 3 good, 2 fair, 1 bad */
    static Color of(int level) {
        return level >= 3 ? GOOD : level == 2 ? FAIR : BAD;
    }
}
