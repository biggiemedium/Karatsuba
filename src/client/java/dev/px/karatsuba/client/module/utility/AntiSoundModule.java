package dev.px.karatsuba.client.module.utility;

import dev.px.core.event.Subscribe;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.adapter.event.game.sound.SoundPlayEvent;

import java.util.Locale;

/**
 * Silences chosen sounds.
 *
 * <p>The worked example for <b>{@code MultiEnumSetting}</b>, and for why it beats
 * a wall of booleans. The original of this module has twelve
 * {@code BooleanSetting}s, six of them nested under a seventh with
 * {@code visibleWhen} conditions to hide them; here the same choice is one
 * checkbox list, which persists as one JSON array and renders as one row that
 * expands.
 *
 * <p>Each option owns the id fragments it silences, so adding a category is one
 * enum constant and nothing else &mdash; no new setting, no new branch in the
 * handler, no change to the config format.
 *
 * <p>Matching is a substring test against the full registry id, which is what
 * makes {@code entity.wither} cover the spawn, the ambient and the death sound
 * without naming all three.
 */
@ModuleInfo(name = "Anti Sound", description = "Silences chosen sounds", category = "Utility")
public final class AntiSoundModule extends Module {

    private final MultiEnumSetting<Group> groups =
            multi("Silence", Group.class, Group.EXPLOSIONS, Group.WITHER)
                    .describe("Which families of sound to drop");

    private final StringSetting custom = text("Custom", "")
            .describe("Extra id fragments, comma separated, e.g. block.anvil, entity.ghast");

    @Subscribe
    private void onSound(SoundPlayEvent event) {
        if (silencedByGroup(event) || silencedByCustom(event)) {
            event.cancel();
        }
    }

    private boolean silencedByGroup(SoundPlayEvent event) {
        for (Group group : Group.values()) {
            if (groups.has(group) && group.matches(event)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The user's own fragments.
     *
     * <p>Split on every call rather than cached on change: a sound is a rare event
     * next to a frame, the string is short, and a cache here would need
     * invalidating from {@code onChange} for no measurable gain.
     */
    private boolean silencedByCustom(SoundPlayEvent event) {
        if (custom.isEmpty()) {
            return false;
        }
        for (String fragment : custom.get().split(",")) {
            String trimmed = fragment.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && event.idContains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A family of sounds, and the id fragments that identify it.
     *
     * <p>The fragments live on the constant rather than in the handler, so the
     * list of what is silenced and the list of what can be silenced are the same
     * list.
     */
    public enum Group {

        EXPLOSIONS("entity.generic.explode", "entity.end_crystal.explode",
                "block.tnt.explode", "block.bed.explode", "block.respawn_anchor.explode"),

        WITHER("entity.wither"),

        CREEPER("entity.creeper"),

        /** The hurt and death grunts, which are the loudest thing in a fight. */
        PLAYER_HURT("entity.player.hurt", "entity.player.death"),

        ARMOR("item.armor.equip"),

        PISTON("block.piston"),

        DOORS("block.wooden_door", "block.iron_door", "block.fence_gate", "block.trapdoor"),

        CHESTS("block.chest", "block.barrel", "block.shulker_box"),

        PORTAL("block.portal", "entity.player.teleport"),

        MINECART("entity.minecart");

        private final String[] fragments;

        Group(String... fragments) {
            this.fragments = fragments;
        }

        boolean matches(SoundPlayEvent event) {
            for (String fragment : fragments) {
                if (event.idContains(fragment)) {
                    return true;
                }
            }
            return false;
        }
    }
}
