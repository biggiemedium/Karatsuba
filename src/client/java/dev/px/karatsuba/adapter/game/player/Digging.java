package dev.px.karatsuba.adapter.game.player;

import dev.px.core.math.Direction;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.mixin.player.ClientPlayerInteractionManagerInvoker;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/**
 * Breaking a block the crosshair is not on.
 *
 * <p>The game breaks blocks in one place: {@code MinecraftClient.handleBlockBreaking},
 * in {@code handleInputEvents}, with the attack key held and the crosshair on a
 * block. There it calls {@code updateBlockBreakingProgress}, which starts the dig,
 * adds each tick's progress at the held tool's real speed, finishes it, and sends
 * every packet of it with its sequence number; then it swings. Not holding the key,
 * it cancels whatever was being broken.
 *
 * <p>This changes one thing about that: <em>which</em> block. A module names a
 * block and the face to dig it from, for this tick, and the adapter's mixin on
 * {@code handleBlockBreaking} makes the same call on it, at the same point in the
 * tick, followed by the same particles and swing. So the start, the progress, the
 * finish, the 5-tick pause after a block, the abort when the held item changes, and
 * the place in the tick all stay the game's. Grim's break checks see a player
 * holding the attack key on that block; whether the server's rotation looks at it
 * is the module's to arrange.
 *
 * <p>A request lasts one tick: a module that stops asking stops digging, and the
 * game cancels the block that same tick, as it does when a player lets go of the
 * key. The player's own attack key comes first: while it is held, the game
 * breaks what the crosshair is on and a request is ignored, unless a module has
 * asked {@link #holdOffHand} for the tick.
 *
 * <p>What Grim 2.3.73 checks about digging, and why the game's own path passes
 * each, is in {@code client/anticheat/Grim.md} under "Breaking".
 *
 * <p><b>Game thread only.</b>
 */
public final class Digging {

    private static BlockPos block;
    private static net.minecraft.util.math.Direction face;
    private static Interactions.Swing swing = Interactions.Swing.CLIENT;
    private static boolean paused;

    /** {@code player.age} when the request was made: it counts ticks, and rises after the input handling. */
    private static int askedAt = Integer.MIN_VALUE;

    /** {@code player.age} when {@link #holdOffHand} was last asked. */
    private static int handHeldOffAt = Integer.MIN_VALUE;

    private static boolean finishNow;

    /** Wall-clock times of the last START and FINISHED this class sent for a block taking more than one hit. */
    private static long startedAt = Long.MIN_VALUE;
    private static long finishedAt = Long.MIN_VALUE;

    private Digging() {
    }

    /**
     * Digs this block from this face during this tick's input handling, as the game
     * would with the attack key held on it. Ask every tick for as long as it should
     * go on; a later call in the same tick replaces an earlier one.
     *
     * @param swing how the arm swing that follows each tick of digging goes out
     */
    public static void request(Vec3i position, Direction from, Interactions.Swing swing) {
        ask(position, from, swing, false, false);
    }

    /**
     * As {@link #request}, finishing the block this tick if it is already being dug:
     * the game's progress is made full before its own update runs, so the update
     * sends the finish, breaks the block and starts its pause exactly as it does
     * for a block that ran out. Ignored on a block not yet started.
     */
    public static void finish(Vec3i position, Direction from, Interactions.Swing swing) {
        ask(position, from, swing, false, true);
    }

    /**
     * Neither digs nor lets go this tick: a dig in progress keeps its progress and
     * sends nothing, where the game would cancel it. For a tick in which the hands
     * already did something else (an attack, a click), which the game never pairs
     * with a dig.
     */
    public static void pause(Vec3i position) {
        ask(position, Direction.DOWN, Interactions.Swing.NONE, true, false);
    }

