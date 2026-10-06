package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.gui.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.simulation.DriftMonitor;
import dev.px.core.network.anticheat.Detection;
import dev.px.core.network.lag.LagService;
import dev.px.core.network.packet.PacketKind;
import dev.px.core.network.server.ServerBrand;
import dev.px.core.network.server.ServerInfo;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.network.MinecraftPacketKind;
import dev.px.karatsuba.client.debug.AlertType;
import dev.px.karatsuba.client.debug.DebugAlert;
import dev.px.karatsuba.client.debug.DebugMonitor;
import dev.px.karatsuba.client.debug.PacketSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A live debug panel: the connection, the player's movement, the server, the
 * packets going past, and a log of everything worth noticing.
 *
 * <p>Every number comes from somewhere else &mdash; Core's network services, the
 * movement simulation, and {@link DebugMonitor}, which collects packets and
 * alerts whether this is visible or not. This only filters and lays them out,
 * so turning a section off costs nothing but the drawing.
 *
 * <h2>Filters</h2>
 * The packet feed and the busiest list share one filter: kinds (as the adapter's
 * describer files them), direction, and two name lists. A name list is
 * comma-separated and matched anywhere in the name, ignoring case, so
 * {@code move, ping} keeps movement and pings and an exclude of {@code chunk}
 * drops every chunk packet. Packets the describer does not name are
 * {@code OTHER}, hidden by default since they are most of the traffic.
 * The log is filtered by alert type and age.
 */
public final class DebugElement extends AbstractHudElement {

    private static final float PADDING = 4f;
    private static final float ROW_GAP = 1f;
    private static final float SECTION_GAP = 3f;
    private static final float LABEL_GAP = 6f;

    // -------------------------------------------------------------- sections

    private final BooleanSetting connection = bool("Connection", true)
            .describe("TPS, ping, packet rates and lag");
    private final BooleanSetting movement = bool("Movement", true)
            .describe("Position, measured speed, ground, rotation and simulation drift");
    private final BooleanSetting server = bool("Server", true)
            .describe("Software and the most likely anticheat");
    private final BooleanSetting packetRates = bool("Busiest Packets", true)
            .describe("The packet types arriving or leaving most often over the last second");
    private final BooleanSetting packetFeed = bool("Packet Feed", false)
            .describe("Every packet as it goes past, newest first");
    private final BooleanSetting alerts = bool("Alerts", true)
            .describe("Rubberbands, lag spikes, anticheat, knockback and servers joined");

    private final GroupSetting sections = group("Sections", true)
            .containing(connection, movement, server, packetRates, packetFeed, alerts);

    // ---------------------------------------------------------- packet filter

    private final MultiEnumSetting<MinecraftPacketKind> kinds =
            multi("Kinds", MinecraftPacketKind.class, MinecraftPacketKind.values())
                    .describe("Which kinds of packet to show");
    private final BooleanSetting otherPackets = bool("Other Packets", false)
            .describe("Packets the describer does not name; most of the traffic");
    private final EnumSetting<Direction> direction = enumOf("Direction", Direction.BOTH);
    private final StringSetting include = text("Only Names", "")
            .describe("Comma-separated; keep only packets whose name contains one");
    private final StringSetting exclude = text("Hide Names", "")
            .describe("Comma-separated; drop packets whose name contains one");
    private final NumberSetting<Integer> rateLines = integer("Busiest Lines", 5, 1, 20)
            .visibleWhen(packetRates);
    private final NumberSetting<Integer> feedLines = integer("Feed Lines", 8, 1, 30)
            .visibleWhen(packetFeed);

    private final GroupSetting packetFilter = group("Packet Filter", false)
            .containing(kinds, otherPackets, direction, include, exclude, rateLines, feedLines);

    // ------------------------------------------------------------ alert filter

    private final MultiEnumSetting<AlertType> alertTypes =
            multi("Alert Types", AlertType.class, AlertType.values());
    private final NumberSetting<Integer> alertLines = integer("Alert Lines", 6, 1, 30);
    private final NumberSetting<Integer> alertSeconds = integer("Alert Seconds", 30, 5, 600)
            .describe("How long an alert stays in the log");

    private final GroupSetting alertFilter = group("Alert Filter", false)
            .containing(alertTypes, alertLines, alertSeconds);

    // -------------------------------------------------------------- appearance

    private final ColorSetting headerColor = color("Header Colour", Color.of(120, 180, 255));
    private final ColorSetting labelColor = color("Label Colour", Color.of(150, 160, 180));
    private final ColorSetting valueColor = color("Value Colour", Color.WHITE);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(headerColor, labelColor, valueColor);

