package dev.px.karatsuba.client.command;

import dev.px.core.Core;
import dev.px.core.command.Command;
import dev.px.core.command.CommandContext;
import dev.px.core.command.CommandInfo;
import dev.px.core.network.anticheat.Detection;
import dev.px.core.network.anticheat.TransactionPattern;
import dev.px.core.network.lag.LagService;
import dev.px.core.network.server.ServerBrand;
import dev.px.core.network.server.ServerInfo;
import dev.px.core.network.tps.TpsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything Core knows about the connection, in chat.
 *
 * <p>The HUD shows the answers; this shows the evidence behind them &mdash; the
 * raw brand, the registered channels, the transaction pattern and every
 * detection with its reason &mdash; which is what to read when a server is named
 * wrongly or not at all.
 */
@CommandInfo(name = "server", aliases = {"srv"}, description = "Shows the server, TPS, ping and anticheat",
        usage = "server [channels]")
public final class ServerCommand extends Command {

    @Override
    public void execute(CommandContext context) {
        ServerInfo server = Core.server().getInfo();
        if (!server.isConnected()) {
            context.fail("Not connected to a server");
        }
        if (context.get(0, "").equalsIgnoreCase("channels")) {
            channels(context, server);
            return;
        }

        context.reply(server.isSingleplayer() ? "Singleplayer" : "Server: " + server.getAddress());
        if (server.isMultiplayer()) {
            context.reply("Brand: " + brand(server.getBrand()));
        }

        TpsService tps = Core.tps();
        context.reply(tps.hasEstimate()
                ? String.format(Locale.ROOT, "TPS: %.2f (%.1f ms a tick)", tps.getTps(), tps.getTickMillis())
                : "TPS: no estimate yet");

        LagService lag = Core.lag();
        context.reply(lag.getPing() < 0
                ? "Ping: not reported yet"
                : String.format(Locale.ROOT, "Ping: %d ms (avg %.0f, jitter %.0f)",
                        lag.getPing(), lag.getAveragePing(), lag.getPingJitter()));
        context.reply(String.format(Locale.ROOT, "Packets: %.0f/s in, %.0f/s out%s",
                lag.getInboundRate(), lag.getOutboundRate(),
                lag.isLagging() ? String.format(Locale.ROOT, ", silent for %.1fs", lag.getLagMillis() / 1000d) : ""));

        if (server.isMultiplayer()) {
            anticheat(context);
        }
    }

    private static String brand(ServerBrand brand) {
        if (brand == null) {
            return "not sent";
        }
        List<String> parts = new ArrayList<>();
        parts.add(brand.getSoftware().isKnown() ? brand.getSoftware().getName() : "unrecognised");
        if (brand.getProxy() != null) {
            parts.add("via " + brand.getProxy().getName());
        }
        return String.join(" ", parts) + " \"" + brand.getRaw() + "\"";
    }

    private static void anticheat(CommandContext context) {
        TransactionPattern transactions = Core.anticheat().getTransactions();
        context.reply("Transactions: " + transactions);
        List<Detection> detections = Core.anticheat().getDetections();
        if (detections.isEmpty()) {
            context.reply("Anticheat: none detected");
            return;
        }
        for (Detection detection : detections) {
            context.reply("Anticheat: " + detection.getName() + " ("
                    + detection.getConfidence().name().toLowerCase(Locale.ROOT) + ") - " + detection.getReason());
        }
    }

    private static void channels(CommandContext context, ServerInfo server) {
        List<String> channels = new ArrayList<>(server.getChannels());
        channels.sort(String.CASE_INSENSITIVE_ORDER);
        context.reply(channels.isEmpty()
                ? "No channels registered"
                : channels.size() + " channels: " + String.join(", ", channels));
    }

    @Override
    public List<String> complete(CommandContext context, int index) {
        return index == 0 ? List.of("channels") : List.of();
    }
}
