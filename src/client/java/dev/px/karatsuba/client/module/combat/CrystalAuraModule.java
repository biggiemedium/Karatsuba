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
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.karatsuba.client.module.ClientModule;
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
import dev.px.karatsuba.adapter.event.game.combat.CrystalSpawnEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.ActionProfile;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

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
 * How strict the hands are is the Interaction section of Client Settings, shared by every
 * module; this one only says what it wants.
 *
 * <h2>Breaking on spawn</h2>
 * With {@code Instant} on, a crystal is judged the moment its spawn reaches the
 * client ({@link CrystalSpawnEvent}) rather than on the next tick, and the break
 * goes out at the start of the next tick, before anything else that tick
 * &mdash; if the rotation the server already has hits it. That is why the head
 * stays on a crystal still on its way while there is nothing else to do.
 *
 * <h2>Break and replace</h2>
 * A crystal worth breaking is broken together with its replacement: the place is
 * searched as if that crystal were already gone ({@code CombatKit.asIfGone}),
 * since the server handles the attack before the click. When the look at the
 * place's click meets the crystal well inside the reach, the attack aims there
 * too, so one rotation serves both and the two go out in one tick (with the
 * Interaction settings' Same Tick on). A place that needs the broken crystal gone
 * goes only behind that attack ({@code Action.afterAttack}). A crystal already
 * hit no longer blocks its base either. See {@code documentation/modules/CRYSTAL_AURA.md}.
 *
 * <h2>Hurt Sync</h2>
 * For 10 ticks after a hit lands in full, a player takes only what a new hit
 * beats it by. With {@code Hurt Sync} on, the search remembers our hits on each
 * target ({@code hurtWindow}, from {@code kit.hurtCooldown()}) and judges a break
 * by what it would land, so a crystal that would land nothing is left standing
 * and broken the tick the window closes, the look already held on it. The next
 * crystal is not placed with that break but timed to arrive as the window closes,
 * aimed at where the target is then; one that has gone stale stops counting
 * toward Max Crystals. {@code Sync Margin} waits 11 ticks rather than 10, for a
 * hit that reaches the server a tick early. Off, only the one-explosion-a-tick
 * rule applies, as before.
 *
 * <h2>Max Crystals</h2>
 * How many crystals may be out, placed and not yet broken. At 1 the aura places,
 * waits for it, then breaks and replaces in one tick, rather than turning between
 * several bases while the first is on its way. While that many are out the place
 * search is skipped, which is most of a tick's work.
 */
@ModuleInfo(name = "Crystal Aura", description = "Places and breaks end crystals", category = "Combat")
public final class CrystalAuraModule extends ClientModule {

    private static final String CRYSTAL = "end_crystal";

    /** Ticks past the round trip a placed crystal is waited for, for jitter. */
    private static final int PENDING_SLACK = 3;

    /** Ticks a crystal that has shown up still counts toward Max Crystals while it waits to be broken. */
    private static final int BREAK_GRACE = 6;

    /** How far, in blocks, a crystal may stand from where one was placed and still be that one. */
    private static final double SAME_CRYSTAL = 0.5d;

    /**
     * Blocks inside the reach a shared look must meet the crystal by: room for the
     * look the server holds to differ a little from the one aimed (mouse rounding,
     * the move). A tuning knob.
     */
    private static final double AIM_MARGIN = 0.25d;

    /** How far inside a crystal's box a held look aims, so it lands inside rather than on an edge, as the action service does. */
    private static final double BODY_INSET = 0.1d;

    /** Closer than this, a held look aims at the box's centre instead: as {@code ActionService}'s own aim. */
    private static final double MIN_AIM_DISTANCE = 0.5d;

    /** Ticks the crystals-a-second figure is averaged over: three seconds. */
    private static final int RATE_WINDOW = 60;

    /** Ticks the last target is still shown after its last crystal: twenty seconds. */
    private static final int TARGET_MEMORY = 400;

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

    private final NumberSetting<Integer> maxCrystals = integer("Max Crystals", 1, 1, 3)
            .describe("Crystals out at once, placed and not yet broken; 1 places the next as the last is broken")
            .visibleWhen(place);

    private final GroupSetting placing = group("Placing", true)
            .containing(place, placeRange, placeDelay, maxCrystals);

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

    private final BooleanSetting hurtSync = bool("Hurt Sync", true)
            .describe("Time breaks to the target's hurt cooldown: for 10 ticks after a hit, a player only takes what"
                    + " a new hit beats it by, so a crystal is held and broken when it lands in full instead of wasted")
            .visibleWhen(breaks);

    private final BooleanSetting syncMargin = bool("Sync Margin", true)
            .describe("Wait 11 ticks instead of 10, so a hit that reaches the server a tick early to lag is never"
                    + " blocked by the cooldown; costs about 5% of the hit rate")
            .visibleWhen(() -> breaks.isOn() && hurtSync.isOn());

    private final GroupSetting breaking = group("Breaking", true)
            .containing(breaks, breakRange, breakDelay, minAge, inhibit, instant, ownOnly, hurtSync, syncMargin);

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

    private final NumberSetting<Integer> moveTime = integer("Move Time", 150, 50, 300)
            .step(10)
            .describe("Milliseconds the slide to a new base takes")
            .visibleWhen(() -> render.isOn() && interpolate.isOn());

    private final BooleanSetting showRate = bool("Show Crystals/s", true)
            .describe("Show the last target and crystals broken a second, instead of the damage");

    private final GroupSetting appearance = group("Appearance", false)
            .containing(render, renderColor, fill, fillOpacity, thickness, linger, animations, interpolate,
                    moveTime, showRate);

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

    /** Crystals placed and not yet broken, oldest first: what {@link #maxCrystals} counts. */
    private final List<Out> out = new ArrayList<>();

    private String display = "";

    /** Whether the last tick found no crystals to place with: said whatever the display shows. */
    private boolean noCrystals;

    /** The ticks crystals were broken on, within the last {@link #RATE_WINDOW}, oldest first. */
    private final ArrayDeque<Long> broken = new ArrayDeque<>();

    /** The target last acted against: whose hurt window Hurt Sync times the next crystal to. */
    private Tracked<?> watched;

    /** Who a crystal last went out against, and when; null before the first. */
    private String lastTarget;
    private long lastTargetAt;

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
        while (!broken.isEmpty() && tick - broken.peekFirst() >= RATE_WINDOW) {
            broken.pollFirst();
        }
        noCrystals = false;
        if (search == null || !Players.present()) {
            display = "";
            return;
        }
        if (!hasCrystals()) {
            noCrystals = true;
            display = "No crystals";
            return;
        }

        forgetOut();

        BreakOption<?> hit = null;
        boolean searched = breaks.isOn() && tick - lastBreak >= breakDelay.getInt();
        if (searched) {
            hit = search.findBreak();
        }
        if (hit != null && ownOnly.isOn() && !hit.isOwn()) {
            hit = null;
        }
        if (hit == null && searched) {
            forgetStale();
        }
        PlaceOption<?> spot = placeAfter(hit);
        if (hit != null) {
            submitBreak(hit, spot);
        }
        if (spot != null) {
            submitPlace(spot, hit);
        }
        if (hit == null && spot == null) {
            watchPending();
        }

        display = spot != null ? describe(spot.getTarget(), spot.getDamage())
                : hit != null ? describe(hit.getTarget(), hit.getDamage())
                : "";
    }

    /**
     * Judged the moment it spawns, and its replacement with it: both go out at the
     * start of the next tick, under the look that placed it.
     */
    @Subscribe
    private void onSpawn(CrystalSpawnEvent event) {
        if (search == null || !Players.present() || !breaks.isOn() || !instant.isOn()
                || tick - lastBreak < breakDelay.getInt()) {
            return;
        }
        BreakOption<?> now = search.spawned(event.getCrystal());
        if (now == null || (ownOnly.isOn() && !now.isOwn())) {
            return;
        }
        PlaceOption<?> spot = hasCrystals() ? placeAfter(now) : null;
        submitBreak(now, spot);
        if (spot != null) {
            submitPlace(spot, now);
        }
    }

    /**
     * Where to place this tick, if anywhere: with a break going out, searched as
     * if that crystal were already gone, so its base can take the next one at once.
     * Not searched at all while {@link #maxCrystals} are out, the crystal being
     * broken aside.
     */
    private PlaceOption<?> placeAfter(BreakOption<?> breaking) {
        if (!place.isOn() || tick - lastPlace < placeDelay.getInt()) {
            return null;
        }
        if (hurtSync.isOn() && waitToPlace(breaking)) {
            return null;
        }
        int counted = out.size();
        if (breaking != null && outAt(breaking.getCrystal().getPosition()) != null) {
            counted--;
        }
        if (counted >= maxCrystals.getInt()) {
            return null;
        }
        PlaceOption<?> spot = breaking == null ? search.findPlace()
                : kit.asIfGone(breaking.getCrystal().get(), search::findPlace);
        return spot != null && spot.getClick() != null ? spot : null;
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
        if (!showRate.isOn() || noCrystals) {
            return display;
        }
        if (lastTarget == null || tick - lastTargetAt > TARGET_MEMORY) {
            return "None";
        }
        double perSecond = broken.size() / (RATE_WINDOW / 20d);
        return lastTarget + " " + String.format("%.1f", perSecond);
    }

    // ------------------------------------------------------------- actions

    /**
     * Attacks a crystal. With a place going out too, the attack aims at the
     * place's click when that look also meets the crystal within reach: one
     * rotation then serves both, and the two go out in the same tick.
     */
    private void submitBreak(BreakOption<?> option, PlaceOption<?> then) {
        Tracked<?> crystal = option.getCrystal();
        int id = kit.id(crystal.get());
        if (id < 0) {
            return;
        }
        Services.actions().submit(this, Action.attack(id)
                .aimAt(breakAim(option, then))
                .priority(ClaimPriority.HIGH)
                .then(outcome -> {
                    if (outcome == Outcome.SENT && search != null) {
                        search.attacked(crystal);
                        lastBreak = tick;
                        out.remove(outAt(crystal.getPosition()));
                        broken.addLast(tick);
                        sentAgainst(option.getTarget());
                    }
                }));
    }

    /**
     * Places a crystal. On the base of the crystal being broken, it goes only
     * behind that attack.
     *
     * <p>The client's refusal of a place means nothing when all that stands in its
     * room is a crystal we already hit: the click went out regardless, and the
     * server, which handled the attack first, places it. The client just still
     * shows the dead crystal. Such a place is counted as placed, or it would be
     * placed again every tick until the removal arrives.
     */
    private void submitPlace(PlaceOption<?> option, BreakOption<?> breaking) {
        boolean behind = needsBreak(option, breaking);
        Action click = Action.click(option.getClick())
                .holding(CRYSTAL)
                .anyFace()
                .priority(ClaimPriority.HIGH);
        if (behind) {
            click.afterAttack();
        }
        Services.actions().submit(this, click
                .then(outcome -> {
                    // Attack callbacks run first, so a crystal hit this tick is already inhibited here.
                    boolean went = outcome == Outcome.SENT
                            || (outcome == Outcome.REFUSED && !kit.everything().any(roomOf(option)));
                    if (went && search != null) {
                        search.placed(option);
                        lastPlace = tick;
                        placedOn = Vec3i.of(option.getX(), option.getY(), option.getZ());
                        placedAt = tick;
                        out.add(new Out(kit.crystalRules().position(option.getX(), option.getY(), option.getZ()),
                                kit.crystalRules().box(option.getX(), option.getY(), option.getZ()),
                                option.getClick().getHit(), tick));
                        sentAgainst(option.getTarget());
                    }
                }));
    }

    /** Remembers who a crystal just went out against, for the display. */
    private void sentAgainst(Tracked<?> target) {
        lastTarget = kit.name(target.get());
        lastTargetAt = tick;
        watched = target;
    }

    /**
     * The place's click, when the look there also meets the crystal well inside
     * the reach; otherwise null, and the action service aims at the point of the
     * crystal's box nearest the eyes. Never the crystal's centre: at the edge of
     * reach a look at the centre meets the box only just inside it, or past it,
     * and the attack waits on a look that never lands.
     */
    private Vec3 breakAim(BreakOption<?> option, PlaceOption<?> then) {
        return then == null ? null : sharedLook(option.getCrystal().getBox(), then.getClick().getHit());
    }

    /**
     * @return {@code click} when a look at it meets {@code crystal} at least
     *         {@link #AIM_MARGIN} inside the break reach, so one look serves the
     *         attack and the click; null when it does not
     */
    private Vec3 sharedLook(Box crystal, Vec3 click) {
        // From where the server will hold the look: the eyes after this tick's move.
        Vec3 eye = Players.eyePosition().add(Players.velocity());
        double along = crystal.clip(eye, click);
        double reach = Math.min(breakRange.getDouble(), Players.entityReach()) - AIM_MARGIN;
        return !Double.isNaN(along) && along * eye.distanceTo(click) <= reach ? click : null;
    }

    /** Whether placing on this base needs the crystal being broken out of the way first. */
    private static boolean needsBreak(PlaceOption<?> spot, BreakOption<?> breaking) {
        return breaking != null && roomOf(spot).intersects(breaking.getCrystal().getBox());
    }

    /** The room the game wants empty to place on this base: its footprint, two blocks up from its top (EndCrystalItem). */
    private static Box roomOf(PlaceOption<?> spot) {
        return Box.of(spot.getX(), spot.getY() + 1, spot.getZ(), spot.getX() + 1, spot.getY() + 3, spot.getZ() + 1);
    }

    /**
     * Keeps the head on the newest crystal still on its way, while there is
     * nothing to do, so the server's look already hits it when it spawns and the
     * instant break needs no turn. On the place's click when that look meets the
     * crystal well inside the reach, so its replacement needs none either;
     * otherwise on the point of the crystal's box nearest the eyes, since the
     * break matters more.
     */
    private void watchPending() {
        ActionProfile profile = Services.actions().getProfile();
        if (out.isEmpty() || !profile.rotate()) {
            return;
        }
        // Held while it counts, not just while it is on its way: under Hurt Sync a
        // crystal that has shown up waits for the cooldown, and the break that
        // ends the wait needs no turn either.
        Out newest = out.get(out.size() - 1);
        Vec3 eye = Players.eyePosition().add(Players.velocity());
        Vec3 shared = sharedLook(newest.body, newest.click);
        Vec3 aim = shared != null ? shared : newest.body.inset(BODY_INSET).closestPoint(eye);
        if (aim.distanceTo(eye) < MIN_AIM_DISTANCE) {
            // The eyes are in the box: the nearest point is the eyes, no direction at all
            // (log 5 held (-990, 0) and the server did not take the instant break).
            aim = newest.body.getCenter();
        }
        Vec2 look = eye.rotationTo(aim);
        Core.rotations().request(this, RotationRequest.at(look)
                .priority(ClaimPriority.NORMAL)
                .step(profile.turnSpeed())
                .mode(profile.rotationMode()));
    }

    /**
     * Lets go of crystals that no longer count toward {@link #maxCrystals}: never
     * broken in time, or due by now and not there (refused, or someone else
     * broke it).
     */
    private void forgetOut() {
        int due = pendingTicks();
        // Long enough to sit out the hurt cooldown: a held crystal still counts while it waits.
        int grace = Math.max(BREAK_GRACE, hurtWindow());
        out.removeIf(crystal -> tick - crystal.placedAt > due + grace
                || (tick - crystal.placedAt > due && !standing(crystal.at)));
    }

    /**
     * Under Hurt Sync, whether to hold off placing: a crystal placed now would
     * stand through the target's cooldown while they move, and be worth nothing
     * by the time it may be broken. So no replacement goes out with a break (that
     * hit opens a new window), and otherwise the next is placed only once the
     * window is about as short as a crystal takes to show up, so it arrives as
     * the window closes, aimed at where the target is then.
     */
    private boolean waitToPlace(BreakOption<?> breaking) {
        if (breaking != null) {
            return true;
        }
        return watched != null && kit.log().cooldownLeft(watched.get(), judgedWindow()) > arrivalTicks();
    }

    /**
     * Ticks from deciding to place until the crystal shows up: sent at the start
     * of the next tick, a round trip, and the server's tick before it sends the spawn.
     */
    private static int arrivalTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 50d) + 2;
    }

    /**
     * Under Hurt Sync, stops counting crystals of ours that have shown up and are
     * not worth breaking once the target's window has closed: placed for where the
     * target was, they would hold the Max Crystals slot while nothing else is placed.
     * Called after a break search that found nothing.
     */
    private void forgetStale() {
        if (!hurtSync.isOn() || watched == null || kit.log().cooldownLeft(watched.get(), judgedWindow()) > 0) {
            return;
        }
        out.removeIf(crystal -> standing(crystal.at));
    }

    /** @return the crystal out at {@code position}, or null */
    private Out outAt(Vec3 position) {
        for (Out crystal : out) {
            if (crystal.at.distanceTo(position) <= SAME_CRYSTAL) {
                return crystal;
            }
        }
        return null;
    }

    /** @return whether a crystal stands at {@code position} */
    private boolean standing(Vec3 position) {
        boolean[] found = { false };
        kit.crystals().forEachWithin(position, SAME_CRYSTAL, crystal -> {
            if (crystal.getPosition().distanceTo(position) <= SAME_CRYSTAL) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean hasCrystals() {
        return Inventories.hotbarHas(CRYSTAL) || Inventories.offhandHolds(CRYSTAL);
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
                .pendingTicks(CrystalAuraModule::pendingTicks)
                .hurtWindow(this::judgedWindow)
                .build();
    }

    /**
     * {@link #hurtWindow} as the search must judge it. A hit is logged on the tick
     * its attack is sent, and a break decided now is sent at the start of the next
     * tick, so judged on the full window it would go out a tick after the window
     * closed (12 ticks apart at 11, in log 5). One tick less, it goes out the tick
     * the window closes.
     */
    private int judgedWindow() {
        return Math.max(1, hurtWindow() - 1);
    }

    /**
     * The targets' hurt cooldown the search times breaks to: the game's, a tick
     * longer with {@link #syncMargin}; 1 without {@link #hurtSync}, which is the
     * one-explosion-a-tick rule alone.
     */
    private int hurtWindow() {
        if (!hurtSync.isOn() || kit == null) {
            return 1;
        }
        return kit.hurtCooldown() + (syncMargin.isOn() ? 1 : 0);
    }

    /** One-way latency in ticks, rounded up: how long until the server acts on what is sent now. */
    private static int pingTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 2d / 50d);
    }

    /**
     * How long a placed crystal is waited for: the round trip, rounded up, and a
     * little for jitter. The search's default is a fixed half second, which a
     * high ping outlasts.
     */
    private static int pendingTicks() {
        return (int) Math.ceil(Core.lag().getPing() / 50d) + PENDING_SLACK;
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
        out.clear();
        watched = null;
        broken.clear();
        lastTarget = null;
        noCrystals = false;
        display = "";
    }

    public enum Priority {
        CLOSEST,
        LOWEST_HEALTH
    }

    /** A crystal placed and not yet broken: where it will stand, its box, the click that placed it, and when. */
    private static final class Out {
        final Vec3 at;
        final Box body;
        final Vec3 click;
        final long placedAt;

        Out(Vec3 at, Box body, Vec3 click, long placedAt) {
            this.at = at;
            this.body = body;
            this.click = click;
            this.placedAt = placedAt;
        }
    }
}
