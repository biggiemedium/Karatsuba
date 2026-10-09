package dev.px.karatsuba.client.module.mode;

import dev.px.core.Core;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A module that does one job several different ways.
 *
 * <p>Some features have genuinely different implementations rather than
 * different settings: a velocity modifier that drops the packet and one that
 * stalls movement to make the server apply it late are not two configurations of
 * one algorithm, they are two algorithms. Writing both into one class gives a
 * file where every handler opens with a switch, every field belongs to one branch
 * or another, and adding a third way means touching all of it.
 *
 * <p>So each way is a {@link SubMode}: its own class, its own settings, its own
 * handlers, its own state.
 *
 * <pre>{@code
 * @ModuleInfo(name = "Velocity", category = "Combat")
 * public final class VelocityModule extends ModalModule<VelocityModule.Mode> {
 *
 *     public enum Mode { CANCEL, SCALED, BUFFER }
 *
 *     private final CancelVelocity cancel = attach(new CancelVelocity());
 *     private final GroupSetting cancelPanel = panelFor(cancel);
 *
 *     public VelocityModule() {
 *         super(Mode.CANCEL);
 *     }
 * }
 * }</pre>
 *
 * <p>Two lines per mode, and the second is optional &mdash; a mode with no
 * settings of its own needs no panel.
 *
 * <h2>What this class actually does</h2>
 * <ul>
 *   <li><b>Declares the selection.</b> An {@code EnumSetting} named "Mode",
 *       which renders as a dropdown and persists by name, so reordering the enum
 *       cannot shift a saved config onto the wrong mode.</li>
 *   <li><b>Puts each mode on the event bus, once.</b> A mode's handlers are then
 *       gated by {@link SubMode#isListening()} rather than by subscribing and
 *       unsubscribing, so switching modes cannot leave one live.</li>
 *   <li><b>Folds each mode's settings into a collapsible group</b> that is only
 *       visible while that mode is selected, so the panel shows the settings for
 *       the thing that is actually running.</li>
 * </ul>
 *
 * <h2>Why the settings still persist</h2>
 * A mode's settings are declared on the mode, not on the module, so Core's field
 * discovery would never see them. {@link #panelFor} hands them to a
 * {@code GroupSetting}, which <em>is</em> a module field &mdash; and a group
 * serialises its children. So a mode's settings are saved and loaded with
 * everything else, including for modes that are not currently selected.
 *
 * @param <E> the enum naming the modes
 */
public abstract class ModalModule<E extends Enum<E>> extends ClientModule {

    private final List<SubMode<E>> modes = new ArrayList<>(4);

    /**
     * The selection.
     *
     * <p>Declared here rather than by each subclass so every modal module calls
     * it the same thing, and so the enum and the modes cannot disagree about
     * which constants exist.
     */
    private final EnumSetting<E> mode;

    /**
     * @param defaultMode the mode a fresh install starts on
     */
    protected ModalModule(E defaultMode) {
        this.mode = enumOf("Mode", defaultMode)
                .describe("Which implementation to use");

        // Fires on a change through the GUI or a command, but not on a config
        // load -- Core applies those silently, on purpose, so that a profile
        // being read does not run half the client's reaction code.
        this.mode.onChange(this::notifySelected);
    }

    /**
     * Registers a mode and puts it on the event bus.
     *
     * <p>Call from a field initialiser, which runs after this constructor, so the
     * selection setting already exists by the time a mode is attached.
     *
     * @return the mode, so it can be attached and kept in one line
     */
    protected final <M extends SubMode<E>> M attach(M subMode) {
        subMode.bindTo(this);
        modes.add(subMode);
        // Once, for the life of the client. Gated by SubMode#isListening rather
        // than unsubscribed on a mode change, which is why nothing here has to be
        // undone when the selection moves.
        Core.bus().subscribe(subMode);
        return subMode;
    }

    /**
     * A collapsible panel holding one mode's settings, shown only while that mode
     * is selected.
     *
     * <p>Assign it to a field. It has to be a field for Core to discover it, and
     * discovering it is what makes the settings inside persist.
     */
    protected final GroupSetting panelFor(SubMode<E> subMode) {
        return group(subMode.getName(), true)
                .containing(subMode.settingsArray())
                .visibleWhen(() -> mode.get() == subMode.getKey())
                .describe("Settings for the " + subMode.getName() + " mode");
    }

    /** @return the selected mode's enum constant */
    public final E getMode() {
        return mode.get();
    }

    /** Switches mode from a command or a keybind, as if the dropdown had been used. */
    public final void setMode(E selected) {
        mode.set(selected);
    }

    /**
     * @return whether this mode's handlers should currently run
     *
     * <p>Asked by every attached mode on every event it is subscribed to, so it
     * is two reads and a comparison and nothing else.
     */
    final boolean isActive(SubMode<E> subMode) {
        return isEnabled() && mode.get() == subMode.getKey();
    }

    /** @return the attached modes, in the order they were attached. */
    public final List<SubMode<E>> getModes() {
        return Collections.unmodifiableList(modes);
    }

    /** @return the mode object currently selected, or null if none matches */
    public final SubMode<E> getActiveMode() {
        for (SubMode<E> candidate : modes) {
            if (candidate.getKey() == mode.get()) {
                return candidate;
            }
        }
        return null;
    }

    private void notifySelected(E selected) {
        for (SubMode<E> candidate : modes) {
            if (candidate.getKey() == selected) {
                candidate.selected();
            }
        }
    }

    /**
     * The ArrayList label: the mode's name.
     *
     * <p>Which mode is running is the only thing worth showing for a module whose
     * whole point is having several. A subclass with something better to say
     * overrides it.
     */
    @Override
    public String getDisplayInfo() {
        SubMode<E> active = getActiveMode();
        return active == null ? "" : active.getName();
    }
}
