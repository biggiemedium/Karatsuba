package dev.px.karatsuba.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.px.core.setting.Setting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Any number of a list of options, each its own row with a checkbox: what a
 * profile includes, which kinds of entity a module looks at.
 *
 * <p>Core's {@code MultiEnumSetting} is the same idea with the options fixed when
 * the code is compiled, and the GUIs draw it compactly, as a chip or a row of
 * options. This is the one drawn as the whole list, every option in view and one
 * press away, and its options are read live like {@link ChoiceSetting}'s, so a list
 * that grows shows the new option the next time it is drawn.
 *
 * <pre>{@code
 * private final ListSetting parts = new ListSetting("Includes", () -> List.of("modules", "hud", "theme"))
 *         .labelledBy(Parts::label)
 *         .requireOne();
 * if (parts.has("hud")) { ... }
 * }</pre>
 *
 * <p>The value is kept as text in the options' order, and an option ticked that is
 * no longer offered is kept rather than dropped, so a list that shrinks and grows
 * back loses nothing. Saved with the profile like any other setting; drawn by
 * {@code ListRow} in both GUIs.
 */
public final class ListSetting extends Setting<Set<String>> {

    private final Supplier<List<String>> options;
    private Function<String, String> labels = option -> option;
    private boolean requireOne;

    /**
     * @param options read every time they are asked for
     * @param ticked  the options ticked by default
     */
    public ListSetting(String name, Supplier<List<String>> options, String... ticked) {
        super(name, ordered(List.of(ticked), options.get()));
        this.options = options;
    }

    // ---------------------------------------------------------------- reads

    /** @return every option, now */
    public List<String> getOptions() {
        return options.get();
    }

    public boolean has(String option) {
        return get().contains(option);
    }

    /** @return how many of the options offered now are ticked */
    public int count() {
        int ticked = 0;
        for (String option : options.get()) {
            if (has(option)) {
                ticked++;
            }
        }
        return ticked;
    }

    public String labelOf(String option) {
        return labels.apply(option);
    }

    /** @return whether unticking the last option is refused */
    public boolean requiresOne() {
        return requireOne;
    }

    /** @return whether {@code option} can be changed now: anything but the last one ticked, when one is required */
    public boolean canToggle(String option) {
        return !(requireOne && has(option) && count() == 1);
    }

    // --------------------------------------------------------------- writes

    /** Ticks or unticks {@code option}; refused if it is the last one ticked and one is required. */
    public void toggle(String option) {
        select(option, !has(option));
    }

    public void select(String option, boolean ticked) {
        if (has(option) == ticked || (!ticked && !canToggle(option))) {
            return;
        }
        Set<String> updated = new LinkedHashSet<>(get());
        if (ticked) {
            updated.add(option);
        } else {
            updated.remove(option);
        }
        set(updated);
    }

    /** Ticks every option offered now. */
    public void selectAll() {
        Set<String> updated = new LinkedHashSet<>(get());
        updated.addAll(options.get());
        set(updated);
    }

    /** Keeps the value in the options' order, so equal choices compare and save the same. */
    @Override
    protected Set<String> coerce(Set<String> incoming) {
        return ordered(incoming == null ? Collections.emptySet() : incoming, options == null ? List.of() : options.get());
    }

    // ------------------------------------------------------------ declaring

    /** Shows each option as {@code labels} says rather than as stored, which stays the config key. */
    public ListSetting labelledBy(Function<String, String> function) {
        this.labels = function;
        return this;
    }

    /** Never lets the list go empty: the last option ticked cannot be unticked. */
    public ListSetting requireOne() {
        this.requireOne = true;
        return this;
    }

    @Override
    public ListSetting describe(String text) {
        super.describe(text);
        return this;
    }

    @Override
    public ListSetting visibleWhen(BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public ListSetting onChange(Consumer<Set<String>> listener) {
        super.onChange(listener);
        return this;
    }

    // ---------------------------------------------------------- persistence

    @Override
    public String getTypeId() {
        return "list";
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (String option : get()) {
            array.add(option);
        }
        return array;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return;
        }
        Set<String> loaded = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
                loaded.add(element.getAsString());
            }
        }
        setSilently(loaded);
    }

    /** "None", "All", the one ticked, or "2 of 5". */
    @Override
    public String displayValue() {
        int ticked = count();
        int all = options.get().size();
        if (ticked == 0) {
            return "None";
        }
        if (ticked == all) {
            return "All";
        }
        if (ticked == 1) {
            for (String option : options.get()) {
                if (has(option)) {
                    return labelOf(option);
                }
            }
        }
        return ticked + " of " + all;
    }

    /** @return {@code ticked} in the order of {@code order}, then anything not offered, unmodifiable */
    private static Set<String> ordered(Collection<String> ticked, List<String> order) {
        Set<String> out = new LinkedHashSet<>();
        for (String option : order) {
            if (ticked.contains(option)) {
                out.add(option);
            }
        }
        out.addAll(ticked);
        return Collections.unmodifiableSet(out);
    }
}
