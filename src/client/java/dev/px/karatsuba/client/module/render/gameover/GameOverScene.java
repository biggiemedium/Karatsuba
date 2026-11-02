package dev.px.karatsuba.client.module.render.gameover;

import dev.px.core.input.Key;
import dev.px.core.input.MouseButton;

/**
 * One death screen, from the death until the player respawns or leaves.
 *
 * <p>Shown by {@link dev.px.karatsuba.client.screen.GameOverOverlay}, which owns
 * when it starts and stops; a scene only draws and answers input. Every key and
 * click goes to it while it shows, whether it uses them or not, so nothing behind
 * it (a module bind, the click GUI) fires while the player is dead.
 */
public interface GameOverScene {

    /** Draws a frame, in Core's scaled 2D space. */
    void render(float width, float height);

    default void keyPressed(Key key) {
    }

    default void mousePressed(float x, float y, MouseButton button) {
    }

    /**
     * The scene is over: respawned, left the world, or the module was turned off.
     * Hand back anything borrowed. Called once.
     */
    default void end() {
    }
}
