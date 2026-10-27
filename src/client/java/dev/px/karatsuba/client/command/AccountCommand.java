package dev.px.karatsuba.client.command;

import dev.px.core.account.Account;
import dev.px.core.account.AccountType;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandException;
import dev.px.core.command.CommandInfo;
import dev.px.core.util.text.ChatColor;
import dev.px.karatsuba.client.account.Accounts;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages saved accounts from chat.
 *
 * <p>Chat rather than a screen for anything that needs typing: a name and an
 * email are text, and the game already has a text field that handles paste,
 * selection and a caret. The screen beside this one does the part chat is bad at
 * &mdash; looking at a list and picking one.
 */
@CommandInfo(
        name = "account",
        aliases = {"alt", "acc"},
        description = "Adds, removes and signs in to saved accounts",
        usage = "account <list|add|offline|remove|forget|login> [name]")
public final class AccountCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        switch (context.get(0).toLowerCase()) {
            case "list" -> list(context);
            case "add" -> add(context);
            case "offline" -> offline(context);
            case "remove", "rem", "del" -> remove(context);
            case "forget" -> forget(context);
            case "login", "switch" -> login(context);
            default -> context.failUsage();
        }
    }

    private void list(CommandContext context) {
        List<Account> accounts = Accounts.all();
        if (accounts.isEmpty()) {
            context.reply("No accounts saved. " + ChatColor.GRAY
                    + "Add one with .account add <email>");
            return;
        }
        context.reply(ChatColor.GRAY + "Playing as " + ChatColor.WHITE + Accounts.current());

        for (Account account : accounts) {
            context.reply(ChatColor.WHITE + account.getName()
                    + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + describe(account));
        }
    }

    /**
     * Adds a Microsoft account.
     *
     * <p>Nothing is verified here and nothing can be: the account is only a label
     * until the first sign-in, which is when Microsoft says what it is really
     * called. Adding a typo costs nothing and fails at login.
     */
    private void add(CommandContext context) {
        String email = context.get(1);
        String label = context.has(2) ? context.rest(2) : email;

        if (Accounts.find(label).isPresent()) {
            throw new CommandException("An account called " + label + " already exists");
        }
        Accounts.addMicrosoft(label, email);
        context.reply("Added " + label + ChatColor.GRAY + " - sign in with .account login " + label);
    }

    private void offline(CommandContext context) {
        String name = context.get(1);
        if (Accounts.find(name).isPresent()) {
            throw new CommandException("An account called " + name + " already exists");
        }
        Accounts.addOffline(name);
        context.reply("Added offline account " + name);
    }

    private void remove(CommandContext context) {
        String name = context.rest(1);
        if (!Accounts.remove(name)) {
            throw new CommandException("No account called " + name);
        }
        context.reply("Removed " + name);
    }

    private void forget(CommandContext context) {
        String name = context.rest(1);
        if (!Accounts.forget(name)) {
            throw new CommandException("No account called " + name);
        }
        context.reply("Forgot the saved sign-in for " + name
                + ChatColor.GRAY + " - the next login will ask for a code");
    }

    /**
     * Starts a sign-in.
     *
     * <p>Replies immediately and says nothing about the outcome, because there is
     * not one yet: a Microsoft account with no saved sign-in waits for a person to
     * type a code into a browser. The result arrives as a notification.
     */
    private void login(CommandContext context) {
        String name = context.rest(1);
        Account account = Accounts.find(name)
                .orElseThrow(() -> new CommandException("No account called " + name));

        Accounts.login(account);
        context.reply("Signing in as " + account.getName() + "...");
    }

    /** @return the right-hand column of the list: type, and whether it is ready to go */
    private static String describe(Account account) {
        if (account.getType() == AccountType.OFFLINE) {
            return "offline";
        }
        String state = Accounts.isRemembered(account) ? "signed in before" : "needs a code";
        return account.getEmail() == null ? state : account.getEmail() + ", " + state;
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        if (index == 0) {
            return List.of("list", "add", "offline", "remove", "forget", "login");
        }
        if (index != 1) {
            return List.of();
        }
        // Only the verbs that act on something already saved offer a name; "add"
        // and "offline" are asking for one that does not exist yet.
        String action = context.get(0, "").toLowerCase();
        if (!List.of("remove", "rem", "del", "forget", "login", "switch").contains(action)) {
            return List.of();
        }
        return Accounts.all().stream().map(Account::getName).collect(Collectors.toList());
    }
}