    /** The name lists, parsed once per change rather than once per packet per frame. */
    private String parsedInclude;
    private String parsedExclude;
    private List<String> includeTerms = List.of();
    private List<String> excludeTerms = List.of();

    public DebugElement() {
        super("debug", "Debug", HudLayout.at(Anchor.TOP_LEFT, 4f, 24f));
    }

    @Override
    public void content(Content content) {
        content.background(GuiStyle.surface(), GuiStyle.radius()).padding(PADDING).gap(SECTION_GAP);
        content.align(Align.STRETCH);
        content.min(150f, 12f);

        DebugMonitor monitor = DebugMonitor.get();
        if (monitor == null) {
            content.text("Debug monitor not running", Color.GRAY);
            return;
        }
        refreshNameFilters();

        if (connection.isOn()) {
            section(content, "Connection", this::connection);
        }
        if (movement.isOn()) {
            section(content, "Movement", body -> movement(body, monitor));
        }
        if (server.isOn()) {
            section(content, "Server", this::server);
        }
        if (packetRates.isOn()) {
            section(content, "Busiest Packets", body -> packetRates(body, monitor));
        }
        if (packetFeed.isOn()) {
            section(content, "Packet Feed", body -> packetFeed(body, monitor));
        }
        if (alerts.isOn()) {
            section(content, "Alerts", body -> alerts(body, monitor));
        }
    }

    // ----------------------------------------------------------------- sections

    private void connection(Content body) {
        LagService lag = Core.lag();
        row(body, "TPS", Core.tps().hasEstimate() ? format("%.2f", Core.tps().getTps()) : "--", valueColor.resolve());
        int ping = lag.getPing();
        row(body, "Ping", ping < 0 ? "--" : ping + "ms" + (lag.getAveragePing() >= 0
                ? format(" (avg %.0f, jitter %.0f)", lag.getAveragePing(), lag.getPingJitter()) : ""), valueColor.resolve());
        row(body, "Packets", format("%.0f in / %.0f out /s", lag.getInboundRate(), lag.getOutboundRate()), valueColor.resolve());
        row(body, "Lag", lag.isLagging() ? format("silent %.1fs", lag.getLagMillis() / 1000d) : "none",
                lag.isLagging() ? Color.RED : valueColor.resolve());
    }

    private void movement(Content body, DebugMonitor monitor) {
        if (!Players.present()) {
            row(body, "Player", "not in a world", Color.GRAY);
            return;
        }
        Vec3 at = Players.position();
        Vec2 look = Players.rotation();
        row(body, "Position", format("%.2f, %.2f, %.2f", at.getX(), at.getY(), at.getZ()), valueColor.resolve());
        row(body, "Speed", format("%.2f b/s", monitor.getHorizontalSpeed()), valueColor.resolve());
        row(body, "Vertical", format("%+.2f b/s", monitor.getVerticalSpeed()), valueColor.resolve());
        row(body, "Ground", Players.onGround() ? "yes" : "no", valueColor.resolve());
        row(body, "Rotation", format("%.1f / %.1f", look.getYaw(), look.getPitch()), valueColor.resolve());

        DriftMonitor drift = Core.simulation().getDrift();
        row(body, "Drift", drift.isReliable()
                ? format("%.4f h, %.4f v", drift.getHorizontalError(), drift.getVerticalError())
                : "sampling", drift.isReliable() ? valueColor.resolve() : Color.GRAY);

        int rubberbands = monitor.getRubberbands();
        row(body, "Rubberbands", rubberbands == 0 ? "0"
                : format("%d (last %.2f)", rubberbands, monitor.getLastRubberbandDistance()),
                rubberbands == 0 ? valueColor.resolve() : AlertType.RUBBERBAND.getColor());
    }

    private void server(Content body) {
        ServerInfo info = Core.server().getInfo();
        if (!info.isMultiplayer()) {
            row(body, "Server", info.isSingleplayer() ? "singleplayer" : "not connected", Color.GRAY);
            return;
        }
        row(body, "Address", info.getAddress(), valueColor.resolve());
        ServerBrand brand = info.getBrand();
        String software = brand == null ? "--"
                : (brand.getSoftware().isKnown() ? brand.getSoftware().getName() : brand.getRaw())
                + (brand.getProxy() != null ? " via " + brand.getProxy().getName() : "");
        row(body, "Software", software, valueColor.resolve());
        Optional<Detection> primary = Core.anticheat().getPrimary();
        row(body, "Anticheat", primary.map(d -> d.getName() + " (" + lower(d.getConfidence().name()) + ")")
                .orElse("none detected"), primary.isPresent() ? AlertType.ANTICHEAT.getColor() : Color.GRAY);
    }

