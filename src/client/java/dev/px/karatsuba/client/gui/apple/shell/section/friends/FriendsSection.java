package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.social.Friend;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.frame.PageStack;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.FieldRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.gui.apple.shell.widget.EmptyState;
import dev.px.karatsuba.client.gui.apple.shell.widget.FieldMessage;
import dev.px.karatsuba.client.gui.apple.shell.widget.PageIntro;
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
 * <p>Pages, as Modules and Settings are ({@code gui/apple/DESIGN.md} §2.6b, PLAN.md
 * step 12): the list is a card of friends online and one of those offline, each
 * row opening that friend's page (Profile and Activity cards, and Remove Friend
 * under them). "Add Player…" pushes a page with a Username field and the players
 * on this server who are not friends yet. The sidebar's search is the one search:
 * it finds friends by name, alias or note, lists matching players here, and offers
 * a name that is not a friend yet.
 *
 * <p>Removing someone asks nothing: the list shows "Removed Steve · Undo" at its
 * top for a few seconds, and Ctrl+Z does the same.
 *
 * <p>Everything stays on this computer: names are checked locally, never looked
 * up, and notes are never sent anywhere.
 */
public final class FriendsSection implements Section {

    public static final String ID = "friends";

    /** The Add Player page's {@link #place()}; a friend's page is {@link #FRIEND} and their name. */
    private static final String ADD = "+add";
    private static final String FRIEND = "friend:";

    private final Shell shell;
    private final Presence presence = new Presence();
    private final PageStack stack = new PageStack();
    private final CardPage list = new CardPage();
    private final CardPage detail = new CardPage();
    private final CardPage adding = new CardPage();
    private final UndoBar undo;
    private final FieldRow username;
    private final List<FieldRow> fields = new ArrayList<>();

    /** The friend whose page is open, or null. */
    private Friend open;
    /** Whether the Add Player page is open (and {@link #open} is null). */
    private boolean addOpen;
    private Friend built;
    private String listSignature;
    private String addSignature;
    private boolean showAllSuggestions;

    /** What the Username field holds, and what is wrong with it after Enter. */
    private String draft = "";
    private String message = "";
    private FieldMessage.Kind messageKind = FieldMessage.Kind.INFO;

    /** The last removal, for Undo. */
    private String removedName;
    private String removedAlias;
    private FriendRecords.Record removedRecord;

    public FriendsSection(Shell shell) {
        this.shell = shell;
        stack.page(list);
        stack.page(detail);
        stack.page(adding);
        this.undo = new UndoBar(() -> "Removed " + removedName, this::undoRemove);
        this.username = new FieldRow("Username", "Their name in game", 16, () -> draft, text -> {
            draft = text;
            message = "";
        }).onSubmit(this::submitName);
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
        return stack;
    }

    @Override
    public void tick() {
        presence.refresh();
        // A friend removed from elsewhere (the .friend command) closes their page.
        if (open != null && !Core.social().isFriend(open.getName())) {
            open = null;
            if (!searching()) {
                stack.jump(list);
            }
        }
        // While the search holds anything, the list shows what it finds; after, the page it left.
        if (searching()) {
            if (stack.showing() != list) {
                stack.jump(list);
            }
        } else if (stack.showing() == list && (open != null || addOpen)) {
            stack.jump(open != null ? detail : adding);
        }

        String now = listSignature();
        if (!now.equals(listSignature)) {
            listSignature = now;
            rebuildList();
        }
        if (built != open) {
            rebuildDetail();
        }
        String suggestions = addSignature();
        if (!suggestions.equals(addSignature)) {
            addSignature = suggestions;
            rebuildAdd();
        }
        for (FieldRow row : fields) {
            row.refresh();
        }
        username.refresh();
    }

    @Override
    public String heading() {
        if (searching()) {
            return "Search";
        }
        if (open != null) {
            return open.getDisplayName();
        }
        return addOpen ? "Add Player" : title();
    }

    /** "" on the list, {@code +add} on Add Player, {@code friend:Steve} on Steve's page. */
    @Override
    public String place() {
        return open != null ? FRIEND + open.getName() : addOpen ? ADD : "";
    }

    @Override
    public boolean slidesPlaces() {
        return true;
    }

    @Override
    public void restore(String place, int direction) {
        open = place.startsWith(FRIEND) ? Core.social().find(place.substring(FRIEND.length())).orElse(null) : null;
        addOpen = open == null && place.equals(ADD);
        if (open != null) {
            rebuildDetail();
        }
        Widget page = open != null ? detail : addOpen ? adding : list;
        if (direction > 0) {
            stack.push(page);
        } else if (direction < 0) {
            stack.pop(page);
        } else {
            stack.jump(page);
        }
    }

    /** Escape on a friend's page or Add Player goes back to the list, as ‹ does. */
    @Override
    public boolean back() {
        if ((open == null && !addOpen) || searching()) {
            return false;
        }
        showList();
        return true;
    }

    @Override
    public boolean ownsSearch() {
        return true;
    }

