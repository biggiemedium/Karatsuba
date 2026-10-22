package dev.px.karatsuba.client.module.utility;

import dev.px.core.Core;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.simulation.DriftMonitor;
import dev.px.core.movement.timeline.EntryType;
import dev.px.core.network.packet.PacketKind;
import dev.px.core.movement.timeline.Timeline;
import dev.px.core.movement.timeline.TimelineJson;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.network.MinecraftPacketKind;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Records the player's movement, and the packets around it, to a file.
 *
 * <p>The worked example for <b>Core's timeline recorder</b>. Switching this on
 * calls {@code Core.timeline().begin}; switching it off ends the recording and
 * writes it to {@code .minecraft/karatsuba/recordings/} as JSON Lines, one entry
 * per line, in the exact order things happened across the game and network
 * threads.
 *
 * <p>Every tick of a recording holds the player's state before and after the
 * client reports it &mdash; position, velocity, ground, the keys held, sprint and
 * sneak, rotation &mdash; with the movement packets actually sent between the two.
 * A teleport, knockback or explosion from the server is followed by a correction
 * entry pointing at the client state it overruled. None of that is this module's
 * doing: the adapter posts the events and describes the packets, and this only
 * says when to start and stop.
 *
 * <p>{@code .record} drives it from chat, with a label and named marks; see
 * {@code RecordCommand}.
 *
 * <h2>Writing happens off the game thread</h2>
 * A long recording is hundreds of thousands of lines, and serialising it on the
 * game thread is a visible hitch at exactly the moment the player stopped to look
 * at something. The timeline is an immutable snapshot by the time {@code end()}
 * returns, so it is handed to a worker as is.
 */
@ModuleInfo(name = "Recorder", description = "Records movement and packets to a timeline file",
        category = "Utility")
public final class RecorderModule extends ClientModule {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final String DEFAULT_LABEL = "recording";

    /** Which packets a recording keeps. Ticks and motion are always kept. */
    public enum Packets {
        /** Everything, chunk data and other entities' traffic included. */
        ALL,
        /** Drops world-state and unclassified traffic, which is most of the volume and none of the movement. */
        MOVEMENT,
        /** Motion and ticks only. */
        NONE
    }

    private final EnumSetting<Packets> packets = enumOf("Packets", Packets.MOVEMENT)
            .describe("Which packets to keep; ticks and the player's motion are always recorded");

    /** The label the next recording starts under, set by the command. */
    private String nextLabel = DEFAULT_LABEL;

    private long startedNanos;

    /**
     * Starts a recording under a label.
     *
     * <p>A label goes into the file name and the file's header, so a folder of
     * recordings can be told apart without opening each one.
     */
    public void start(String label) {
        nextLabel = label == null || label.isBlank() ? DEFAULT_LABEL : label;
        enable();
    }

    @Override
    protected void onEnable() {
        // Also what stops a recording resuming by itself: a module saved enabled
        // is switched on at boot, on the title screen, and lands here.
        if (!Players.present()) {
            Notices.warn(Source.MODULE_MESSAGES, "Recorder", "Join a world to record");
            disable();
            return;
        }
        // Read live, so changing the setting mid-recording takes effect at once.
        Core.timeline().setFilter(description -> keeps(description.getKind()));
        Core.timeline().begin(nextLabel);
        startedNanos = System.nanoTime();
        Notices.info(Source.MODULE_MESSAGES, "Recorder", "Recording \"" + nextLabel + "\"");
    }

    @Override
    protected void onDisable() {
        String label = nextLabel;
        nextLabel = DEFAULT_LABEL;
        if (!Core.timeline().isRecording()) {
            return;
        }
        Timeline timeline = Core.timeline().end();
        Core.timeline().setFilter(null);

        // The drift monitor is the simulation's own opinion of how well it
        // tracked the game over the recording, which is worth reading next to it.
        String drift = describe(Core.simulation().getDrift());
        File file = fileFor(label);
        Core.threads().submit(() -> save(timeline, file, drift));
    }

    /**
     * Elapsed time rather than a tick or entry count: this is read every frame by
     * the ArrayList, and asking the recorder for a count means copying the buffer.
     */
    @Override
    public String getDisplayInfo() {
        if (!Core.timeline().isRecording()) {
            return "";
        }
        return (System.nanoTime() - startedNanos) / 1_000_000_000L + "s";
    }

    private boolean keeps(PacketKind kind) {
        return switch (packets.getValue()) {
            case ALL -> true;
            case MOVEMENT -> !kind.is(PacketKind.OTHER) && !kind.is(MinecraftPacketKind.WORLD_STATE);
            case NONE -> false;
        };
    }

    // --------------------------------------------------------------- saving

    /** Worker thread. Reports back through {@code sync}, because notifications are game state. */
    private static void save(Timeline timeline, File file, String drift) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                TimelineJson.write(timeline, out);
            }
        } catch (IOException failed) {
            Core.log().error("Could not write recording to " + file, failed);
            Core.threads().sync(() -> Notices.error(Source.MODULE_MESSAGES, "Recorder", "Could not save: " + failed.getMessage()));
            return;
        }

        String summary = timeline.getTicks() + " ticks, " + timeline.size() + " entries, "
                + timeline.ofType(EntryType.CORRECTION).size() + " corrections"
                + (timeline.getDropped() > 0 ? ", " + timeline.getDropped() + " dropped" : "");
        Core.log().info("Saved recording " + file + " (" + summary + "; " + drift + ")");
        Core.threads().sync(() -> Notices.success(Source.MODULE_MESSAGES, "Recorder", "Saved " + file.getName() + " - " + summary));
    }

    private static File fileFor(String label) {
        String safe = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        File folder = new File(Core.platform().getDataDirectory(), "recordings");
        return new File(folder, safe + "_" + LocalDateTime.now().format(STAMP) + ".jsonl");
    }

    /** Also used by the command's status line. */
    public static String describe(DriftMonitor drift) {
        return String.format(Locale.ROOT, "simulation %s, error h %.4f v %.4f b/t over %d ticks",
                drift.isReliable() ? "reliable" : "unreliable",
                drift.getHorizontalError(), drift.getVerticalError(), drift.getSampleCount());
    }
}
