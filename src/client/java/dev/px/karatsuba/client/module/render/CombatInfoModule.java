package dev.px.karatsuba.client.module.render;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.PresenceTracker;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.combat.TotemPopEvent;
import dev.px.karatsuba.adapter.event.game.render.WorldOverlayEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.BlastResistance;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.module.render.combatinfo.CombatCard;
import dev.px.karatsuba.client.module.render.combatinfo.CombatCardPainter;
import dev.px.karatsuba.client.module.render.combatinfo.CombatPainter;
import dev.px.karatsuba.client.module.render.combatinfo.CombatTowerPainter;
import dev.px.karatsuba.client.render.ThemeColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A card beside each player you are fighting, at their midsection, with what
 * matters in a crystal fight: health and absorption, totems popped, whether they
 * hold a totem, what is in their hands, how worn their armour is, their effects,
 * and whether they are in a hole.
 *
 * <h2>Close range, on purpose</h2>
 * Name Tags is for anyone in sight; this is for the few people near enough to
 * fight, so its range is short and it shows the nearest {@code Max Players} only.
 * A card pops in as a player comes into range and fades out as they leave, the way
 * a name tag does, sliding out from their body as it appears.
 *
 * <h2>Tracking</h2>
 * The card's point is beside the player's midsection, on your right of them as you
 * look at them, and projected to the screen every frame from their interpolated
 * position. With {@code Smooth Tracking} the card follows that point with a little
 * easing rather than being pinned to it, so it glides after a player who strafes
 * or gets knocked back instead of jittering with every step. Health eases too, so
 * a crystal hit drains the bar rather than snapping it.
 *
 * <h2>What it knows</h2>
 * Everything comes from what the server tells every client: health, the items in
 * a player's hands and on their body (with counts and durability), their effects,
 * and their totem pops. Their inventory is not sent, so "Crystal 64" is what is in
 * their hands, not what they carry. Pops are counted from the moment this module
 * sees them, and reset when the player dies.
 */
@ModuleInfo(name = "Combat Info", description = "Crystal PvP stats beside the players you are fighting",
        category = "Render")
public final class CombatInfoModule extends ClientModule {

    /** How far beside the hitbox's edge the card sits, in blocks. */
    private static final double SIDE_GAP = 0.35d;

    /** How far the card slides out from the body as it pops in, in screen pixels. */
    private static final float SLIDE = 14f;

    private static final float POP_FROM = 0.7f;

    /** How long a pop's flash lasts. */
    private static final long POP_FLASH_MILLIS = 700L;

    private static final double FULL_SIZE_DISTANCE = 4d;
    private static final double MIN_DISTANCE_FACTOR = 0.55d;

    private static final Color FRIEND = Color.rgb(0x00AAFF);
    private static final Color GOOD = Color.rgb(0x5BE37A);
    private static final Color BAD = Color.rgb(0xFF5A5A);
    private static final Color WARN = Color.rgb(0xFFB040);
    private static final Color INFO = Color.rgb(0xC9C9C9);

    private final NumberSetting<Integer> range = integer("Range", 10, 3, 32)
            .describe("How close a player has to be for a card, in blocks");

    private final NumberSetting<Integer> maxPlayers = integer("Max Players", 3, 1, 8)
            .describe("Cards for at most this many, nearest first");

    private final BooleanSetting friends = bool("Friends", false)
            .describe("Cards for friends too");

    private final EnumSetting<Style> style = enumOf("Style", Style.CARD)
            .describe("CARD is wide and compact; TOWER is a tall column, one stat a line, in larger text");

    private final NumberSetting<Float> scale = number("Scale", 0.8f, 0.3f, 2f).step(0.05f);

    private final BooleanSetting smoothTracking = bool("Smooth Tracking", true)
            .describe("Ease the card after the player rather than pinning it to them");

    private final NumberSetting<Float> trackingSpeed = number("Tracking Speed", 16f, 4f, 40f).step(1f)
            .describe("How quickly the card catches up; higher follows tighter")
            .visibleWhen(smoothTracking);

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop cards in and out, and ease health changes");

    // ---------------------------------------------------------------- shown

    private final BooleanSetting hands = bool("Hands", true)
            .describe("Totem in the offhand (or not), and crystals, gapples, XP or pearls in hand");
    private final BooleanSetting armour = bool("Armour", true)
            .describe("Each piece's durability, and any slot left bare");
    private final BooleanSetting effects = bool("Effects", true)
            .describe("Strength, weakness, resistance, fire resistance and the rest that change a fight");
    private final BooleanSetting position = bool("Position", true)
            .describe("In a hole, surrounded, burrowed or open");

    private final GroupSetting shown = group("Shown", true)
            .containing(hands, armour, effects, position);

