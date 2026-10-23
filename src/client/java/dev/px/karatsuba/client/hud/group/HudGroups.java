package dev.px.karatsuba.client.hud.group;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.px.core.Core;
import dev.px.core.config.ConfigSection;
import dev.px.core.hud.HudLayout;
import dev.px.core.hud.PlacementArranger;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Element groups ({@code hud/DESIGN.md} §6): elements held edge to edge, so they
 * never overlap. Each element in a group is <b>attached</b> to one other, on one of
 * its sides, lined up along that side; the element at the top of the chain (the
 * <b>root</b>) is placed by its own layout as any element is, and everything
 * attached follows it.
 *
 * <pre>
 *   Ping                 Ping is the root: its layout places it
 *   TPS     below Ping   TPS sits on Ping's bottom edge, left edges lined up
 *   Server  below TPS    Server sits on TPS's, and so on
 * </pre>
 *
 * <p>A group is the set of elements linked this way. Groups grow in any direction
 * (above, below, left, right) and touch, with no gap. They re-stack every frame,
 * in game and in the editor, through Core's {@link PlacementArranger}: when Ping
 * grows, TPS and Server move down with it. Nothing is written to a member's own
 * layout while it is attached; the links are kept here, per profile, in
 * {@code hud-groups.json}.
 *
 * <p>A hidden member keeps its place in the chain at no size, so what is attached
 * to it closes up and opens again when it is shown.
 */
public final class HudGroups implements ConfigSection, PlacementArranger {

    public static final String ID = "hud-groups";

    /** Which of its parent's sides an element sits on. */
    public enum Side {
        ABOVE, BELOW, LEFT, RIGHT;

        /** @return whether it stacks up or down, so it lines up along the parent's width */
        public boolean vertical() {
            return this == ABOVE || this == BELOW;
        }
    }

    /** How an attached element lines up along its parent's side: its start (left or top), centre or end. */
    public enum Along {
        START, CENTER, END
    }

    /** One element's attachment: to {@code parent}, on its {@code side}, lined up {@code along} it. */
    public record Link(String parent, Side side, Along along) {
    }

    /** No chain is this long; past it, a corrupt file's loop is cut rather than followed. */
    private static final int MAX_DEPTH = 64;

    private static HudGroups instance;

    /** By attached element's id. Insertion order is kept so the file reads in the order groups were made. */
    private final Map<String, Link> links = new LinkedHashMap<>();

    private HudGroups() {
    }

    /** Registers the section and the arranger. Before {@code Core.start()}, which loads the config. */
    public static void install() {
        instance = new HudGroups();
        Core.config().register(instance);
        Core.hud().setArranger(instance);
    }

    /** @return the groups; null before {@link #install()} */
    public static HudGroups get() {
        return instance;
    }

    // ----------------------------------------------------------------- reading

    /** @return how {@code id} is attached, or null for an element that is not (a root, or alone) */
    public Link linkOf(String id) {
        return links.get(id);
    }

    /**
     * @return where {@code id} was drawn last frame, after grouping: for an element that
     *         lays itself out by where it is on screen (ArrayList's Auto sort and
     *         alignment). Null before its first frame, or while it is hidden
     */
    public Bounds lastPlaced(String id) {
        return lastBounds.get(id);
    }

    /** @return whether {@code id} is in a group at all: attached, or something is attached to it */
    public boolean isGrouped(String id) {
        return links.containsKey(id) || !childrenOf(id).isEmpty();
    }

    /** @return what is attached directly to {@code id}, in the order it was attached */
    public List<String> childrenOf(String id) {
        List<String> children = new ArrayList<>();
        for (Map.Entry<String, Link> entry : links.entrySet()) {
            if (entry.getValue().parent().equals(id)) {
                children.add(entry.getKey());
            }
        }
        return children;
    }

    /** @return {@code id} and everything attached to it, however far down: what moves when it moves */
    public Set<String> subtreeOf(String id) {
        Set<String> found = new HashSet<>();
        collect(id, found, 0);
        return found;
    }

    private void collect(String id, Set<String> found, int depth) {
        if (depth > MAX_DEPTH || !found.add(id)) {
            return;
        }
        for (String child : childrenOf(id)) {
            collect(child, found, depth + 1);
        }
    }

    // ----------------------------------------------------------------- changing

