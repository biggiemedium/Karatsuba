package dev.px.karatsuba.client.module.mode;

import dev.px.core.event.Listenable;
import dev.px.core.setting.Setting;
import dev.px.core.setting.SettingHolder;
import dev.px.core.util.text.TextUtil;

/**
 * One way a {@link ModalModule} can do its job.
 *
 * <p>A mode is a module in every way that matters except that it has no toggle
 * of its own: it declares its own settings, it subscribes its own
 * {@code @Subscribe} handlers, and it holds its own state. What it does not do is
 * decide when it runs &mdash; that is the selection on the module above it.
 *
 * <p>This is what stops a module with five strategies being one file with a
 * five-way switch in every handler. Each strategy is a class, and adding a sixth
 * touches nothing that already worked.
 *
 * <pre>{@code
 * public final class CancelVelocity extends SubMode<VelocityModule.Mode> {
 *
 *     private final BooleanSetting explosions = bool("Explosions", true);
 *
 *     public CancelVelocity() {
 *         super(VelocityModule.Mode.CANCEL);
 *     }
 *
 *     @Subscribe
 *     private void onKnockback(KnockbackEvent event) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <h2>Handlers are gated, not subscribed and unsubscribed</h2>
 * A mode is put on the event bus once, when it is attached, and stays there.
 * {@link #isListening()} is what decides whether its handlers actually run
 * &mdash; false unless the module is enabled <em>and</em> this mode is the one
 * selected.
 *
 * <p>That is the same mechanism Core uses for modules themselves, one level
 * down, and it is here for the same reason: switching modes cannot leave a
 * handler subscribed, and a mode cannot leak.
 *
 * <h2>State should expire rather than be reset</h2>
 * There is no {@code onDisabled} hook, deliberately. A mode stops receiving
 * events the moment it is deselected, which means any "I am halfway through
 * something" state is frozen rather than cleaned up. So prefer state that heals
 * on its own &mdash; a deadline that has passed, a claim that has expired
 * &mdash; over a counter that has to be zeroed. {@link #onSelected()} is there
 * for the cases where that is genuinely not possible.
 *
 * @param <E> the module's mode enum, which is also what selects this one
 */
public abstract class SubMode<E extends Enum<E>> extends SettingHolder implements Listenable {

    /** Which constant of the module's enum selects this mode. */
    private final E key;

    private final String name;

    /** Set by {@link ModalModule#attach}; null until then. */
    private ModalModule<E> owner;

    /**
     * @param key the enum constant this mode answers to. Its prettified name
     *            becomes the mode's label, so the dropdown entry and the settings
     *            group cannot drift apart
     */
    protected SubMode(E key) {
        this.key = key;
        this.name = TextUtil.prettify(key.name());
    }

    /** @param label an explicit label, where the enum name is not the right words */
    protected SubMode(E key, String label) {
        this.key = key;
        this.name = label;
    }

    public final E getKey() {
        return key;
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * @return whether this mode's handlers should run right now
     *
     * <p>The module is enabled and this is the selected mode. Final, because a
     * mode that could answer this for itself would be a mode that can run while
     * another one is selected.
     */
    @Override
    public final boolean isListening() {
        return owner != null && owner.isActive(this);
    }

    /**
     * Called when this mode becomes the selected one.
     *
     * <p>Not called when the module is merely enabled, and not called on
     * deselection. For clearing state that cannot expire on its own; see the
     * note on this class about preferring state that can.
     */
    protected void onSelected() {
    }

    /** @return the module this mode belongs to, or null before it is attached */
    protected final ModalModule<E> module() {
        return owner;
    }

    /** @return this mode's own settings, for the module to fold into its panel */
    final Setting<?>[] settingsArray() {
        return getSettings().toArray(new Setting<?>[0]);
    }

    final void bindTo(ModalModule<E> module) {
        this.owner = module;
    }

    final void selected() {
        onSelected();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }
}
