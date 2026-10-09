package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Subscribe;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.PresenceTracker;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.render.ColorUtil;
import dev.px.karatsuba.adapter.event.game.render.NameTagEvent;
import dev.px.karatsuba.adapter.event.game.render.WorldOverlayEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.render.nametag.EnchantNames;
import dev.px.karatsuba.client.module.render.nametag.NameTagPainter;
import dev.px.karatsuba.client.module.render.nametag.NameTag;
import dev.px.karatsuba.client.module.render.nametag.NameTagPainter.ItemColumn;
import dev.px.karatsuba.client.module.render.nametag.NameTagPainter.Note;
import dev.px.karatsuba.client.module.render.nametag.NameTagPainter.Segment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Replaces the vanilla name over other players with a fuller tag: name, game
 * mode, ping and health, and above it everything they are holding and wearing,
 * with durability and enchantments.
 *
 * <p>Ported from Leapfrog's {@code NameTags}, setting for setting. Where that drew
 * in world space with the game's font, this projects each player to the screen
 * on the world overlay layer: in the client's own font and theme, through walls
 * with no depth handling, and still under any open screen. Tags shrink with
 * distance down to a readable floor, pop in when a player comes into range and
 * fade out when they leave.
 *
 * <p>The module is the decisions: who gets a tag, what it says, what colour and
 * how big. Core's {@link PresenceTracker} keeps which tags are alive and how far
 * through their animation, {@link NameTagPainter} is the layout, and
 * {@link EnchantNames} is the abbreviations, so each can be read and changed
 * alone.
 */
@ModuleInfo(name = "NameTags", description = "Shows player information above their heads", category = "Render")
public final class NameTagsModule extends ClientModule {

    /** How far above the top of the hitbox the tag sits, in blocks. Leapfrog's spacing. */
    private static final double HEAD_CLEARANCE = 0.5d;

    /** Players up to this far past the range are still followed, so a fading tag tracks them. */
    private static final int LINGER_RANGE = 2;

    /** Tags are full size within this many blocks of the camera, then shrink with distance. */
    private static final double FULL_SIZE_DISTANCE = 8d;

    /** The smallest a tag gets from distance alone: reached at twenty blocks. */
    private static final double MIN_DISTANCE_FACTOR = 0.4d;

    /** The size a tag pops in from, as a fraction of its full size. */
    private static final float POP_FROM = 0.6f;

    private static final String GOD_APPLE = "minecraft:enchanted_golden_apple";

    /**
     * Main hand, then armour from the head down, then off hand: the order a
     * glance reads a loadout in, and Leapfrog's.
     */
    private static final Equipment.Slot[] DISPLAY_ORDER = {
            Equipment.Slot.MAIN_HAND,
            Equipment.Slot.HEAD,
            Equipment.Slot.CHEST,
            Equipment.Slot.LEGS,
            Equipment.Slot.FEET,
            Equipment.Slot.OFF_HAND
    };

    private static final Color NAME = Color.WHITE;
    private static final Color FRIEND = Color.rgb(0x00AAFF);
    private static final Color INVISIBLE = Color.rgb(0xFF2500);
    private static final Color SNEAKING = Color.rgb(0xFF9900);
    private static final Color DETAIL = Color.GRAY;
    private static final Color ENCHANT = Color.WHITE;
    private static final Color CURSE = Color.rgb(0xFF0000);
    private static final Color GOD = Color.rgb(0xC34E41);

    private final NumberSetting<Integer> distance = integer("Distance", 20, 5, 100)
            .describe("How far away a player can be and still get a tag, in blocks");
    private final NumberSetting<Float> scale = number("Scale", 1f, 0.1f, 3f).step(0.1f);
    private final BooleanSetting distanceScaling = bool("Distance Scaling", true)
            .describe("Shrink tags with distance, like the world does, down to a readable minimum");
    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop tags in when a player comes into range and fade them out when they leave");

