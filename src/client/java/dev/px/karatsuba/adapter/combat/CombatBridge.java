package dev.px.karatsuba.adapter.combat;

import dev.px.combat.anchor.AnchorLookup;
import dev.px.combat.bed.Bed;
import dev.px.core.Core;
import dev.px.core.event.impl.PacketEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.combat.explosion.Explosive;
import dev.px.karatsuba.adapter.event.game.combat.CrystalSpawnEvent;
import dev.px.karatsuba.adapter.event.game.combat.ExplosionEvent;
import dev.px.karatsuba.adapter.event.game.combat.TotemPopEvent;
import dev.px.karatsuba.adapter.game.entity.CrystalTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * What the combat library hears from the game between ticks.
 *
 * <ul>
 *   <li><b>Crystals, the moment they appear.</b> Tracked as the client adds one to
 *       the world and forgotten as it removes one, so a search finds a new
 *       crystal before the next tick and never hits one already gone; then
 *       {@link CrystalSpawnEvent}, for breaking on spawn.</li>
 *   <li><b>Explosions, for the damage monitor.</b> The packet names no explosive
 *       and no power, so this finds what went off at its centre: a crystal
 *       standing exactly there, or a bed's head or a respawn anchor whose centre
 *       it is. The server sends the explosion before the block or entity
 *       removals it caused, so both are still there to be found. Anything else
 *       &mdash; TNT, creepers &mdash; is not a profile this client keeps, and is
 *       left out of the monitor rather than guessed at.</li>
 *   <li>Then {@link ExplosionEvent}, for anything else that cares what went off
 *       where (a fake player taking the damage).</li>
 *   <li><b>Totem pops</b> (status 35), which tell the monitor an explosion did at
 *       least everything a target had; then {@link TotemPopEvent}.</li>
 * </ul>
 *
 * <p>Packets are read in Core's {@code APPLIED} phase: on the game thread, just
 * before the game handles them, so health has not yet changed.
 */
final class CombatBridge {

    /** Totem of undying used: {@code EntityStatuses.USE_TOTEM_OF_UNDYING}. */
    private static final byte TOTEM_POP = 35;

    /** How close, squared, a crystal must stand to an explosion's centre to be what went off. */
    private static final double SAME_POINT = 1.0E-4d;

    private final MinecraftCombatKit kit;
    private final CrystalTracker crystals;

    CombatBridge(MinecraftCombatKit kit, Core core) {
        this.kit = kit;
        this.crystals = core.getEntityService().require(CrystalTracker.class);
    }

    void install(Core core) {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof EndCrystalEntity crystal) {
                crystals.track(crystal);
                Events.post(new CrystalSpawnEvent(crystal, crystal.getId(),
                        Vec3.of(crystal.getX(), crystal.getY(), crystal.getZ())));
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof EndCrystalEntity crystal) {
                crystals.forget(crystal);
            }
        });

        core.getBus().on(PacketEvent.class, event -> {
            if (event.getPhase() != PacketEvent.Phase.APPLIED) {
                return;
            }
            Object packet = event.getPacket();
            if (packet instanceof ExplosionS2CPacket explosion) {
                exploded(explosion.center());
            } else if (packet instanceof EntityStatusS2CPacket status && status.getStatus() == TOTEM_POP) {
                popped(status);
            }
        });

        // A different server may bring different block states with it.
        core.getBus().on(WorldEvent.class, event -> GameBlocks.INSTANCE.clear());
    }

    private void exploded(Vec3d centre) {
        Vec3 origin = Vec3.of(centre.x, centre.y, centre.z);

        boolean[] crystal = { false };
        crystals.forEachWithin(origin, 1d, tracked -> {
            if (tracked.getPosition().squaredDistanceTo(origin) < SAME_POINT) {
                crystal[0] = true;
            }
        });
        if (crystal[0]) {
            kit.monitor().exploded(origin, ExplosionProfile.END_CRYSTAL);
            announce(origin, ExplosionProfile.END_CRYSTAL);
            return;
        }

        // Beds and anchors go off from the centre of a block.
        int x = (int) Math.floor(centre.x);
        int y = (int) Math.floor(centre.y);
        int z = (int) Math.floor(centre.z);
        if (centre.x != x + 0.5d || centre.y != y + 0.5d || centre.z != z + 0.5d) {
            return;
        }
        Direction facing = GameBlocks.bedHeadAt(x, y, z);
        if (facing != null) {
            Bed bed = Bed.ofHead(Vec3i.of(x, y, z), facing);
            kit.monitor().exploded(origin, ExplosionProfile.BED, kit.bedRules().blocksWhenFired(bed));
            announce(origin, ExplosionProfile.BED);
            return;
        }
        if (GameBlocks.anchorChargesAt(x, y, z) != AnchorLookup.NONE) {
            Vec3i cell = Vec3i.of(x, y, z);
            kit.monitor().exploded(origin, ExplosionProfile.RESPAWN_ANCHOR, kit.anchorRules().blocksWhenFired(cell));
            announce(origin, ExplosionProfile.RESPAWN_ANCHOR);
        }
    }

    /** Tells the rest of the client, after the damage monitor. */
    private static void announce(Vec3 origin, Explosive explosive) {
        Events.post(new ExplosionEvent(origin, explosive.getName(), explosive.getPower()));
    }

    private void popped(EntityStatusS2CPacket status) {
        ClientWorld world = MinecraftClient.getInstance().world;
        Entity entity = world == null ? null : status.getEntity(world);
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        kit.monitor().popped(living);
        Events.post(new TotemPopEvent(living.getId(), living.getName().getString(),
                Vec3.of(living.getX(), living.getY(), living.getZ()),
                living == MinecraftClient.getInstance().player));
    }
}
