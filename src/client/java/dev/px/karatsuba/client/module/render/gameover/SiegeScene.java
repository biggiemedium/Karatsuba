package dev.px.karatsuba.client.module.render.gameover;

import dev.px.core.input.Key;
import dev.px.core.input.MouseButton;
import dev.px.core.render.Alignment;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.Texture;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.font.Font;
import dev.px.karatsuba.adapter.asset.Assets;
import dev.px.karatsuba.adapter.asset.PlayerFaces;
import dev.px.karatsuba.adapter.asset.Sounds;
import dev.px.karatsuba.adapter.game.Options;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.item.ItemIcons;
import dev.px.karatsuba.adapter.game.player.DeathCause;
import dev.px.karatsuba.adapter.screen.DeathScreens;

import java.util.Locale;

/**
 * Rainbow Six Siege's old death (before Y6S2), after a screenshot of it: the
 * camera pulls back from the body, then cuts to a close-up of the killer, with
 * "NAME KILLED YOU" and a red skull above, and the killer's card on the left.
 *
 * <p>The card is the game's operator card, in Minecraft terms:
 * <ul>
 *   <li>a blue header with the portrait (the killer's face) standing over its top
 *       right, what they are ("Player", "Zombie") where the operator's name was,
 *       their name, and four health segments, live;</li>
 *   <li>a loadout grid under it: main hand and off hand where the primary and
 *       secondary were, the chestplate where the gadget was, and what killed you
 *       highlighted blue where the ability was.</li>
 * </ul>
 * With no killer, or a death the player caused, the card and the close-up are the
 * player's own.
 *
 * <p>Sizes are fractions of the screen height, measured off the screenshot, so the
 * layout holds at any GUI scale. The camera is third person for the scene
 * (borrowed from the player's F5 setting and handed back at the end) and moved by
 * {@link SiegeGameOver}'s camera handlers. Any key or click respawns, once a second
 * has passed.
 */
final class SiegeScene implements GameOverScene {

    /** The pull back from the body, then the close-up of the killer. */
    private static final long ZOOM_MS = 1_500L;
    private static final long CLOSE_UP_MS = 1_500L;
    private static final long CARD_FADE_MS = 250L;
    private static final long RESPAWN_AFTER_MS = 1_000L;

    /** From just behind the head out to the game's own third-person distance. */
    private static final float START_DISTANCE = 1f;
    private static final float END_DISTANCE = 4f;

    /** Looking down on the body at the end of the pull back. */
    private static final float END_PITCH = 45f;

    /** The close-up pushes in slowly, from this far in front of the face to this. */
    private static final float CLOSE_UP_FROM = 2.2f;
    private static final float CLOSE_UP_TO = 1.5f;

    private static final float BLUR = 8f;

    /** Clear of the hotbar, which the game still draws under the scene. */
    private static final float PROMPT_FROM_BOTTOM = 60f;

    // Colours sampled from the screenshot.
    private static final Color HEADER = Color.of(38, 120, 215, 235);
    private static final Color KIND = Color.of(175, 205, 240, 255);
    private static final Color GRID = Color.of(28, 24, 24, 175);
    private static final Color USED = Color.of(46, 106, 182, 235);
    private static final Color SEGMENT_EMPTY = Color.of(255, 255, 255, 60);
    private static final Color SKULL = Color.of(225, 45, 45, 255);
    private static final Color SHADOW = Color.of(0, 0, 0, 150);

    private final Animation zoom = Animation.fade(ZOOM_MS, Easing.SINE_IN).target(1f);
    private final Animation prompt = Animation.fade(400L, Easing.QUAD_OUT);
    private final long startedAt = System.currentTimeMillis();

    private final DeathCause cause;
    private final boolean blur;
    private final Options.View borrowedView;
    private boolean respawning;

    /** @param voiceLine a clip to play over the death sound, or null for none */
    SiegeScene(DeathCause cause, boolean blur, float volume, String voiceLine) {
        this.cause = cause;
        this.blur = blur;
        borrowedView = Options.view();
        Options.view(Options.View.THIRD_PERSON_BACK);
        Sounds.play("rainbowgameover", volume);
        if (voiceLine != null) {
            Sounds.play(voiceLine, volume);
        }
    }

    // --------------------------------------------------------------- camera

    /** @return whether the pull back is over and the camera is on the killer */
    boolean inCloseUp() {
        return elapsed() >= ZOOM_MS;
    }

    /** @return the entity the close-up is on: the killer, or the player's own body */
    int closeUpTarget() {
        return cause.getEntityId();
    }

