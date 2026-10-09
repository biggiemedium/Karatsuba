package dev.px.karatsuba.client.gui.shell.section.friends;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.social.Friend;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.shell.Section;
import dev.px.karatsuba.client.gui.shell.Shell;
import dev.px.karatsuba.client.gui.shell.frame.ModuleColumn;
import dev.px.karatsuba.client.gui.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.shell.frame.SettingsColumn;
import dev.px.karatsuba.client.gui.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.shell.setting.FieldRow;
import dev.px.karatsuba.client.gui.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.shell.style.Palette;
import dev.px.karatsuba.client.gui.shell.widget.Caption;
import dev.px.karatsuba.client.gui.shell.widget.FieldMessage;
import dev.px.karatsuba.client.gui.shell.widget.ListHeading;
import dev.px.karatsuba.client.gui.shell.widget.PillButton;
import dev.px.karatsuba.client.gui.shell.widget.SearchField;
import dev.px.karatsuba.client.gui.shell.widget.SectionHeader;
import dev.px.karatsuba.client.gui.shell.widget.SectionTitle;
import dev.px.karatsuba.client.gui.shell.widget.TextButton;
import dev.px.karatsuba.client.social.FriendRecords;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Friends: the players the client is on your side of. Combat modules leave them
 * alone, and ESP and nametags mark them.
 *
 * <p>The list on the left, the chosen friend on the right. One field above the
 * list both searches and adds: typing filters the list, and a name that is not a
 * friend yet is offered as "Add" at the top, taken with Enter. Friends online on
 * this server come first, then the rest; under them, the players on this server
 * who are not friends, each a press away from being one.
 *
 * <p>The inspector edits as you type: an alias, a private note. It also shows
 * where the friend is, when they were last seen and when they were added.
 * Removing someone is undone from the bar that appears under the list, or with
 * Ctrl+Z, so it never asks first.
 *
 * <p>Everything stays on this computer: names are checked locally, never looked
 * up, and notes are never sent anywhere.
 */
public final class FriendsSection implements Section {

    public static final String ID = "friends";

    private static String lastSelected;

    private final Shell shell;
    private final Row content;
    private final ModuleColumn listColumn;
    private final SettingsColumn inspector;
    private final SearchField field;
    private final SmoothScroll list;
    private final UndoBar undo;
    private final Presence presence = new Presence();
    private final Reveal paneReveal = new Reveal();
    private final List<FieldRow> fields = new ArrayList<>();

    private String message = "";
    private FieldMessage.Kind messageKind = FieldMessage.Kind.INFO;
    private String justAdded;
    private String signature;

    private Friend selected;
    private Friend built;
    private boolean firstBuild = true;

    /** The last removal, for Undo. */
    private String removedName;
    private String removedAlias;
    private FriendRecords.Record removedRecord;

    public FriendsSection(Shell shell) {
        this.shell = shell;
        this.content = new Row();

        // ---- the list, under the section's title and the search-or-add field
        this.listColumn = content.add(new ModuleColumn(new Reveal()));
        listColumn.gap(Palette.GAP_ROW);
        listColumn.add(new SectionTitle("Friends", this::caption));
        listColumn.add(new Spacer(Palette.INSET - Palette.GAP_ROW));
        this.field = listColumn.add(new SearchField("Search or add a player"));
        field.maxLength(32);
        field.onSubmit(this::submit);
        field.onChange(text -> message = "");
        listColumn.add(new FieldMessage(() -> message, () -> messageKind));
        listColumn.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        this.list = listColumn.add(new SmoothScroll());
        list.gap(Palette.GAP_ROW);
        list.grow(1f);
        this.undo = listColumn.add(new UndoBar(() -> "Removed " + removedName, this::undoRemove));

        // ---- the chosen friend
        this.inspector = content.add(new SettingsColumn(paneReveal));
        inspector.gap(Palette.GAP_ROW * 2f);
        inspector.grow(1f);
        shell.veil(paneReveal, () -> inspector);

        if (lastSelected != null) {
            selected = Core.social().find(lastSelected).orElse(null);
        }
        if (selected == null && !Core.social().getFriends().isEmpty()) {
            List<Friend> all = new ArrayList<>(Core.social().getFriends().all());
            all.sort(Comparator.comparing(friend -> friend.getDisplayName().toLowerCase(Locale.ROOT)));
            selected = all.get(0);
        }
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Friends";
    }

    @Override
    public Glyph glyph() {
        return Glyph.PEOPLE;
    }

    @Override
    public Widget content() {
        return content;
    }

