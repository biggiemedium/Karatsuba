package dev.px.karatsuba.adapter.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes a game option past its own validator.
 *
 * <p>{@code SimpleOption.setValue} runs the value through the callbacks that
 * describe the slider it came from, and a value outside that range is not
 * clamped &mdash; it is <b>rejected</b>:
 *
 * <pre>{@code
 * T validated = this.callbacks.validate(value).orElseGet(() -> {
 *     LOGGER.error("Illegal option value {} for {}", value, this.text);
 *     return this.defaultValue;
 * });
 * }</pre>
 *
 * <p>So asking for a gamma of 6 logs an error and quietly assigns the
 * <em>default</em>. A module that sets it every tick therefore does nothing at
 * all except write one error line per tick, which is how a log grows by a
 * megabyte a minute while the feature it belongs to appears broken.
 *
 * <p>Going past the vanilla range is the entire point of a brightness module, so
 * the value is written to the field directly. The field is package-private,
 * which is exactly what an {@code @Accessor} is for.
 *
 * <p><b>Nothing is notified.</b> No validation, no change callback, no save to
 * {@code options.txt}. For gamma that is correct &mdash; the lightmap reads the
 * value fresh every frame &mdash; but it makes this the wrong tool for an option
 * something caches. Anything borrowing an option this way owes it back; see
 * {@code adapter.game.Options}.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {

    /**
     * Assigns the backing field.
     *
     * <p>Typed as {@code Object} because {@code SimpleOption}'s type parameter
     * erases to it, which is what the field descriptor actually says. A caller
     * passes the boxed type the option holds &mdash; a {@code Double} for gamma
     * &mdash; and passing the wrong one fails later, at the read, rather than
     * here.
     */
    @Accessor("value")
    void karatsuba$setValue(Object value);
}
