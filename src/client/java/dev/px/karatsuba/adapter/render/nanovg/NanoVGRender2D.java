package dev.px.karatsuba.adapter.render.nanovg;

import dev.px.core.render.Color;
import dev.px.core.render.Render2D;
import dev.px.core.render.Texture;
import dev.px.core.render.font.Font;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;

/**
 * Core's 2D backend, drawn with NanoVG.
 *
 * <p>NanoVG is a vector rasteriser: rounded rectangles, arcs and stroked outlines
 * are curves it tessellates itself, not quads someone approximated. That is the
 * reason to prefer it over immediate-mode GL here, because every shape Core asks
 * for is a shape NanoVG already draws properly.
 *
 * <p>It also keeps its own GL state and its own shader, which is the hazard.
 * {@link GlStateGuard} brackets each frame so Minecraft's state cache is still
 * accurate when the game draws next.
 *
 * <h2>Push and pop share one stack</h2>
 * Core's clip, transform and alpha stacks all map onto {@code nvgSave} /
 * {@code nvgRestore}, which saves every piece of state at once. Nesting them
 * properly is therefore required: push clip, push alpha, pop alpha, pop clip is
 * fine; push clip, push alpha, pop clip, pop alpha is not. The body-taking forms
 * on the {@code Render} facade cannot get this wrong, and are worth preferring.
 */
public final class NanoVGRender2D implements Render2D {

    /** Vertical steps used to approximate a true four-corner gradient. */
    private static final int GRADIENT_BANDS = 16;

    private final long context;
    private final GlStateGuard guard = new GlStateGuard();

    // Reused rather than allocated per call: these are off-heap structs, and a
    // frame of GUI makes thousands of colour conversions.
    private final NVGColor scratchFill = NVGColor.create();
    private final NVGColor scratchStroke = NVGColor.create();
    private final NVGColor scratchA = NVGColor.create();
    private final NVGColor scratchB = NVGColor.create();
    private final NVGPaint scratchPaint = NVGPaint.create();

    private final float[] alphaStack = new float[64];
    private int alphaDepth;

    private NanoVGRender2D(long context) {
        this.context = context;
    }

