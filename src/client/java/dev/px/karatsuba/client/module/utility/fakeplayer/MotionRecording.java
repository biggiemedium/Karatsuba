package dev.px.karatsuba.client.module.utility.fakeplayer;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A stretch of the local player's movement, a tick at a time, and a playhead
 * that plays it back on a loop.
 *
 * <p>Recorded as positions, not inputs: a fake player has no physics of its own
 * to replay keys through, and positions reproduce the path exactly, jumps, falls
 * and strafes included. Played back a frame a tick, so it moves at the speed it
 * was recorded at.
 */
public final class MotionRecording {

    /** One tick of movement. */
    public record Frame(Vec3 position, Vec2 rotation, boolean sneaking) {
    }

    /** How a recording repeats. */
    public enum LoopMode {
        /** From the end, straight back to the start. */
        RESTART,
        /** Forwards, then backwards, then forwards: never a jump back to the start. */
        PING_PONG
    }

    private final List<Frame> frames = new ArrayList<>();
    private int playhead;
    private int direction = 1;

    public void clear() {
        frames.clear();
        rewind();
    }

    public void add(Frame frame) {
        frames.add(frame);
    }

    public int size() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    /** Back to the first frame, going forwards. */
    public void rewind() {
        playhead = 0;
        direction = 1;
    }

    public Frame first() {
        return frames.get(0);
    }

    /**
     * @return the frame to show this tick, then moves the playhead on
     */
    public Frame next(LoopMode mode) {
        Frame frame = frames.get(playhead);
        if (frames.size() == 1) {
            return frame;
        }
        int following = playhead + direction;
        if (following < 0 || following >= frames.size()) {
            if (mode == LoopMode.PING_PONG) {
                direction = -direction;
                following = playhead + direction;
            } else {
                following = 0;
            }
        }
        playhead = following;
        return frame;
    }

    /**
     * @return whether the next frame is the very first: a fresh start or a restart,
     *         which should be jumped to rather than walked to across the map
     */
    public boolean atStart() {
        return playhead == 0 && direction == 1;
    }

    /** @return the length in seconds, at twenty frames a second */
    public double seconds() {
        return frames.size() / 20d;
    }
}