    // --------------------------------------------------------------- colours

    private final BooleanSetting themeColours = bool("Theme Colours", true)
            .describe("The outline and background in the client theme's colours");

    private final ColorSetting accentColor = color("Accent", Color.of(180, 60, 255, 255))
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting backgroundColor = color("Background", Color.of(14, 14, 18, 200));

    private final GroupSetting colours = group("Colours", false)
            .containing(themeColours, accentColor, backgroundColor);

    private final CombatPainter card = new CombatCardPainter();
    private final CombatPainter tower = new CombatTowerPainter();
    private final PresenceTracker<Integer, GameEntity> tracker = PresenceTracker.popIn();

    /** Where each card is drawn, eased toward where it should be. */
    private final Map<Integer, Vec2> anchors = new HashMap<>();

    /** Health as drawn, eased toward the real value. */
    private final Map<Integer, Float> shownHealth = new HashMap<>();

    /** Pops since each player last died, by name: a respawn keeps the name. */
    private final Map<String, Pops> pops = new HashMap<>();

    private long lastFrameNanos;

    @Override
    protected void onDisable() {
        tracker.clear();
        anchors.clear();
        shownHealth.clear();
        lastFrameNanos = 0L;
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        tracker.clear();
        anchors.clear();
        shownHealth.clear();
        pops.clear();
    }

    @Subscribe
    private void onPop(TotemPopEvent event) {
        if (!event.isSelf()) {
            Pops count = pops.computeIfAbsent(event.getName(), ignored -> new Pops());
            count.count++;
            count.lastPopMillis = System.currentTimeMillis();
        }
    }