    /**
     * Attaches {@code id} (with whatever is attached to it) to {@code parent}'s
     * {@code side}. Something already on that side is moved out to the end of
     * {@code id}'s own chain on that side, so dropping into a stack inserts rather
     * than overlaps.
     *
     * @return whether it attached; never onto itself or onto something attached to it
     */
    public boolean attach(String id, String parent, Side side, Along along) {
        if (id.equals(parent) || subtreeOf(id).contains(parent)) {
            return false;
        }
        detach(id);
        String displaced = null;
        for (String child : childrenOf(parent)) {
            if (links.get(child).side() == side) {
                displaced = child;
                break;
            }
        }
        links.put(id, new Link(parent, side, along));
        if (displaced != null) {
            Link was = links.get(displaced);
            links.put(displaced, new Link(endOfChain(id, side), side, was.along()));
        }
        return true;
    }

    /**
     * Takes {@code id} out of its group, alone: whatever was attached to it takes its
     * place, so the rest close up. The first thing on the same side as {@code id} was
     * (the chain carrying on) takes its exact place; anything else attached to it goes
     * along with that, or, if nothing carried on, the first child takes its place.
     *
     * <p>Its own layout is not touched: a caller pulling it out by hand has already
     * moved it, and one resetting it is about to.
     */
    public void detach(String id) {
        Link own = links.remove(id);
        List<String> children = childrenOf(id);
        if (children.isEmpty()) {
            return;
        }
        String heir = children.get(0);
        if (own != null) {
            for (String child : children) {
                if (links.get(child).side() == own.side()) {
                    heir = child;
                    break;
                }
            }
        }
        Link heirLink = links.remove(heir);
        if (own != null) {
            links.put(heir, new Link(own.parent(), own.side(), heirLink.along()));
        } else {
            // It was the root: the heir becomes the root, where it already is on screen.
            Bounds at = lastBounds.get(heir);
            if (at != null) {
                HudLayout layout = Core.hud().layoutOf(heir);
                if (layout != null) {
                    Core.hud().moveTo(layout, at.getX(), at.getY(), at.getWidth(), at.getHeight());
                }
            }
        }
        for (String child : children) {
            if (!child.equals(heir)) {
                Link link = links.get(child);
                links.put(child, new Link(heir, link.side(), link.along()));
            }
        }
    }

    /** Ungroups everything: Reset All. Each element is left where its own layout puts it. */
    public void clear() {
        links.clear();
    }

    /** Drops links to elements that no longer exist, so a renamed or removed element leaves no hole. */
    private void prune() {
        links.entrySet().removeIf(entry -> Core.hud().find(entry.getKey()).isEmpty()
                || Core.hud().find(entry.getValue().parent()).isEmpty());
    }

