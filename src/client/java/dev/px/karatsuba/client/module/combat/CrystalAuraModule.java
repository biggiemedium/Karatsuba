package dev.px.karatsuba.client.module.combat;

import dev.px.combat.search.CrystalSearch;
import dev.px.combat.search.option.BreakOption;
import dev.px.combat.search.option.PlaceOption;
import dev.px.combat.search.rule.Reach;
import dev.px.combat.search.rule.ReachPoint;
import dev.px.combat.search.rule.Thresholds;
import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.input.Bind;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
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
import dev.px.karatsuba.client.module.combat.render.PlacementBox;
import dev.px.karatsuba.adapter.event.game.combat.CrystalSpawnEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Places end crystals where they hurt an enemy most and breaks them, built on
 * the combat library's crystal search and sent through {@code Services.actions()}.
 *
 * <h2>Who decides what</h2>
 * <ul>
 *   <li><b>The search</b> ({@code CombatKit.crystalSearch()}) decides <em>what is
 *       worth doing</em>: the best base to place on and the best crystal to break,
 *       by damage to the target against damage to you and your friends, with the
 *       game's own explosion maths. Every threshold below is read live.</li>
 *   <li><b>The action service</b> decides <em>whether and how</em>: it turns to the
 *       target, gets crystals into a hand, waits until the rotation the server has
 *       looks at it, checks reach, and sends it where a vanilla click goes.</li>
 *   <li>This module only joins them, and tells the search what was really sent:
 *       {@code placed} and {@code attacked} on {@link Outcome#SENT}, never
 *       before.</li>
 * </ul>
 *
 * <h2>Grim</h2>
 * Nothing here is unchecked. A place is a click on a face turned toward you, seen
 * along a clear line, in reach ({@code strictStyle}), and only once the server's
 * view of your head looks at it. A break is in your entity reach, measured to the
 * crystal's box. One place and one break a tick at most, and one rotation, so
 * when the two point different ways the break goes first and the place follows.
 * How strict the hands are is the {@code Interact} module's, shared by every
 * module; this one only says what it wants.
 *
 * <h2>Breaking on spawn</h2>
 * With {@code Instant} on, a crystal is judged the moment its spawn reaches the
 * client ({@link CrystalSpawnEvent}) rather than on the next tick, and the break
 * goes out at the end of the tick it arrives in.
 */
@ModuleInfo(name = "Crystal Aura", description = "Places and breaks end crystals", category = "Combat")
public final class CrystalAuraModule extends Module {

    private static final String CRYSTAL = "end_crystal";

    // ---------------------------------------------------------------- targets

    private final NumberSetting<Double> targetRange = decimal("Target Range", 10d, 4d, 16d)
            .step(0.5d)
            .describe("How far away a player can be and still be targeted");

    private final EnumSetting<Priority> priority = enumOf("Priority", Priority.CLOSEST)
            .describe("Which player to go for first");

    private final BooleanSetting predict = bool("Predict", true)
            .describe("Score damage where the target will be when the crystal goes off");

    private final GroupSetting targeting = group("Targeting", true)
            .containing(targetRange, priority, predict);

    // ------------------------------------------------------------------ place

    private final BooleanSetting place = bool("Place", true);

    private final NumberSetting<Double> placeRange = decimal("Place Range", 4.5d, 1d, 6d)
            .step(0.1d)
            .describe("To the click on the base; the server's block reach caps it")
            .visibleWhen(place);

    private final NumberSetting<Integer> placeDelay = integer("Place Delay", 0, 0, 10)
            .describe("Ticks between this module's placements")
            .visibleWhen(place);

    private final GroupSetting placing = group("Placing", true)
            .containing(place, placeRange, placeDelay);

    // ------------------------------------------------------------------ break

    private final BooleanSetting breaks = bool("Break", true);

    private final NumberSetting<Double> breakRange = decimal("Break Range", 3d, 1d, 6d)
            .step(0.1d)
            .describe("To the crystal's box; the server's entity reach caps it")
            .visibleWhen(breaks);

    private final NumberSetting<Integer> breakDelay = integer("Break Delay", 0, 0, 10)
            .describe("Ticks between this module's breaks")
            .visibleWhen(breaks);

    private final NumberSetting<Integer> minAge = integer("Min Age", 0, 0, 10)
            .describe("Ticks a crystal must exist before it is broken on a tick")
            .visibleWhen(breaks);

    private final NumberSetting<Integer> inhibit = integer("Inhibit", 5, 0, 20)
            .describe("Ticks to leave a crystal alone after hitting it, while the server answers")
            .visibleWhen(breaks);

    private final BooleanSetting instant = bool("Instant", true)
            .describe("Break a crystal the moment it spawns, not on the next tick")
            .visibleWhen(breaks);

    private final BooleanSetting ownOnly = bool("Own Only", false)
            .describe("Only break crystals this module placed")
            .visibleWhen(breaks);

    private final GroupSetting breaking = group("Breaking", true)
            .containing(breaks, breakRange, breakDelay, minAge, inhibit, instant, ownOnly);

    // ----------------------------------------------------------------- damage

    private final NumberSetting<Double> minDamage = decimal("Min Damage", 6d, 0d, 36d)
            .step(0.5d)
            .describe("Least damage to the target worth a crystal");

    private final NumberSetting<Double> maxSelf = decimal("Max Self Damage", 8d, 0d, 36d)
            .step(0.5d)
            .describe("Most damage a crystal may do to you");

    private final NumberSetting<Double> antiSuicide = decimal("Anti Suicide", 2d, 0d, 10d)
            .step(0.5d)
            .describe("Never leave yourself within this much health; 0 is off");

    private final NumberSetting<Double> lethal = decimal("Lethal Multiplier", 1d, 0d, 3d)
            .step(0.1d)
            .describe("A crystal that kills at this many times its damage needs no minimum; 0 is off");

    private final BooleanSetting lethalOverrides = bool("Lethal Overrides", false)
            .describe("A killing crystal may break the self-damage cap (anti suicide still applies)");

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
            .describe("Most damage a crystal may do to any friend in the blast");

    private final GroupSetting damage = group("Damage", false)
            .containing(minDamage, maxSelf, antiSuicide, lethal, lethalOverrides, faceHealth, faceDamage,
                    faceKey, armourBreak, friendDamage);

    // ----------------------------------------------------------------- render

    private final BooleanSetting render = bool("Render", true)
            .describe("Draw the base the aura is placing on");

    private final ColorSetting renderColor = color("Render Colour", Color.of(180, 60, 255, 200))
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
            .describe("Slide the box to a new base instead of fading one out and another in")
            .visibleWhen(render);

    private final NumberSetting<Integer> moveTime = integer("Move Time", 150, 50, 500)
            .step(10)
            .describe("Milliseconds the slide to a new base takes")
            .visibleWhen(() -> render.isOn() && interpolate.isOn());

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, linger, animations, interpolate,
                    moveTime);

    // ------------------------------------------------------------------ state

    private CombatKit<?> kit;
    private CrystalSearch<?> search;

    private long tick;
    private long lastPlace = Long.MIN_VALUE / 2;
    private long lastBreak = Long.MIN_VALUE / 2;

    /** The base being placed on, popped in, faded out and slid between bases. */
    private final PlacementBox placement = new PlacementBox();

    /** The base last placed on, and the tick it was. */
    private Vec3i placedOn;
    private long placedAt;

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
        updateBoxes();
        if (search == null || !Players.present()) {
            display = "";
            return;
        }
        if (!Inventories.hotbarHas(CRYSTAL) && !Inventories.offhandHolds(CRYSTAL)) {
            display = "No crystals";
            return;
        }

        BreakOption<?> hit = breaks.isOn() && tick - lastBreak >= breakDelay.getInt() ? search.findBreak() : null;
        if (hit != null && (!ownOnly.isOn() || hit.isOwn())) {
            submitBreak(hit);
        }

        PlaceOption<?> spot = place.isOn() && tick - lastPlace >= placeDelay.getInt() ? search.findPlace() : null;
        if (spot != null && spot.getClick() != null) {
            submitPlace(spot);
        }

        display = spot != null ? describe(spot.getTarget(), spot.getDamage())
                : hit != null ? describe(hit.getTarget(), hit.getDamage())
                : "";
    }

    /** Judged the moment it spawns: break it on this tick rather than the next. */
    @Subscribe
    private void onSpawn(CrystalSpawnEvent event) {
        if (search == null || !breaks.isOn() || !instant.isOn() || tick - lastBreak < breakDelay.getInt()) {
            return;
        }
        BreakOption<?> now = search.spawned(event.getCrystal());
        if (now != null && (!ownOnly.isOn() || now.isOwn())) {
            submitBreak(now);
        }
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

    /**
     * The tick's half of the drawing: which base is shown, and where a slide is
     * heading. The frame only reads what this decided.
     */
    private void updateBoxes() {
        boolean showing = render.isOn() && placedOn != null && tick - placedAt <= linger.getInt();
        Box target = showing
                ? Box.of(placedOn.getX(), placedOn.getY(), placedOn.getZ(),
                        placedOn.getX() + 1d, placedOn.getY() + 1d, placedOn.getZ() + 1d)
                : null;
        placement.update(target, animations.isOn(), interpolate.isOn(), moveTime.getInt());
    }

    @Override
    public String getDisplayInfo() {
        return display;
    }

    // ------------------------------------------------------------- actions

    private void submitBreak(BreakOption<?> option) {
        Tracked<?> crystal = option.getCrystal();
        int id = kit.id(crystal.get());
        if (id < 0) {
            return;
        }
        Services.actions().submit(this, Action.attack(id)
                .aimAt(option.getAim())
                .priority(ClaimPriority.HIGH)
                .then(outcome -> {
                    if (outcome == Outcome.SENT && search != null) {
                        search.attacked(crystal);
                        lastBreak = tick;
                    }
                }));
    }

    private void submitPlace(PlaceOption<?> option) {
        Services.actions().submit(this, Action.click(option.getClick())
                .holding(CRYSTAL)
                .anyFace()
                .priority(ClaimPriority.HIGH)
                .then(outcome -> {
                    if (outcome == Outcome.SENT && search != null) {
                        search.placed(option);
                        lastPlace = tick;
                        placedOn = Vec3i.of(option.getX(), option.getY(), option.getZ());
                        placedAt = tick;
                    }
                }));
    }

    // ---------------------------------------------------------------- search

    /**
     * The search, in the kit's entity type. Generic so this module never names
     * the game's class: {@code E} is only ever a type variable here.
     */
    private <E> CrystalSearch<E> build(CombatKit<E> kit) {
        TargetSort<E> sort = (entity, context) -> priority.get() == Priority.LOWEST_HEALTH
                ? kit.vitals().pool(entity.get())
                : context.squaredDistanceTo(entity);

        TargetSelector<E> enemies = TargetSelector.from(kit.players())
                .range(targetRange::getDouble)
                .where(entity -> !kit.isFriend(entity))
                .sort(sort)
                .build();

        // Wide enough to cover everywhere a crystal in place range can reach.
        TargetSelector<E> friends = TargetSelector.from(kit.players())
                .range(() -> placeRange.getDouble() + kit.crystalRules().getRange())
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
                .breakMinAge(minAge::getInt)
                .inhibit(inhibit::getInt)
                .build();

        return kit.crystalSearch()
                .targets(Core.targets(), enemies)
                .protect(friends)
                .placeReach(Reach.of(() -> Math.min(placeRange.getDouble(), Players.blockReach()),
                        () -> Math.min(placeRange.getDouble(), Players.blockReach())))
                .breakReach(Reach.of(() -> Math.min(breakRange.getDouble(), Players.entityReach()),
                        () -> Math.min(breakRange.getDouble(), Players.entityReach()))
                        .measuredTo(ReachPoint.NEAREST))
                .clicks(kit.clicks(kit.strictStyle()))
                .thresholds(thresholds)
                .placeDelay(() -> predict.isOn() ? pingTicks() + breakDelay.getInt() + 1 : 0)
                .useDelay(() -> predict.isOn() ? pingTicks() : 0)
                .build();
    }

    /** One-way latency in ticks, rounded up: how long until the server acts on what is sent now. */
    private static int pingTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 2d / 50d);
    }

    private String describe(Tracked<?> target, double damage) {
        return kit.name(target.get()) + " " + String.format("%.1f", damage);
    }

    private void reset() {
        tick = 0;
        lastPlace = Long.MIN_VALUE / 2;
        lastBreak = Long.MIN_VALUE / 2;
        placedOn = null;
        placement.clear();
        display = "";
    }

    public enum Priority {
        CLOSEST,
        LOWEST_HEALTH
    }
}
