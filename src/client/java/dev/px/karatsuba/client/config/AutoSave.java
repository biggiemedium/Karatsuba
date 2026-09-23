package dev.px.karatsuba.client.config;

import dev.px.core.Core;
import dev.px.core.config.ConfigLoadEvent;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.module.ModuleToggleEvent;
import dev.px.core.setting.SettingChangeEvent;

/**
 * Writes the config out after a change instead of only at shutdown.
 *
 * <p>Core already persists the state: {@code Core.start()} registers a
 * {@code ToggleableSection("modules", moduleRegistry)}, which saves each module's
 * enabled flag and every setting it declares, keyed by name so that adding or
 * reordering modules cannot shift a saved config onto the wrong one. The gap is
 * <em>when</em> that happens &mdash; {@code ConfigService.save()} runs from
 * {@code Core.stop()}, so a crash, a force-quit or a killed dev run loses
 * everything changed since launch.
 *
 * <p>So: mark dirty on any change, and flush once the changes stop. The quiet
 * period matters because dragging a slider fires a change event per frame, and
 * writing the whole profile per frame would stutter the GUI that is drawing it.
 *
 * <p>Leaving a world flushes immediately, since that is the moment someone is most
 * likely to close the game outright.
 *
 * <h2>What the bus does not announce</h2>
 * Two kinds of change reach the config without ever posting an event: a HUD
 * layout, which is plain saved data rather than a {@code Setting}, and the flags
 * on a colour picker and the position of a GUI window, which Core applies
 * directly. Those callers say so through {@link #markDirty()} instead, which is
 * why it is public.
 */
public final class AutoSave {

    /** How long changes have to stop for before the profile is written. */
    private static final long QUIET_PERIOD_MILLIS = 2_000L;

    /**
     * The installed instance, so {@link #markDirty()} can be reached from a
     * caller that has no reason to be holding one.
     *
     * <p>Null before {@link #install()}, and calls are dropped until then rather
     * than throwing: marking a config dirty before there is a config is not an
     * error, it is just early.
     */
    private static volatile AutoSave instance;

    /** Zero means clean; otherwise the timestamp of the most recent change. */
    private volatile long dirtySince;

    private AutoSave() {
    }

    /**
     * Subscribes to the bus.
     *
     * <p>Safe to install before {@code Core.start()}: the first thing that clears
     * the dirty flag is the config load inside {@code start()} itself.
     */
    public static void install() {
        AutoSave autoSave = new AutoSave();
        Core.bus().subscribe(autoSave);
        instance = autoSave;
    }

    /**
     * Records that something worth saving changed.
     *
     * <p>For the changes the event bus never hears about &mdash; see the note on
     * this class. Cheap enough to call on every frame of a drag: it writes one
     * timestamp and the flush is what costs anything.
     */
    public static void markDirty() {
        AutoSave autoSave = instance;
        if (autoSave != null) {
            autoSave.dirtySince = System.currentTimeMillis();
        }
    }

    @Subscribe
    private void onModuleToggled(ModuleToggleEvent event) {
        markDirty();
    }

    @Subscribe
    private void onSettingChanged(SettingChangeEvent event) {
        markDirty();
    }

    /**
     * Loading a profile toggles modules, which marks everything dirty.
     *
     * <p>Core posts this once the last section has been applied, so clearing the
     * flag here is what stops a load from being written straight back out. It also
     * means a load that failed halfway is not committed to disk two seconds later.
     */
    @Subscribe
    private void onConfigLoaded(ConfigLoadEvent event) {
        dirtySince = 0L;
    }

    @Subscribe
    private void onWorldChanged(WorldEvent event) {
        if (event.isUnloaded() && dirtySince != 0L) {
            flush();
        }
    }

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (dirtySince != 0L && System.currentTimeMillis() - dirtySince >= QUIET_PERIOD_MILLIS) {
            flush();
        }
    }

    private void flush() {
        // Cleared first: a failed write should not spin, retrying every tick.
        dirtySince = 0L;
        Core.config().save();
    }
}
