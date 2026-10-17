package dev.px.karatsuba.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.px.core.setting.Setting;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One of a list of options that is only known at runtime: the fonts in the mod's
 * font folder, say. Core's {@code EnumSetting} is the same idea with the options
 * fixed when the code is compiled.
 *
 * <p>The options are read live, so a list that grows (a font added to the folder)
 * shows the new option the next time the row is drawn. The value is kept as text,
 * and kept even when it is no longer an option: a font removed and put back comes
 * back chosen. Until then {@link #resolved()} answers the default, which is
 * always the first option.
 */
public final class ChoiceSetting extends Setting<String> {

    private final Supplier<List<String>> options;
    private Function<String, String> labels = option -> option;
    private Function<String, String> migration = saved -> saved;

    /**
     * @param options       read every time they are asked for; the first is the default
     *                      and must always be present
     */
    public ChoiceSetting(String name, Supplier<List<String>> options) {
        super(name, options.get().get(0));
        this.options = options;
    }

    /** @return every option, now */
    public List<String> getOptions() {
        return options.get();
    }

    /** @return the chosen option if it is still one, else the default */
    public String resolved() {
        List<String> now = options.get();
        return now.contains(get()) ? get() : getDefaultValue();
    }

    public String labelOf(String option) {
        return labels.apply(option);
    }

    /** Shows each option as {@code labels} says rather than as stored, which stays the config key. */
    public ChoiceSetting labelledBy(Function<String, String> function) {
        this.labels = function;
        return this;
    }

    /** Reads a saved value through {@code function} first, for an option that was renamed: a font that moved folder. */
    public ChoiceSetting migratedBy(Function<String, String> function) {
        this.migration = function;
        return this;
    }

    @Override
    public String getTypeId() {
        return "choice";
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            setSilently(migration.apply(json.getAsString()));
        }
    }

    @Override
    public String displayValue() {
        return labelOf(resolved());
    }

    @Override
    public ChoiceSetting describe(String text) {
        super.describe(text);
        return this;
    }

    @Override
    public ChoiceSetting visibleWhen(BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public ChoiceSetting onChange(Consumer<String> listener) {
        super.onChange(listener);
        return this;
    }
}
