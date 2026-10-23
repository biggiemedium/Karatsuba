package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Content;
import dev.px.core.module.Module;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.client.hud.group.HudGroups;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.AnimatedRows;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.setting.SettingLabels;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The modules switched on, each with its metadata (a mode, a count) if wanted.
 *
 * <p><b>Auto</b> follows where the list is on screen, as it is drawn (groups
 * included): in the top half the widest name is on top and the list narrows
 * downwards, in the bottom half the other way up, so the widest is always against
 * the screen's edge; on the left half the names line up on the left, on the right
 * half on the right. Sort can instead keep the old order, longest name first; Align
 * (Display) can fix the side.
 *
 * <p>Metadata follows the name in the secondary text colour, set off as the player
 * chooses: {@code Name [Mode]}, {@code Name (Mode)}, {@code Name | Mode},
 * {@code Name - Mode}, or plainly {@code Name Mode}.
 *
 * <p>Each name is a list item ({@code hud/DESIGN.md} §3.2): its colour is the
 * preset's, by its place in the list. Legacy colours it by its Tint. As modules
 * are switched on and off the list eases to its new size. An empty list takes no
 * space; while positioning, a sample keeps it grabbable.
 */
public final class ArrayListElement extends StyledElement {

    public enum Sort { AUTO, LONGEST_FIRST }

    /** How metadata is set off from the name. */
    public enum MetadataStyle {
        BRACKETS("[", "]"), PARENTHESES("(", ")"), BAR("| ", ""), DASH("- ", ""), PLAIN("", "");

        private final String before;
        private final String after;

        MetadataStyle(String before, String after) {
            this.before = before;
            this.after = after;
        }

        String wrap(String metadata) {
            return before + metadata + after;
        }
    }

    /** One line of the list: a module's name, and its metadata as shown (empty for none). */
    private record Entry(String name, String metadata) {
    }

    /** Every line drawn, by module name: on, or still leaving, with how far each has come in. */
    private final AnimatedRows<Entry> rows = new AnimatedRows<>(Entry::name);

    private final EnumSetting<Sort> sort = enumOf("Sort", Sort.AUTO)
            .describe("Auto puts the widest name against the screen's edge: on top in the top half,"
                    + " at the bottom in the bottom half. Longest First always puts it on top");

    // Saved as "Show Info" since before metadata had a name: relabelled, so what players set is kept.
    private final BooleanSetting showMetadata = SettingLabels.relabel(bool("Show Info", true)
            .describe("The extra text a module puts after its name, like a mode"), "Show Metadata");

    private final EnumSetting<MetadataStyle> metadataStyle = enumOf("Metadata Style", MetadataStyle.BRACKETS)
            .describe("How the metadata is set off from the name: [ ], ( ), a bar, a dash, or a space");

    /**
     * Where a Tint saved before 2026-10-17 arrives, when Tint was this element's
     * setting: never shown, and emptied once it has been passed to the HUD Style,
     * which now holds Tint for the Legacy preset.
     */
    private final StringSetting movedTint = text("Tint", "");

    public ArrayListElement() {
        super("arraylist", "ArrayList", HudLayout.at(Anchor.TOP_RIGHT, -4f, 4f));
        movedTint.visibleWhen(() -> false);
        metadataStyle.visibleWhen(showMetadata::isOn);
    }

    /** Names line up by the side they are on, even in a preset that left-aligns (hud/DESIGN.md §4.16). */
    @Override
    protected boolean keepsAnchorAlignment() {
        return true;
    }

    /** Auto lines the names up on the side of the screen the list is on, wherever it is anchored. */
    @Override
    protected Align autoAlignment() {
        Bounds at = drawnAt();
        if (at == null) {
            return super.autoAlignment();
        }
        return at.getCenterX() < Core.platform().getScreenWidth() / 2f ? Align.START : Align.END;
    }

    @Override
    protected void describe(Content body) {
        if (!movedTint.get().isEmpty()) {
            // After every section has loaded, whichever order they loaded in.
            HudStyle.get().takeMovedListTint(movedTint.get());
            movedTint.set("");
        }
        List<Entry> entries = entries();
        watch(entries);
        List<AnimatedRows.Row<Entry>> lines = rows.update(entries, order());
        if (lines.isEmpty()) {
            return;
        }
        // Their own column with no gap: each line carries its share of the gap, so a line
        // growing in or out pushes the ones after it by exactly as much as it takes.
        Align side = alignment();
        float gap = tokens().gap();
        body.column(column -> {
            column.align(side);
            for (int i = 0; i < lines.size(); i++) {
                line(column, lines.get(i), i, lines.size(), gap * lines.get(i).gapAbove(), side);
            }
        });
    }