    @Override
    public void tick() {
        presence.refresh();
        float squeeze = Math.max(0f, Math.min(1f, (Palette.FRAME_WIDTH - shell.windowWidth()) / 280f));
        listColumn.width(Palette.LIST_WIDTH + 30f - 40f * squeeze);
        // A friend removed from elsewhere (the .friend command) leaves the inspector.
        if (selected != null && !Core.social().isFriend(selected.getName())) {
            selected = null;
        }
        String now = signature();
        if (!now.equals(signature)) {
            signature = now;
            rebuildList();
        }
        if (built != selected || firstBuild) {
            rebuildInspector();
        }
        for (FieldRow row : fields) {
            row.refresh();
        }
    }

    @Override
    public boolean find() {
        field.grab();
        field.selectAll();
        return true;
    }

    @Override
    public boolean typed(char character) {
        if (Character.isISOControl(character) || field.isFocused()) {
            return false;
        }
        field.grab();
        field.insert(String.valueOf(character));
        return true;
    }

    @Override
    public boolean back() {
        // Escape clears the field before it leaves.
        if (!field.getText().isEmpty()) {
            field.setText("");
            message = "";
            return true;
        }
        return false;
    }

    @Override
    public boolean key(Key key, Set<Modifier> modifiers) {
        if (key == Key.Z && modifiers.contains(Modifier.CTRL) && removedName != null) {
            undoRemove();
            return true;
        }
        return false;
    }

    // --------------------------------------------------------------- actions

    /** Enter in the field: add the name, or find the friend it already is, or say what is wrong with it. */
    private void submit(String text) {
        String name = text.trim();
        if (name.isEmpty()) {
            return;
        }
        Friend existing = Core.social().find(name).orElse(null);
        if (existing != null) {
            select(existing);
            field.setText("");
            message = existing.getName() + " is already a friend";
            messageKind = FieldMessage.Kind.INFO;
            return;
        }
        String problem = NameRules.problem(name);
        if (problem != null) {
            // The field keeps what was typed, so it can be corrected rather than retyped.
            message = problem;
            messageKind = FieldMessage.Kind.ERROR;
            return;
        }
        add(name);
    }

    private void add(String name) {
        Friend friend = Core.social().add(name);
        FriendRecords.added(friend.getName());
        justAdded = friend.getName();
        select(friend);
        field.setText("");
        message = "";
        AutoSave.markDirty();
    }

    private void remove(Friend friend) {
        removedName = friend.getName();
        removedAlias = friend.getAlias();
        Core.social().remove(friend.getName());
        removedRecord = FriendRecords.forget(friend.getName());
        if (selected == friend) {
            selected = null;
        }
        undo.show();
        AutoSave.markDirty();
    }

    private void undoRemove() {
        if (removedName == null) {
            return;
        }
        Friend friend = Core.social().add(removedName);
        friend.setAlias(removedAlias);
        FriendRecords.restore(removedName, removedRecord);
        removedName = null;
        removedRecord = null;
        undo.hide();
        select(friend);
        AutoSave.markDirty();
    }

    private void select(Friend friend) {
        if (friend != selected) {
            paneReveal.start();
        }
        selected = friend;
        lastSelected = friend == null ? null : friend.getName();
    }

    // ------------------------------------------------------------------ list

    private String caption() {
        int total = Core.social().getFriends().size();
        int online = 0;
        for (Friend friend : Core.social().getFriends()) {
            if (presence.isOnline(friend.getName())) {
                online++;
            }
        }
        return total == 0 ? "" : online > 0 ? total + "  ·  " + online + " online" : String.valueOf(total);
    }

    /** @return everything the list shows, so it is rebuilt only when something in it changes */
    private String signature() {
        StringBuilder out = new StringBuilder(query()).append('|');
        for (Friend friend : Core.social().getFriends()) {
            out.append(friend.getName()).append(':').append(friend.getAlias()).append(':')
                    .append(presence.isOnline(friend.getName())).append(',');
        }
        out.append('|');
        for (String name : presence.onlineNames()) {
            out.append(name).append(',');
        }
        return out.toString();
    }

    private String query() {
        return field.getText().trim().toLowerCase(Locale.ROOT);
    }