    /** A player at zero health died: their pops start again. */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (pops.isEmpty() || !Players.present()) {
            return;
        }
        for (GameEntity entity : Entities.nearby(1f, 64d)) {
            if (entity.isPlayer() && entity.getHealth() <= 0f) {
                pops.remove(entity.getName());
            }
        }
    }

    // ---------------------------------------------------------------- frame

    @Subscribe
    private void onWorldOverlay(WorldOverlayEvent event) {
        float seconds = secondsSinceLastFrame();
        tracker.setAnimated(animations.isOn());
        tracker.begin();
        for (GameEntity player : candidates(event.getPartialTicks())) {
            boolean wanted = player.getPosition().distanceTo(Players.position()) <= range.getInt();
            if (wanted || tracker.has(player.getId())) {
                tracker.report(player.getId(), wanted, player);
            }
        }
        tracker.end();

        List<PresenceTracker.Entry<GameEntity>> cards = tracker.entries();
        // Forget the eased state of anyone whose card has gone.
        anchors.keySet().removeIf(id -> !tracker.has(id));
        shownHealth.keySet().removeIf(id -> !tracker.has(id));

        Vec3 camera = Render.cameraPosition();
        cards.sort(Comparator.comparingDouble(
                (PresenceTracker.Entry<GameEntity> card) -> card.value().getPosition().squaredDistanceTo(camera))
                .reversed());
        Color accent = ThemeColors.of(accentColor, themeColours.isOn(), 0f);
        Color background = themeColours.isOn()
                ? Core.themes().getSurface().withAlpha(backgroundColor.get().getAlpha())
                : backgroundColor.resolve();
        for (PresenceTracker.Entry<GameEntity> card : cards) {
            paint(card, camera, seconds, accent, background);
        }
    }

    /** The players who may get a card: the nearest {@code Max Players}, a little past range so fading ones keep following. */
    private List<GameEntity> candidates(float partialTicks) {
        Vec3 feet = Players.position();
        List<GameEntity> players = new ArrayList<>();
        for (GameEntity entity : Entities.nearby(partialTicks, range.getInt() + 4d)) {
            if (entity.getKind() == GameEntity.Kind.PLAYER && entity.getId() != Players.entityId()
                    && (friends.isOn() || !entity.isFriend())) {
                players.add(entity);
            }
        }
        players.sort(Comparator.comparingDouble(entity -> entity.getPosition().distanceTo(feet)));
        return players.size() > maxPlayers.getInt() ? players.subList(0, maxPlayers.getInt()) : players;
    }

    private void paint(PresenceTracker.Entry<GameEntity> card, Vec3 camera, float seconds,
                       Color accent, Color background) {
        GameEntity player = card.value();
        Vec2 target = Render.worldToScreen(sidePoint(player));
        if (target == null) {
            // Behind the camera; start fresh when it comes back rather than sliding in from wherever it was.
            anchors.remove(player.getId());
            return;
        }
        Vec2 anchor = track(player.getId(), target, seconds);

        float presence = card.presence();
        float pop = POP_FROM + (1f - POP_FROM) * presence;
        float size = sizeAt(camera.distanceTo(player.getPosition())) * pop;
        float slide = (1f - Math.min(1f, presence)) * SLIDE;

        CombatCard content = cardFor(player, seconds);
        (style.get() == Style.TOWER ? tower : this.card).paint((float) anchor.getX() - slide, (float) anchor.getY(), size, card.alpha(), content,
                accent, background);
    }

    /**
     * The point the card hangs from: halfway up the player, just past their right
     * edge as you see them. "Right" is your camera's right, so the card stays on
     * the same side of the screen however they turn.
     */
    private static Vec3 sidePoint(GameEntity player) {
        double yaw = Math.toRadians(Players.rotation().getYaw());
        // The camera's right in the game's axes: yaw 0 faces +Z, so right is -X.
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double out = player.getWidth() / 2d + SIDE_GAP;
        return player.getPosition().add(rightX * out, player.getHeight() * 0.5d, rightZ * out);
    }

    /** Eases the card's screen point toward its target, framerate-independent. */
    private Vec2 track(int id, Vec2 target, float seconds) {
        Vec2 current = anchors.get(id);
        if (current == null || !smoothTracking.isOn() || seconds <= 0f) {
            anchors.put(id, target);
            return target;
        }
        double follow = 1d - Math.exp(-trackingSpeed.getFloat() * seconds);
        Vec2 eased = Vec2.of(current.getX() + (target.getX() - current.getX()) * follow,
                current.getY() + (target.getY() - current.getY()) * follow);
        anchors.put(id, eased);
        return eased;
    }

    private float sizeAt(double distance) {
        double factor = FULL_SIZE_DISTANCE / Math.max(distance, 0.001d);
        return scale.getFloat() * (float) Math.max(MIN_DISTANCE_FACTOR, Math.min(1d, factor));
    }

    // ---------------------------------------------------------------- content

    private CombatCard cardFor(GameEntity player, float seconds) {
        float health = easedHealth(player, seconds);
        Pops count = pops.get(player.getName());
        float flash = 0f;
        if (count != null) {
            long since = System.currentTimeMillis() - count.lastPopMillis;
            flash = since < POP_FLASH_MILLIS ? 1f - since / (float) POP_FLASH_MILLIS : 0f;
        }
        Equipment equipment = Equipment.of(player.getId());
        return new CombatCard(
                player.getName(),
                player.isFriend() ? FRIEND : Color.WHITE,
                PlayerList.ping(player.getId()),
                health, player.getMaxHealth(), player.getAbsorption(),
                count == null ? 0 : count.count, flash,
                hands.isOn() ? handChips(player, equipment) : null,
                armour.isOn() ? armourPieces(equipment) : null,
                effects.isOn() ? effectChips(player) : null,
                position.isOn() ? positionChip(player) : null,
                player.getPosition().distanceTo(Players.position()));
    }

    private float easedHealth(GameEntity player, float seconds) {
        float real = player.getHealth();
        Float shown = shownHealth.get(player.getId());
        if (shown == null || !animations.isOn() || seconds <= 0f) {
            shownHealth.put(player.getId(), real);
            return real;
        }
        float eased = shown + (real - shown) * (float) (1d - Math.exp(-10d * seconds));
        if (Math.abs(eased - real) < 0.05f) {
            eased = real;
        }
        shownHealth.put(player.getId(), eased);
        return eased;
    }

    /** The offhand first (a totem or its absence is the headline), then the main hand if it matters. */
    private static List<CombatCard.Chip> handChips(GameEntity player, Equipment equipment) {
        List<CombatCard.Chip> chips = new ArrayList<>(3);
        GameItem offhand = equipment.get(Equipment.Slot.OFF_HAND);
        GameItem main = equipment.get(Equipment.Slot.MAIN_HAND);
        boolean totem = isTotem(offhand) || isTotem(main);
        chips.add(totem ? new CombatCard.Chip("Totem", GOOD) : new CombatCard.Chip("No Totem", BAD));
        for (GameItem item : new GameItem[]{offhand, main}) {
            CombatCard.Chip chip = itemChip(item);
            if (chip != null) {
                chips.add(chip);
            }
        }
        String using = Entities.usingItem(player.getId());
        if (using.endsWith("golden_apple")) {
            chips.add(new CombatCard.Chip("Eating", WARN));
        }
        return chips;
    }

    /** @return a chip for an item that matters in a crystal fight, with its count; null for anything else */
    private static CombatCard.Chip itemChip(GameItem item) {
        if (item.isEmpty()) {
            return null;
        }
        String id = item.getId();
        String label = switch (id) {
            case "minecraft:end_crystal" -> "Crystal";
            case "minecraft:enchanted_golden_apple" -> "God Apple";
            case "minecraft:golden_apple" -> "Gapple";
            case "minecraft:experience_bottle" -> "XP";
            case "minecraft:ender_pearl" -> "Pearl";
            case "minecraft:obsidian" -> "Obsidian";
            case "minecraft:respawn_anchor" -> "Anchor";
            case "minecraft:glowstone" -> "Glowstone";
            default -> id.endsWith("_bed") ? "Bed" : null;
        };
        if (label == null) {
            return null;
        }
        return new CombatCard.Chip(item.getCount() > 1 ? label + " " + item.getCount() : label, INFO);
    }

    private static boolean isTotem(GameItem item) {
        return item.getId().equals("minecraft:totem_of_undying");
    }

    private static List<CombatCard.Piece> armourPieces(Equipment equipment) {
        Equipment.Slot[] order = {Equipment.Slot.HEAD, Equipment.Slot.CHEST, Equipment.Slot.LEGS, Equipment.Slot.FEET};
        List<CombatCard.Piece> pieces = new ArrayList<>(4);
        for (Equipment.Slot slot : order) {
            GameItem item = equipment.get(slot);
            pieces.add(new CombatCard.Piece(item.getDurabilityFraction(), item.isEmpty()));
        }
        return pieces;
    }

    /** Only the effects that change a crystal fight, abbreviated; anything else is noise on a small card. */
    private static List<CombatCard.Chip> effectChips(GameEntity player) {
        List<CombatCard.Chip> chips = new ArrayList<>();
        for (Entities.Effect effect : Entities.effectsOf(player.getId())) {
            String label = switch (effect.id()) {
                case "minecraft:strength" -> "Str";
                case "minecraft:weakness" -> "Weak";
                case "minecraft:resistance" -> "Res";
                case "minecraft:fire_resistance" -> "Fire";
                case "minecraft:regeneration" -> "Regen";
                case "minecraft:absorption" -> "Abs";
                case "minecraft:speed" -> "Speed";
                case "minecraft:slowness" -> "Slow";
                case "minecraft:jump_boost" -> "Jump";
                case "minecraft:slow_falling" -> "Fall";
                case "minecraft:invisibility" -> "Invis";
                default -> null;
            };
            if (label == null) {
                continue;
            }
            String level = effect.level() > 1 ? " " + roman(effect.level()) : "";
            String time = effect.infinite() ? "" : " " + clock(effect.ticksLeft());
            chips.add(new CombatCard.Chip(label + level + time, effect.beneficial() ? GOOD : BAD));
        }
        return chips;
    }

    /**
     * Where the player stands: burrowed (inside a blast-proof block), in a hole
     * (four blast-proof walls and floor), surrounded by some, or open.
     */
    private static CombatCard.Chip positionChip(GameEntity player) {
        Vec3 at = player.getPosition();
        Vec3i feet = Vec3i.of((int) Math.floor(at.getX()), (int) Math.floor(at.getY() + 0.2d),
                (int) Math.floor(at.getZ()));
        if (Blocks.blastResistance(feet) != BlastResistance.NONE) {
            return new CombatCard.Chip("Burrowed", WARN);
        }
        int walls = 0;
        boolean bedrock = true;
        for (Vec3i side : new Vec3i[]{feet.add(1, 0, 0), feet.add(-1, 0, 0), feet.add(0, 0, 1), feet.add(0, 0, -1)}) {
            BlastResistance wall = Blocks.blastResistance(side);
            if (wall != BlastResistance.NONE) {
                walls++;
            }
            bedrock &= wall == BlastResistance.UNBREAKABLE;
        }
        BlastResistance floor = Blocks.blastResistance(feet.add(0, -1, 0));
        if (walls == 4 && floor != BlastResistance.NONE) {
            return new CombatCard.Chip(bedrock && floor == BlastResistance.UNBREAKABLE ? "Bedrock Hole" : "Hole", GOOD);
        }
        if (walls == 0) {
            return new CombatCard.Chip("Open", BAD);
        }
        return new CombatCard.Chip("Surround " + walls + "/4", WARN);
    }

    private static String clock(int ticks) {
        int seconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    private float secondsSinceLastFrame() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        return previous == 0L ? 0f : (float) Math.min(0.1d, (now - previous) / 1.0e9d);
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(tracker.size());
    }

    /** How a card looks. Both show the same things, tracked the same way. */
    public enum Style {
        /** Wide and compact: a stat grid and a row of pills. */
        CARD,
        /** A tall column beside the player: one thing a line, in larger text. */
        TOWER
    }

    private static final class Pops {
        int count;
        long lastPopMillis;
    }
}
