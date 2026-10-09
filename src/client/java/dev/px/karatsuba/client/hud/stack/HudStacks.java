package dev.px.karatsuba.client.hud.stack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.px.core.Core;
import dev.px.core.config.ConfigSection;
import dev.px.core.hud.HudLayout;
import dev.px.core.hud.Placement;
import dev.px.karatsuba.client.config.AutoSave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which HUD elements are docked together, and in what order.
 *
 * <p>A stack is a list of element ids. The first is the <b>host</b>: it keeps its
 * own place on screen, and draws every other member under it on one shared plate
 * (see {@code StyledElement}). The others are <b>followers</b>: they take no
 * space of their own while docked, and keep their settings.
 *
 * <p>Core's HUD knows nothing of this, which is the point: a stack is a client
 * idea laid over elements Core places one by one, so Core's layout model and its
 * editor stay as they are.
 *
 * <p>Whenever the host changes (the host leaves, or another member is moved to
 * the top) the new host takes the old one's place, so the stack does not jump.
 * Saved per profile as {@code hud-stacks.json}, beside the layout it belongs to.
 */
public final class HudStacks implements ConfigSection {

    /** The config section id, and its file name. */
    public static final String ID = "hud-stacks";

    /** Room left between a stack and an element taken out of it. */
    private static final float UNDOCK_GAP = 4f;

    private static HudStacks instance;

    /** One stack: its members in order, host first, and whether lines divide them. */
    public static final class Stack {
        private final List<String> members = new ArrayList<>();
        private boolean dividers;

        public List<String> members() {
            return Collections.unmodifiableList(members);
        }

        public boolean dividers() {
            return dividers;
        }

        public String host() {
            return members.get(0);
        }
    }

    private final List<Stack> stacks = new ArrayList<>();

    private HudStacks() {
    }

    /** Creates the stacks and registers their config section. Before {@code Core.start()}. */
    public static void install(Core core) {
        instance = new HudStacks();
        core.getConfigService().register(instance);
    }

    /** @return the stacks, or null before {@link #install} (a harness, a test) */
    public static HudStacks get() {
        return instance;
    }

    // ----------------------------------------------------------------- reading

    /** @return the stack an element is in, or null */
    public Stack stackOf(String id) {
        for (Stack stack : stacks) {
            if (stack.members.contains(id)) {
                return stack;
            }
        }
        return null;
    }

    /** @return whether the element is drawn by another: in a stack and not its host */
    public boolean isFollower(String id) {
        Stack stack = stackOf(id);
        return stack != null && !stack.host().equals(id);
    }

    /** @return the members drawn under a host, in order; empty for anything else */
    public List<String> followersOf(String id) {
        Stack stack = stackOf(id);
        if (stack == null || !stack.host().equals(id)) {
            return List.of();
        }
        return stack.members.subList(1, stack.members.size());
    }

    // ---------------------------------------------------------------- changing

    /**
     * Docks {@code id} into {@code target}'s stack, just after {@code target},
     * making a stack of the two if {@code target} is in none. Leaves any stack
     * {@code id} was in first.
     */
    public void dock(String id, String target) {
        if (id.equals(target)) {
            return;
        }
        leave(id);
        Stack stack = stackOf(target);
        if (stack == null) {
            stack = new Stack();
            stack.members.add(target);
            stacks.add(stack);
        }
        stack.members.add(stack.members.indexOf(target) + 1, id);
        AutoSave.markDirty();
    }

    /**
     * Takes an element out of its stack and sets it down beside it: just below, or
     * just above for a stack anchored to the bottom, so it does not land on top.
     */
    public void undock(String id) {
        Stack stack = stackOf(id);
        if (stack == null) {
            return;
        }
        String host = stack.host();
        // The whole stack's box, measured before it shrinks, so the element clears it.
        Placement box = placementOf(host);
        leave(id);
        HudLayout layout = Core.hud().layoutOf(id);
        if (layout != null) {
            // A host already sits where the stack does; a follower's own layout is
            // wherever it was before it docked, so it starts from the host's.
            if (!host.equals(id)) {
                handOver(host, id);
            }
            if (box != null) {
                float shift = box.getBounds().getHeight() + UNDOCK_GAP;
                layout.setOffsetY(layout.getOffsetY() + (layout.getAnchor().isBottom() ? -shift : shift));
            }
        }
        AutoSave.markDirty();
    }

    /** Moves an element one place up ({@code -1}) or down ({@code 1}) its stack. */
    public void move(String id, int by) {
        Stack stack = stackOf(id);
        if (stack == null) {
            return;
        }
        int from = stack.members.indexOf(id);
        int to = Math.max(0, Math.min(stack.members.size() - 1, from + by));
        if (from == to) {
            return;
        }
        String oldHost = stack.host();
        stack.members.remove(from);
        stack.members.add(to, id);
        handOver(oldHost, stack.host());
        AutoSave.markDirty();
    }

    public void setDividers(String id, boolean dividers) {
        Stack stack = stackOf(id);
        if (stack != null && stack.dividers != dividers) {
            stack.dividers = dividers;
            AutoSave.markDirty();
        }
    }

    /** Removes an element from its stack, handing the place over if it was the host, and drops a stack of one. */
    private void leave(String id) {
        Stack stack = stackOf(id);
        if (stack == null) {
            return;
        }
        String oldHost = stack.host();
        stack.members.remove(id);
        if (stack.members.size() < 2) {
            stacks.remove(stack);
        }
        if (!stack.members.isEmpty()) {
            handOver(oldHost, stack.host());
        }
    }

    /** The new host takes the old one's place, keeping its own hidden flag. */
    private static void handOver(String oldHost, String newHost) {
        if (oldHost.equals(newHost)) {
            return;
        }
        HudLayout from = Core.hud().layoutOf(oldHost);
        HudLayout to = Core.hud().layoutOf(newHost);
        if (from != null && to != null) {
            boolean hidden = to.isHidden();
            to.copyFrom(from);
            to.setHidden(hidden);
        }
    }

    private static Placement placementOf(String id) {
        for (Placement placement : Core.hud().resolve(true)) {
            if (placement.getId().equals(id)) {
                return placement;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- persistence

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonObject save() {
        JsonArray list = new JsonArray();
        for (Stack stack : stacks) {
            JsonObject entry = new JsonObject();
            JsonArray members = new JsonArray();
            stack.members.forEach(members::add);
            entry.add("members", members);
            entry.addProperty("dividers", stack.dividers);
            list.add(entry);
        }
        JsonObject json = new JsonObject();
        json.add("stacks", list);
        return json;
    }

    @Override
    public void load(JsonObject json) {
        stacks.clear();
        if (json == null || !json.has("stacks") || !json.get("stacks").isJsonArray()) {
            return;
        }
        for (JsonElement element : json.getAsJsonArray("stacks")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            Stack stack = new Stack();
            if (entry.has("members") && entry.get("members").isJsonArray()) {
                for (JsonElement member : entry.getAsJsonArray("members")) {
                    String id = member.getAsString();
                    // An element in two stacks would be drawn twice; the first one wins.
                    if (stackOf(id) == null && !stack.members.contains(id)) {
                        stack.members.add(id);
                    }
                }
            }
            stack.dividers = entry.has("dividers") && entry.get("dividers").getAsBoolean();
            if (stack.members.size() >= 2) {
                stacks.add(stack);
            }
        }
    }
}