    // ----------------------------------------------------------------- info

    private final BooleanSetting showName = bool("Show Name", true);
    private final BooleanSetting showPing = bool("Show Ping", true);
    private final BooleanSetting showHealth = bool("Show Health", true)
            .describe("Health plus absorption, coloured from green to red");
    private final BooleanSetting showGameMode = bool("Show GameMode", false);

    private final GroupSetting info = group("Info", true)
            .containing(showName, showPing, showHealth, showGameMode);

    // ---------------------------------------------------------------- items

    private final BooleanSetting showArmor = bool("Show Armor", true)
            .describe("Armour and both hands, above the name");
    private final BooleanSetting showDurability = bool("Show Durability", true)
            .visibleWhen(showArmor);
    private final BooleanSetting showEnchants = bool("Show Enchantments", true)
            .visibleWhen(showArmor);

    private final GroupSetting items = group("Items", true)
            .containing(showArmor, showDurability, showEnchants);

    private final NameTagPainter painter = new NameTagPainter();
    private final PresenceTracker<Integer, NameTag> tracker = PresenceTracker.popIn();

    /**
     * Hides the vanilla label for anyone this module tags, so the two do not
     * stack &mdash; including while a tag is still fading out. Players out of
     * range keep theirs, and so does everything else with a name.
     */
    @Subscribe
    private void onNameTag(NameTagEvent event) {
        if (event.isPlayer() && (inRange(event.getPosition()) || tracker.has(event.getEntityId()))) {
            event.cancel();
        }
    }

    /**
     * Draws a frame of tags.
     *
     * <p>On the world overlay layer, so an open inventory, chat box or menu covers
     * the tags the way it covers the world. Players a little past the range are
     * still looked up, so a tag fading out keeps following its player rather than
     * freezing where they crossed the line.
     */
    @Subscribe
    private void onWorldOverlay(WorldOverlayEvent event) {
        tracker.setAnimated(animations.isOn());
        tracker.begin();
        for (GameEntity entity : Entities.nearby(event.getPartialTicks(), distance.getInt() * LINGER_RANGE)) {
            if (entity.getKind() != GameEntity.Kind.PLAYER) {
                continue;
            }
            boolean inRange = inRange(entity.getPosition());
            if (inRange || tracker.has(entity.getId())) {
                tracker.report(entity.getId(), inRange, new NameTag(entity, infoLine(entity), itemRow(entity)));
            }
        }
        tracker.end();

        Vec3 camera = Render.cameraPosition();
        for (PresenceTracker.Entry<NameTag> tag : farthestFirst(camera)) {
            paint(tag, camera);
        }
    }

    /** @return every live tag, farthest first, so nearer tags are drawn over farther ones */
    private List<PresenceTracker.Entry<NameTag>> farthestFirst(Vec3 camera) {
        List<PresenceTracker.Entry<NameTag>> tags = tracker.entries();
        tags.sort(Comparator.comparingDouble(
                (PresenceTracker.Entry<NameTag> tag) -> tag.value().entity().getPosition().squaredDistanceTo(camera))
                .reversed());
        return tags;
    }

    private void paint(PresenceTracker.Entry<NameTag> tag, Vec3 camera) {
        NameTag content = tag.value();
        GameEntity entity = content.entity();
        Vec3 head = entity.getPosition().add(0d, entity.getHeight() + HEAD_CLEARANCE, 0d);
        Vec2 anchor = Render.worldToScreen(head);
        if (anchor == null) {
            // Behind the camera.
            return;
        }
        float presence = tag.presence();
        float alpha = tag.alpha();
        // Grows from POP_FROM to full size as it appears; BACK_OUT's overshoot past
        // 1 is what gives the little bounce at the end.
        float pop = POP_FROM + (1f - POP_FROM) * presence;

        painter.paint((float) anchor.getX(), (float) anchor.getY(),
                sizeAt(camera.distanceTo(head)) * pop, alpha, content.info(), content.items());
    }

