package dev.px.karatsuba.adapter.combat;

import dev.px.core.Core;

/**
 * Where a module gets the combat library, wired to the game.
 *
 * <pre>{@code
 * CombatKit<?> kit = Combat.kit();
 * }</pre>
 *
 * <p>Typed {@code CombatKit<?>} on purpose: the real type argument is the game's
 * {@code LivingEntity}, and a module that held it would have to name it. Hand the
 * kit to a generic method and work in {@code E} there; see {@link CombatKit}.
 */
public final class Combat {

    private static MinecraftCombatKit kit;

    private Combat() {
    }

    /**
     * Builds the kit and starts listening for crystals, explosions and pops.
     *
     * <p>From {@code MinecraftAdapter.install}, after Core's entity source and
     * trackers are in place: the kit reads its trackers from there.
     */
    public static void install(Core core) {
        kit = new MinecraftCombatKit(core);
        new CombatBridge(kit, core).install(core);
    }

    /**
     * @throws IllegalStateException if called before the adapter installed it
     *
     * <p>Loud rather than null, like {@code Services}: reaching for the kit
     * before boot is a boot-order mistake.
     */
    public static CombatKit<?> kit() {
        if (kit == null) {
            throw new IllegalStateException("the combat kit is not installed yet;"
                    + " MinecraftAdapter.install(core) builds it during boot, before Core.start()");
        }
        return kit;
    }
}