    /** @return the last element of {@code id}'s chain on {@code side}: where something new on that side goes */
    private String endOfChain(String id, Side side) {
        String at = id;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            String next = null;
            for (String child : childrenOf(at)) {
                if (links.get(child).side() == side) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return at;
            }
            at = next;
        }
        return at;
    }

    // ------------------------------------------- for the editors, groups included

    /** Resets every element's layout and ungroups everything: the editors' Reset All. */
    public static void resetAll() {
        if (instance != null) {
            instance.clear();
        }
        Core.hud().resetAll();
    }

    /** Takes {@code id} out of its group and puts it back where it started: a single element's reset. */
    public static void resetLayout(String id) {
        if (id == null) {
            return;
        }
        if (instance != null) {
            instance.detach(id);
        }
        Core.hud().resetLayout(id);
    }

    /** @return the whole arrangement, for an undo step: every element's layout and the groups */
    public static JsonObject snapshot() {
        JsonObject snapshot = new JsonObject();
        snapshot.add("hud", Core.hud().save().deepCopy());
        snapshot.add("groups", instance == null ? new JsonObject() : instance.save());
        return snapshot;
    }

    /** Puts back a {@link #snapshot()}. */
    public static void restore(JsonObject snapshot) {
        Core.hud().load(snapshot.getAsJsonObject("hud"));
        if (instance != null && snapshot.has("groups")) {
            instance.load(snapshot.getAsJsonObject("groups"));
        }
    }

    // ---------------------------------------------------------------- arranging

    /** Where each element was put last frame, attached or not: for a root that is pulled out to stay put. */
    private Map<String, Bounds> lastBounds = new HashMap<>();

    @Override
    public Map<String, Bounds> arrange(Map<String, Bounds> placed, float screenWidth, float screenHeight) {
        if (links.isEmpty()) {
            lastBounds = new HashMap<>(placed);
            return Map.of();
        }
        Map<String, Bounds> done = new HashMap<>();
        for (String id : links.keySet()) {
            boundsOf(id, placed, done, screenWidth, screenHeight, 0);
        }
        Map<String, Bounds> moved = new HashMap<>();
        for (String id : links.keySet()) {
            // Only what was resolved this frame is drawn; a hidden member was only a placeholder.
            if (placed.containsKey(id)) {
                moved.put(id, done.get(id));
            }
        }
        Map<String, Bounds> all = new HashMap<>(placed);
        all.putAll(moved);
        lastBounds = all;
        return moved;
    }

    /**
     * @return where {@code id} goes this frame: its own place if it is a root, else
     *         against its parent's side. A hidden element (absent from {@code placed})
     *         is there at no size, so the chain closes up across it
     */
    private Bounds boundsOf(String id, Map<String, Bounds> placed, Map<String, Bounds> done,
                            float screenWidth, float screenHeight, int depth) {
        Bounds known = done.get(id);
        if (known != null) {
            return known;
        }
        Link link = links.get(id);
        Bounds own = placed.get(id);
        Bounds result;
        if (link == null || depth > MAX_DEPTH) {
            result = own != null ? own : zeroSized(id, screenWidth, screenHeight);
        } else {
            Bounds parent = boundsOf(link.parent(), placed, done, screenWidth, screenHeight, depth + 1);
            float w = own == null ? 0f : own.getWidth();
            float h = own == null ? 0f : own.getHeight();
            result = keepOnScreen(against(parent, w, h, link), screenWidth, screenHeight);
        }
        done.put(id, result);
        return result;
    }

    /** @return a box {@code w} by {@code h} on {@code parent}'s side, lined up along it, touching */
    static Bounds against(Bounds parent, float w, float h, Link link) {
        return switch (link.side()) {
            case BELOW -> Bounds.of(line(parent.getX(), parent.getWidth(), w, link.along()), parent.getBottom(), w, h);
            case ABOVE -> Bounds.of(line(parent.getX(), parent.getWidth(), w, link.along()), parent.getY() - h, w, h);
            case RIGHT -> Bounds.of(parent.getRight(), line(parent.getY(), parent.getHeight(), h, link.along()), w, h);
            case LEFT -> Bounds.of(parent.getX() - w, line(parent.getY(), parent.getHeight(), h, link.along()), w, h);
        };
    }

    private static float line(float start, float length, float size, Along along) {
        return switch (along) {
            case START -> start;
            case CENTER -> start + (length - size) / 2f;
            case END -> start + length - size;
        };
    }

    /** A hidden root: where its layout would put it, at no size, so its group keeps its place. */
    private static Bounds zeroSized(String id, float screenWidth, float screenHeight) {
        HudLayout layout = Core.hud().layoutOf(id);
        return layout == null ? Bounds.of(0f, 0f, 0f, 0f)
                : Core.hud().place(layout, Size.of(0f, 0f), screenWidth, screenHeight);
    }

    private static Bounds keepOnScreen(Bounds b, float screenWidth, float screenHeight) {
        float x = Math.max(0f, Math.min(screenWidth - b.getWidth(), b.getX()));
        float y = Math.max(0f, Math.min(screenHeight - b.getHeight(), b.getY()));
        return x == b.getX() && y == b.getY() ? b : Bounds.of(x, y, b.getWidth(), b.getHeight());
    }

    // ------------------------------------------------------------------ config

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonObject save() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Link> entry : links.entrySet()) {
            Link link = entry.getValue();
            JsonObject saved = new JsonObject();
            saved.addProperty("parent", link.parent());
            saved.addProperty("side", link.side().name());
            saved.addProperty("along", link.along().name());
            json.add(entry.getKey(), saved);
        }
        return json;
    }

    /** Tolerant: an entry it cannot read is skipped, and a link that would loop is dropped. */
    @Override
    public void load(JsonObject json) {
        links.clear();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                JsonObject saved = entry.getValue().getAsJsonObject();
                Link link = new Link(saved.get("parent").getAsString(),
                        Side.valueOf(saved.get("side").getAsString()),
                        saved.has("along") ? Along.valueOf(saved.get("along").getAsString()) : Along.START);
                if (!entry.getKey().equals(link.parent()) && !subtreeOf(entry.getKey()).contains(link.parent())) {
                    links.put(entry.getKey(), link);
                }
            } catch (RuntimeException unreadable) {
                Core.log().warn("Skipped an unreadable HUD group entry for " + entry.getKey());
            }
        }
        prune();
    }
}
