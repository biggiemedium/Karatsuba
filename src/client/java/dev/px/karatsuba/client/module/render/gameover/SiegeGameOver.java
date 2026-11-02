package dev.px.karatsuba.client.module.render.gameover;

import dev.px.core.event.Subscribe;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.event.game.player.DeathScreenEvent;
import dev.px.karatsuba.adapter.event.game.render.CameraDistanceEvent;
import dev.px.karatsuba.adapter.event.game.render.CameraEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.render.GameOverModule;
import dev.px.karatsuba.client.screen.Overlays;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rainbow Six Siege's death in place of the death screen, with an announcer line;
 * see {@link SiegeScene}. Also moves the camera while the scene shows.
 */
public final class SiegeGameOver extends SubMode<GameOverModule.Mode> {

    /** The announcer's lines, each a clip in {@code sounds.json}. */
    public enum VoiceLine {
        CONTAINER_FAIL_1("containerfail1"),
        CONTAINER_FAIL_2("containerfail2"),
        CONTAINER_FAIL_3("containerfail3"),
        CONTAINER_FAIL_4("containerfail4"),
        DEFUSE_FAIL("defusefail");

        private final String sound;

        VoiceLine(String sound) {
            this.sound = sound;
        }
    }

    private final NumberSetting<Float> volume = number("Volume", 1f, 0f, 1f)
            .describe("How loud the death sound and the announcer play");

    private final BooleanSetting blur = bool("Blur", false)
            .describe("Blur the world behind the card; Siege itself leaves it sharp");

    private final BooleanSetting announcer = bool("Announcer", true)
            .describe("An announcer line over the death sound");

    private final MultiEnumSetting<VoiceLine> voiceLines = multi("Voice Lines", VoiceLine.class, VoiceLine.values())
            .describe("Which lines the announcer picks from, at random");

    /** Blocks the close-up aims beside the face, to put the face right of centre. */
    private static final double CLOSE_UP_OFFSET = 0.45d;

    /** The scene this mode last showed; the camera follows it while it is up. */
    private SiegeScene scene;

    public SiegeGameOver() {
        super(GameOverModule.Mode.SIEGE);
        voiceLines.visibleWhen(announcer);
    }

    @Subscribe
    private void onDeathScreen(DeathScreenEvent event) {
        // Asked again whenever the game rebuilds its death screen; one death is one scene.
        if (!Overlays.gameOver().isShowing()) {
            scene = new SiegeScene(event.getCause(), blur.isOn(), volume.get(),
                    announcer.isOn() ? pickVoiceLine() : null);
            Overlays.gameOver().show(scene);
        }
        event.cancel();
    }

    /** Pulls the camera back from the body. Walls still bring it in. */
    @Subscribe
    private void onCameraDistance(CameraDistanceEvent event) {
        if (showing()) {
            event.setDistance(scene.distance());
        }
    }

    /**
     * Tilts the camera down onto the body, keeping it the distance the game placed
     * it at (so a wall that pulled it in still does), around the eyes.
     */
    @Subscribe
    private void onCamera(CameraEvent event) {
        if (!showing() || !event.isThirdPerson()) {
            return;
        }
        if (scene.inCloseUp() && closeUp(event)) {
            return;
        }
        GameEntity self = Entities.self(event.getTickDelta());
        if (self == null) {
            return;
        }
        Vec3 eyes = self.getEyePosition();
        double distance = event.getPosition().distanceTo(eyes);
        Vec2 rotation = event.getRotation();
        Vec2 tilted = Vec2.rotation(rotation.getYaw(), scene.pitch(rotation.getPitch()));
        event.setPosition(RotationMath.project(eyes, tilted, -distance));
        event.setRotation(tilted);
    }

    /**
     * The close-up: in front of the killer's face, pushing in slowly, framed with
     * them right of centre as the old screen had it. On the player's own body when
     * nobody killed them, or when the killer isn't loaded.
     *
     * @return whether the camera was placed
     */
    private boolean closeUp(CameraEvent event) {
        GameEntity target = Entities.byId(scene.closeUpTarget(), event.getTickDelta());
        if (target == null) {
            target = Entities.self(event.getTickDelta());
        }
        if (target == null) {
            return false;
        }
        Vec3 eyes = target.getEyePosition();
        Vec3 facing = RotationMath.direction(target.getRotation().getYaw(), 0f);
        Vec3 camera = eyes.add(facing.scale(scene.closeUpDistance())).add(0d, 0.1d, 0d);
        // The camera looks back along -facing; its right is (-z, 0, x) of that.
        // Aiming left of the face puts the face right of centre.
        Vec3 viewRight = Vec3.of(facing.getZ(), 0d, -facing.getX());
        Vec3 aim = eyes.add(viewRight.scale(-CLOSE_UP_OFFSET)).add(0d, -0.15d, 0d);
        event.setPosition(camera);
        event.setRotation(camera.rotationTo(aim));
        return true;
    }

    private boolean showing() {
        return Overlays.gameOver().isShowing(scene);
    }

    private String pickVoiceLine() {
        List<VoiceLine> picked = new ArrayList<>(voiceLines.get());
        return picked.isEmpty() ? null : picked.get(ThreadLocalRandom.current().nextInt(picked.size())).sound;
    }
}
