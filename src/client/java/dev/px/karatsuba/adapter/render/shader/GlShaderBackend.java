package dev.px.karatsuba.adapter.render.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.px.core.render.Texture;
import dev.px.core.shader.Shader;
import dev.px.core.shader.ShaderBackend;
import dev.px.core.shader.ShaderException;
import dev.px.core.shader.ShaderSource;
import dev.px.core.shader.ShaderStage;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core's {@code ShaderBackend} on the game's GL context: the one class that
 * compiles and binds the client's own programs.
 *
 * <h2>Why raw programs work next to the game's</h2>
 * Checked against the 1.21.4 bytecode:
 * <ul>
 *   <li>The game keeps no record of the bound program.
 *       {@code GlStateManager._glUseProgram} goes straight to the driver, every
 *       {@code ShaderProgram.bind()} binds its own program, and every
 *       {@code unbind()} leaves program 0. So binding ours and leaving 0 behind
 *       is exactly the state the game expects between its own draws.</li>
 *   <li>{@code BufferRenderer.draw(BuiltBuffer)} (unlike
 *       {@code drawWithGlobalProgram}) uploads and draws with whatever program is
 *       bound, which is how {@link ShaderPasses} draws its geometry with ours.</li>
 *   <li>The game <em>does</em> cache the active texture unit and each unit's
 *       binding. Samplers therefore go through {@code GlStateManager}, so that
 *       cache stays true.</li>
 * </ul>
 *
 * <h2>Vertex attributes</h2>
 * A {@code VertexFormat} enables attribute <em>i</em> for its <em>i</em>th
 * element. Every pass draws {@code POSITION_TEXTURE_COLOR}, so before linking,
 * {@code Position}, {@code UV0} and {@code Color} are bound to 0, 1 and 2: the
 * game's own names, in that format's order. A vertex shader declares the ones it
 * uses by those names, and needs no layout qualifiers.
 *
 * <h2>Version</h2>
 * The game runs a 3.2 core profile, so GLSL 150. A stage that declares no
 * {@code #version} gets {@code #version 150} rather than the driver's default of
 * 110, which a core profile refuses.
 *
 * <p><b>Render thread only</b>, like everything Core's shader service calls.
 */
public final class GlShaderBackend implements ShaderBackend {

    /** Attribute names, by the location they are bound to. See the class notes. */
    static final String[] ATTRIBUTES = {"Position", "UV0", "Color"};

    private static final String DEFAULT_VERSION = "#version 150";

    /** The program uniforms are being written to, or null between passes. */
    private GlShader current;

    /** The active texture unit before the first sampler of a pass, or -1 if none was set. */
    private int activeTextureBefore = -1;

    // ------------------------------------------------------------- programs

    @Override
    public Shader compile(ShaderSource source, Map<ShaderStage, String> glsl) {
        RenderSystem.assertOnRenderThread();
        int program = GL20.glCreateProgram();
        if (program == 0) {
            throw new ShaderException("the driver could not create a program");
        }
        List<Integer> stages = new ArrayList<>(glsl.size());
        try {
            for (Map.Entry<ShaderStage, String> stage : glsl.entrySet()) {
                int id = compileStage(stage.getKey(), stage.getValue());
                stages.add(id);
                GL20.glAttachShader(program, id);
            }
            for (int i = 0; i < ATTRIBUTES.length; i++) {
                GL20.glBindAttribLocation(program, i, ATTRIBUTES[i]);
            }
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
                throw new ShaderException("link: " + GL20.glGetProgramInfoLog(program).trim());
            }
        } catch (RuntimeException failure) {
            GL20.glDeleteProgram(program);
            throw failure;
        } finally {
            // Flagged for deletion; the driver frees them once the program is gone.
            for (int id : stages) {
                GL20.glDeleteShader(id);
            }
        }
        return new GlShader(source.getName(), program);
    }

    private static int compileStage(ShaderStage stage, String text) {
        int id = GL20.glCreateShader(typeOf(stage));
        if (id == 0) {
            throw new ShaderException(stage + " stage: the driver could not create a shader");
        }
        GL20.glShaderSource(id, withVersion(text));
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(id).trim();
            GL20.glDeleteShader(id);
            throw new ShaderException(stage + " stage: " + log);
        }
        return id;
    }

    /** Core hoists a declared version to the first line, so checking the start is enough. */
    private static String withVersion(String text) {
        return text.stripLeading().startsWith("#version") ? text : DEFAULT_VERSION + "\n" + text;
    }

    private static int typeOf(ShaderStage stage) {
        return switch (stage) {
            case VERTEX -> GL20.GL_VERTEX_SHADER;
            case FRAGMENT -> GL20.GL_FRAGMENT_SHADER;
            case GEOMETRY -> GL32.GL_GEOMETRY_SHADER;
            // Compute needs GL 4.3; the game asks for 3.2.
            case COMPUTE -> throw new ShaderException("compute stage: needs GL 4.3, the game runs 3.2");
        };
    }

    @Override
    public boolean supports(ShaderStage stage) {
        return stage != ShaderStage.COMPUTE;
    }

    // -------------------------------------------------------------- binding

    @Override
    public void bind(Shader shader) {
        current = (GlShader) shader;
        GlStateManager._glUseProgram(current.program());
    }

    /**
     * Leaves program 0, as every game draw does, and puts the active texture unit
     * back if a sampler moved it.
     */
    @Override
    public void unbind() {
        current = null;
        GlStateManager._glUseProgram(0);
        if (activeTextureBefore != -1) {
            GlStateManager._activeTexture(activeTextureBefore);
            activeTextureBefore = -1;
        }
    }

    // ------------------------------------------------------------- uniforms

    @Override
    public void floats(String name, float[] values, int count) {
        int location = location(name);
        if (location < 0) {
            return;
        }
        switch (count) {
            case 1 -> GL20.glUniform1f(location, values[0]);
            case 2 -> GL20.glUniform2f(location, values[0], values[1]);
            case 3 -> GL20.glUniform3f(location, values[0], values[1], values[2]);
            case 4 -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
            default -> throw new ShaderException("float uniform " + name + " has " + count + " components");
        }
    }

    @Override
    public void ints(String name, int[] values, int count) {
        int location = location(name);
        if (location < 0) {
            return;
        }
        switch (count) {
            case 1 -> GL20.glUniform1i(location, values[0]);
            case 2 -> GL20.glUniform2i(location, values[0], values[1]);
            case 3 -> GL20.glUniform3i(location, values[0], values[1], values[2]);
            case 4 -> GL20.glUniform4i(location, values[0], values[1], values[2], values[3]);
            default -> throw new ShaderException("int uniform " + name + " has " + count + " components");
        }
    }

    @Override
    public void matrix(String name, float[] values, int order) {
        int location = location(name);
        if (location < 0) {
            return;
        }
        int size = order * order;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(size);
            buffer.put(values, 0, size).flip();
            switch (order) {
                case 2 -> GL20.glUniformMatrix2fv(location, false, buffer);
                case 3 -> GL20.glUniformMatrix3fv(location, false, buffer);
                case 4 -> GL20.glUniformMatrix4fv(location, false, buffer);
                default -> throw new ShaderException("matrix uniform " + name + " has order " + order);
            }
        }
    }

    /**
     * Binds the texture to the unit through {@code GlStateManager}, whose cache of
     * unit bindings the game trusts, and points the uniform at the unit.
     *
     * <p>A texture that is not a {@link GlTexture}, or has been disposed, leaves the
     * uniform alone: the shader samples whatever the unit held, which is a visible
     * mistake rather than a crash.
     */
    @Override
    public void sampler(String name, Texture texture, int unit) {
        int location = location(name);
        if (location < 0 || !(texture instanceof GlTexture gl)) {
            return;
        }
        int id = gl.glTextureId();
        if (id == 0) {
            return;
        }
        if (activeTextureBefore == -1) {
            activeTextureBefore = GlStateManager._getActiveTexture();
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(id);
        GL20.glUniform1i(location, unit);
    }

    private int location(String name) {
        return current == null ? -1 : current.location(name);
    }
}
