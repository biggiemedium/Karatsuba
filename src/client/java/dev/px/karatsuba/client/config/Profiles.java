package dev.px.karatsuba.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.px.core.Core;
import dev.px.core.config.ConfigLocation;
import dev.px.core.config.ConfigSection;
import dev.px.core.config.ConfigService;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Config profiles, and what each one includes.
 *
 * <p>A profile is a folder of config sections that switches together. It can hold
 * everything, or only some {@link ProfilePart parts} of it: a "PvP" profile of
 * modules alone switches the modules and leaves the HUD as it was. A part a profile
 * does not include is the {@value ConfigService#DEFAULT_PROFILE} profile's: read
 * from there when the profile is switched to, and saved there when it changes, so
 * nothing changed while it is active is lost. Default always includes everything.
 *
 * <p>Core does the routing ({@code ConfigService.setHolds}); this class keeps the
 * list of what each profile includes, in a shared section so it is read before any
 * profile is, and sequences the saves and loads so that switching, creating and
 * changing a profile never loses an unsaved change or writes one profile's state
 * over another's.
 *
 * <pre>{@code
 * Profiles.create("pvp", EnumSet.of(ProfilePart.MODULES));
 * Profiles.load("default");
 * }</pre>
 */
public final class Profiles {

    public static final String DEFAULT = ConfigService.DEFAULT_PROFILE;

    /** The shared section that lists what each profile includes. */
    static final String ID = "profile-parts";

    /** Longest profile name; the name is also a folder. */
    private static final int MAX_NAME = 32;

    /** What each profile includes, by folder name; a profile not listed includes everything. */
    private static final Map<String, Set<ProfilePart>> PARTS = new HashMap<>();

    private Profiles() {
    }

    /**
     * Registers the section and tells Core which sections each profile keeps.
     * Before {@code Core.start()}, which is the first load.
     */
    public static void install() {
        Core.config().register(new Section(), ConfigLocation.shared(ID));
        Core.config().setHolds((profile, sectionId) -> includes(profile, ProfilePart.of(sectionId)));
    }

    // ---------------------------------------------------------------- reads

    /** @return every profile, Default first, then by name */
    public static List<String> all() {
        List<String> names = new ArrayList<>(Core.config().listProfiles());
        names.remove(DEFAULT);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        names.add(0, DEFAULT);
        return names;
    }

    /** @return the profile in use */
    public static String active() {
        return Core.config().getActiveProfile();
    }

    public static boolean isActive(String profile) {
        return active().equalsIgnoreCase(profile);
    }

    public static boolean isDefault(String profile) {
        return DEFAULT.equalsIgnoreCase(profile);
    }

    /** @return the name a person reads: "Default" for the default profile, else its folder name */
    public static String label(String profile) {
        return isDefault(profile) ? "Default" : profile;
    }

    /** @return the profile called {@code name} whatever its case, or null */
    public static String find(String name) {
        String wanted = name.trim();
        for (String profile : all()) {
            if (profile.equalsIgnoreCase(wanted)) {
                return profile;
            }
        }
        return null;
    }

    /** @return what {@code profile} includes; everything for Default and for a profile from before parts */
    public static Set<ProfilePart> parts(String profile) {
        Set<ProfilePart> parts = isDefault(profile) ? null : PARTS.get(key(profile));
        return parts == null ? EnumSet.allOf(ProfilePart.class) : EnumSet.copyOf(parts);
    }

    public static boolean includes(String profile, ProfilePart part) {
        return parts(profile).contains(part);
    }

    /** @return what is wrong with {@code name} as a new profile, or null if it would do */
    public static String problem(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "Enter a name";
        }
        if (trimmed.length() > MAX_NAME) {
            return "Names are " + MAX_NAME + " characters or fewer";
        }
        if (!trimmed.matches("[A-Za-z0-9._-]+") || trimmed.matches("\\.+")) {
            return "Names can only contain letters, numbers, dots, dashes and underscores";
        }
        return null;
    }

    // --------------------------------------------------------------- writes

    /**
     * Saves what is on now as a new profile including {@code parts}, and switches
     * to it. The parts it leaves out are Default's from then on, so they change to
     * Default's as it opens.
     *
     * @return the new profile's name
     * @throws IllegalArgumentException if the name is not one {@link #problem} allows, or is taken
     */
    public static String create(String name, Set<ProfilePart> parts) {
        String trimmed = name.trim();
        if (problem(trimmed) != null || find(trimmed) != null) {
            throw new IllegalArgumentException("Not a free profile name: " + name);
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A profile includes at least one part");
        }
        ConfigService config = Core.config();
        // Keep what changed on the profile being left, before anything moves.
        config.save();
        PARTS.put(key(trimmed), EnumSet.copyOf(parts));
        // A profile with no folder keeps the current values for what it includes, and
        // reads the rest from Default; the save then writes each where it belongs.
        config.load(trimmed);
        config.save();
        Notices.success(Source.CLIENT, "Profiles", "Created " + trimmed);
        return trimmed;
    }

    /** Switches to {@code profile}, keeping what changed on the one being left. */
    public static void load(String profile) {
        ConfigService config = Core.config();
        config.save();
        config.load(profile);
        Notices.success(Source.CLIENT, "Profiles", "Switched to " + profile);
    }

    /**
     * Changes what the profile in use includes.
     *
     * <p>A part added starts as what is on now (Default's, until then); a part taken
     * away goes back to Default's. Only the profile in use, because what is on now is
     * the only state there is to give a part that is added.
     *
     * @throws IllegalStateException for Default, which always includes everything, or
     *         a profile not in use
     */
    public static void setParts(String profile, Set<ProfilePart> parts) {
        if (isDefault(profile) || !isActive(profile) || parts.isEmpty()) {
            throw new IllegalStateException("Only a profile in use, not Default, can change what it includes");
        }
        Set<ProfilePart> before = parts(profile);
        if (before.equals(parts)) {
            return;
        }
        ConfigService config = Core.config();
        config.save();

        // Additions first: written from what is on now, over anything left in the
        // folder from when the part was last included.
        Set<ProfilePart> grown = EnumSet.copyOf(before);
        grown.addAll(parts);
        if (!grown.equals(before)) {
            PARTS.put(key(profile), grown);
            config.save();
        }
        // Then removals: what is taken away is read back from Default.
        PARTS.put(key(profile), EnumSet.copyOf(parts));
        if (!parts.containsAll(before)) {
            config.load(active());
        }
        config.saveShared();
    }

    /**
     * Deletes {@code profile}, switching to Default first if it is in use. Its folder
     * is moved aside rather than erased, so {@link #restore} can bring it back.
     *
     * @return what {@link #restore} needs, or null if it could not be deleted
     */
    public static Deleted delete(String profile) {
        if (isDefault(profile) || find(profile) == null) {
            return null;
        }
        ConfigService config = Core.config();
        if (isActive(profile)) {
            load(DEFAULT);
        }
        Path folder = config.getProfileDirectory(profile);
        Path aside = config.getDirectory().resolve("deleted").resolve(profile);
        try {
            erase(aside);
            Files.createDirectories(aside.getParent());
            Files.move(folder, aside);
        } catch (IOException failure) {
            Core.log().error("Could not delete profile " + profile, failure);
            Notices.error(Source.CLIENT, "Profiles", "Could not delete " + profile);
            return null;
        }
        Set<ProfilePart> parts = PARTS.remove(key(profile));
        config.saveShared();
        return new Deleted(profile, parts, aside);
    }

    /** Puts back a profile {@link #delete} moved aside: an undo. @return whether it could */
    public static boolean restore(Deleted deleted) {
        if (deleted == null || find(deleted.name) != null || !Files.isDirectory(deleted.aside)) {
            return false;
        }
        try {
            Files.move(deleted.aside, Core.config().getProfileDirectory(deleted.name));
        } catch (IOException failure) {
            Core.log().error("Could not restore profile " + deleted.name, failure);
            return false;
        }
        if (deleted.parts != null) {
            PARTS.put(key(deleted.name), deleted.parts);
        }
        Core.config().saveShared();
        return true;
    }

    /** A deleted profile, held for as long as it can be undone. */
    public static final class Deleted {

        private final String name;
        private final Set<ProfilePart> parts;
        private final Path aside;

        private Deleted(String name, Set<ProfilePart> parts, Path aside) {
            this.name = name;
            this.parts = parts;
            this.aside = aside;
        }

        public String name() {
            return name;
        }
    }

    // ------------------------------------------------------------- helpers

    /** Folder names are matched without case: Windows would put "PvP" and "pvp" in one. */
    private static String key(String profile) {
        return profile.trim().toLowerCase(Locale.ROOT);
    }

    private static void erase(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> paths = new ArrayList<>();
            walk.forEach(paths::add);
            paths.sort(Comparator.reverseOrder());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** {@code profile-parts.json}: each profile listed by name, with the parts it includes. */
    private static final class Section implements ConfigSection {

        @Override
        public String getId() {
            return ID;
        }

        @Override
        public JsonObject save() {
            JsonObject json = new JsonObject();
            for (Map.Entry<String, Set<ProfilePart>> entry : PARTS.entrySet()) {
                JsonArray parts = new JsonArray();
                for (ProfilePart part : entry.getValue()) {
                    parts.add(part.key());
                }
                json.add(entry.getKey(), parts);
            }
            return json;
        }

        @Override
        public void load(JsonObject json) {
            PARTS.clear();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (!entry.getValue().isJsonArray()) {
                    continue;
                }
                Set<ProfilePart> parts = EnumSet.noneOf(ProfilePart.class);
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    ProfilePart part = element.isJsonPrimitive() ? ProfilePart.byKey(element.getAsString()) : null;
                    if (part != null) {
                        parts.add(part);
                    }
                }
                // A profile listed with nothing it knows is read as everything, never as empty.
                if (!parts.isEmpty()) {
                    PARTS.put(entry.getKey(), parts);
                }
            }
        }
    }

    /** @return the parts as their keys, for a list of parts */
    public static List<String> keys(Set<ProfilePart> parts) {
        List<String> keys = new ArrayList<>();
        for (ProfilePart part : parts) {
            keys.add(part.key());
        }
        return Collections.unmodifiableList(keys);
    }

    /** @return the parts named by {@code keys}, ignoring any no part has */
    public static Set<ProfilePart> fromKeys(Set<String> keys) {
        Set<ProfilePart> parts = EnumSet.noneOf(ProfilePart.class);
        for (String key : keys) {
            ProfilePart part = ProfilePart.byKey(key);
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }
}
