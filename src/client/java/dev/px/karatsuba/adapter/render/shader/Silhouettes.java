package dev.px.karatsuba.adapter.render.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.px.core.render.Texture;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.HandRenderEvent;
import dev.px.karatsuba.adapter.render.WorldRender3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Set;

/**
 * Entities and the held items, drawn again into a mask of their own, for a shader
 * to outline, fill or glow.
 *
 * <p>The mask is a framebuffer the size of the screen, cleared to transparent,
 * with the targets drawn into it by the game's own renderers: their alpha is
 * exactly their shape, cut-outs included. A shader then reads it back with
 * {@link ShaderPasses#overlay}; see {@code effect/silhouette.fsh}.
 *
 * <h2>How the game's renderers end up drawing here</h2>
 * Every render layer an entity or held item uses starts by binding
 * {@code MinecraftClient.getFramebuffer()} (1.21.4: the main target's begin action,
 * and the Fabulous item and translucent targets when their own framebuffers are
 * null). While a capture runs, {@code MinecraftClientMixin} answers that with the
 * mask and {@code WorldRendererMixin} answers null for those two, so the draws
 * land here without a single renderer being touched. Labels are dropped for the
 * duration ({@code EntityRendererMixin}), and shadows and hitboxes switched off.
 *
 * <h2>When to call</h2>
 * <ul>
 *   <li>{@link #entities} from {@code Render3DEvent}: the camera, frustum and
 *       matrices are this frame's.</li>
 *   <li>{@link #hands} from {@code HandRenderEvent}, posted straight after the game
 *       draws the held items, with the hand projection still set.</li>
 * </ul>
 *
 * <p>Both share one mask: composite each before taking the next.
 *
 * <p><b>Render thread only.</b>
 */
public final class Silhouettes {

    /** What {@link #entities} can draw, in the buckets a module offers. */
    public enum Target {
        /** You, in third person. */
        SELF,
        PLAYERS,
        /** Hostile mobs. */
        HOSTILES,
        /** Animals. */
        PASSIVES,
        /** Everything else alive: villagers, golems, fish, bats. */
        NEUTRALS,
        CRYSTALS,
        /** Dropped items. */
        ITEMS,
        /** Thrown ender pearls. */
        PEARLS,
        /** Thrown experience bottles and experience orbs. */
        EXPERIENCE,
        /** Arrows, tridents and the rest of what is shot or thrown. */
        PROJECTILES
    }

    private static final MaskTexture MASK = new MaskTexture();

    /** The mask while a capture runs; what {@code getFramebuffer()} answers. */
    private static Framebuffer redirect;

    /** Draws the held items again, valid only while {@link HandRenderEvent} is posted. */
    private static Runnable handRedraw;

    private Silhouettes() {
    }

    // ----------------------------------------------------------------- API

