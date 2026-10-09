package dev.px.karatsuba.client.settings.general;

import dev.px.core.Core;
import dev.px.core.module.toggle.ModuleToggleEvent;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.adapter.platform.MinecraftPlatform;
import dev.px.karatsuba.client.screen.Overlays;
import dev.px.karatsuba.client.settings.ClientSection;

/**
 * How the client talks to you: whether it signs its chat messages, and whether
 * it says so when a keybind switches a module on or off.
 *
 * <p>Both answer the same question, "did that just happen?". A command's reply
 * in chat is easy to mistake for a server message without the client's name on
 * it; a module switched by a key mid-fight has no other sign than the ArrayList.
 * A module switched from the GUI is not announced: the switch moving under the
 * cursor already says it.
 */
public final class GeneralSettings extends ClientSection {

    private final BooleanSetting chatTag = bool("Chat Tag", true)
            .describe("Sign the client's chat messages with its name, in the theme's colour");

    private final StringSetting tagName = text("Tag Name", "")
            .maxLength(16)
            .describe("The name in the tag; empty uses the client's own")
            .visibleWhen(chatTag);

    private final BooleanSetting toggleAlerts = bool("Toggle Alerts", true)
            .describe("Show a notification when a keybind switches a module on or off");

    public GeneralSettings() {
        super("general", "General", "How the client tells you what it is doing");
    }

    @Override
    protected void onRegister(Core core) {
        MinecraftPlatform.tagMessages(this::tag, () -> Core.themes().getPrimary().toARGB());
        core.getBus().on(ModuleToggleEvent.class, this::onToggled);
    }

    /** @return the name chat messages are signed with, or null for none */
    private String tag() {
        if (!chatTag.isOn()) {
            return null;
        }
        return tagName.isEmpty() ? Core.get().getClientName() : tagName.get();
    }

    private void onToggled(ModuleToggleEvent event) {
        // Only in game, and only when no GUI is open: config loads and the GUI's own
        // switches are not news.
        if (!toggleAlerts.isOn() || !Core.platform().isInGame() || Overlays.gui().isCapturing()) {
            return;
        }
        String name = event.getModule().getName();
        if (event.isNowEnabled()) {
            Core.notifications().success(name, "Enabled");
        } else {
            Core.notifications().info(name, "Disabled");
        }
    }
}
