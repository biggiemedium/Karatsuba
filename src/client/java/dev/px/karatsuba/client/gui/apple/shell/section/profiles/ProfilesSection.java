package dev.px.karatsuba.client.gui.apple.shell.section.profiles;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.config.ProfilePart;
import dev.px.karatsuba.client.config.Profiles;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.frame.PageStack;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.FieldRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.LinkRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ListRow;
import dev.px.karatsuba.client.gui.apple.shell.widget.FieldMessage;
import dev.px.karatsuba.client.gui.apple.shell.widget.PageIntro;
import dev.px.karatsuba.client.gui.apple.shell.widget.UndoBar;
import dev.px.karatsuba.client.setting.ListSetting;

import java.util.Set;

/**
 * Profiles: sets of settings to switch between, each holding everything or only
 * some parts of it.
 *
 * <p>Pages, as Accounts is ({@code gui/apple/DESIGN.md} §2.6d): the list is a card
 * of profiles, each row saying what it includes and which is in use, and opening
 * that profile's page: what it includes, as a list of checkboxes, with Delete
 * Profile and Switch To under it. "New Profile…" pushes a page with a Name field and
 * the same list, every part ticked to start with.
 *
 * <p>Everything goes through {@link Profiles}, which sequences the saves and loads;
 * a part a profile leaves out is Default's, and the pages say so. Deleting asks
 * nothing: the list shows "Deleted pvp · Undo", and Ctrl+Z does the same.
 */
public final class ProfilesSection implements Section {

    public static final String ID = "profiles";

    /** The New Profile page's {@link #place()}; a profile's page is {@link #PROFILE} and its name. */
    private static final String NEW = "+new";
    private static final String PROFILE = "profile:";

    private final Shell shell;
    private final PageStack stack = new PageStack();
    private final CardPage list = new CardPage();
    private final CardPage detail = new CardPage();
    private final CardPage creating = new CardPage();
    private final UndoBar undo;

    /** The New Profile page's controls, built once. */
    private final FieldRow name;
    private final ListSetting newParts = includes();

    /** The profile whose page is open, or null. */
    private String open;
    /** Whether the New Profile page is open (and {@link #open} is null). */
    private boolean newOpen;
    private String built;
    private String listSignature;

    private String draft = "";
    private String message = "";

    /** The last profile deleted, for Undo. */
    private Profiles.Deleted deleted;

    public ProfilesSection(Shell shell) {
        this.shell = shell;
        stack.page(list);
        stack.page(detail);
        stack.page(creating);
        this.undo = new UndoBar(() -> deleted == null ? "" : "Deleted " + deleted.name(), this::undoDelete);
        this.name = new FieldRow("Name", "PvP, Building…", 32, () -> draft, text -> {
            draft = text;
            message = "";
        }).onSubmit(text -> submit());
        buildNew();
    }

    /** @return a list of every part, ticked, that can never be left empty */
    private static ListSetting includes() {
        return new ListSetting("Includes", ProfilePart::keys, ProfilePart.keys().toArray(new String[0]))
                .labelledBy(ProfilePart::labelOf)
                .requireOne();
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Profiles";
    }

    @Override
    public Glyph glyph() {
        return Glyph.LAYERS;
    }

    @Override
    public Widget content() {
        return stack;
    }

    @Override
    public void tick() {
        // A profile deleted from elsewhere (the .config command) closes its page.
        if (open != null && Profiles.find(open) == null) {
            open = null;
            stack.jump(list);
        }
        String now = String.join(",", Profiles.all());
        if (!now.equals(listSignature)) {
            listSignature = now;
            rebuildList();
        }
        if (open != null ? !open.equals(built) : built != null) {
            rebuildDetail();
        }
        name.refresh();
    }

    @Override
    public String heading() {
        if (open != null) {
            return Profiles.label(open);
        }
        return newOpen ? "New Profile" : title();
    }

    /** "" on the list, {@code +new} on New Profile, {@code profile:pvp} on pvp's page. */
    @Override
    public String place() {
        return open != null ? PROFILE + open : newOpen ? NEW : "";
    }

    @Override
    public boolean slidesPlaces() {
        return true;
    }

    @Override
    public void restore(String place, int direction) {
        open = place.startsWith(PROFILE) ? Profiles.find(place.substring(PROFILE.length())) : null;
        newOpen = open == null && place.equals(NEW);
        if (open != null) {
            rebuildDetail();
        }
        Widget page = open != null ? detail : newOpen ? creating : list;
        if (direction > 0) {
            stack.push(page);
        } else if (direction < 0) {
            stack.pop(page);
        } else {
            stack.jump(page);
        }
    }

    /** Escape on a profile's page or New Profile goes back to the list, as ‹ does. */
    @Override
    public boolean back() {
        if (open == null && !newOpen) {
            return false;
        }
        showList();
        return true;
    }

    @Override
    public boolean find() {
        return shell.focusSearch();
    }

    /** Typing with nothing focused: into the Name field on New Profile, the sidebar's search elsewhere. */
    @Override
    public boolean typed(char character) {
        if (Character.isISOControl(character)) {
            return false;
        }
        if (newOpen) {
            name.insert(String.valueOf(character));
            return true;
        }
        return shell.typeIntoSearch(character);
    }

