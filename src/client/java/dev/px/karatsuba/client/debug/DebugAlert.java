package dev.px.karatsuba.client.debug;

import lombok.Getter;

/** One line in the debug log: what happened, and when, on the wall clock. */
@Getter
public final class DebugAlert {

    private final AlertType type;
    private final String message;
    private final long millis;

    public DebugAlert(AlertType type, String message, long millis) {
        this.type = type;
        this.message = message;
        this.millis = millis;
    }

    /** @return how long ago this happened, in seconds */
    public double ageSeconds(long nowMillis) {
        return (nowMillis - millis) / 1000d;
    }
}
