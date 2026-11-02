package dev.px.karatsuba.client.module.render;

import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.module.mode.ModalModule;
import dev.px.karatsuba.client.module.render.gameover.SiegeGameOver;
import dev.px.karatsuba.client.module.render.gameover.VanillaGameOver;
import dev.px.karatsuba.client.module.render.gameover.ZeldaGameOver;
import dev.px.karatsuba.client.screen.Overlays;

/**
 * Death screens from other games.
 *
 * <p>Ported from Leapfrog's Death Adjustments. Each way of dying is a mode in
 * {@code gameover/}; Zelda and Siege draw a {@code GameOverScene} through
 * {@code GameOverOverlay} in place of the game's death screen, which the adapter
 * offers up as a {@code DeathScreenEvent}.
 *
 * @author James
 */
@ModuleInfo(name = "Game Over", description = "Death screens from other games: Zelda's fade to black, Siege's death cam",
        category = "Render")
public final class GameOverModule extends ModalModule<GameOverModule.Mode> {

    public enum Mode {
        VANILLA,
        ZELDA,
        SIEGE
    }

    // Field order is the order the panels appear in, so it matches the enum.
    private final VanillaGameOver vanilla = attach(new VanillaGameOver());
    private final GroupSetting vanillaPanel = panelFor(vanilla);

    private final ZeldaGameOver zelda = attach(new ZeldaGameOver());
    private final GroupSetting zeldaPanel = panelFor(zelda);

    private final SiegeGameOver siege = attach(new SiegeGameOver());
    private final GroupSetting siegePanel = panelFor(siege);

    public GameOverModule() {
        super(Mode.VANILLA);
    }

    @Override
    protected void onDisable() {
        // Off mid-death: the game's own death screen takes over next tick.
        Overlays.gameOver().close();
    }
}