    @Override
    public boolean key(Key key, Set<Modifier> modifiers) {
        if (key == Key.Z && modifiers.contains(Modifier.CTRL) && deleted != null) {
            undoDelete();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ navigation

    private void openProfile(String profile) {
        open = profile;
        newOpen = false;
        rebuildDetail();
        stack.push(detail);
    }

    private void openNew() {
        open = null;
        newOpen = true;
        message = "";
        newParts.selectAll();
        stack.push(creating);
        name.grab();
    }

    private void showList() {
        open = null;
        newOpen = false;
        stack.pop(list);
    }

    // --------------------------------------------------------------- actions

    /** Enter in Name, or Create Profile: creates it and opens its page, opens the one so named, or says what is wrong. */
    private void submit() {
        String typed = draft.trim();
        if (typed.isEmpty()) {
            return;
        }
        String existing = Profiles.find(typed);
        if (existing != null) {
            draft = "";
            openProfile(existing);
            return;
        }
        String problem = Profiles.problem(typed);
        if (problem != null) {
            message = problem;
            return;
        }
        String created = Profiles.create(typed, Profiles.fromKeys(newParts.get()));
        draft = "";
        message = "";
        openProfile(created);
    }

    private void delete(String profile) {
        Profiles.Deleted gone = Profiles.delete(profile);
        if (gone == null) {
            return;
        }
        deleted = gone;
        undo.show();
        showList();
    }

    private void undoDelete() {
        if (deleted == null) {
            return;
        }
        Profiles.restore(deleted);
        deleted = null;
        undo.hide();
    }

    // ------------------------------------------------------------- the list

    private void rebuildList() {
        list.clearCards();
        Column top = list.group(null);
        top.add(undo);
        top.visibleWhen(undo::showing);

        list.group(null).add(new PageIntro("Switch between sets of settings. A profile can include only "
                + "some parts; what it leaves out comes from Default"));
        Column group = list.group(null);
        Card card = group.add(new Card());
        for (String profile : Profiles.all()) {
            card.add(new LinkRow(Profiles.label(profile), () -> summary(profile), () -> openProfile(profile)));
        }
        list.actions(group, new PushButton("New Profile…", this::openNew));
    }

    /** @return what a list row says: whether it is in use, and what it includes */
    private static String summary(String profile) {
        String parts = ProfilePart.describe(Profiles.parts(profile));
        return Profiles.isActive(profile) ? "In use  ·  " + parts : parts;
    }

    // ------------------------------------------------------- a profile's page

    /** Fills the open profile's page: only when another opens, never per frame. */
    private void rebuildDetail() {
        built = open;
        if (open == null) {
            // Kept as it was while the page slides away; rebuilt when another opens.
            return;
        }
        detail.clearCards();
        String profile = open;
        boolean isDefault = Profiles.isDefault(profile);

        detail.group(null).add(new PageIntro(isDefault
                ? "Default includes everything. Other profiles take whatever they leave out from it"
                : "What switching to this profile changes. What it leaves out comes from Default"));

        ListSetting parts = includes();
        parts.setSilently(Set.copyOf(Profiles.keys(Profiles.parts(profile))));
        parts.onChange(keys -> Profiles.setParts(profile, Profiles.fromKeys(keys)));
        Column group = detail.group(null);
        ListRow row = group.add(new Card()).add(new ListRow(parts));
        row.enabledWhen(() -> !isDefault && Profiles.isActive(profile));
        group.add(new FieldMessage(() -> note(profile), () -> FieldMessage.Kind.INFO));

        PushButton use = new PushButton(() -> Profiles.isActive(profile) ? "In Use" : "Switch to Profile",
                () -> Profiles.load(profile));
        use.enabledWhen(() -> !Profiles.isActive(profile));
        if (isDefault) {
            detail.actions(group, use);
            return;
        }
        PushButton remove = new PushButton("Delete Profile", () -> delete(profile));
        remove.tooltip("Undo from the list, or Ctrl+Z");
        detail.actions(group, remove, use);
    }

    /** @return why the list of parts cannot be changed now, or nothing when it can */
    private static String note(String profile) {
        if (Profiles.isDefault(profile)) {
            return "";
        }
        return Profiles.isActive(profile) ? "" : "Switch to this profile to change what it includes";
    }

    // ----------------------------------------------------------- New Profile

    /** Fills New Profile: its sentence, the Name card, what it includes, and Create Profile. */
    private void buildNew() {
        creating.group(null).add(new PageIntro("Saves what is on now as a new profile and switches to it. "
                + "What it leaves out comes from Default"));
        Column form = creating.group(null);
        form.add(new Card()).add(name);
        form.add(new FieldMessage(() -> message, () -> FieldMessage.Kind.ERROR));

        Column parts = creating.group(null);
        parts.add(new Card()).add(new ListRow(newParts));
        PushButton create = new PushButton("Create Profile", this::submit);
        create.enabledWhen(() -> !name.text().isBlank());
        creating.actions(parts, create);
    }
}
