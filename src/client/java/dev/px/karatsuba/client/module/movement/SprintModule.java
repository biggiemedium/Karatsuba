package dev.px.karatsuba.client.module.movement;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.event.game.player.PlayerUpdateEvent;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * Keeps the player sprinting.
 *
 * <p>Also the shortest worked example of the shape every module takes: a setting
 * exists because it is declared, a handler exists because it is annotated, the
 * handler stops firing on its own while the module is disabled, and the game is
 * reached through the adapter rather than through {@code net.minecraft}.
 *
 * <p>It listens on {@link PlayerUpdateEvent} rather than {@code TickEvent}, which
 * is the difference between the two events in one line: {@code TickEvent} is the
 * whole client tick and fires in a menu with no player, while this one brackets the
 * player's own update. {@link Stage#POST} is after the game has read this tick's
 * movement input, which is what the decision below depends on.
 */
@ModuleInfo(name = "Sprint", description = "Sprints without holding the key", category = "Movement")
public final class SprintModule extends ClientModule {

    private final BooleanSetting whileSneaking = bool("While Sneaking", false)
            .describe("Keep sprinting when crouched, which the game normally forbids");

    @Subscribe(stage = Stage.POST)
    private void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!Players.present()) {
            return;
        }
        // Sprinting backwards or sideways looks obviously wrong, and the server
        // rejects it anyway, so only forward movement counts.
        boolean allowed = whileSneaking.isOn() || !Players.sneaking();

        Players.setSprinting(Players.movingForward() && allowed);
    }

    @Override
    protected void onDisable() {
        // Leaving the flag set would keep the player sprinting after a toggle off.
        Players.setSprinting(false);
    }
}