    private void rebuildList() {
        list.clear();
        String raw = field.getText().trim();
        String query = query();

        List<Friend> friends = new ArrayList<>(Core.social().getFriends().all());
        friends.sort(Comparator.comparing(friend -> friend.getDisplayName().toLowerCase(Locale.ROOT)));
        List<Friend> online = new ArrayList<>();
        List<Friend> offline = new ArrayList<>();
        for (Friend friend : friends) {
            if (!query.isEmpty() && !matches(friend, query)) {
                continue;
            }
            (presence.isOnline(friend.getName()) ? online : offline).add(friend);
        }

        boolean offerAdd = !raw.isEmpty() && NameRules.valid(raw) && !Core.social().isFriend(raw);
        if (offerAdd) {
            list.add(new AddRow(raw, this::add));
            list.add(new Spacer(Palette.GAP_ROW));
        }

        if (friends.isEmpty() && query.isEmpty()) {
            list.add(new Caption("No friends yet. Combat modules leave friends alone, and ESP and nametags "
                    + "mark them. Type a name above, or add someone on this server below."));
        }
        group(list, "Online", online);
        group(list, "Offline", offline);

        List<String> strangers = new ArrayList<>();
        for (String name : presence.onlineNames()) {
            if (!Core.social().isFriend(name) && (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query))) {
                strangers.add(name);
            }
        }
        strangers.sort(String.CASE_INSENSITIVE_ORDER);
        if (!strangers.isEmpty()) {
            list.add(new Spacer(Palette.GAP_SECTION));
            list.add(new ListHeading("On this server", () -> String.valueOf(strangers.size())));
            for (String name : strangers) {
                list.add(new SuggestionRow(name, this::add));
            }
        }

        if (!query.isEmpty() && online.isEmpty() && offline.isEmpty() && strangers.isEmpty() && !offerAdd) {
            list.add(new Caption("No players match \"" + raw + "\""));
            list.add(new TextButton("Clear search", () -> false, () -> field.setText("")));
        }
        justAdded = null;
    }

    private void group(SmoothScroll into, String title, List<Friend> friends) {
        if (friends.isEmpty()) {
            return;
        }
        into.add(new ListHeading(title, () -> String.valueOf(friends.size())));
        for (Friend friend : friends) {
            into.add(new FriendRow(friend, presence, () -> friend == selected, () -> select(friend),
                    this::remove, friend.getName().equalsIgnoreCase(justAdded)));
        }
        into.add(new Spacer(Palette.GAP_ROW * 2f));
    }

    private static boolean matches(Friend friend, String query) {
        return friend.getName().toLowerCase(Locale.ROOT).contains(query)
                || friend.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                || FriendRecords.of(friend.getName()).note().toLowerCase(Locale.ROOT).contains(query);
    }

    // ------------------------------------------------------------- inspector

    private void rebuildInspector() {
        firstBuild = false;
        built = selected;
        inspector.clear();
        fields.clear();
        if (selected == null) {
            if (!Core.social().getFriends().isEmpty()) {
                inspector.add(new Caption("Select a friend to see their details"));
            }
            return;
        }
        Friend friend = selected;
        String name = friend.getName();
        FriendRecords.Record record = FriendRecords.of(name);

        inspector.add(new FriendHeader(friend, presence));
        inspector.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        SmoothScroll rows = inspector.add(new SmoothScroll());
        rows.gap(Palette.GAP_ROW);
        rows.grow(1f);

        rows.add(new SectionHeader("Profile", null));
        FieldRow alias = rows.add(new FieldRow("Alias", name, 16,
                () -> friend.getAlias() == null ? "" : friend.getAlias(), text -> {
            friend.setAlias(text.trim().isEmpty() ? null : text.trim());
            AutoSave.markDirty();
        }));
        alias.tooltip("What this list calls them. Their name in game is unchanged.");
        FieldRow note = rows.add(new FieldRow("Note", "Only you see this", 64, record::note, record::note));
        note.tooltip("Private, and kept on this computer");
        fields.add(alias);
        fields.add(note);
        rows.add(new InfoRow("Username", () -> name, "Copy", () -> Core.platform().setClipboard(name)));

        rows.add(new Spacer(Palette.GAP_SECTION));
        rows.add(new SectionHeader("Activity", null));
        rows.add(new InfoRow("Now", () -> presence.full(name)));
        rows.add(new InfoRow("Last seen", () -> presence.isOnline(name) ? "Now" : Presence.lastSeen(name)));
        rows.add(new InfoRow("Added", () -> Presence.added(name)));

        rows.add(new Spacer(Palette.GAP_SECTION));
        Row foot = rows.add(new Row());
        foot.add(new PillButton(Glyph.REMOVE, "Remove friend", false, PillButton.Style.PLAIN, null, null,
                () -> remove(friend)))
                .tooltip("Combat modules will treat them like anyone else. Undo from the bar under the list.");
    }
}
