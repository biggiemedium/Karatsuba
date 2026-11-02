package dev.px.karatsuba.client.module.render.gameover;

import dev.px.core.event.Subscribe;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.event.game.player.DeathScreenEvent;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.render.GameOverModule;

import java.util.concurrent.ThreadLocalRandom;

/** The game's own death screen, with a line of our own for the title. */
public final class VanillaGameOver extends SubMode<GameOverModule.Mode> {

    private static final String[] TITLES = {
            "You croaked.",
            "Outplayed. For now...",
            "He's hacking for sure.",
            "That went well... kinda.",
            "Slain, but still stylish.",
            "Happens to the best of us.",
            "Insert coin to continue...",
            "Config issue.",
            "This ain't it, chief.",
    };

    private final BooleanSetting customTitle = bool("Custom Title", true)
            .describe("Replace \"You Died!\" with a random line");

    public VanillaGameOver() {
        super(GameOverModule.Mode.VANILLA);
    }

    @Subscribe
    private void onDeathScreen(DeathScreenEvent event) {
        if (customTitle.isOn()) {
            event.setTitle(TITLES[ThreadLocalRandom.current().nextInt(TITLES.length)]);
        }
    }
}