    /** @return how far in front of the face the close-up camera is now */
    float closeUpDistance() {
        float t = Math.min(1f, (elapsed() - ZOOM_MS) / (float) CLOSE_UP_MS);
        return CLOSE_UP_FROM + (CLOSE_UP_TO - CLOSE_UP_FROM) * Easing.QUAD_OUT.apply(Math.max(0f, t));
    }

    /** @return how far behind the body the pull-back camera sits now */
    float distance() {
        return zoom.get(START_DISTANCE, END_DISTANCE);
    }

    /** @return the pull-back camera's pitch now, eased from the body's own towards looking down on it */
    float pitch(float bodyPitch) {
        return bodyPitch + (END_PITCH - bodyPitch) * zoom.get();
    }

    // --------------------------------------------------------------- drawing

    @Override
    public void render(float width, float height) {
        if (blur && Render.supportsBlur()) {
            Render.blur(0f, 0f, width, height, BLUR);
        }
        float alpha = Math.max(0f, Math.min(1f, (elapsed() - ZOOM_MS) / (float) CARD_FADE_MS));
        if (alpha > 0f) {
            float u = height / 100f;
            drawHeadline(width, u, alpha);
            drawCard(u, alpha);
        }

        prompt.target(canRespawn());
        float promptAlpha = prompt.get();
        if (promptAlpha > 0f) {
            Font small = Assets.fontFile("sf-pro/sf-pro.ttf", 11f);
            String text = respawning ? "Respawning..." : "Press any key to continue...";
            Render.text(small, text, width / 2f, height - PROMPT_FROM_BOTTOM, Alignment.CENTER,
                    Color.WHITE.withAlpha(promptAlpha));
        }
    }

    /** "NAME KILLED YOU" a quarter of the way down, with the red skull after it. */
    private void drawHeadline(float width, float u, float alpha) {
        Font font = Assets.fontFile("sf-pro/sf-pro.ttf", 3.2f * u);
        String text = cause.isSelf() ? "ELIMINATED" : cause.getName() + " KILLED YOU";
        float skull = 2.8f * u;
        float gap = 1.2f * u;
        float textWidth = font.widthOf(text);
        float x = (width - textWidth - gap - skull) / 2f;
        float y = 24.5f * u - font.getHeight() / 2f;
        Render.textShadowed(font, text, x, y, Color.WHITE.withAlpha(alpha), SHADOW.fade(alpha));
        drawSkull(x + textWidth + gap + skull / 2f, 24.5f * u, skull, alpha);
    }

    /** A small skull over crossed bones, from circles and lines. */
    private static void drawSkull(float cx, float cy, float size, float alpha) {
        Color red = SKULL.fade(alpha);
        float r = size * 0.32f;
        Render.line(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f, size * 0.12f, red);
        Render.line(cx + size / 2f, cy - size / 2f, cx - size / 2f, cy + size / 2f, size * 0.12f, red);
        Render.circle(cx, cy - r * 0.25f, r, red);
        Render.rect(cx - r * 0.6f, cy + r * 0.3f, r * 1.2f, r * 0.7f, red);
        Color hole = Color.of(40, 8, 8, 255).fade(alpha);
        Render.circle(cx - r * 0.4f, cy - r * 0.2f, r * 0.26f, hole);
        Render.circle(cx + r * 0.4f, cy - r * 0.2f, r * 0.26f, hole);
    }

