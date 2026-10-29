package dev.px.karatsuba.client.module.utility;

import dev.px.core.Core;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.input.MouseButton;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

/**
 * Puts one action on the middle mouse button, chosen by {@code Action}: toggle a
 * friend, throw a pearl, or use a firework.
 *
 * <p>The worked example for <b>the crosshair seam and the inventory seam</b>
 * together, and for how much a module shrinks once those exist. The original
 * walks nine hotbar slots comparing {@code ItemStack} items, remembers the
 * selected slot, swaps, uses, and swaps back &mdash; three times over, once per
 * item. Here that is {@code Inventories.useFromHotbar(...)}, because remembering
 * to swap back is the adapter's problem and not this module's.
 *
 * <p>Likewise the crosshair: no {@code HitResult}, no type test, no cast. The
 * question is "is there a player under the cursor", and {@code Targets} answers
 * it in Core's types.
 *
 * <p>The friends list is Core's. {@code SocialService} owns it, persists it with
 * the rest of the config, and is what ESP and any future targeting consult, so
 * this toggles an entry in that rather than keeping a list of its own.
 */
@ModuleInfo(name = "Middle Click", description = "Runs actions on the middle mouse button",
        category = "Utility")
public final class MiddleClickModule extends ClientModule {

    private static final String ENDER_PEARL = "ender_pearl";
    private static final String FIREWORK = "firework_rocket";

    private final EnumSetting<Action> action = enumOf("Action", Action.FRIEND)
            .describe("FRIEND adds or removes the player under the cursor; PEARL throws an ender pearl; "
                    + "FIREWORK uses a firework rocket, from the hotbar");

    /**
     * Handles the click.
     *
     * <p>Core's {@code MouseEvent} rather than a game one: the adapter already
     * turns a GLFW button into this, and it only posts while nothing of the
     * client's is on screen &mdash; so a middle click inside the click GUI cannot
     * reach here and toggle a friend behind it.
     */
    @Subscribe
    private void onMouse(MouseEvent event) {
        if (event.getButton() != MouseButton.MIDDLE || !event.isPressed()) {
            return;
        }
        switch (action.get()) {
            case FRIEND -> toggleFriend();
            case PEARL -> Inventories.useFromHotbar(ENDER_PEARL);
            case FIREWORK -> Inventories.useFromHotbar(FIREWORK);
        }
    }

    @Override
    public String getDisplayInfo() {
        return action.get().label;
    }

    /**
     * Adds or removes the player under the cursor.
     *
     * <p>A toggle rather than an add, because the button is the same either way
     * and the state is visible: an ESP outline in the friend colour is the
     * confirmation, and a notification says which direction it went.
     */
    private void toggleFriend() {
        // Partial ticks of 1 takes the entity where the last tick left it, which is
        // right outside a render pass -- this runs from an input callback.
        GameEntity target = Targets.entity(1f);
        if (target == null || !target.isPlayer()) {
            return;
        }
        String name = target.getName();

        if (Core.social().isFriend(name)) {
            Core.social().remove(name);
            Notices.info(Source.MODULE_MESSAGES, "Social", "Removed " + name);
        } else {
            Core.social().add(name);
            Notices.success(Source.MODULE_MESSAGES, "Social", "Added " + name);
        }
    }

    /** What the middle button does: one thing at a time. */
    public enum Action {
        /** Add or remove the player under the cursor. */
        FRIEND("Friend"),
        /** Throw an ender pearl from the hotbar. */
        PEARL("Pearl"),
        /** Use a firework rocket from the hotbar. */
        FIREWORK("Firework");

        private final String label;

        Action(String label) {
            this.label = label;
        }
    }
}
