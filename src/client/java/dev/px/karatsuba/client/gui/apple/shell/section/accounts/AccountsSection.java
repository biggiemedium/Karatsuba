package dev.px.karatsuba.client.gui.apple.shell.section.accounts;

import dev.px.core.account.Account;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.gui.Popup;
import dev.px.gui.Screen;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.account.Accounts;
import dev.px.karatsuba.client.account.SignIn;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.frame.PageStack;
import dev.px.karatsuba.client.gui.apple.shell.popup.Unroll;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.ChoiceRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.FieldRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.apple.shell.widget.EmptyState;
import dev.px.karatsuba.client.gui.apple.shell.widget.FieldMessage;
import dev.px.karatsuba.client.gui.apple.shell.widget.PageIntro;
import dev.px.karatsuba.client.gui.apple.shell.widget.UndoBar;
import dev.px.karatsuba.client.social.NameRules;

import java.util.List;
import java.util.Set;

/**
 * Accounts: who the game plays as, and the saved accounts to switch between.
 *
 * <p>Pages, as Friends is ({@code gui/apple/DESIGN.md} §2.6c): the list leads with
 * who the game is playing as now, then a card of saved accounts, each row opening
 * that account's page (Account and Sign-in cards, Remove Account and Sign In under
 * them). "Add Account…" pushes a page with a Microsoft / Offline choice and the one
 * field that kind needs.
 *
 * <p>Everything about an account goes through {@link Accounts}, never Core or the
 * adapter directly: that class knows which thread a sign-in finishes on, when the
 * config is written and what the player is told. A sign-in returns at once; the
 * outcome is a notification, and the words on these pages follow it.
 *
 * <p>Removing an account or forgetting its sign-in asks nothing: an undo strip
 * shows at the top of the page it leaves you on, and Ctrl+Z does the same.
 */
public final class AccountsSection implements Section {

    public static final String ID = "accounts";

    /** The Add Account page's {@link #place()}; an account's page is {@link #ACCOUNT} and its name. */
    private static final String ADD = "+add";
    private static final String ACCOUNT = "account:";

    /** What kind of account the Add Account page adds. */
    private enum Kind {
        MICROSOFT("Microsoft"),
        OFFLINE("Offline");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final Shell shell;
    private final PageStack stack = new PageStack();
    private final CardPage list = new CardPage();
    private final CardPage detail = new CardPage();
    private final CardPage adding = new CardPage();

    /** Undo strips: the list's after a removal, an account's page's after a forget. */
    private final UndoBar listUndo;
    private final UndoBar pageUndo;

    /** The Add Account page's controls, built once: nothing on that page changes but its values. */
    private final EnumSetting<Kind> kind = new EnumSetting<>("Type", Kind.MICROSOFT).labelledBy(k -> k.label);
    private final FieldRow email;
    private final FieldRow username;

    /** The account whose page is open, or null. */
    private Account open;
    /** Whether the Add Account page is open (and {@link #open} is null). */
    private boolean addOpen;
    private Account built;
    private String listSignature;

    /** The sign-in card while it is open, and the sign-in whose card was last closed by hand. */
    private Unroll sheet;
    private SignIn dismissed;

    /** What the fields hold, and what is wrong with the one in use after Enter. */
    private String draftEmail = "";
    private String draftName = "";
    private String message = "";

    /** The last thing taken away, for Undo: what the strip says and how to put it back. */
    private String undoMessage = "";
    private Runnable undo;

    public AccountsSection(Shell shell) {
        this.shell = shell;
        stack.page(list);
        stack.page(detail);
        stack.page(adding);
        this.listUndo = new UndoBar(() -> undoMessage, this::undo);
        this.pageUndo = new UndoBar(() -> undoMessage, this::undo);

        this.email = new FieldRow("Email", "name@outlook.com", 254, () -> draftEmail, text -> {
            draftEmail = text;
            message = "";
        }).onSubmit(text -> submit());
        email.visibleWhen(() -> kind.get() == Kind.MICROSOFT);
        email.tooltip("The Microsoft account that owns Minecraft. It is only a label until you sign in.");

        this.username = new FieldRow("Username", "Their name in game", 16, () -> draftName, text -> {
            draftName = text;
            message = "";
        }).onSubmit(text -> submit());
        username.visibleWhen(() -> kind.get() == Kind.OFFLINE);

        kind.onChange(chosen -> message = "");
        buildAdd();
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Accounts";
    }

