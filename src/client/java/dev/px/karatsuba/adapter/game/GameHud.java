package dev.px.karatsuba.adapter.game;

import dev.px.core.layout.Bounds;
import dev.px.karatsuba.adapter.mixin.render.BossBarHudAccessor;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's own HUD, as the client's HUD needs to know it: what screen is open,
 * whether F3 is up, and where the vanilla HUD is drawing right now.
 *
 * <p>Everything is in scaled GUI units, the same units the client's HUD is laid
 * out in, and the numbers are the game's own layout constants (InGameHud, 1.21.4):
 * the hotbar is 182 wide, a boss bar 19 tall from 12 down, an effect icon 25 wide.
 * The scoreboard is measured the way the game measures it. They are close rather
 * than exact where the game's own layout depends on things a HUD element does not
 * need (a demo banner, a resource pack's taller font), which only means an element
 * is pushed a pixel further than it had to be.
 *
 * <p>Game thread only, like everything that reads the client.
 */
public final class GameHud {

    /** What kind of screen is open, as far as the HUD cares. */
    public enum ScreenKind {
        /** None: playing. */
        NONE,
        /** The chat box. */
        CHAT,
        /** The client's own GUI. */
        CLIENT,
        /** Anything else: an inventory, a menu, a sign. */
        OTHER
    }

    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    /** The off-hand slot sits beside the hotbar, on either side. */
    private static final int OFFHAND_MARGIN = 29;
    /** Hotbar, experience bar, health and food, and the armour row above them. */
    private static final int STATUS_HEIGHT = 50;
    private static final int BOSS_TOP = 12;
    private static final int BOSS_ROW = 19;
    private static final int EFFECT_WIDTH = 25;
    private static final int EFFECT_ROW = 26;
    private static final int LINE = 9;

    private GameHud() {
    }

    /** @return the screen open now */
    public static ScreenKind screen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return ScreenKind.NONE;
        }
        Screen screen = client.currentScreen;
        if (screen == null) {
            return ScreenKind.NONE;
        }
        if (screen instanceof ChatScreen) {
            return ScreenKind.CHAT;
        }
        return screen instanceof CoreScreen ? ScreenKind.CLIENT : ScreenKind.OTHER;
    }

    /** @return whether the F3 debug screen is up */
    public static boolean debugShown() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.getDebugHud().shouldShowDebugHud();
    }

    /**
     * @return the areas the game's own HUD is drawing in this frame: the hotbar and
     *         status bars, boss bars, effect icons and the scoreboard sidebar.
     *         Empty out of a world, or with the HUD hidden (F1).
     */
    public static List<Bounds> vanillaZones() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<Bounds> zones = new ArrayList<>(4);
        if (client == null) {
            return zones;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.options.hudHidden) {
            return zones;
        }
        float width = client.getWindow().getScaledWidth();
        float height = client.getWindow().getScaledHeight();

        hotbar(client, width, height, zones);
        bossBars(client, width, zones);
        effects(player, width, zones);
        scoreboard(client, player, width, height, zones);
        return zones;
    }

    private static void hotbar(MinecraftClient client, float width, float height, List<Bounds> zones) {
        ClientPlayerInteractionManager interactions = client.interactionManager;
        if (interactions == null || interactions.getCurrentGameMode() == GameMode.SPECTATOR) {
            return;
        }
        int tall = interactions.hasStatusBars() ? STATUS_HEIGHT : HOTBAR_HEIGHT;
        float left = width / 2f - HOTBAR_WIDTH / 2f - OFFHAND_MARGIN;
        zones.add(Bounds.of(left, height - tall, HOTBAR_WIDTH + OFFHAND_MARGIN * 2f, tall));
    }

    private static void bossBars(MinecraftClient client, float width, List<Bounds> zones) {
        int count = ((BossBarHudAccessor) client.inGameHud.getBossBarHud()).karatsuba$bossBars().size();
        if (count > 0) {
            zones.add(Bounds.of(width / 2f - HOTBAR_WIDTH / 2f, 0f, HOTBAR_WIDTH, BOSS_TOP + count * BOSS_ROW));
        }
    }

    private static void effects(ClientPlayerEntity player, float width, List<Bounds> zones) {
        int beneficial = 0;
        int harmful = 0;
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) {
                continue;
            }
            if (effect.getEffectType().value().isBeneficial()) {
                beneficial++;
            } else {
                harmful++;
            }
        }
        int across = Math.max(beneficial, harmful);
        if (across > 0) {
            int rows = harmful > 0 ? 2 : 1;
            zones.add(Bounds.of(width - across * EFFECT_WIDTH, 0f, across * EFFECT_WIDTH, rows * EFFECT_ROW));
        }
    }

    /** Measured as InGameHud.renderScoreboardSidebar measures it: the widest line, centred a third low. */
    private static void scoreboard(MinecraftClient client, ClientPlayerEntity player, float width, float height,
                                   List<Bounds> zones) {
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (slot != null) {
                objective = scoreboard.getObjectiveForSlot(slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return;
        }
        TextRenderer text = client.textRenderer;
        NumberFormat format = objective.getNumberFormatOr(StyledNumberFormat.RED);
        int widest = text.getWidth(objective.getDisplayName());
        int separator = text.getWidth(": ");
        int lines = 0;
        for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
            if (entry.hidden()) {
                continue;
            }
            if (++lines > 15) {
                lines = 15;
                break;
            }
            Text name = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name());
            int score = text.getWidth(entry.formatted(format));
            widest = Math.max(widest, text.getWidth(name) + (score > 0 ? separator + score : 0));
        }
        int rows = lines * LINE;
        float bottom = height / 2f + rows / 3f;
        float left = width - widest - 5f;
        zones.add(Bounds.of(left, bottom - rows - LINE - 1f, width - left, rows + LINE + 2f));
    }
}