    /**
     * How big a tag is at a distance.
     *
     * <p>Full size up close, then shrinking with distance the way the world does,
     * down to a floor so a far tag stays readable. A tag of one fixed screen size
     * is what made distant players look pasted on: someone sixty blocks off had
     * the same size tag as someone beside you.
     */
    private float sizeAt(double cameraDistance) {
        float size = scale.getFloat();
        if (!distanceScaling.isOn()) {
            return size;
        }
        double factor = FULL_SIZE_DISTANCE / Math.max(cameraDistance, 0.001d);
        return size * (float) Math.max(MIN_DISTANCE_FACTOR, Math.min(1d, factor));
    }

    @Override
    protected void onDisable() {
        tracker.clear();
    }

    private boolean inRange(Vec3 position) {
        double range = distance.getInt();
        return position.squaredDistanceTo(Players.eyePosition()) <= range * range;
    }

    // ----------------------------------------------------------- info line

    private List<Segment> infoLine(GameEntity player) {
        List<Segment> line = new ArrayList<>(4);
        if (showName.isOn()) {
            line.add(new Segment(player.getName(), nameColor(player)));
        }
        if (showGameMode.isOn()) {
            PlayerList.GameMode mode = PlayerList.gameMode(player.getId());
            if (mode != null) {
                line.add(new Segment(" [" + gameModeLabel(mode) + "]", DETAIL));
            }
        }
        if (showPing.isOn()) {
            int ping = PlayerList.ping(player.getId());
            if (ping != PlayerList.UNKNOWN_PING) {
                line.add(new Segment(" " + ping + "ms", DETAIL));
            }
        }
        if (showHealth.isOn()) {
            float total = player.getHealth() + player.getAbsorption();
            line.add(new Segment(String.format(Locale.ROOT, " %.1f", total),
                    ColorUtil.health(player.getHealth(), player.getMaxHealth())));
        }
        return line;
    }

    /** Friend beats invisible beats sneaking: who someone is matters most. */
    private static Color nameColor(GameEntity player) {
        if (player.isFriend()) {
            return FRIEND;
        }
        if (player.isInvisible()) {
            return INVISIBLE;
        }
        return player.isSneaking() ? SNEAKING : NAME;
    }

    private static String gameModeLabel(PlayerList.GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
            case SPECTATOR -> "SP";
        };
    }

    // ------------------------------------------------------------- item row

    /** The item row, in {@link #DISPLAY_ORDER}, skipping empty slots. */
    private List<ItemColumn> itemRow(GameEntity player) {
        if (!showArmor.isOn()) {
            return List.of();
        }
        Equipment equipment = Equipment.of(player.getId());
        List<ItemColumn> row = new ArrayList<>(6);
        for (Equipment.Slot slot : DISPLAY_ORDER) {
            GameItem item = equipment.get(slot);
            if (!item.isEmpty()) {
                row.add(new ItemColumn(item, notesFor(item)));
            }
        }
        return row;
    }

    /** Durability nearest the icon, then the enchantments stacked above it, first listed on top. */
    private List<Note> notesFor(GameItem item) {
        List<Note> notes = new ArrayList<>();
        if (showDurability.isOn() && item.isDamageable()) {
            float left = item.getDurabilityFraction();
            notes.add(new Note(Math.round(left * 100f) + "%", Color.hsb(left / 3f, 1f, 1f)));
        }
        if (showEnchants.isOn()) {
            if (item.getId().equals(GOD_APPLE)) {
                notes.add(new Note("God", GOD));
            }
            List<GameItem.Enchant> enchants = item.getEnchantments();
            for (int i = enchants.size() - 1; i >= 0; i--) {
                GameItem.Enchant enchant = enchants.get(i);
                notes.add(new Note(EnchantNames.shortName(enchant), enchant.isCurse() ? CURSE : ENCHANT));
            }
        }
        return notes;
    }
}
