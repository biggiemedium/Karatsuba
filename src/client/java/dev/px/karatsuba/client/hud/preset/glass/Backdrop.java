package dev.px.karatsuba.client.hud.preset.glass;

import dev.px.core.Core;
import dev.px.karatsuba.adapter.render.shader.SceneTexture;
import dev.px.karatsuba.adapter.render.shader.ShaderPasses;
import dev.px.karatsuba.client.hud.preset.HudPlate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is behind each element's plates: how bright, and how busy
 * ({@code hud/DESIGN.md} §4.6, §4.14). Read from the HUD glass's own capture,
 * shrunk to a few dozen pixels and read back, so it costs no capture of its own
 * (§4.3); and only a few times a second, since a read back waits for the GPU and
 * what is behind a plate rarely changes faster than that.
 *
 * <p>Each element keeps a light or dark verdict with hysteresis, so a capsule
 * that adapts to its backdrop never flickers between the two.
 */
public final class Backdrop {

    /** Frames between readings: about six a second at 60 fps. */
    private static final int EVERY = 10;

    /** The read back's width, in pixels: enough to tell one corner's plate from another. */
    private static final int SAMPLE_WIDTH = 64;

    /** Brighter than this behind a plate and it turns light; darker than the other and it turns dark again. */
    private static final float LIGHT_ABOVE = 0.62f;
    private static final float DARK_BELOW = 0.45f;

    /**
     * What is behind one element's plates.
     *
     * @param brightness its mean luminance, 0 to 1
     * @param busy       how much it varies: the luminance's standard deviation, 0 to about 0.5
     * @param light      whether the plates read as light (bright behind them), with hysteresis
     * @param capsule    whether its plates were capsules, which alone adapt (§4.14)
     */
    public record Reading(float brightness, float busy, boolean light, boolean capsule) {
    }

    private static final Map<String, Reading> READINGS = new HashMap<>();
    private static int frame;

    private Backdrop() {
    }

    /** @return the last reading behind {@code owner}'s plates, or null before the first */
    public static Reading of(String owner) {
        return READINGS.get(owner);
    }

    /**
     * Reads what is behind {@code plates}, every few frames. Right after their glass
     * is drawn, while the capture it shared still holds the screen behind them.
     */
    static void measure(List<HudPlate> plates) {
        if (frame++ % EVERY != 0 || plates.isEmpty()) {
            return;
        }
        read(plates, ShaderPasses.sampleScene(SAMPLE_WIDTH),
                Core.platform().getScreenWidth(), Core.platform().getScreenHeight());
    }

    /** Takes one reading behind {@code plates} from {@code sample}, a picture of the whole screen. */
    static void read(List<HudPlate> plates, SceneTexture.Sample sample, float screenWidth, float screenHeight) {
        if (sample == null || screenWidth <= 0f || screenHeight <= 0f) {
            return;
        }
        Map<String, double[]> sums = new HashMap<>();
        Map<String, Boolean> capsules = new HashMap<>();
        for (HudPlate plate : plates) {
            int left = (int) Math.floor(plate.x() / screenWidth * sample.width());
            int right = (int) Math.ceil((plate.x() + plate.width()) / screenWidth * sample.width()) - 1;
            int top = (int) Math.floor(plate.y() / screenHeight * sample.height());
            int bottom = (int) Math.ceil((plate.y() + plate.height()) / screenHeight * sample.height()) - 1;
            double[] sum = sums.computeIfAbsent(plate.owner(), owner -> new double[3]);
            for (int row = top; row <= Math.max(top, bottom); row++) {
                for (int column = left; column <= Math.max(left, right); column++) {
                    float luminance = sample.luminance(column, row);
                    sum[0] += luminance;
                    sum[1] += luminance * luminance;
                    sum[2]++;
                }
            }
            capsules.merge(plate.owner(), plate.capsule(), Boolean::logicalAnd);
        }
        sums.forEach((owner, sum) -> {
            if (sum[2] <= 0d) {
                return;
            }
            float mean = (float) (sum[0] / sum[2]);
            float spread = (float) Math.sqrt(Math.max(0d, sum[1] / sum[2] - mean * (double) mean));
            Reading last = READINGS.get(owner);
            boolean wasLight = last != null && last.light();
            boolean light = wasLight ? mean > DARK_BELOW : mean > LIGHT_ABOVE;
            READINGS.put(owner, new Reading(mean, spread, light, capsules.getOrDefault(owner, false)));
        });
    }
}
