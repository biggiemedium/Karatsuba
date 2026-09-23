package dev.px.karatsuba.adapter.platform;

import dev.px.core.util.CoreLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core's log, routed into the game's.
 *
 * <p>Core defaults to a {@code ConsoleLogger} writing to {@code System.out},
 * which is the right default for a library with no game on its classpath and the
 * wrong one inside Minecraft. Everything a player is ever asked for &mdash;
 * {@code latest.log}, the crash report, the launcher console &mdash; comes from
 * the game's logger, and a {@code println} reaches none of them with a
 * timestamp, a level or a thread name attached. A stack trace fares worse still:
 * {@code ConsoleLogger} prints it to a stream nothing captures in order, so it
 * interleaves with whatever else was writing.
 *
 * <p>SLF4J rather than Log4j directly: it is what the game itself logs through on
 * modern versions, so this needs no dependency the game has not already loaded.
 *
 * <p>Installed through {@link dev.px.core.CoreBuilder#logger}, which is the only
 * seam for it &mdash; Core resolves its logger at construction and every service
 * is handed it there, so this cannot be swapped in afterwards.
 */
public final class MinecraftLogger implements CoreLogger {

    private final Logger delegate;

    /**
     * @param name the logger name, shown in every line. The client name is the
     *             useful value: it is what a player greps for in a log they are
     *             about to paste into a bug report.
     */
    public MinecraftLogger(String name) {
        this.delegate = LoggerFactory.getLogger(name);
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warn(message);
    }

    @Override
    public void error(String message) {
        delegate.error(message);
    }

    @Override
    public void error(String message, Throwable thrown) {
        delegate.error(message, thrown);
    }

    @Override
    public void debug(String message) {
        // Debug is off in a normal game log, which is the point: Core's debug lines
        // are for someone who has turned them on, not for every player's disk.
        delegate.debug(message);
    }
}
