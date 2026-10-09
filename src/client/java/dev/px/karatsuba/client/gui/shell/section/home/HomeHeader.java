package dev.px.karatsuba.client.gui.shell.section.home;

import dev.px.core.Core;
import dev.px.core.network.anticheat.Detection;
import dev.px.gui.Widget;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home's top line: a greeting on the left, and on the right the state of the
 * session as a few chips (the server, its ping and tick rate, the anticheat
 * it seems to run). Each chip is a word and a dot, so its state never rests on
 * the dot's colour alone; a value Core has no reading for says so rather than
 * showing a zero.
 */
public final class HomeHeader extends Widget {

    /** How a chip's dot reads: fine, worth watching, or bad. Neutral for plain facts. */
    public enum Tone {
        GOOD, WARN, BAD, NEUTRAL
    }

    /** One fact about the session: what to show, and how it is going. */
    public record Chip(String text, Tone tone) {
    }

    /** @return "Good evening, Dev" by the clock */
    public String greeting() {
        int hour = LocalTime.now().getHour();
        String part = hour < 5 ? "Good evening" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        String name = Core.platform().getUsername();
        return name == null || name.isEmpty() ? part : part + ", " + name;
    }

    /** @return the session's chips, left to right */
    public List<Chip> chips() {
        List<Chip> chips = new ArrayList<>();
        if (!Core.platform().isInGame()) {
            chips.add(new Chip("Not in a world", Tone.NEUTRAL));
            return chips;
        }
        if (!Core.server().isMultiplayer()) {
            chips.add(new Chip("Singleplayer", Tone.NEUTRAL));
            return chips;
        }
        String host = Core.server().getHost();
        chips.add(new Chip(host == null || host.isEmpty() ? "Multiplayer" : host, Tone.GOOD));

        int ping = Core.lag().getPing();
        if (ping > 0) {
            chips.add(new Chip(ping + " ms", ping < 80 ? Tone.GOOD : ping < 160 ? Tone.WARN : Tone.BAD));
        }
        if (Core.tps().hasEstimate()) {
            double tps = Core.tps().getTps();
            chips.add(new Chip(String.format(Locale.ROOT, "%.1f TPS", tps),
                    tps >= 19d ? Tone.GOOD : tps >= 15d ? Tone.WARN : Tone.BAD));
        }
        Core.anticheat().getPrimary().map(Detection::getName)
                .ifPresent(name -> chips.add(new Chip(name, Tone.NEUTRAL)));
        return chips;
    }
}
