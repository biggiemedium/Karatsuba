package dev.px.karatsuba.adapter.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.px.core.shader.Shader;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.lwjgl.opengl.GL20;

/**
 * One linked GL program, and the uniform locations asked of it so far.
 *
 * <p>Locations are looked up once per name and kept, including the misses: a
 * uniform the driver optimised away answers -1 every time, and asking again every
 * frame would be a driver round trip per uniform per pass for nothing. Core's
 * builtins ({@code uTime}, {@code uResolution}, {@code uMouse}) are set on every
 * pass whether a program declares them or not, so the misses are the common case.
 */
final class GlShader implements Shader {

    /** Not yet asked. GL's own "no such uniform" is -1, so this has to be something else. */
    private static final int UNKNOWN = -2;

    private final String name;
    private final Object2IntOpenHashMap<String> locations = new Object2IntOpenHashMap<>();

    private int program;

    GlShader(String name, int program) {
        this.name = name;
        this.program = program;
        locations.defaultReturnValue(UNKNOWN);
    }

    int program() {
        return program;
    }

    /** @return the uniform's location, or -1 if the program has no such active uniform */
    int location(String uniform) {
        int location = locations.getInt(uniform);
        if (location == UNKNOWN) {
            location = GL20.glGetUniformLocation(program, uniform);
            locations.put(uniform, location);
        }
        return location;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isValid() {
        return program != 0;
    }

    /**
     * Deletes the program on the render thread.
     *
     * <p>Core may dispose from the shutdown path, which is not necessarily the
     * render thread, and GL objects belong to the thread whose context made them.
     * Off it, the delete is queued; one queued after the game loop has stopped never
     * runs, which is fine, because the context goes with the process.
     */
    @Override
    public void dispose() {
        int doomed = program;
        if (doomed == 0) {
            return;
        }
        program = 0;
        locations.clear();
        if (RenderSystem.isOnRenderThread()) {
            GL20.glDeleteProgram(doomed);
        } else {
            RenderSystem.recordRenderCall(() -> GL20.glDeleteProgram(doomed));
        }
    }

    @Override
    public String toString() {
        return "GlShader[" + name + ", program " + program + "]";
    }
}
