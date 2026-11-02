package dev.px.karatsuba.adapter.screen;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.DeathScreenEvent;
import dev.px.karatsuba.adapter.game.player.DeathCause;
import dev.px.karatsuba.adapter.mixin.render.DeathScreenAccessor;
import dev.px.karatsuba.adapter.mixin.render.ScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Dying, for a client that draws its own death screen: whether the player is dead,
 * and the two things the game's death screen can do about it.
 *
 * <p>{@link #onShow} is the adapter's half of {@link DeathScreenEvent}, called by
 * {@code MinecraftClientMixin}.
 *
 * <p><b>Game thread only.</b>
 */
public final class DeathScreens {

    private DeathScreens() {
    }

    /** @return whether the player is dead: from the hit that killed them until the server respawns them */
    public static boolean dead() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && player.isDead();
    }

    /**
     * Asks the server to respawn the player, as the death screen's Respawn button
     * does (Spectate World in hardcore). The player stays dead until it answers.
     */
    public static void respawn() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.requestRespawn();
        }
    }

    /** Leaves the world for the title screen, exactly as the death screen's Title Screen button does. */
    public static void quitToTitle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            client.world.disconnect();
        }
        client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        client.setScreen(new TitleScreen());
    }

    /**
     * Called with the screen {@code setScreen} is about to show, after the game
     * picked it. Anything but a death screen is returned untouched.
     *
     * @return the screen to show instead: the same death screen (titled as asked),
     *         a bare {@link CoreScreen} for an overlay that took over, or null
     */
    public static Screen onShow(Screen screen) {
        if (!(screen instanceof DeathScreen) || !Events.isArmed()) {
            return screen;
        }
        DeathScreenAccessor death = (DeathScreenAccessor) screen;
        Text message = death.karatsuba$getMessage();
        DeathScreenEvent event = Events.post(new DeathScreenEvent(
                message == null ? "" : message.getString(), death.karatsuba$isHardcore(), DeathCause.of(message)));
        if (event.isCancelled()) {
            return OverlayHost.isCapturing() ? new CoreScreen() : null;
        }
        if (event.getTitle() != null) {
            ((ScreenAccessor) screen).karatsuba$setTitle(Text.literal(event.getTitle()));
        }
        return screen;
    }
}