    /**
     * Draws every entity in {@code targets} within {@code range} of the camera into
     * the mask.
     *
     * @param throughWalls false keeps the world in front of them: the mask starts
     *                     with the world's depth, so a hidden part stays hidden
     * @return the mask, or null if nothing was drawn (so there is nothing to shade)
     */
    public static Texture entities(Set<Target> targets, double range, boolean throughWalls) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!RenderSystem.isOnRenderThread() || client.world == null || targets.isEmpty()) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        Vec3d eye = camera.getPos();
        WorldRender3D render3D = MinecraftAdapter.render3D();
        Frustum frustum = render3D == null ? null : render3D.frustum();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        float tickDelta = client.getRenderTickCounter().getTickDelta(false);
        double rangeSquared = range * range;

        // The game's own entity batch: it keeps a buffer of its own for each layer
        // drawn alongside another (an item and its glint), which a one-buffer batch
        // cannot. Anything still pending in it goes to the real framebuffer first.
        VertexConsumerProvider.Immediate buffers = client.getBufferBuilders().getEntityVertexConsumers();
        buffers.draw();

        int[] drawn = new int[1];
        capture(!throughWalls, () -> {
            boolean hitboxes = dispatcher.shouldRenderHitboxes();
            dispatcher.setRenderShadows(false);
            dispatcher.setRenderHitboxes(false);
            try {
                MatrixStack matrices = new MatrixStack();
                for (Entity entity : client.world.getEntities()) {
                    if (!isTarget(entity, targets, camera)
                            || entity.squaredDistanceTo(eye) > rangeSquared
                            || (frustum != null && !dispatcher.shouldRender(entity, frustum, eye.x, eye.y, eye.z))) {
                        continue;
                    }
                    double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - eye.x;
                    double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - eye.y;
                    double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - eye.z;
                    dispatcher.render(entity, x, y, z, tickDelta, matrices, buffers,
                            dispatcher.getLight(entity, tickDelta));
                    drawn[0]++;
                }
                buffers.draw();
            } finally {
                dispatcher.setRenderShadows(client.options.getEntityShadows().getValue());
                dispatcher.setRenderHitboxes(hitboxes);
            }
        });
        return drawn[0] == 0 ? null : MASK;
    }

    /**
     * Draws the held items into the mask, exactly as the game just drew them: same
     * hand projection, bobbing and swing.
     *
     * @return the mask, or null outside {@link HandRenderEvent}
     */
    public static Texture hands() {
        Runnable redraw = handRedraw;
        if (redraw == null) {
            return null;
        }
        capture(false, redraw);
        return MASK;
    }

    // -------------------------------------------------------------- mixins

    /** @return the mask while a capture runs, else null; {@code getFramebuffer()} answers it */
    public static Framebuffer redirect() {
        return redirect;
    }

    /** @return whether a capture is running, so the game's side targets and labels stand aside */
    public static boolean isCapturing() {
        return redirect != null;
    }

    /**
     * Called by {@code GameRendererMixin} once the game has drawn the held items,
     * with a way to draw them again; posts {@link HandRenderEvent} around it.
     */
    public static void afterHands(Runnable redraw) {
        if (!Events.isArmed() || isCapturing()) {
            return;
        }
        handRedraw = redraw;
        try {
            Events.post(new HandRenderEvent());
        } finally {
            handRedraw = null;
        }
    }

    // ------------------------------------------------------------ internal

    /**
     * Clears the mask, optionally seeds it with the world's depth, runs the draw
     * with every main-target draw redirected into it, and puts back the
     * framebuffers that were bound.
     */
    private static void capture(boolean worldDepth, Runnable draw) {
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        Framebuffer mask = MASK.ensure(main);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        mask.clear();
        if (worldDepth) {
            mask.copyDepthFrom(main);
        }
        mask.beginWrite(false);
        redirect = mask;
        try {
            draw.run();
        } finally {
            redirect = null;
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn);
        }
    }

    private static boolean isTarget(Entity entity, Set<Target> targets, Camera camera) {
        if (entity == camera.getFocusedEntity()) {
            // Not drawn by the game in first person, so not outlined either.
            return camera.isThirdPerson() && targets.contains(Target.SELF);
        }
        if (entity instanceof PlayerEntity) {
            return targets.contains(Target.PLAYERS);
        }
        if (entity instanceof EndCrystalEntity) {
            return targets.contains(Target.CRYSTALS);
        }
        if (entity instanceof ItemEntity) {
            return targets.contains(Target.ITEMS);
        }
        if (entity instanceof EnderPearlEntity) {
            return targets.contains(Target.PEARLS);
        }
        if (entity instanceof ExperienceBottleEntity || entity instanceof ExperienceOrbEntity) {
            return targets.contains(Target.EXPERIENCE);
        }
        if (entity instanceof ProjectileEntity) {
            return targets.contains(Target.PROJECTILES);
        }
        if (entity instanceof Monster) {
            return targets.contains(Target.HOSTILES);
        }
        if (entity instanceof AnimalEntity) {
            return targets.contains(Target.PASSIVES);
        }
        return entity instanceof LivingEntity && targets.contains(Target.NEUTRALS);
    }

    /** Frees the mask. From {@code MinecraftAdapter.shutdown}. */
    public static void release() {
        MASK.release();
    }

    /** The mask framebuffer's colour, as a texture the shader samples. */
    private static final class MaskTexture implements Texture, GlTexture {

        private Framebuffer framebuffer;

        Framebuffer ensure(Framebuffer main) {
            int width = main.textureWidth;
            int height = main.textureHeight;
            if (framebuffer == null) {
                framebuffer = new SimpleFramebuffer(width, height, true);
                framebuffer.setClearColor(0f, 0f, 0f, 0f);
            } else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
                framebuffer.resize(width, height);
            }
            return framebuffer;
        }

        void release() {
            if (framebuffer != null) {
                framebuffer.delete();
                framebuffer = null;
            }
        }

        @Override
        public int glTextureId() {
            return framebuffer == null ? 0 : framebuffer.getColorAttachment();
        }

        @Override
        public int getWidth() {
            return framebuffer == null ? 0 : framebuffer.textureWidth;
        }

        @Override
        public int getHeight() {
            return framebuffer == null ? 0 : framebuffer.textureHeight;
        }

        @Override
        public boolean isValid() {
            return framebuffer != null;
        }

        /** Shared, so a caller's dispose does nothing; see {@link Silhouettes#release()}. */
        @Override
        public void dispose() {
        }
    }
}
