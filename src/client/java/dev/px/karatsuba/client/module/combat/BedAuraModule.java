package dev.px.karatsuba.client.module.combat;

import dev.px.combat.bed.Bed;
import dev.px.combat.search.BedSearch;
import dev.px.combat.search.option.BedPlaceOption;
import dev.px.combat.search.option.BedUseOption;
import dev.px.combat.search.rule.Reach;
import dev.px.combat.search.rule.Thresholds;
import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.input.Bind;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.target.TargetSelector;
import dev.px.core.target.TargetSort;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.client.render.PlacementBox;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Places beds where they hurt an enemy most and sets them off, where beds
 * explode: the Nether, the End, anywhere a bed does not work.
 *
 * <p>Built like {@link CrystalAuraModule}, on the same engine: the combat
 * library's {@code BedSearch} decides what is worth doing &mdash; which bed to set
 * off, and where to place one and which way it faces &mdash; by damage to the
 * target against damage to you and your friends. {@code Services.actions()}
 * decides whether and how it goes out.
 *
 * <h2>Two clicks, two ticks</h2>
 * A bed takes a right-click to place and another to set off, and the action
 * service sends one right-click a tick, as a player can. So a bed is placed on
 * one tick and set off on a later one: each tick this uses a bed already
 * standing if one is worth it, and otherwise places one. The search finds a
 * placed bed by the block itself, so it never has to be told.
 *
 * <h2>Facing</h2>
 * The game lays a bed out ahead of you, from the yaw the server has. The search
 * chooses a click that faces it the right way when looked along, and the action
 * waits until the server's yaw is in that direction, so a bed is never placed
 * turned the wrong way into cells it was not checked for.
 *
 * <h2>Where</h2>
 * In the Overworld beds work, so the search finds nothing and this does nothing.
 *
 * <h2>Sneaking</h2>
 * Do not sneak while it runs: sneaking with an item in hand places against a
 * bed instead of using it.
 */
@ModuleInfo(name = "Bed Aura", description = "Places and explodes beds where they do not work",
        category = "Combat")
public final class BedAuraModule extends Module {

    /** Every bed, by colour; any of them will do. */
    private static final String[] BEDS = {
            "white_bed", "orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed", "lime_bed",
            "pink_bed", "gray_bed", "light_gray_bed", "cyan_bed", "purple_bed", "blue_bed",
            "brown_bed", "green_bed", "red_bed", "black_bed"
    };

    /** A bed's collision height: what the drawn box is. */
    private static final double BED_HEIGHT = 0.5625d;

    // ---------------------------------------------------------------- targets

    private final NumberSetting<Double> targetRange = decimal("Target Range", 10d, 4d, 16d)
            .step(0.5d)
            .describe("How far away a player can be and still be targeted");

    private final EnumSetting<CrystalAuraModule.Priority> priority =
            enumOf("Priority", CrystalAuraModule.Priority.CLOSEST)
                    .describe("Which player to go for first");

    private final BooleanSetting predict = bool("Predict", true)
            .describe("Score damage where the target will be when the bed goes off");

    private final GroupSetting targeting = group("Targeting", true)
            .containing(targetRange, priority, predict);

    // ------------------------------------------------------------------ place

    private final BooleanSetting place = bool("Place", true);

    private final NumberSetting<Double> placeRange = decimal("Place Range", 4.5d, 1d, 6d)
            .step(0.1d)
            .describe("To the click into the foot's cell; the server's block reach caps it")
            .visibleWhen(place);

    private final NumberSetting<Integer> placeDelay = integer("Place Delay", 0, 0, 10)
            .describe("Ticks between this module's placements")
            .visibleWhen(place);

    private final GroupSetting placing = group("Placing", true)
            .containing(place, placeRange, placeDelay);

    // -------------------------------------------------------------------- use

    private final BooleanSetting use = bool("Explode", true)
            .describe("Set off beds standing in reach");

    private final NumberSetting<Double> useRange = decimal("Explode Range", 4.5d, 1d, 6d)
            .step(0.1d)
            .describe("To the half clicked; the server's block reach caps it")
            .visibleWhen(use);

    private final NumberSetting<Integer> useDelay = integer("Explode Delay", 0, 0, 10)
            .describe("Ticks between this module's explosions")
            .visibleWhen(use);

    private final NumberSetting<Integer> inhibit = integer("Inhibit", 5, 0, 20)
            .describe("Ticks to leave a bed alone after using it, while the server answers")
            .visibleWhen(use);

    private final GroupSetting exploding = group("Exploding", true)
            .containing(use, useRange, useDelay, inhibit);

    // ----------------------------------------------------------------- damage

    private final NumberSetting<Double> minDamage = decimal("Min Damage", 6d, 0d, 36d)
            .step(0.5d)
            .describe("Least damage to the target worth a bed");

    private final NumberSetting<Double> maxSelf = decimal("Max Self Damage", 8d, 0d, 36d)
            .step(0.5d)
            .describe("Most damage a bed may do to you");

