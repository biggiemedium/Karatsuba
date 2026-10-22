package dev.px.karatsuba.client.service.dig;

/**
 * How a caller's dig went on the last tick it was decided: what it is waiting
 * for, or how far through the block it is.
 *
 * @param state    where the dig stands
 * @param progress how far through the block, 0 to 1, while {@link State#DIGGING}; 0 otherwise
 * @param reason   what it waits for or why it cannot go, in a few words; empty while digging
 */
public record DigReport(State state, float progress, String reason) {

    public enum State {
        /** Nothing asked for. */
        IDLE,
        /** Another caller's dig won this tick. */
        OUTBID,
        /** It cannot be dug from here: out of reach or sight, or unbreakable. */
        BLOCKED,
        /** Held, progress kept: for the tool, the look, or hands busy this tick. */
        WAITING,
        /** The game's pause after a block. */
        COOLING,
        /** Being dug. */
        DIGGING
    }

    public static final DigReport IDLE = new DigReport(State.IDLE, 0f, "");
    public static final DigReport OUTBID = new DigReport(State.OUTBID, 0f, "another dig");
    public static final DigReport COOLING = new DigReport(State.COOLING, 0f, "");

    public static DigReport blocked(String reason) {
        return new DigReport(State.BLOCKED, 0f, reason);
    }

    public static DigReport waiting(String reason) {
        return new DigReport(State.WAITING, 0f, reason);
    }

    public static DigReport digging(float progress) {
        return new DigReport(State.DIGGING, progress, "");
    }
}
