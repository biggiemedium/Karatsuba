package dev.px.karatsuba.client.module.render.gameover;

import dev.px.core.event.Subscribe;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.DeathScreenEvent;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.render.GameOverModule;
import dev.px.karatsuba.client.screen.Overlays;

/** Zelda's game over in place of the death screen; see {@link ZeldaScene}. */
public final class ZeldaGameOver extends SubMode<GameOverModule.Mode> {

    private final NumberSetting<Float> volume = number("Volume", 1f, 0f, 1f)
            .describe("How loud the game over music plays");

    public ZeldaGameOver() {
        super(GameOverModule.Mode.ZELDA);
    }

    @Subscribe
    private void onDeathScreen(DeathScreenEvent event) {
        // Asked again whenever the game rebuilds its death screen; one death is one scene.
        if (!Overlays.gameOver().isShowing()) {
            Overlays.gameOver().show(new ZeldaScene(event.isHardcore(), volume.get()));
        }
        event.cancel();
    }
}