    private final NumberSetting<Double> antiSuicide = decimal("Anti Suicide", 2d, 0d, 10d)
            .step(0.5d)
            .describe("Never leave yourself within this much health; 0 is off");

    private final NumberSetting<Double> lethal = decimal("Lethal Multiplier", 1d, 0d, 3d)
            .step(0.1d)
            .describe("A bed that kills at this many times its damage needs no minimum; 0 is off");

    private final BooleanSetting lethalOverrides = bool("Lethal Overrides", false)
            .describe("A killing bed may break the self-damage cap (anti suicide still applies)");

    private final NumberSetting<Double> faceHealth = decimal("Face Place Health", 8d, 0d, 36d)
            .step(0.5d)
            .describe("At or below this health, place for less damage; 0 is off");

    private final NumberSetting<Double> faceDamage = decimal("Face Place Damage", 2d, 0d, 10d)
            .step(0.5d)
            .describe("The minimum while face placing or breaking armour");

    private final BindSetting faceKey = bind("Face Place Key")
            .describe("Hold to face place whatever their health");

    private final NumberSetting<Double> armourBreak = decimal("Armour Break", 15d, 0d, 100d)
            .step(1d)
            .describe("Face place when their most worn armour is at or below this percent; 0 is off");

    private final NumberSetting<Double> friendDamage = decimal("Max Friend Damage", 6d, 0d, 36d)
            .step(0.5d)
            .describe("Most damage a bed may do to any friend in the blast");

    private final GroupSetting damage = group("Damage", false)
            .containing(minDamage, maxSelf, antiSuicide, lethal, lethalOverrides, faceHealth, faceDamage,
                    faceKey, armourBreak, friendDamage);

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the bed being placed");

    private final ColorSetting renderColor = color("Render Colour", Color.of(255, 85, 85, 200))
            .visibleWhen(render);

