package dev.px.karatsuba.client.screen;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.karatsuba.adapter.screen.DeathScreens;
import dev.px.karatsuba.adapter.screen.Overlay;
import dev.px.karatsuba.client.module.render.gameover.GameOverScene;

import java.util.Set;

/**
 * Game Over's death screens on screen: one {@link GameOverScene} at a time, drawn
 * over everything and given all input while it shows.
 *
 * <p>Opened from a {@code DeathScreenEvent} handler with {@link #show}, which is
 * what makes the adapter put up the bare screen instead of the game's death screen.
 * It closes itself once the player is alive again, and when the game takes the
 * screen away (leaving the world).
 */
public final class GameOverOverlay implements Overlay {

    /**
     * How long a scene waits to see the player dead before giving up. The death
     * message can arrive a tick before the health that makes the player dead, so
     * "alive" only counts as "respawned" after "dead" has been seen.
     */
    private static final long DEATH_GRACE_MS = 3_000L;

    private GameOverScene scene;
    private long shownAt;
    private boolean seenDead;

    /** Starts a scene, ending any that was showing. Safe inside a {@code DeathScreenEvent} handler. */
    public void show(GameOverScene next) {
        close();
        scene = next;
        shownAt = System.currentTimeMillis();
        seenDead = DeathScreens.dead();
    }

    /** @return whether a scene is showing */
    public boolean isShowing() {
        return scene != null;
    }

    /** @return whether this scene is the one showing */
    public boolean isShowing(GameOverScene candidate) {
        return candidate != null && scene == candidate;
    }

    /** Ends the scene, if one is showing. */
    public void close() {
        GameOverScene ending = scene;
        scene = null;
        if (ending != null) {
            ending.end();
        }
    }

    @Override
    public boolean isCapturing() {
        return scene != null;
    }

    @Override
    public void render(float screenWidth, float screenHeight) {
        if (scene == null) {
            return;
        }
        boolean dead = DeathScreens.dead();
        seenDead |= dead;
        if (seenDead ? !dead : System.currentTimeMillis() - shownAt > DEATH_GRACE_MS) {
            close();
            return;
        }
        scene.render(screenWidth, screenHeight);
    }

    @Override
    public void dismiss() {
        close();
    }

    @Override
    public boolean keyPressed(Key key, Set<Modifier> modifiers) {
        scene.keyPressed(key);
        return true;
    }

    @Override
    public boolean charTyped(char character) {
        return true;
    }

    @Override
    public boolean mousePressed(float x, float y, MouseButton button) {
        scene.mousePressed(x, y, button);
        return true;
    }

    @Override
    public boolean scrolled(float amount, float x, float y) {
        return true;
    }
}