    @Override
    public Glyph glyph() {
        return Glyph.PERSON;
    }

    @Override
    public Widget content() {
        return stack;
    }

    @Override
    public void tick() {
        // An account removed from elsewhere (the .account command) closes its page.
        if (open != null && Accounts.find(open.getName()).orElse(null) != open) {
            open = null;
            stack.jump(list);
        }
        String now = listSignature();
        if (!now.equals(listSignature)) {
            listSignature = now;
            rebuildList();
        }
        if (built != open) {
            rebuildDetail();
        }
        email.refresh();
        tickSheet();
        username.refresh();
    }

    @Override
    public String heading() {
        if (open != null) {
            return Accounts.displayName(open);
        }
        return addOpen ? "Add Account" : title();
    }

    /** "" on the list, {@code +add} on Add Account, {@code account:main} on main's page. */
    @Override
    public String place() {
        return open != null ? ACCOUNT + open.getName() : addOpen ? ADD : "";
    }

    @Override
    public boolean slidesPlaces() {
        return true;
    }

    @Override
    public void restore(String place, int direction) {
        open = place.startsWith(ACCOUNT) ? Accounts.find(place.substring(ACCOUNT.length())).orElse(null) : null;
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

    /** Escape on an account's page or Add Account goes back to the list, as ‹ does. */
    @Override
    public boolean back() {
        if (open == null && !addOpen) {
            return false;
        }
        showList();
        return true;
    }

    @Override
    public boolean find() {
        return shell.focusSearch();
    }

    /** Typing with nothing focused: into the field on Add Account, the sidebar's search elsewhere. */
    @Override
    public boolean typed(char character) {
        if (Character.isISOControl(character)) {
            return false;
        }
        if (addOpen) {
            field().insert(String.valueOf(character));
            return true;
        }
        return shell.typeIntoSearch(character);
    }

    @Override
    public boolean key(Key key, Set<Modifier> modifiers) {
        if (key == Key.Z && modifiers.contains(Modifier.CTRL) && undo != null) {
            undo();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ navigation

    /** Pushes {@code account}'s page in from the right. */
    private void openAccount(Account account) {
        open = account;
        addOpen = false;
        rebuildDetail();
        stack.push(detail);
    }

    /** Pushes Add Account in from the right, the caret in its field. */
    private void openAdd() {
        open = null;
        addOpen = true;
        message = "";
        stack.push(adding);
        field().grab();
    }

    /** Pops back to the list. */
    private void showList() {
        open = null;
        addOpen = false;
        stack.pop(list);
    }

    // --------------------------------------------------------------- actions

    /** @return the field the chosen kind needs */
    private FieldRow field() {
        return kind.get() == Kind.MICROSOFT ? email : username;
    }

    /**
     * Enter in the field, or Add Account: adds the account and opens its page, opens
     * the one already saved under that name, or says what is wrong.
     */
    private void submit() {
        boolean microsoft = kind.get() == Kind.MICROSOFT;
        String name = (microsoft ? draftEmail : draftName).trim();
        if (name.isEmpty()) {
            return;
        }
        Account existing = Accounts.find(name).orElse(null);
        if (existing != null) {
            clearDrafts();
            openAccount(existing);
            return;
        }
        String problem = microsoft ? emailProblem(name) : NameRules.problem(name);
        if (problem != null) {
            // The field keeps what was typed, so it can be corrected rather than retyped.
            message = problem;
            return;
        }
        Account added = microsoft ? Accounts.addMicrosoft(name, name) : Accounts.addOffline(name);
        clearDrafts();
        openAccount(added);
    }

    /** @return what is wrong with {@code text} as an email, or null; checked here, never looked up */
    private static String emailProblem(String text) {
        int at = text.indexOf('@');
        if (at <= 0 || at != text.lastIndexOf('@') || text.indexOf('.', at) < at + 2 || text.endsWith(".")
                || text.contains(" ")) {
            return "Enter the email of a Microsoft account, like name@outlook.com";
        }
        return null;
    }

    private void clearDrafts() {
        draftEmail = "";
        draftName = "";
        message = "";
    }

    private void remove(Account account) {
        Accounts.remove(account.getName());
        offerUndo(listUndo, "Removed " + Accounts.displayName(account), () -> {
            if (Accounts.find(account.getName()).isEmpty()) {
                Accounts.restore(account);
            }
        });
        showList();
    }

    private void forget(Account account) {
        Accounts.KeptSignIn kept = Accounts.forget(account);
        offerUndo(pageUndo, "Forgot the sign-in for " + Accounts.displayName(account), () -> Accounts.keep(account, kept));
    }

    /** Shows {@code bar} saying {@code text}, and makes {@code action} what Undo and Ctrl+Z do. */
    private void offerUndo(UndoBar bar, String text, Runnable action) {
        listUndo.hide();
        pageUndo.hide();
        undoMessage = text;
        undo = action;
        bar.show();
    }

    private void undo() {
        if (undo == null) {
            return;
        }
        Runnable action = undo;
        undo = null;
        listUndo.hide();
        pageUndo.hide();
        action.run();
    }

    // ------------------------------------------------------------- the list

    /**
     * @return what the list's structure depends on, so it is rebuilt only when that
     *         changes: which accounts, in what order. What each row says is read live.
     */
    private String listSignature() {
        StringBuilder out = new StringBuilder();
        for (Account account : Accounts.all()) {
            out.append(account.getName()).append(':').append(account.getType()).append(',');
        }
        return out.toString();
    }

    private void rebuildList() {
        list.clearCards();
        // The undo strip, at the top where the list arrives after a removal.
        Column top = list.group(null);
        top.add(listUndo);
        top.visibleWhen(listUndo::showing);

        List<Account> accounts = Accounts.all();
        if (accounts.isEmpty()) {
            list.group(null).add(new EmptyState(Glyph.PERSON, "No accounts saved",
                    "Save a Microsoft or offline account to switch to it without restarting the game.",
                    "Add Account…", this::openAdd));
            return;
        }

        // Who the game is playing as: a heading, so no dot here; the row below has the dot and "In use".
        list.card(null).add(new AccountHeader(Accounts::current, Accounts::currentUuid,
                () -> Accounts.inUse().map(account -> AccountWords.kind(account) + " account")
                        .orElse("Not a saved account"),
                () -> false));

        Column saved = list.group("Saved");
        Card card = saved.add(new Card());
        for (Account account : accounts) {
            card.add(new AccountRow(account, () -> openAccount(account)));
        }
        list.actions(saved, new PushButton("Add Account…", this::openAdd));
    }

    // ----------------------------------------------------- an account's page

    /** Fills the open account's page: only when another opens, never per frame. */
    private void rebuildDetail() {
        built = open;
        if (open == null) {
            // Kept as it was while the page slides away; rebuilt when another opens.
            return;
        }
        detail.clearCards();
        Account account = open;

        Column top = detail.group(null);
        top.add(pageUndo);
        top.visibleWhen(pageUndo::showing);

        detail.card(null).add(new AccountHeader(() -> Accounts.displayName(account), () -> Accounts.uuid(account),
                () -> AccountWords.caption(account), () -> Accounts.isInUse(account)));

        Column last = detail.group("Account");
        Card facts = last.add(new Card());
        facts.add(new InfoRow("Type", () -> AccountWords.kind(account)));
        // The header names it by the name it plays as, so its email is only said here.
        if (!account.isOffline() && account.getEmail() != null) {
            facts.add(new InfoRow("Email", account::getEmail));
        }
        facts.add(new InfoRow("Name in Game", () -> AccountWords.inGameName(account)));
        facts.add(new InfoRow("Last Used", () -> AccountWords.lastUsed(account)));

        if (!account.isOffline()) {
            last = detail.group("Sign-in");
            Card signIn = last.add(new Card());
            InfoRow kept = new InfoRow("Saved Sign-In",
                    () -> Accounts.isRemembered(account) ? "Kept" : "None",
                    () -> Accounts.isRemembered(account) ? "Forget" : null,
                    () -> forget(account));
            kept.describe("Kept on this computer so switching needs no code");
            signIn.add(kept);
        }

        PushButton remove = new PushButton("Remove Account", () -> remove(account));
        remove.tooltip("Undo from the list, or Ctrl+Z");
        PushButton signIn = new PushButton(() -> signInLabel(account), () -> signIn(account));
        signIn.enabledWhen(() -> !Accounts.isSigningIn() || canShowCode(account));
        signIn.tooltip(() -> signInTip(account));
        detail.actions(last, remove, signIn);
    }

    /** Sign In, or "Show Code…" when this account's code is waiting behind a closed card. */
    private String signInLabel(Account account) {
        if (canShowCode(account)) {
            return "Show Code…";
        }
        return Accounts.isSigningIn() ? "Signing In…" : "Sign In";
    }

    private void signIn(Account account) {
        if (canShowCode(account)) {
            showSheet();
            return;
        }
        // The card first, so a person sees what is happening before Microsoft answers;
        // a kept sign-in needs no person, and gets a card only if it is refused.
        if (Accounts.login(account) != null && !account.isOffline() && !Accounts.isRemembered(account)) {
            showSheet();
        }
    }

    private static String signInTip(Account account) {
        if (account.isOffline()) {
            return "Plays as this name; only servers in offline mode let it join";
        }
        return Accounts.isRemembered(account)
                ? "Signs in without a code"
                : "Shows a code to enter at microsoft.com";
    }

    // --------------------------------------------------------- the sign-in card

    /** @return whether {@code account}'s sign-in is waiting on a code while its card is closed */
    private boolean canShowCode(Account account) {
        SignIn now = Accounts.signIn();
        return sheet == null && Accounts.isSigningIn(account) && now.hasPrompt();
    }

    /** Opens the sign-in card over the page, if the page is on screen. */
    private void showSheet() {
        Screen screen = stack.getScreen();
        if (screen == null || sheet != null) {
            return;
        }
        SignIn showing = Accounts.signIn();
        sheet = Unroll.open(screen, new SignInCard(this::closeSheet), Popup.centred().onClose(() -> {
            sheet = null;
            // Closed with the code still waiting: not opened again on its own; "Show Code…" brings it back.
            dismissed = showing;
        }));
    }

    private void closeSheet() {
        if (sheet != null && sheet.getScreen() != null) {
            sheet.getScreen().close(sheet);
        }
        sheet = null;
    }

    /**
     * Keeps the card in step with the sign-in: opened when a code arrives that no one
     * asked to see (a kept sign-in refused), closed when the sign-in is cancelled.
     */
    private void tickSheet() {
        SignIn now = Accounts.signIn();
        if (sheet != null && (now == null || now.step() == SignIn.Step.CANCELLED)) {
            closeSheet();
        } else if (sheet == null && now != null && now.step() == SignIn.Step.WAITING && now != dismissed) {
            showSheet();
        }
    }

    // ----------------------------------------------------------- Add Account

    /** Fills Add Account: its sentence, the Type and field card, the problem, and Add Account. */
    private void buildAdd() {
        adding.group(null).add(new PageIntro("A Microsoft account asks for a code at microsoft.com the first time; "
                + "an offline account needs only a name and joins only servers in offline mode"));
        Column form = adding.group(null);
        Card card = form.add(new Card());
        card.add(ChoiceRow.of(kind));
        card.add(email);
        card.add(username);
        form.add(new FieldMessage(() -> message, () -> FieldMessage.Kind.ERROR));
        PushButton add = new PushButton("Add Account", this::submit);
        add.enabledWhen(() -> !field().text().isBlank());
        adding.actions(form, add);
    }
}