    private final BooleanSetting fill = bool("Fill", true)
            .visibleWhen(render);

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.3f, 0.05f, 1f)
            .step(0.05f)
            .describe("The fill's opacity, as a share of the colour's")
            .visibleWhen(() -> render.isOn() && fill.isOn());

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 0.5f, 4f)
            .step(0.1f)
            .visibleWhen(render);

    private final NumberSetting<Integer> linger = integer("Linger", 10, 1, 40)
            .describe("Ticks the box stays after the last placement before fading out")
            .visibleWhen(render);

    private final BooleanSetting animations = bool("Animations", true)
            .describe("Pop the box in when placing starts and fade it out when it stops")
            .visibleWhen(render);

    private final BooleanSetting interpolate = bool("Interpolate", true)
            .describe("Slide the box to a new bed instead of fading one out and another in")
            .visibleWhen(render);

    private final NumberSetting<Integer> moveTime = integer("Move Time", 150, 50, 500)
            .step(10)
            .describe("Milliseconds the slide to a new bed takes")
            .visibleWhen(() -> render.isOn() && interpolate.isOn());

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, linger, animations, interpolate,
                    moveTime);

    // ------------------------------------------------------------------ state

    private CombatKit<?> kit;
    private BedSearch<?> search;

    private long tick;
    private long lastPlace = Long.MIN_VALUE / 2;
    private long lastUse = Long.MIN_VALUE / 2;

    /** The bed last placed, and the tick it was. */
    private Bed placedBed;
    private long placedAt;

    private final PlacementBox placement = new PlacementBox();

    private String display = "";

    /** Whether the face-place key is held down. */
    private boolean faceHeld;

    @Override
    protected void onEnable() {
        kit = Combat.kit();
        search = build(kit);
        reset();
    }

    @Override
    protected void onDisable() {
        if (search != null) {
            search.close();
        }
        search = null;
        reset();
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        tick++;
        updateBox();
        if (search == null || !Players.present()) {
            display = "";
            return;
        }
        if (!kit.bedRules().explodes()) {
            display = "Beds work here";
            return;
        }

        // A standing bed first: it is the second half of one already placed, or a gift.
        BedUseOption<?> standing = use.isOn() && tick - lastUse >= useDelay.getInt() ? search.findUse() : null;
        if (standing != null && standing.getClick() != null) {
            Bed bed = standing.getBed();
            Services.actions().submit(this, Action.click(standing.getClick())
                    .anyFace()
                    .priority(ClaimPriority.HIGH)
                    .then(outcome -> {
                        if (outcome == Outcome.SENT && search != null) {
                            search.used(bed);
                            lastUse = tick;
                        }
                    }));
            display = describe(standing.getTarget(), standing.getDamage());
            return;
        }

        String item = bed();
        if (item == null) {
            display = "No beds";
            return;
        }
        BedPlaceOption<?> spot = place.isOn() && tick - lastPlace >= placeDelay.getInt() ? search.findPlace() : null;
        if (spot == null || spot.getClick() == null) {
            display = "";
            return;
        }
        Bed bed = spot.getBed();
        Services.actions().submit(this, Action.click(spot.getClick())
                .holding(item)
                .facing(spot.getFacing())
                .priority(ClaimPriority.HIGH)
                .then(outcome -> {
                    if (outcome == Outcome.SENT) {
                        lastPlace = tick;
                        placedBed = bed;
                        placedAt = tick;
                    }
                }));
        display = describe(spot.getTarget(), spot.getDamage());
    }

    /** Core reports presses and releases, not what is held, so the face-place key is followed here. */
    @Subscribe
    private void onKey(KeyEvent event) {
        Bind bind = faceKey.get();
        if (bind.isMouse() || !bind.isBound()) {
            return;
        }
        if (event.isPressed() && bind.matches(event.getKey(), event.getModifiers())) {
            faceHeld = true;
        } else if (event.isReleased() && event.getKey() == bind.getKey()) {
            faceHeld = false;
        }
    }

    @Subscribe
    private void onMouse(MouseEvent event) {
        Bind bind = faceKey.get();
        if (!bind.isMouse()) {
            return;
        }
        if (event.isPressed() && bind.matches(event.getButton(), event.getModifiers())) {
            faceHeld = true;
        } else if (event.isReleased() && event.getButton() == bind.getButton()) {
            faceHeld = false;
        }
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        reset();
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        placement.draw(renderColor.resolve(), fill.isOn(), fillOpacity.getFloat(), thickness.getFloat());
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    // ---------------------------------------------------------------- render

    /** The bed last placed, both halves, for as long as it lingers. */
    private void updateBox() {
        boolean showing = render.isOn() && placedBed != null && tick - placedAt <= linger.getInt();
        Box target = null;
        if (showing) {
            Vec3i foot = placedBed.getFoot();
            Vec3i head = placedBed.getHead();
            target = Box.of(Math.min(foot.getX(), head.getX()), foot.getY(), Math.min(foot.getZ(), head.getZ()),
                    Math.max(foot.getX(), head.getX()) + 1d, foot.getY() + BED_HEIGHT,
                    Math.max(foot.getZ(), head.getZ()) + 1d);
        }
        placement.update(target, animations.isOn(), interpolate.isOn(), moveTime.getInt());
    }

    // ---------------------------------------------------------------- search

    /** The search, in the kit's entity type: {@code E} is only ever a type variable here. */
    private <E> BedSearch<E> build(CombatKit<E> kit) {
        TargetSort<E> sort = (entity, context) -> priority.get() == CrystalAuraModule.Priority.LOWEST_HEALTH
                ? kit.vitals().pool(entity.get())
                : context.squaredDistanceTo(entity);

        TargetSelector<E> enemies = TargetSelector.from(kit.players())
                .range(targetRange::getDouble)
                .where(entity -> !kit.isFriend(entity))
                .sort(sort)
                .build();

        // Wide enough to cover everywhere a bed in place range can reach.
        TargetSelector<E> friends = TargetSelector.from(kit.players())
                .range(() -> placeRange.getDouble() + kit.bedRules().getRange())
                .where(kit::isFriend)
                .build();

        Thresholds<E> thresholds = Thresholds.<E>builder()
                .minDamage(minDamage::getDouble)
                .maxSelfDamage(maxSelf::getDouble)
                .antiSuicide(antiSuicide::getDouble)
                .lethal(lethal::getDouble, lethalOverrides::isOn)
                .facePlace(faceHealth::getDouble, faceDamage::getDouble)
                .facePlaceWhen(() -> faceHeld)
                .armourBreak(kit::armourWear, armourBreak::getDouble, faceDamage::getDouble)
                .maxProtectedDamage(friendDamage::getDouble)
                .inhibit(inhibit::getInt)
                .build();

        return kit.bedSearch()
                .targets(Core.targets(), enemies)
                .protect(friends)
                .placeReach(Reach.of(() -> Math.min(placeRange.getDouble(), Players.blockReach()),
                        () -> Math.min(placeRange.getDouble(), Players.blockReach())))
                .useReach(Reach.of(() -> Math.min(useRange.getDouble(), Players.blockReach()),
                        () -> Math.min(useRange.getDouble(), Players.blockReach())))
                .clicks(kit.clicks(kit.strictStyle()))
                .thresholds(thresholds)
                // Placed now, set off on a later tick, then the server acts on it.
                .placeDelay(() -> predict.isOn() ? pingTicks() + useDelay.getInt() + 1 : 0)
                .useDelay(() -> predict.isOn() ? pingTicks() : 0)
                .build();
    }

    /** One-way latency in ticks, rounded up: how long until the server acts on what is sent now. */
    private static int pingTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 2d / 50d);
    }

    /** @return the first bed to hand, any colour; null when there is none */
    private static String bed() {
        for (String bed : BEDS) {
            if (Inventories.hotbarHas(bed) || Inventories.offhandHolds(bed)) {
                return bed;
            }
        }
        return null;
    }

    private String describe(Tracked<?> target, double damage) {
        return kit.name(target.get()) + " " + String.format("%.1f", damage);
    }

    private void reset() {
        tick = 0;
        lastPlace = Long.MIN_VALUE / 2;
        lastUse = Long.MIN_VALUE / 2;
        placedBed = null;
        placement.clear();
        display = "";
    }
}
