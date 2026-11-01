package com.lowdragmc.lowdraglib2.uitest.record;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Converts a region of a colour texture into one I420 frame (BT.709, limited range) on the GPU and
 * reads it into a pixel buffer, so the CPU downloads 1.5 bytes a pixel instead of 4 and ffmpeg has no
 * colour conversion left to do.
 *
 * <p>The frame is laid out exactly as ffmpeg's {@code yuv420p} expects it in memory: the target is an
 * R8 texture {@code width} wide and {@code height * 3 / 2} tall, and each fragment computes which byte
 * of the Y, U or V plane it is from its linear index.
 */
@OnlyIn(Dist.CLIENT)
final class YuvConverter implements AutoCloseable {

    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D Source;
            uniform vec4 Region;
            uniform ivec2 OutSize;
            uniform bool Identity;
            out vec4 fragColor;

            vec3 texel(ivec2 p) {
                ivec2 size = textureSize(Source, 0);
                p = clamp(p, ivec2(0), size - 1);
                return texelFetch(Source, ivec2(p.x, size.y - 1 - p.y), 0).rgb;
            }

            vec3 at(vec2 o) {
                if (Identity) return texel(ivec2(Region.xy) + ivec2(o));
                vec2 p = Region.xy + (o + 0.5) * Region.zw / vec2(OutSize) - 0.5;
                ivec2 i = ivec2(floor(p));
                vec2 f = p - vec2(i);
                return mix(mix(texel(i), texel(i + ivec2(1, 0)), f.x),
                           mix(texel(i + ivec2(0, 1)), texel(i + ivec2(1, 1)), f.x), f.y);
            }

            void main() {
                int w = OutSize.x;
                int h = OutSize.y;
                int index = int(gl_FragCoord.y) * w + int(gl_FragCoord.x);
                int lumaSize = w * h;
                float value;
                if (index < lumaSize) {
                    vec3 c = at(vec2(index % w, index / w));
                    value = 16.0 + 219.0 * dot(c, vec3(0.2126, 0.7152, 0.0722));
                } else {
                    int cw = w / 2;
                    int chromaSize = cw * (h / 2);
                    int j = index - lumaSize;
                    bool isV = j >= chromaSize;
                    if (isV) j -= chromaSize;
                    vec2 o = vec2(j % cw, j / cw) * 2.0;
                    vec3 c = (at(o) + at(o + vec2(1.0, 0.0)) + at(o + vec2(0.0, 1.0)) + at(o + vec2(1.0, 1.0))) * 0.25;
                    value = isV ? 128.0 + 224.0 * dot(c, vec3(0.5, -0.45415, -0.04585))
                                : 128.0 + 224.0 * dot(c, vec3(-0.11457, -0.38543, 0.5));
                }
                fragColor = vec4(value / 255.0, 0.0, 0.0, 1.0);
            }
            """;

    final int width;
    final int height;
    final int rows;
    final int frameBytes;

    private final int program;
    private final int vertexArray;
    private final int texture;
    private final int framebuffer;
    private final int sourceUniform;
    private final int regionUniform;
    private final int outSizeUniform;
    private final int identityUniform;

    /** @param width  output width, even */
    YuvConverter(int width, int height) {
        if (width <= 0 || height <= 0 || (width & 1) != 0 || (height & 1) != 0) {
            throw new IllegalArgumentException("I420 needs a positive, even frame size, got " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.rows = height * 3 / 2;
        this.frameBytes = width * rows;

        program = link(compile(GL_VERTEX_SHADER, VERTEX), compile(GL_FRAGMENT_SHADER, FRAGMENT));
        sourceUniform = glGetUniformLocation(program, "Source");
        regionUniform = glGetUniformLocation(program, "Region");
        outSizeUniform = glGetUniformLocation(program, "OutSize");
        identityUniform = glGetUniformLocation(program, "Identity");
        vertexArray = glGenVertexArrays();

        var previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL_TEXTURE0);
        var previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        texture = glGenTextures();
        GlStateManager._bindTexture(texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, rows, 0, GL_RED, GL_UNSIGNED_BYTE, 0L);
        GlStateManager._bindTexture(previousTexture);
        GlStateManager._activeTexture(previousUnit);

        var previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        var previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        framebuffer = glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        var status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            close();
            throw new IllegalStateException("YUV framebuffer is incomplete: 0x" + Integer.toHexString(status));
        }
    }

    /**
     * Converts {@code region} of {@code sourceTexture}, scaled to this converter's size, and starts an
     * asynchronous read of the result into {@code pixelBuffer}. Leaves every piece of GL state it touches
     * as it found it.
     *
     * @param regionX from the source's top-left corner, in texels; fractional for a moving camera
     */
    void convertInto(int sourceTexture, float regionX, float regionY, float regionWidth, float regionHeight, int pixelBuffer) {
        var previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        var previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        var previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        var previousVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        var previousPackAlignment = glGetInteger(GL_PACK_ALIGNMENT);
        var blend = glIsEnabled(GL_BLEND);
        var scissor = glIsEnabled(GL_SCISSOR_TEST);
        var cull = glIsEnabled(GL_CULL_FACE);
        var previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL_TEXTURE0);
        var previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);

        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            var colorMask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
            try {
                draw(sourceTexture, regionX, regionY, regionWidth, regionHeight, pixelBuffer);
            } finally {
                GlStateManager._pixelStore(GL_PACK_ALIGNMENT, previousPackAlignment);
                GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                GlStateManager._glBindVertexArray(previousVertexArray);
                GlStateManager._bindTexture(previousTexture);
                GlStateManager._activeTexture(previousUnit);
                GlStateManager._glUseProgram(previousProgram);
                GlStateManager._colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                if (cull) GlStateManager._enableCull();
                if (scissor) GlStateManager._enableScissorTest();
                if (blend) GlStateManager._enableBlend();
                GlStateManager._viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw);
                GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
            }
        }
    }

    private void draw(int sourceTexture, float regionX, float regionY, float regionWidth, float regionHeight, int pixelBuffer) {
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        GlStateManager._viewport(0, 0, width, rows);
        GlStateManager._disableBlend();
        GlStateManager._disableScissorTest();
        GlStateManager._disableCull();
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._glUseProgram(program);
        glUniform1i(sourceUniform, 0);
        glUniform4f(regionUniform, regionX, regionY, regionWidth, regionHeight);
        glUniform2i(outSizeUniform, width, height);
        boolean identity = regionWidth == width && regionHeight == height
                && regionX == Math.floor(regionX) && regionY == Math.floor(regionY);
        glUniform1i(identityUniform, identity ? 1 : 0);
        GlStateManager._bindTexture(sourceTexture);
        GlStateManager._glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        GlStateManager._pixelStore(GL_PACK_ALIGNMENT, 1);
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelBuffer);
        glReadPixels(0, 0, width, rows, GL_RED, GL_UNSIGNED_BYTE, 0L);
    }

    @Override
    public void close() {
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(texture);
        glDeleteVertexArrays(vertexArray);
        glDeleteProgram(program);
    }

    private static int compile(int type, String source) {
        var shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            var log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("YUV shader failed to compile: " + log);
        }
        return shader;
    }

    private static int link(int vertex, int fragment) {
        var program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glBindFragDataLocation(program, 0, "fragColor");
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            var log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("YUV shader failed to link: " + log);
        }
        return program;
    }
}