    private void packetRates(Content body, DebugMonitor monitor) {
        List<DebugMonitor.PacketRate> busiest = monitor.busiestPackets(rateLines.getInt(),
                rate -> keeps(rate.name(), rate.kind(), rate.inbound()));
        if (busiest.isEmpty()) {
            row(body, "", "nothing matching", Color.GRAY);
            return;
        }
        for (DebugMonitor.PacketRate rate : busiest) {
            row(body, arrow(rate.inbound()) + " " + rate.name(), format("%.0f/s", rate.perSecond()), valueColor.resolve());
        }
    }

    private void packetFeed(Content body, DebugMonitor monitor) {
        List<PacketSample> feed = monitor.recentPackets(feedLines.getInt(),
                sample -> keeps(sample.getName(), sample.getKind(), sample.isInbound()));
        if (feed.isEmpty()) {
            row(body, "", "nothing matching", Color.GRAY);
            return;
        }
        long now = Core.network().nanoTime();
        for (PacketSample sample : feed) {
            row(body, arrow(sample.isInbound()) + " " + sample.getName(),
                    format("%.1fs", (now - sample.getNanos()) / 1_000_000_000d), labelColor.resolve());
        }
    }

    private void alerts(Content body, DebugMonitor monitor) {
        List<DebugAlert> log = monitor.recentAlerts(alertLines.getInt(), alertSeconds.getInt() * 1000L,
                alert -> alertTypes.has(alert.getType()));
        if (log.isEmpty()) {
            row(body, "", "quiet", Color.GRAY);
            return;
        }
        long now = System.currentTimeMillis();
        for (DebugAlert alert : log) {
            body.row(row -> {
                row.align(Align.CENTER);
                row.text(alert.getType().getLabel(), alert.getType().getColor());
                row.space(LABEL_GAP, 0f);
                row.text(alert.getMessage(), valueColor.resolve());
                row.fill();
                row.space(LABEL_GAP, 0f);
                row.text(format("%.0fs", alert.ageSeconds(now)), labelColor.resolve());
            });
        }
    }

    // ------------------------------------------------------------------ filter

    private boolean keeps(String name, PacketKind kind, boolean inbound) {
        if (direction.get() == Direction.INBOUND && !inbound || direction.get() == Direction.OUTBOUND && inbound) {
            return false;
        }
        MinecraftPacketKind ours = kindOf(kind);
        if (ours == null ? !otherPackets.isOn() : !kinds.has(ours)) {
            return false;
        }
        String lower = lower(name);
        if (!includeTerms.isEmpty() && includeTerms.stream().noneMatch(lower::contains)) {
            return false;
        }
        return excludeTerms.stream().noneMatch(lower::contains);
    }

    /** @return the adapter's kind with this name, or null for {@code OTHER} and anything unknown */
    private static MinecraftPacketKind kindOf(PacketKind kind) {
        for (MinecraftPacketKind candidate : MinecraftPacketKind.values()) {
            if (candidate.is(kind)) {
                return candidate;
            }
        }
        return null;
    }

    private void refreshNameFilters() {
        String in = include.get();
        if (!in.equals(parsedInclude)) {
            parsedInclude = in;
            includeTerms = terms(in);
        }
        String out = exclude.get();
        if (!out.equals(parsedExclude)) {
            parsedExclude = out;
            excludeTerms = terms(out);
        }
    }

    private static List<String> terms(String list) {
        List<String> terms = new ArrayList<>();
        for (String term : list.split(",")) {
            String trimmed = lower(term.trim());
            if (!trimmed.isEmpty()) {
                terms.add(trimmed);
            }
        }
        return terms;
    }

    // ------------------------------------------------------------------ layout

    private void section(Content content, String title, java.util.function.Consumer<Content> body) {
        content.column(column -> {
            column.align(Align.STRETCH).gap(ROW_GAP);
            column.text(title, headerColor.resolve());
            body.accept(column);
        });
    }

    private void row(Content content, String label, String value, Color color) {
        content.row(row -> {
            row.align(Align.CENTER);
            if (!label.isEmpty()) {
                row.text(label, labelColor.resolve());
                row.space(LABEL_GAP, 0f);
            }
            row.fill();
            row.text(value, color);
        });
    }

    private static String arrow(boolean inbound) {
        return inbound ? "<-" : "->";
    }

    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    public enum Direction {
        BOTH,
        INBOUND,
        OUTBOUND
    }
}