    /** Enter in the search: the friend it names, else that name added, else the first friend found. */
    @Override
    public boolean submitSearch() {
        String raw = shell.search().trim();
        Friend exact = Core.social().find(raw).orElse(null);
        if (exact != null) {
            openFriend(exact);
            return true;
        }
        if (NameRules.valid(raw)) {
            add(raw);
            return true;
        }
        for (Friend friend : sorted()) {
            if (matches(friend, query())) {
                openFriend(friend);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean find() {
        return shell.focusSearch();
    }

    /** Typing with nothing focused: into the Username field on Add Player, the sidebar's search elsewhere. */
    @Override
    public boolean typed(char character) {
        if (Character.isISOControl(character)) {
            return false;
        }
        if (addOpen && !searching()) {
            username.insert(String.valueOf(character));
            return true;
        }
        return shell.typeIntoSearch(character);
    }

    @Override
    public boolean key(Key key, Set<Modifier> modifiers) {
        if (key == Key.Z && modifiers.contains(Modifier.CTRL) && removedName != null) {
            undoRemove();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ navigation

    /** Pushes {@code friend}'s page in from the right, leaving any search. */
    private void openFriend(Friend friend) {
        shell.search("");
        open = friend;
        addOpen = false;
        rebuildDetail();
        stack.push(detail);
    }

    /** Pushes Add Player in from the right, the caret in its field. */
    private void openAdd() {
        shell.search("");
        open = null;
        addOpen = true;
        message = "";
        stack.push(adding);
        username.grab();
    }

    /** Pops back to the list. */
    private void showList() {
        open = null;
        addOpen = false;
        stack.pop(list);
    }

    // --------------------------------------------------------------- actions

    /** Enter in Username, or Add Friend: adds the name, opens the friend it already is, or says what is wrong. */
    private void submitName(String text) {
        String name = text.trim();
        if (name.isEmpty()) {
            return;
        }
        Friend existing = Core.social().find(name).orElse(null);
        if (existing != null) {
            draft = "";
            openFriend(existing);
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

    /** Makes {@code name} a friend and opens their page, where an alias or note can go straight on. */
    private void add(String name) {
        Friend friend = Core.social().add(name);
        FriendRecords.added(friend.getName());
        draft = "";
        message = "";
        AutoSave.markDirty();
        openFriend(friend);
    }

    private void remove(Friend friend) {
        removedName = friend.getName();
        removedAlias = friend.getAlias();
        Core.social().remove(friend.getName());
        removedRecord = FriendRecords.forget(friend.getName());
        undo.show();
        AutoSave.markDirty();
        showList();
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
        AutoSave.markDirty();
    }

    // ------------------------------------------------------------- the list

    private String query() {
        return shell.search().trim().toLowerCase(Locale.ROOT);
    }

    private boolean searching() {
        return !query().isEmpty();
    }

    /** @return everything the list shows, so it is rebuilt only when something in it changes */
    private String listSignature() {
        StringBuilder out = new StringBuilder(query()).append('|');
        for (Friend friend : Core.social().getFriends()) {
            out.append(friend.getName()).append(':').append(friend.getAlias()).append(':')
                    .append(presence.isOnline(friend.getName())).append(',');
        }
        if (searching()) {
            out.append('|');
            for (String name : presence.onlineNames()) {
                out.append(name).append(',');
            }
        }
        return out.toString();
    }

    private void rebuildList() {
        list.clearCards();
        // The undo strip, at the top where the list arrives after a removal.
        Column top = list.group(null);
        top.add(undo);
        top.visibleWhen(undo::showing);

        String raw = shell.search().trim();
        String query = query();
        boolean searching = !query.isEmpty();
        List<Friend> friends = sorted();

        if (searching && NameRules.valid(raw) && !Core.social().isFriend(raw)) {
            list.card(null).add(new SuggestionRow(raw, "Not a friend yet", this::add));
        }
        if (friends.isEmpty() && !searching) {
            list.group(null).add(new EmptyState(Glyph.PEOPLE, "No friends yet",
                    "Combat modules leave friends alone, and ESP and nametags mark them.",
                    "Add Player…", this::openAdd));
            return;
        }

        List<Friend> online = new ArrayList<>();
        List<Friend> offline = new ArrayList<>();
        for (Friend friend : friends) {
            if (!searching || matches(friend, query)) {
                (presence.isOnline(friend.getName()) ? online : offline).add(friend);
            }
        }
        Column last = friendsCard("Online", online, null);
        last = friendsCard("Offline", offline, last);

        if (searching) {
            List<String> players = strangers(query);
            if (!players.isEmpty()) {
                Card here = list.card("On this server");
                for (String name : players) {
                    here.add(new SuggestionRow(name, "", this::add));
                }
            }
            if (online.isEmpty() && offline.isEmpty() && players.isEmpty() && !NameRules.valid(raw)) {
                list.group(null).add(new EmptyState(Glyph.SEARCH, "No players match",
                        "No friend or player here is called that, and no note mentions it.",
                        "Clear Search", () -> shell.search("")));
            }
        } else if (last != null) {
            list.actions(last, new PushButton("Add Player…", this::openAdd));
        }
    }

    /** Adds a card of {@code friends} under {@code header}. @return its group, or {@code previous} when there are none */
    private Column friendsCard(String header, List<Friend> friends, Column previous) {
        if (friends.isEmpty()) {
            return previous;
        }
        Column group = list.group(header);
        Card card = group.add(new Card());
        for (Friend friend : friends) {
            card.add(new FriendRow(friend, presence, () -> openFriend(friend)));
        }
        return group;
    }

    /** @return the players on this server who are not friends, matching {@code query} (all for empty), by name */
    private List<String> strangers(String query) {
        List<String> players = new ArrayList<>();
        for (String name : presence.onlineNames()) {
            if (!Core.social().isFriend(name) && (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query))) {
                players.add(name);
            }
        }
        players.sort(String.CASE_INSENSITIVE_ORDER);
        return players;
    }

    private static List<Friend> sorted() {
        List<Friend> friends = new ArrayList<>(Core.social().getFriends().all());
        friends.sort(Comparator.comparing(friend -> friend.getDisplayName().toLowerCase(Locale.ROOT)));
        return friends;
    }

    private static boolean matches(Friend friend, String query) {
        return friend.getName().toLowerCase(Locale.ROOT).contains(query)
                || friend.getDisplayName().toLowerCase(Locale.ROOT).contains(query)
                || FriendRecords.of(friend.getName()).note().toLowerCase(Locale.ROOT).contains(query);
    }

    // ------------------------------------------------------- a friend's page

    /** Fills the open friend's page: only when another opens, never per frame. */
    private void rebuildDetail() {
        built = open;
        if (open == null) {
            // Kept as it was while the page slides away; rebuilt when another opens.
            return;
        }
        detail.clearCards();
        fields.clear();
        Friend friend = open;
        String name = friend.getName();
        FriendRecords.Record record = FriendRecords.of(name);

        detail.card(null).add(new FriendHeader(friend, presence));

        Card profile = detail.card("Profile");
        FieldRow alias = profile.add(new FieldRow("Alias", name, 16,
                () -> friend.getAlias() == null ? "" : friend.getAlias(), text -> {
            friend.setAlias(text.trim().isEmpty() ? null : text.trim());
            AutoSave.markDirty();
        }));
        alias.tooltip("What this list calls them. Their name in game is unchanged.");
        FieldRow note = profile.add(new FieldRow("Note", "Only you see this", 64, record::note, record::note));
        note.tooltip("Private, and kept on this computer");
        fields.add(alias);
        fields.add(note);
        profile.add(new InfoRow("Username", () -> name, "Copy", () -> Core.platform().setClipboard(name)));

        Column activity = detail.group("Activity");
        Card facts = activity.add(new Card());
        facts.add(new InfoRow("Now", () -> presence.full(name)));
        facts.add(new InfoRow("Last seen", () -> presence.isOnline(name) ? "Now" : Presence.lastSeen(name)));
        facts.add(new InfoRow("Added", () -> Presence.added(name)));

        PushButton remove = new PushButton("Remove Friend", () -> remove(friend));
        remove.tooltip("Combat modules will treat them like anyone else. Undo from the list, or Ctrl+Z.");
        detail.actions(activity, remove);
    }

    // ------------------------------------------------------------ Add Player

    /** @return what Add Player's suggestions show, so it is rebuilt only when that changes */
    private String addSignature() {
        StringBuilder out = new StringBuilder().append(showAllSuggestions).append('|');
        for (String name : strangers("")) {
            out.append(name).append(',');
        }
        return out.toString();
    }

    /** Fills Add Player: its sentence, the Username card, Add Friend, and the players on this server. */
    private void rebuildAdd() {
        adding.clearCards();
        adding.group(null).add(new PageIntro(
                "Names are checked on this computer and never looked up, so adding works offline"));
        Column field = adding.group(null);
        field.add(new Card()).add(username);
        field.add(new FieldMessage(() -> message, () -> messageKind));
        PushButton add = new PushButton("Add Friend", () -> submitName(username.text()));
        add.enabledWhen(() -> !username.text().isBlank());
        adding.actions(field, add);

        List<String> players = strangers("");
        if (players.isEmpty()) {
            return;
        }
        int total = players.size();
        Column here = total > Palette.SUGGESTIONS_SHOWN
                ? adding.group("On this server",
                        () -> showAllSuggestions ? "Show Less" : "Show All (" + total + ")",
                        () -> showAllSuggestions = !showAllSuggestions)
                : adding.group("On this server");
        Card card = here.add(new Card());
        int shown = showAllSuggestions ? total : Math.min(total, Palette.SUGGESTIONS_SHOWN);
        for (String name : players.subList(0, shown)) {
            card.add(new SuggestionRow(name, "", this::add));
        }
    }
}