    /**
     * Creates the context. Must run on the render thread with a current GL context.
     *
     * <p>Stencil strokes are deliberately off: they need a stencil buffer on the
     * bound framebuffer, and Minecraft's main target does not reliably have one.
     * Antialiasing alone covers everything Core draws.
     *
     * @throws IllegalStateException if NanoVG cannot create its GL resources
     */
    public static NanoVGRender2D create() {
        long context = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS);
        if (context == 0L) {
            throw new IllegalStateException("Could not create a NanoVG context");
        }
        return new NanoVGRender2D(context);
    }

    /** The raw NanoVG context pointer, for fonts and textures that register against it. */
    public long handle() {
        return context;
    }

    public void destroy() {
        NanoVGGL3.nvgDelete(context);
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void beginFrame(float width, float height, float scale) {
        guard.save();

        // Passing the scaled size as the frame size makes NanoVG's units Minecraft's
        // scaled units, so every coordinate Core hands us is already correct. The
        // pixel ratio is what keeps text and curves rasterised at native resolution.
        NanoVG.nvgBeginFrame(context, width, height, scale);

        alphaDepth = 0;
    }

    @Override
    public void endFrame() {
        NanoVG.nvgEndFrame(context);
        guard.restore();
    }

    // ---------------------------------------------------------------- shapes

    @Override
    public void rect(float x, float y, float width, float height, Color color) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgRect(context, x, y, width, height);
        fill(color);
    }

    @Override
    public void gradientRect(float x, float y, float width, float height,
                             Color topLeft, Color topRight, Color bottomRight, Color bottomLeft) {
        if (topLeft.equals(bottomLeft) && topRight.equals(bottomRight)) {
            paintRect(x, y, width, height, 0f,
                    NanoVG.nvgLinearGradient(context, x, y, x + width, y,
                            nvg(topLeft, scratchA), nvg(topRight, scratchB), scratchPaint));
            return;
        }
        if (topLeft.equals(topRight) && bottomLeft.equals(bottomRight)) {
            paintRect(x, y, width, height, 0f,
                    NanoVG.nvgLinearGradient(context, x, y, x, y + height,
                            nvg(topLeft, scratchA), nvg(bottomLeft, scratchB), scratchPaint));
            return;
        }

        // A genuine four-corner gradient is a mesh, and NanoVG only has two-stop
        // paints. Banding it vertically stays exact horizontally and goes
        // piecewise-linear vertically, which at sixteen steps does not show.
        float band = height / GRADIENT_BANDS;
        for (int i = 0; i < GRADIENT_BANDS; i++) {
            float middle = (i + 0.5f) / GRADIENT_BANDS;
            float bandY = y + i * band;

            // Overdraw by half a pixel so antialiasing cannot leave seams between rows.
            paintRect(x, bandY, width, band + 0.5f, 0f,
                    NanoVG.nvgLinearGradient(context, x, bandY, x + width, bandY,
                            nvg(topLeft.lerp(bottomLeft, middle), scratchA),
                            nvg(topRight.lerp(bottomRight, middle), scratchB),
                            scratchPaint));
        }
    }

    @Override
    public void roundRect(float x, float y, float width, float height, float radius, Color color) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgRoundedRect(context, x, y, width, height, radius);
        fill(color);
    }

    @Override
    public void roundGradient(float x, float y, float width, float height, float radius,
                              Color topLeft, Color topRight, Color bottomRight, Color bottomLeft) {
        // Banding cannot follow rounded corners without a scissor per band, so a
        // rounded gradient always uses a single paint: horizontal when the corners
        // say so, vertical otherwise.
        boolean horizontal = topLeft.equals(bottomLeft) && topRight.equals(bottomRight);
        NVGPaint paint = horizontal
                ? NanoVG.nvgLinearGradient(context, x, y, x + width, y,
                        nvg(topLeft, scratchA), nvg(topRight, scratchB), scratchPaint)
                : NanoVG.nvgLinearGradient(context, x, y, x, y + height,
                        nvg(topLeft.lerp(topRight, 0.5f), scratchA),
                        nvg(bottomLeft.lerp(bottomRight, 0.5f), scratchB), scratchPaint);

        paintRect(x, y, width, height, radius, paint);
    }

    @Override
    public void rectOutline(float x, float y, float width, float height, float thickness, Color color) {
        // NanoVG centres a stroke on its path, so the path is inset by half the
        // thickness to keep the whole stroke inside the bounds Core gave us.
        float inset = thickness / 2f;
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgRect(context, x + inset, y + inset, width - thickness, height - thickness);
        stroke(thickness, color);
    }

    @Override
    public void roundRectOutline(float x, float y, float width, float height,
                                 float radius, float thickness, Color color) {
        float inset = thickness / 2f;
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgRoundedRect(context, x + inset, y + inset,
                width - thickness, height - thickness, Math.max(0f, radius - inset));
        stroke(thickness, color);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float thickness, Color color) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgMoveTo(context, x1, y1);
        NanoVG.nvgLineTo(context, x2, y2);
        stroke(thickness, color);
    }

    @Override
    public void circle(float centerX, float centerY, float radius, Color color) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgCircle(context, centerX, centerY, radius);
        fill(color);
    }

    @Override
    public void circleOutline(float centerX, float centerY, float radius, float thickness, Color color) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgCircle(context, centerX, centerY, radius - thickness / 2f);
        stroke(thickness, color);
    }

    @Override
    public void arc(float centerX, float centerY, float radius,
                    float startAngle, float endAngle, float thickness, Color color) {
        NanoVG.nvgBeginPath(context);
        // Core measures degrees clockwise from twelve; NanoVG measures radians from
        // three, clockwise in a y-down space. One quarter turn reconciles them.
        NanoVG.nvgArc(context, centerX, centerY, radius,
                NanoVG.nvgDegToRad(startAngle - 90f),
                NanoVG.nvgDegToRad(endAngle - 90f),
                NanoVG.NVG_CW);
        stroke(thickness, color);
    }

    @Override
    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        NanoVG.nvgBeginPath(context);
        NanoVG.nvgMoveTo(context, x1, y1);
        NanoVG.nvgLineTo(context, x2, y2);
        NanoVG.nvgLineTo(context, x3, y3);
        NanoVG.nvgClosePath(context);
        fill(color);
    }

    // ------------------------------------------------------------------ text

    @Override
    public void text(Font font, String value, float x, float y, Color color) {
        if (!(font instanceof NanoVGFont typeface) || !typeface.isValid()
                || value == null || value.isEmpty()) {
            return;
        }
        typeface.apply();
        // Core passes the top-left corner, not a baseline.
        NanoVG.nvgTextAlign(context, NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_TOP);
        NanoVG.nvgFillColor(context, nvg(color, scratchFill));
        NanoVG.nvgText(context, x, y, value);
    }

    @Override
    public void textShadowed(Font font, String value, float x, float y, Color color, Color shadowColor) {
        // An offset copy rather than a real blur: blurring text in NanoVG needs an
        // offscreen framebuffer per string, which is more than a HUD shadow is worth.
        text(font, value, x + 1f, y + 1f, shadowColor);
        text(font, value, x, y, color);
    }

    // -------------------------------------------------------------- textures

    @Override
    public void texture(Texture texture, float x, float y, float width, float height, Color tint) {
        roundTexture(texture, x, y, width, height, 0f, tint);
    }

    @Override
    public void roundTexture(Texture texture, float x, float y, float width, float height,
                             float radius, Color tint) {
        if (!(texture instanceof NanoVGTexture image) || !image.isValid()) {
            return;
        }
        NVGPaint paint = NanoVG.nvgImagePattern(context, x, y, width, height, 0f,
                image.image(), tint.alphaF(), scratchPaint);
        paintRect(x, y, width, height, radius, paint);
    }

    // --------------------------------------------------------------- effects

    @Override
    public void blur(float x, float y, float width, float height, float radius) {
        // Deliberately nothing. NanoVG cannot read back what is already on screen,
        // and the contract says to leave the region untouched rather than fake it
        // with a flat fill, so callers can branch on supportsBlur() instead.
    }

    @Override
    public boolean supportsBlur() {
        return false;
    }

    @Override
    public void shadow(float x, float y, float width, float height, float radius, Color color) {
        float spread = Math.max(1f, radius);
        NVGPaint paint = NanoVG.nvgBoxGradient(context, x, y, width, height, radius, spread,
                nvg(color, scratchA), nvg(color.withAlpha(0), scratchB), scratchPaint);

        NanoVG.nvgBeginPath(context);
        NanoVG.nvgRect(context, x - spread, y - spread, width + spread * 2f, height + spread * 2f);
        // Punch the caster's own footprint out, so the shadow only ever falls outside it.
        NanoVG.nvgRoundedRect(context, x, y, width, height, radius);
        NanoVG.nvgPathWinding(context, NanoVG.NVG_HOLE);
        NanoVG.nvgFillPaint(context, paint);
        NanoVG.nvgFill(context);
    }

    // ----------------------------------------------------------------- state

    @Override
    public void pushClip(float x, float y, float width, float height) {
        NanoVG.nvgSave(context);
        // Intersect rather than replace: a scrolling panel inside a scrolling panel
        // has to end up with the overlap of both, not the inner one alone.
        NanoVG.nvgIntersectScissor(context, x, y, width, height);
    }

    @Override
    public void popClip() {
        NanoVG.nvgRestore(context);
    }

    @Override
    public void pushTransform() {
        NanoVG.nvgSave(context);
    }

    @Override
    public void popTransform() {
        NanoVG.nvgRestore(context);
    }

    @Override
    public void translate(float x, float y) {
        NanoVG.nvgTranslate(context, x, y);
    }

    @Override
    public void scale(float x, float y) {
        NanoVG.nvgScale(context, x, y);
    }

    @Override
    public void rotate(float degrees) {
        NanoVG.nvgRotate(context, NanoVG.nvgDegToRad(degrees));
    }

    @Override
    public void pushAlpha(float alpha) {
        // nvgGlobalAlpha replaces rather than multiplies and NanoVG has no getter,
        // so the running product is tracked here.
        float current = alphaDepth == 0 ? 1f : alphaStack[Math.min(alphaDepth, alphaStack.length) - 1];
        float combined = current * alpha;

        NanoVG.nvgSave(context);
        NanoVG.nvgGlobalAlpha(context, combined);

        if (alphaDepth < alphaStack.length) {
            alphaStack[alphaDepth] = combined;
        }
        alphaDepth++;
    }

    @Override
    public void popAlpha() {
        NanoVG.nvgRestore(context);
        if (alphaDepth > 0) {
            alphaDepth--;
        }
    }

    // ---------------------------------------------------------------- shared

    private void fill(Color color) {
        NanoVG.nvgFillColor(context, nvg(color, scratchFill));
        NanoVG.nvgFill(context);
    }

    private void stroke(float thickness, Color color) {
        NanoVG.nvgStrokeWidth(context, thickness);
        NanoVG.nvgStrokeColor(context, nvg(color, scratchStroke));
        NanoVG.nvgStroke(context);
    }

    private void paintRect(float x, float y, float width, float height, float radius, NVGPaint paint) {
        NanoVG.nvgBeginPath(context);
        if (radius > 0f) {
            NanoVG.nvgRoundedRect(context, x, y, width, height, radius);
        } else {
            NanoVG.nvgRect(context, x, y, width, height);
        }
        NanoVG.nvgFillPaint(context, paint);
        NanoVG.nvgFill(context);
    }

    private static NVGColor nvg(Color color, NVGColor target) {
        return NanoVG.nvgRGBA(
                (byte) color.getRed(),
                (byte) color.getGreen(),
                (byte) color.getBlue(),
                (byte) color.getAlpha(),
                target);
    }
}