    /**
     * One line, drawn by hand so it can animate ({@code hud/DESIGN.md} §2.10): as it
     * comes in its height grows from nothing, pushing what is below down, its width
     * grows from the side it lines up on, and its text slides in from the screen's
     * nearer edge as it fades in; leaving, the same backwards. At Motion: Reduced it
     * only fades, and at Off it simply appears.
     */
    private void line(Content column, AnimatedRows.Row<Entry> line, int index, int count, float gapAbove, Align side) {
        Entry entry = line.value();
        float shown = line.shown();
        float grown = line.grown();
        Font nameFont = tokens().font(TextRole.PRIMARY);
        Font metadataFont = tokens().font(TextRole.SECONDARY);
        float fullWidth = width(entry);
        float lineHeight = nameFont.getHeight();
        Color name = cued(tokens().listItem(index, count)).fade(shown);
        Color metadata = cued(tokens().text(TextRole.SECONDARY)).fade(shown);
        float nameWidth = nameFont.widthOf(entry.name());
        // The gap above is already this line's share (AnimatedRows); only the line itself grows here.
        float top = gapAbove;
        column.custom(fullWidth * grown, gapAbove + lineHeight * grown, (x, y, w, h) -> {
            // The text keeps its full width; the part narrows round it from its side, so the
            // text slides out from under the plate's edge rather than squashing.
            float textX = switch (side) {
                case END -> x;
                case CENTER -> x + (w - fullWidth) / 2f;
                default -> x + w - fullWidth;
            };
            Render.text(nameFont, entry.name(), textX, y + top, name);
            if (!entry.metadata().isEmpty()) {
                Render.text(metadataFont, entry.metadata(), textX + nameWidth + LABEL_GAP, y + top, metadata);
            }
        });
    }

    /** Rows slide past the plate's edge while they come and go, so they are clipped to it then. */
    @Override
    protected boolean overflowing() {
        return rows.moving();
    }

    /** Its lines grow and shrink themselves, so the plate follows them exactly. */
    @Override
    protected boolean sizesItself() {
        return true;
    }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        if (isEditing() && Core.modules().enabled().isEmpty()) {
            // A frozen sample while positioning, so the box is not a sliver.
            entries.add(entry("Fullbright", "Gamma"));
            entries.add(entry("Sprint", ""));
        } else {
            // Core 2.3 keeps no "visible" flag: the switch is ClientModule's.
            for (Module module : Core.modules().enabled()) {
                if (!(module instanceof ClientModule client) || client.getVisible().isOn()) {
                    entries.add(entry(module.getName(), module.getDisplayInfo()));
                }
            }
        }
        entries.sort(order());
        return entries;
    }

    private Entry entry(String name, String info) {
        boolean shown = showMetadata.isOn() && info != null && !info.isEmpty();
        return new Entry(name, shown ? metadataStyle.get().wrap(info) : "");
    }

    /**
     * @return the list's order. Longest First is the order it always had, by how many
     *         letters a line has. Auto is by how wide each line is drawn, widest
     *         against the screen's nearer edge
     */
    private Comparator<Entry> order() {
        if (sort.get() == Sort.LONGEST_FIRST) {
            return Comparator.comparingInt((Entry entry) -> letters(entry)).reversed();
        }
        Comparator<Entry> widestFirst = Comparator.comparingDouble((Entry entry) -> width(entry)).reversed();
        return inBottomHalf() ? widestFirst.reversed() : widestFirst;
    }

    private static int letters(Entry entry) {
        return entry.name().length() + (entry.metadata().isEmpty() ? 0 : entry.metadata().length() + 1);
    }

    /** @return how wide a line is drawn, in this frame's fonts */
    private float width(Entry entry) {
        Font name = tokens().font(TextRole.PRIMARY);
        float width = name.widthOf(entry.name());
        if (!entry.metadata().isEmpty()) {
            width += LABEL_GAP + tokens().font(TextRole.SECONDARY).widthOf(entry.metadata());
        }
        return width;
    }

    /** @return whether the list sits in the bottom half of the screen; before it is first drawn, by its anchor */
    private boolean inBottomHalf() {
        Bounds at = drawnAt();
        if (at == null) {
            HudLayout layout = Core.hud().layoutOf(this);
            return (layout == null ? defaultLayout() : layout).getAnchor().isBottom();
        }
        return at.getCenterY() >= Core.platform().getScreenHeight() / 2f;
    }

    /** @return where the list was drawn last frame, after any group moved it; null before its first */
    private Bounds drawnAt() {
        HudGroups groups = HudGroups.get();
        return groups == null ? null : groups.lastPlaced(getId());
    }
}
