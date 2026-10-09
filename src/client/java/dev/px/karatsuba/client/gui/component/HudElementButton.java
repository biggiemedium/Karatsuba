package dev.px.karatsuba.client.gui.component;

import dev.px.core.Core;
import dev.px.gui.legacy.Component;
import dev.px.gui.legacy.GuiStyle;
import dev.px.gui.legacy.Panel;
import dev.px.gui.legacy.SettingRendererRegistry;
import dev.px.core.hud.HudElement;
import dev.px.core.hud.HudLayout;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Render;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.setting.Setting;
import dev.px.core.setting.SettingHolder;
import dev.px.karatsuba.client.config.AutoSave;

/**
 * One HUD element in the HUD elements screen: left-click shows or hides it,
 * right-click opens its settings.
 *
 * <p>The HUD's twin of Core's {@code ModuleButton}, drawn the same way so the two
 * screens read alike: the accent fills the row while the element is shown. Its
 * setting rows are Core's own, from the same renderer registry as the click GUI,
 * built from the element's top-level settings (a group draws its own children).
 *
 * <p>Shown and hidden is the element's {@code HudLayout} flag, the same one the
 * drag editor's right-click flips, so the two never disagree.
 */
public final class HudElementButton extends Panel {

    private final HudElement element;
    private final Animation shown = Animation.fade(140L, Easing.QUAD_OUT);
    private boolean expanded;

    public HudElementButton(HudElement element, SettingRendererRegistry renderers) {
        this.element = element;
        setPadding(GuiStyle.spacing());
        shown.snapTo(isShown() ? 1f : 0f);

        if (element instanceof SettingHolder holder) {
            for (Setting<?> setting : holder.getSettings()) {
                Component row = renderers.create(setting);
                if (row != null) {
                    add(row);
                }
            }
        }
    }

    /** @return whether the element has any setting the GUI can show */
    public boolean hasSettings() {
        return !getChildren().isEmpty();
    }

    /** @return whether the element is drawn in game */
    public boolean isShown() {
        HudLayout layout = Core.hud().layoutOf(element);
        return layout != null && !layout.isHidden();
    }

    /** Shows or hides the element; does nothing when it already is. */
    public void setShown(boolean show) {
        HudLayout layout = Core.hud().layoutOf(element);
        if (layout != null && layout.isHidden() == show) {
            layout.toggleHidden();
            AutoSave.markDirty();
        }
    }

    @Override
    protected boolean showsChildren() {
        return expanded;
    }

    @Override
    public String getTooltip() {
        return hasSettings()
                ? "Left-click to show or hide; right-click for its settings"
                : "Left-click to show or hide";
    }

    @Override
    protected void content(Content c) {
        shown.target(isShown());
        float progress = shown.get();
        float height = GuiStyle.rowHeight();
        float radius = Math.min(GuiStyle.radius(), height / 2f);

        c.height(height).align(Align.STRETCH).background(GuiStyle.surface(), radius);

        if (progress > 0f) {
            int alpha = (int) (progress * 255f);
            c.backdrop((x, y, w, h) -> Render.roundGradient(x, y, w, h, radius,
                    GuiStyle.accent(0f).withAlpha(alpha), GuiStyle.accent(1f).withAlpha(alpha),
                    GuiStyle.accent(1f).withAlpha(alpha), GuiStyle.accent(0f).withAlpha(alpha)));
        }

        c.row(row -> {
            row.grow().padding(GuiStyle.padding(), 0f).align(Align.CENTER);
            row.text(element.getDisplayName(), GuiStyle.textMuted().lerp(GuiStyle.text(), progress));
            row.fill();
            if (hasSettings()) {
                float dot = 2f;
                row.custom("marker", dot * 2f, dot * 2f, (x, y, w, h) -> Render.circle(
                        x + w / 2f, y + h / 2f, dot,
                        expanded ? GuiStyle.accent() : GuiStyle.outline()));
            }
        });
    }

    @Override
    protected boolean onClick(float x, float y, MouseButton button) {
        // Only the header acts: a press that reached here from a setting row was
        // declined by that row, and must not toggle the element.
        if (y > getBounds().getY() + headerHeight(getBounds().getWidth())) {
            return false;
        }
        if (button == MouseButton.LEFT) {
            setShown(!isShown());
            return true;
        }
        if (button == MouseButton.RIGHT && hasSettings()) {
            expanded = !expanded;
            return true;
        }
        return false;
    }
}