    /** The operator card on the left: header with portrait, name and health, then the loadout grid. */
    private void drawCard(float u, float alpha) {
        float x = 1.3f * u;
        float width = 35f * u;
        float headerTop = 24.7f * u;
        float headerHeight = 11f * u;

        Render.rect(x, headerTop, width, headerHeight, HEADER.fade(alpha));
        drawPortrait(x + width - 13f * u, headerTop - 7f * u, 12f * u, alpha);

        Font kind = Assets.fontFile("sf-pro/sf-pro-semibold.otf", 2.2f * u);
        Font name = Assets.fontFile("sf-pro/sf-pro-semibold.otf", 2.8f * u);
        float room = width - 15f * u;
        Render.text(kind, kind.truncate(cause.getKind().toUpperCase(Locale.ROOT), room), x + 1.2f * u,
                headerTop + 2.6f * u, Alignment.LEFT, KIND.fade(alpha));
        Render.text(name, name.truncate(cause.getName(), room), x + 1.2f * u, headerTop + 5.4f * u,
                Alignment.LEFT, Color.WHITE.withAlpha(alpha));
        drawHealth(x + 1f * u, headerTop + headerHeight - 1.6f * u, width - 2f * u, 0.7f * u, alpha);

        float gridTop = headerTop + headerHeight;
        float cellWidth = width / 2f;
        float cellHeight = 14f * u;
        Render.rect(x, gridTop, width, cellHeight * 2f, GRID.fade(alpha));
        Equipment equipment = cause.getEquipment();
        GameItem main = equipment.get(Equipment.Slot.MAIN_HAND);
        GameItem off = equipment.get(Equipment.Slot.OFF_HAND);
        GameItem chest = equipment.get(Equipment.Slot.CHEST);
        drawCell(x, gridTop, cellWidth, cellHeight, main, main.isEmpty() ? "Fist" : main.getName(), false, u, alpha);
        drawCell(x + cellWidth, gridTop, cellWidth, cellHeight, off, off.isEmpty() ? "None" : off.getName(), false,
                u, alpha);
        drawCell(x, gridTop + cellHeight, cellWidth, cellHeight, chest, chest.isEmpty() ? "No Armor" : chest.getName(),
                false, u, alpha);
        drawCell(x + cellWidth, gridTop + cellHeight, cellWidth, cellHeight, cause.getUsed(), cause.getUsedLabel(),
                true, u, alpha);
    }

    /** The killer's face, or a block in the header colour with their initial while it loads or for a mob. */
    private void drawPortrait(float x, float y, float size, float alpha) {
        Texture face = cause.isPlayer() ? PlayerFaces.face(cause.getUuid()) : null;
        if (face != null) {
            Render.texture(face, x, y, size, size, Color.WHITE.withAlpha(alpha));
            return;
        }
        Render.rect(x, y, size, size, HEADER.darker(0.7f).fade(alpha));
        Font initial = Assets.fontFile("sf-pro/sf-pro-semibold.otf", size * 0.5f);
        String letter = cause.getName().isEmpty() ? "?" : cause.getName().substring(0, 1).toUpperCase(Locale.ROOT);
        Render.text(initial, letter, x + size / 2f, y + (size - initial.getHeight()) / 2f, Alignment.CENTER,
                Color.WHITE.withAlpha(alpha));
    }

    /** Four white segments, filled with the killer's health now (or the player's: none). */
    private void drawHealth(float x, float y, float width, float height, float alpha) {
        GameEntity target = Entities.byId(cause.getEntityId(), 1f);
        float fraction = target == null || target.getMaxHealth() <= 0f ? 0f
                : Math.max(0f, Math.min(1f, target.getHealth() / target.getMaxHealth()));
        float gap = height * 0.7f;
        float segment = (width - gap * 3f) / 4f;
        for (int i = 0; i < 4; i++) {
            float left = x + i * (segment + gap);
            Render.rect(left, y, segment, height, SEGMENT_EMPTY.fade(alpha));
            float filled = Math.max(0f, Math.min(1f, fraction * 4f - i));
            if (filled > 0f) {
                Render.rect(left, y, segment * filled, height, Color.WHITE.withAlpha(alpha));
            }
        }
    }

    /** One loadout cell: its label top left, the icon in the middle; the highlighted one is what killed you. */
    private static void drawCell(float x, float y, float width, float height, GameItem item, String label,
                                 boolean highlighted, float u, float alpha) {
        if (highlighted) {
            Render.rect(x, y, width, height, USED.fade(alpha));
        }
        Font font = Assets.fontFile("sf-pro/sf-pro-semibold.otf", 1.7f * u);
        Render.text(font, font.truncate(label.toUpperCase(Locale.ROOT), width - 1.6f * u), x + 0.8f * u, y + 0.8f * u,
                Alignment.LEFT, Color.WHITE.withAlpha(alpha));
        float icon = 5.5f * u;
        if (!item.isEmpty() && alpha > 0.5f) {
            // Drawn by the game, so it can't fade with the card.
            ItemIcons.draw(item, x + (width - icon) / 2f, y + height * 0.58f - icon / 2f, icon);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public void keyPressed(Key key) {
        respawn();
    }

    @Override
    public void mousePressed(float x, float y, MouseButton button) {
        respawn();
    }

    private long elapsed() {
        return System.currentTimeMillis() - startedAt;
    }

    private boolean canRespawn() {
        return elapsed() >= RESPAWN_AFTER_MS;
    }

    private void respawn() {
        if (canRespawn() && !respawning) {
            respawning = true;
            DeathScreens.respawn();
        }
    }

    @Override
    public void end() {
        Options.view(borrowedView);
    }
}
