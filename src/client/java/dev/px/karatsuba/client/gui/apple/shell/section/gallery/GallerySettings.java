package dev.px.karatsuba.client.gui.apple.shell.section.gallery;

import dev.px.core.render.Color;
import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.setting.impl.RangeSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.client.setting.ChoiceSetting;
import dev.px.karatsuba.client.setting.ItemSetting;

import java.util.List;

/**
 * One of every kind of setting, for the Gallery: nothing reads them and nothing
 * saves them. TEMPORARY, for PLAN.md step 8's check; removed in step 17.
 */
final class GallerySettings extends SettingHolder {

    enum Mode { SILENT, NORMAL, STRICT }

    enum Target { PLAYERS, HOSTILES, PASSIVES, VEHICLES, PROJECTILES }

    enum Part { HEAD, BODY, FEET }

    private final BooleanSetting toggle = bool("Toggle", true)
            .describe("A switch: on or off");
    private final NumberSetting<Integer> stepper = integer("Stepper", 3, 1, 8)
            .describe("A whole number with few values: stepped with − and +");
    private final NumberSetting<Double> slider = decimal("Slider", 4.5d, 3d, 6d)
            .describe("A number with many values: a slider");
    private final RangeSetting range = range("Range", 80d, 120d, 0d, 200d)
            .describe("A lower and an upper bound on one track");
    private final EnumSetting<Mode> segmented = enumOf("Segmented", Mode.NORMAL)
            .describe("Up to three short options: a segmented control");

    private final EnumSetting<Target> popup = enumOf("Pop-up", Target.PLAYERS)
            .describe("More options: a pop-up menu");
    private final MultiEnumSetting<Part> options = multi("Options", Part.class, Part.HEAD)
            .describe("Up to four that can each be on: a row of pills");
    private final MultiEnumSetting<Target> many = multi("Many Options", Target.class, Target.PLAYERS, Target.HOSTILES)
            .describe("More than four: a pop-up of ticks");
    private final ChoiceSetting choice = new ChoiceSetting("Choice", () -> List.of("Default", "Inter", "Roboto"));

    private final ColorSetting colour = color("Colour", Color.of(10, 132, 255));
    private final BindSetting bind = bind("Bind");
    private final StringSetting text = text("Text", "Karatsuba");
    private final ItemSetting items = ItemSetting.blocks("Blocks", "minecraft:obsidian");

    private final GroupSetting more = group("More Controls", true)
            .containing(popup, options, many, choice);
    private final GroupSetting other = group("Other Values", true)
            .containing(colour, bind, text, items);

    @Override
    public String getName() {
        return "Gallery";
    }
}
