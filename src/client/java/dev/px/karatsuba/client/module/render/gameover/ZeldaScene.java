package dev.px.karatsuba.client.module.render.gameover;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.MouseButton;
import dev.px.core.render.Alignment;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Texture;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.adapter.asset.Sounds;
import dev.px.karatsuba.adapter.screen.DeathScreens;

/**
 * Zelda's game over: the music, a fade to black with the red GAME OVER coming up
 * through it, then Continue and Quit.
 *
 * <p>Pick with the arrow keys or W and S and confirm with Enter or Space, or point
 * and click. Continue respawns (Spectate in hardcore); Quit leaves for the title
 * screen. The buttons take input once they have started to fade in.
 */
final class ZeldaScene implements GameOverScene {

    private static final String TITLE = Assets.ROOT + "texture/death_screen/zeldagameovertext.png";

    private static final long FADE_MS = 3_000L;
    private static final long BUTTONS_MS = 1_000L;

    private static final float BUTTON_WIDTH = 200f;
    private static final float BUTTON_HEIGHT = 30f;
    private static final float BUTTON_GAP = 10f;

    private static final Color FRAME = Color.of(126, 87, 66, 90);
    private static final Color SELECTED = Color.of(241, 222, 197, 255);
    private static final Color LABEL = Color.of(255, 255, 255, 221);
    private static final Color HINT = Color.of(255, 255, 255, 180);

    private final Animation fade = Animation.fade(FADE_MS, Easing.LINEAR).target(1f);
    private final Animation buttons = Animation.fade(BUTTONS_MS, Easing.LINEAR);
    private final String[] labels;

    /** Laid out by {@link #render}, read by input: {@code x, y} of each button. */
    private final float[][] layout = new float[2][2];

    private int selected;
    private boolean chosen;

    ZeldaScene(boolean hardcore, float volume) {
        this.labels = new String[] {hardcore ? "Spectate" : "Continue", "Quit"};
        Sounds.play("zeldagameover", volume);
    }

    @Override
    public void render(float width, float height) {
        float shade = fade.get();
        Render.rect(0f, 0f, width, height, Color.BLACK.withAlpha(shade));

        Texture title = Assets.texture(TITLE);
        if (title != null) {
            float titleWidth = Math.min(width * 0.55f, 560f);
            float titleHeight = titleWidth * title.getHeight() / title.getWidth();
            Render.texture(title, (width - titleWidth) / 2f, height * 0.3f - titleHeight / 2f,
                    titleWidth, titleHeight, Color.WHITE.withAlpha(shade));
        }

        if (fade.isFinished()) {
            buttons.target(1f);
        }
        float alpha = buttons.get();
        if (alpha <= 0f) {
            return;
        }
        float mouseX = Core.platform().getMouseX();
        float mouseY = Core.platform().getMouseY();
        Font font = Assets.fontFile("sf-pro/sf-pro.ttf", 15f);
        for (int i = 0; i < labels.length; i++) {
            float x = (width - BUTTON_WIDTH) / 2f;
            float y = height / 2f + 10f + i * (BUTTON_HEIGHT + BUTTON_GAP);
            layout[i][0] = x;
            layout[i][1] = y;
            if (over(i, mouseX, mouseY)) {
                selected = i;
            }
            drawButton(font, labels[i], x, y, i == selected, alpha);
        }
        Font small = Assets.fontFile("sf-pro/sf-pro.ttf", 10f);
        float hintY = layout[1][1] + BUTTON_HEIGHT + 20f;
        Render.text(small, "Continue from last save.", width / 2f, hintY, Alignment.CENTER, HINT.fade(alpha));
    }

    private static void drawButton(Font font, String label, float x, float y, boolean selected, float alpha) {
        if (selected) {
            Render.shadow(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 6f, SELECTED.fade(alpha * 0.6f));
            Render.rectOutline(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 1.5f, SELECTED.fade(alpha));
        } else {
            Render.rectOutline(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 1.5f, FRAME.fade(alpha));
        }
        Render.text(font, label, x + BUTTON_WIDTH / 2f, y + (BUTTON_HEIGHT - font.getHeight()) / 2f,
                Alignment.CENTER, LABEL.fade(alpha));
    }

    @Override
    public void keyPressed(Key key) {
        if (!ready()) {
            return;
        }
        switch (key) {
            case UP, W -> selected = 0;
            case DOWN, S -> selected = 1;
            case ENTER, NUMPAD_ENTER, SPACE -> choose(selected);
            default -> {
            }
        }
    }

    @Override
    public void mousePressed(float x, float y, MouseButton button) {
        if (!ready() || button != MouseButton.LEFT) {
            return;
        }
        for (int i = 0; i < labels.length; i++) {
            if (over(i, x, y)) {
                choose(i);
            }
        }
    }

    /** @return whether the buttons are up enough to press: they have started to fade in */
    private boolean ready() {
        return buttons.get() > 0f;
    }

    private boolean over(int button, float x, float y) {
        float left = layout[button][0];
        float top = layout[button][1];
        return x >= left && x <= left + BUTTON_WIDTH && y >= top && y <= top + BUTTON_HEIGHT;
    }

    private void choose(int button) {
        if (chosen) {
            return;
        }
        chosen = true;
        if (button == 0) {
            DeathScreens.respawn();
        } else {
            DeathScreens.quitToTitle();
        }
    }
}