    private static void ask(Vec3i position, Direction from, Interactions.Swing swing, boolean pause,
                            boolean finishing) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        block = Voxels.toBlockPos(position);
        face = Voxels.toGame(from);
        Digging.swing = swing;
        paused = pause;
        finishNow = finishing;
        askedAt = player.age;
    }

    /** Withdraws this tick's request, if any. The game then cancels the dig at its input handling. */
    public static void withdraw() {
        block = null;
    }

    /**
     * For this tick, the player's own attack key digs nothing: holding it on a block
     * is treated as not holding it, so a request still runs and, without one, the
     * game lets go of whatever it was breaking. Ask every tick it should hold.
     *
     * <p>Only the player's own key: one held down through {@code Inputs} (a flow
     * breaking a block) still digs as normal.
     */
    public static void holdOffHand() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            handHeldOffAt = player.age;
        }
    }

    /**
     * Called by the mixin at the head of {@code handleBlockBreaking}.
     *
     * @param breaking what the game passes: the player is holding the attack key, in game
     * @return whether this handled the tick, and the game's own handling must not run
     *         (it would cancel the dig, or dig the crosshair's block)
     */
    public static boolean onHandleBlockBreaking(boolean breaking) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        boolean handHeldOff = breaking && player != null && handHeldOffAt == player.age
                && Inputs.playerHolds(Inputs.Control.ATTACK);
        if (dig(handHeldOff ? false : breaking)) {
            return true;
        }
        if (handHeldOff && interactions != null) {
            // What the game does with the key up (the mixin zeroes the cooldown).
            if (!player.isUsingItem()) {
                interactions.cancelBlockBreaking();
            }
            return true;
        }
        return false;
    }

    /** @return whether this tick's request was dug (or held), standing in for the game's handling */
    private static boolean dig(boolean breaking) {
        BlockPos target = block;
        block = null;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        ClientWorld world = client.world;
        if (target == null || breaking || player == null || interactions == null || world == null
                || askedAt != player.age) {
            return false;
        }
        // The game's own conditions for breaking at all: no screen over the game, and
        // nothing being eaten, drunk or drawn. Left to it, it cancels or waits as usual.
        if (client.currentScreen != null || player.isUsingItem()) {
            return false;
        }
        // The game only digs what its crosshair can rest on: something with an
        // outline. Air, fluids, bubble columns, light blocks and moving pistons have
        // none, and Grim's AirLiquidBreak flags a dig on any of them.
        if (!hasOutline(world, target)) {
            return false;
        }
        if (paused) {
            // Held where it is: no progress, no packet, and no cancel.
            return true;
        }
        ClientPlayerInteractionManagerInvoker state = (ClientPlayerInteractionManagerInvoker) interactions;
        // What the update itself will decide: carry on, or start again (a new START).
        boolean wasBreaking = interactions.isBreakingBlock() && state.karatsuba$isCurrentlyBreaking(target);
        if (finishNow && wasBreaking) {
            // Full now, so the update below adds a tick and finishes, as for any block that ran out.
            state.karatsuba$setCurrentBreakingProgress(1f);
        }
        if (interactions.updateBlockBreakingProgress(target, face)) {
            client.particleManager.addBlockBreakingParticles(target, face);
            Interactions.swing(Interactions.Hand.MAIN, swing);
        }
        boolean nowBreaking = interactions.isBreakingBlock() && state.karatsuba$getCurrentBreakingPos().equals(target);
        if (!wasBreaking && nowBreaking) {
            startedAt = System.currentTimeMillis();
        } else if (wasBreaking && !nowBreaking && state.karatsuba$getBlockBreakingCooldown() > 0) {
            // Ended with the game's pause after a block: it finished, rather than being let go.
            finishedAt = System.currentTimeMillis();
        }
        return true;
    }

    // -------------------------------------------------------------- reading

    /**
     * @return whether the next update carries on digging this block, rather than
     *         starting it (or starting it again: the held stack changed, Mending
     *         repairing the tool included), which sends a START
     */
    public static boolean continues(Vec3i position) {
        ClientPlayerInteractionManager interactions = MinecraftClient.getInstance().interactionManager;
        return interactions != null && interactions.isBreakingBlock()
                && ((ClientPlayerInteractionManagerInvoker) interactions)
                .karatsuba$isCurrentlyBreaking(Voxels.toBlockPos(position));
    }

    /** @return how far through this block the game is, exactly (0 to 1), or -1 when it is not breaking it */
    public static float progress(Vec3i position) {
        ClientPlayerInteractionManager interactions = MinecraftClient.getInstance().interactionManager;
        if (!breaking(position)) {
            return -1f;
        }
        return ((ClientPlayerInteractionManagerInvoker) interactions).karatsuba$getCurrentBreakingProgress();
    }

    /** @return whether the game is in its pause after a block, when an update digs nothing and sends nothing */
    public static boolean coolingDown() {
        ClientPlayerInteractionManager interactions = MinecraftClient.getInstance().interactionManager;
        return interactions != null
                && ((ClientPlayerInteractionManagerInvoker) interactions).karatsuba$getBlockBreakingCooldown() > 0;
    }

    /**
     * @return when this class last sent a START for a block that takes more than one
     *         hit (wall clock, ms), or {@code Long.MIN_VALUE}
     */
    public static long startedAt() {
        return startedAt;
    }

    /** @return when this class last sent a FINISHED (wall clock, ms), or {@code Long.MIN_VALUE} */
    public static long finishedAt() {
        return finishedAt;
    }

    /** @return whether the game is partway through breaking this block */
    public static boolean breaking(Vec3i position) {
        ClientPlayerInteractionManager interactions = MinecraftClient.getInstance().interactionManager;
        return interactions != null && interactions.isBreakingBlock()
                && ((ClientPlayerInteractionManagerInvoker) interactions).karatsuba$getCurrentBreakingPos()
                .equals(Voxels.toBlockPos(position));
    }

    /**
     * @return the share of the block one tick of digging takes off with what is in
     *         hand now, the game's own figure (tool, enchantments, haste, fatigue,
     *         water, on the ground); 1 or more breaks it on the first hit, 0 never
     *         breaks it
     */
    public static float progressPerTick(Vec3i position) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            return 0f;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        if (world.isOutOfHeightLimit(pos)) {
            return 0f;
        }
        return world.getBlockState(pos).calcBlockBreakingDelta(client.player, world, pos);
    }

    /**
     * @return {@link #progressPerTick(Vec3i)} as it would be holding the item in this
     *         hotbar slot instead (0 to 8; any other value is the hand as it is now),
     *         by the game's own formula ({@code PlayerEntity.getBlockBreakingSpeed}):
     *         the tool's speed and Efficiency, haste, fatigue, the break-speed
     *         attributes, water and the ground
     */
    public static float progressPerTick(Vec3i position, int hotbarSlot) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        if (world == null || player == null) {
            return 0f;
        }
        if (hotbarSlot < 0 || hotbarSlot >= PlayerInventory.getHotbarSize()
                || hotbarSlot == player.getInventory().selectedSlot) {
            return progressPerTick(position);
        }
        BlockPos pos = Voxels.toBlockPos(position);
        if (world.isOutOfHeightLimit(pos)) {
            return 0f;
        }
        BlockState state = world.getBlockState(pos);
        float hardness = state.getHardness(world, pos);
        if (hardness < 0f) {
            return 0f;
        }
        ItemStack stack = player.getInventory().getStack(hotbarSlot);
        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1f) {
            int efficiency = enchantmentLevel(stack, Enchantments.EFFICIENCY);
            if (efficiency > 0) {
                speed += efficiency * efficiency + 1;
            }
        }
        if (StatusEffectUtil.hasHaste(player)) {
            speed *= 1f + (StatusEffectUtil.getHasteAmplifier(player) + 1) * 0.2f;
        }
        if (player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
            speed *= switch (player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1E-4f;
            };
        }
        speed *= (float) player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
        if (player.isSubmergedIn(FluidTags.WATER)) {
            speed *= (float) player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
        }
        if (!player.isOnGround()) {
            speed /= 5f;
        }
        boolean harvests = !state.isToolRequired() || stack.isSuitableFor(state);
        return speed / hardness / (harvests ? 30f : 100f);
    }

    private static int enchantmentLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /**
     * @return whether a player in survival could break this block at all: something
     *         there, not a fluid, not unbreakable, inside the border, and not
     *         refused by the game mode (adventure, spectator)
     */
    public static boolean breakable(Vec3i position) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        if (world == null || player == null || interactions == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        if (world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos)) {
            return false;
        }
        // Unbreakable (hardness -1) would never finish, and Grim flags a finish on one.
        return hasOutline(world, pos)
                && world.getBlockState(pos).getHardness(world, pos) >= 0f
                && !player.isBlockBreakingRestricted(world, pos, interactions.getCurrentGameMode());
    }

    /** @return whether the block has an outline the crosshair could rest on */
    private static boolean hasOutline(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return !state.isAir() && !state.getOutlineShape(world, pos).isEmpty();
    }
}
