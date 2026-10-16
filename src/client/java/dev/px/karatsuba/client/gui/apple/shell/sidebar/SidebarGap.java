package dev.px.karatsuba.client.gui.apple.shell.sidebar;

import dev.px.gui.container.Column;

import java.util.List;

/** Room above a group of sidebar entries, there only while something in the group is. */
public final class SidebarGap extends Column {

    SidebarGap(List<SidebarEntry> group) {
        visibleWhen(() -> group.stream().anyMatch(SidebarEntry::isVisible));
    }
}
