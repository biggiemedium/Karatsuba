package dev.px.karatsuba.client.command;

import dev.px.core.Core;

/**
 * What a line typed in chat is, as far as client commands go: whether it is one,
 * and what it is most likely on its way to being.
 *
 * <p>Both answers come from Core's {@code CommandRegistry}, the same registry that
 * runs the line when it is sent, so the chat never promises something that would
 * not happen: a line is a command exactly when Core would run it as one, and a
 * prediction is only ever something Core offered as a completion.
 */
public final class CommandLine {

    private CommandLine() {
    }

    /** @return whether sending {@code text} would run it as a client command */
    public static boolean isCommand(String text) {
        String prefix = Core.commands().getPrefix().get();
        return !prefix.isEmpty() && text.startsWith(prefix);
    }

    /**
     * Completes the word being typed at the end of a command line: {@code ".he"}
     * becomes {@code ".help"}, {@code ".toggle sp"} becomes {@code ".toggle Sprint"}.
     *
     * <p>The first completion Core offers that the word is the start of, ignoring
     * case, in the order Core lists them (which for command names is the order
     * {@link Commands} registers them in). Nothing while no word has been started,
     * so a bare prefix or a trailing space predicts nothing.
     *
     * @return the whole line with its last word completed, or null for no prediction
     */
    public static String predict(String text) {
        if (!isCommand(text)) {
            return null;
        }
        int prefixLength = Core.commands().getPrefix().get().length();
        String line = text.substring(prefixLength);
        int wordStart = line.lastIndexOf(' ') + 1;
        String word = line.substring(wordStart);
        if (word.isEmpty()) {
            return null;
        }
        for (String candidate : Core.commands().complete(line)) {
            if (candidate.length() > word.length() && candidate.regionMatches(true, 0, word, 0, word.length())) {
                return text.substring(0, prefixLength + wordStart) + candidate;
            }
        }
        return null;
    }
}
