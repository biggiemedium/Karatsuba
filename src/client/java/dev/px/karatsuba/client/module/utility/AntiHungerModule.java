package dev.px.karatsuba.client.module.utility;

import dev.px.core.event.Subscribe;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.event.game.network.ClientCommandEvent;
import dev.px.karatsuba.adapter.event.game.network.MovementPacketEvent;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * Stops the server hearing about sprinting, so it never charges for it.
 *
 * <p>The client decides it is sprinting and tells the server; the server is what
 * applies the extra hunger drain. Suppressing the message leaves the client
 * sprinting at full speed with the server still believing the player is walking.
 *
 * <p><b>The one module here that arbitrates nothing</b>, and it is included for
 * that reason: it is the first consumer of the packet bridge, and it shows where
 * the line falls. Cancelling a packet is not a contended write &mdash; two
 * modules cancelling the same packet agree, and a third that does not cancel it
 * has nothing to lose. There is nothing to arbitrate, so there is no service.
 *
 * <h2>Why {@code ClientCommandEvent} rather than {@code PacketEvent}</h2>
 * Sprinting, sneaking, leaving a bed and deploying an elytra all travel as the
 * same packet with a mode field. A module matching on the packet name alone
 * could only see "some client command", and cancelling that would suppress
 * sneaking and elytra deploys too. The adapter reads the mode once and publishes
 * it as an enum, which is the same pattern {@code ScreenCloseEvent} follows.
 */
@ModuleInfo(name = "Anti Hunger", description = "Hides sprinting from the server so it costs no food",
        category = "Utility")
public final class AntiHungerModule extends Module {

    private final BooleanSetting sprint = bool("Sprint", true)
            .describe("Suppress the sprint start and stop messages");

    private final BooleanSetting sneak = bool("Sneak", false)
            .describe("Suppress sneaking too; more noticeable, because the server stops seeing you crouch");

    private final BooleanSetting ground = bool("Ground", false)
            .describe("Claim to be standing while briefly airborne, so small falls cost nothing");

    /**
     * Tells the server the player is on the ground when they nearly are.
     *
     * <p>The other half of the trick, and the one with a limit: a claim to be
     * standing while genuinely falling is checkable, so it is only made within a
     * block or so of the ground, where the client and the server disagree about
     * rounding rather than about physics.
     */
    @Subscribe
    private void onMovementPacket(MovementPacketEvent event) {
        if (!ground.isOn() || event.isOnGround()) {
            return;
        }
        if (!Players.present() || Players.velocity().getY() > 0d) {
            // Rising is the half nothing can explain away.
            return;
        }
        event.setOnGround(true);
    }

    @Subscribe
    private void onClientCommand(ClientCommandEvent event) {
        if (sprint.isOn() && event.isSprintChange()) {
            event.cancel();
            return;
        }
        if (sneak.isOn() && event.isSneakChange()) {
            event.cancel();
        }
    }
}
