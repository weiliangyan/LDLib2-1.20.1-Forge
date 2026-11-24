package com.lowdragmc.lowdraglib2.client.font.glyph;

import com.lowdragmc.lowdraglib2.LDLib2;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Rasterizes TrueType/OpenType outlines and turns them into signed distance fields.
 * <p>
 * Rasterization goes through stb_truetype, the rasterizer Minecraft itself bundles and uses for its own TTF
 * fonts. On 1.21 LDLib used FreeType for this, but 1.20.1 ships no {@code lwjgl-freetype} binding at all: the
 * first text draw threw {@code NoClassDefFoundError} and the whole renderer had to be turned off. stb is on the
 * classpath on every Minecraft version, needs no native download, and is the same library that produces the
 * vanilla advances, so nothing extra has to be shipped with the mod.
 * <p>
 * Metrics are in the same 9 pixel design space vanilla uses, with one em scaled to {@code emPixels} device
 * pixels before dividing by the oversample factor. Scaling by the em (rather than by ascent minus descent, as
 * {@code stbtt_ScaleForPixelHeight} does) is what keeps LDLib's text the size its stylesheets ask for:
 * {@code font-size} means the em, and a font whose ascender and descender together exceed one em would
 * otherwise render noticeably smaller than requested.
 */
@OnlyIn(Dist.CLIENT)
public class TrueTypeSdfSource implements GlyphSource {
    /**
     * Padding around each glyph, also the distance in pixels the field spreads over.
     */
    private static final int PADDING = 6;
    /**
     * How close {@code size * oversample} has to be to a whole pixel size for the exact size raster path to be
     * able to map one texel to one device pixel.
     */
    private static final float EXACT_TOLERANCE = 0.01f;
    /**
     * Baseline of the 9 pixel design space, plus the offset {@code BakedGlyph#render} takes off again. Placing
     * glyphs against this baseline is what keeps TTF text on the same line as the bitmap and unifont glyphs it
     * is mixed with, and inside the line box the caret and selection are drawn in.
     */
    private static final float BASELINE_TOP = 7f + QUAD_OFFSET;

    /**
     * stb keeps a pointer into this buffer, so it has to stay off heap and outlive the font info.
     */
    @Nullable
    private ByteBuffer fontData;
    @Nullable
    private STBTTFontinfo info;
    /**
     * The design size from the font definition, i.e. what one em is worth in design space.
     */
    private final float size;
    /**
     * Font units to pixels at the configured em size, from {@code stbtt_ScaleForMappingEmToPixels}.
     */
    private final float emScale;
    private final float oversample;
    private final float shiftX;
    private final float shiftY;
    private final IntSet skip = new IntArraySet();

    private TrueTypeSdfSource(ByteBuffer fontData, STBTTFontinfo info, float size, int emPixels,
                              float shiftX, float shiftY, String skip) {
        this.fontData = fontData;
        this.info = info;
        this.size = size;
        this.emScale = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, emPixels);
        this.oversample = emPixels / size;
        this.shiftX = shiftX;
        this.shiftY = shiftY;
        skip.codePoints().forEach(this.skip::add);
    }

    /**
     * @param emPixels pixel height one em is rasterized at, higher keeps corners sharper at large font sizes
     */
    @Nullable
    public static TrueTypeSdfSource load(ResourceManager resourceManager, TrueTypeGlyphProviderDefinition definition,
                                         int emPixels) {
        var file = definition.location().withPrefix("font/");
        ByteBuffer data = null;
        STBTTFontinfo info = null;
        try (InputStream stream = resourceManager.open(file)) {
            var bytes = stream.readAllBytes();
            data = MemoryUtil.memAlloc(bytes.length);
            data.put(bytes).flip();
            info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, data)) {
                throw new IOException("Invalid ttf font " + file);
            }
            return new TrueTypeSdfSource(data, info, definition.size(), emPixels,
                    definition.shift().x(), definition.shift().y(), definition.skip());
        } catch (IOException | RuntimeException | LinkageError e) {
            if (info != null) {
                info.free();
            }
            if (data != null) {
                MemoryUtil.memFree(data);
            }
            LDLib2.LOGGER.error("Failed to load font {}", file, e);
            return null;
        }
    }

    /**
     * Advance in design space: the font's hmetric times {@code size} over its units per em, which is what
     * scaling to {@code emPixels} and dividing by the oversample factor works out to.
     */
    @Nullable
    @Override
    public synchronized GlyphMetrics metrics(int codepoint) {
        var index = glyphIndex(codepoint);
        if (index == 0) {
            return null;
        }
        try (var stack = MemoryStack.stackPush()) {
            var advance = stack.ints(0);
            var leftSideBearing = stack.ints(0);
            STBTruetype.stbtt_GetGlyphHMetrics(info, index, advance, leftSideBearing);
            return GlyphMetrics.of(advance.get(0) * emScale / oversample);
        }
    }

    @Nullable
    @Override
    public synchronized RawGlyph glyph(int codepoint) {
        var index = glyphIndex(codepoint);
        if (index == 0) {
            return null;
        }
        var metrics = metrics(codepoint);
        if (metrics == null) {
            return null;
        }
        try (var stack = MemoryStack.stackPush()) {
            var box = glyphBox(stack, index, emScale);
            if (box.width() <= 0 || box.height() <= 0) {
                return RawGlyph.space(metrics.advance());
            }
            // fromCoverage, not fromMask: the rasterizer's grey levels say where the outline runs inside each
            // edge pixel, and throwing that away is what makes large text look jagged
            var coverage = render(index, box.width(), box.height(), emScale);
            var sdf = DistanceTransform.fromCoverage(coverage, box.width(), box.height(), PADDING, PADDING);
            var designPadding = PADDING / oversample;
            var left = box.x0() / oversample + shiftX - designPadding;
            var top = BASELINE_TOP + box.y0() / oversample + shiftY - designPadding;
            var width = box.width() + PADDING * 2;
            var height = box.height() + PADDING * 2;
            return RawGlyph.of(sdf, width, height, left, top,
                    left + width / oversample, top + height / oversample, metrics.advance());
        }
    }

    @Nullable
    @Override
    public synchronized RawGlyph rasterGlyph(int codepoint, float oversample, boolean exactOnly) {
        var index = glyphIndex(codepoint);
        if (index == 0) {
            return null;
        }
        var exactPixelSize = size * oversample;
        var pixelSize = Math.round(exactPixelSize);
        if (pixelSize <= 0) {
            return null;
        }
        // Under nearest sampling one texel has to land on one device pixel, or columns get dropped or doubled.
        // The tolerance is relative because the same absolute slip matters far less on a tall glyph.
        if (exactOnly && Math.abs(exactPixelSize - pixelSize) / pixelSize > EXACT_TOLERANCE) {
            return null;
        }
        // the advance stays in the distance field's design space, layout must not shift between buckets
        var metrics = metrics(codepoint);
        if (metrics == null) {
            return null;
        }
        var scale = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, pixelSize);
        try (var stack = MemoryStack.stackPush()) {
            var box = glyphBox(stack, index, scale);
            if (box.width() <= 0 || box.height() <= 0) {
                return RawGlyph.space(metrics.advance());
            }
            var coverage = render(index, box.width(), box.height(), scale);
            var left = box.x0() / oversample + shiftX;
            var top = BASELINE_TOP + box.y0() / oversample + shiftY;
            return RawGlyph.of(coverage, box.width(), box.height(), left, top,
                    left + box.width() / oversample, top + box.height() / oversample, metrics.advance());
        }
    }

    /**
     * The integer bitmap box stb will fill for this glyph at {@code scale}: relative to the pen position, with
     * the baseline at y = 0, so {@code y0} is the (negative) ascent of the ink and {@code y1} its descent.
     */
    private record Box(int x0, int y0, int x1, int y1) {
        int width() {
            return x1 - x0;
        }

        int height() {
            return y1 - y0;
        }
    }

    private Box glyphBox(MemoryStack stack, int index, float scale) {
        var x0 = stack.ints(0);
        var y0 = stack.ints(0);
        var x1 = stack.ints(0);
        var y1 = stack.ints(0);
        STBTruetype.stbtt_GetGlyphBitmapBox(info, index, scale, scale, x0, y0, x1, y1);
        return new Box(x0.get(0), y0.get(0), x1.get(0), y1.get(0));
    }

    /**
     * Renders one glyph into a tightly sized 8 bit coverage bitmap. The buffer is exactly the box stb reported
     * and its stride is the bitmap width, which is what keeps the rasterizer inside it.
     */
    private byte[] render(int index, int width, int height, float scale) {
        var buffer = MemoryUtil.memAlloc(width * height);
        try {
            MemoryUtil.memSet(buffer, 0);
            STBTruetype.stbtt_MakeGlyphBitmapSubpixel(info, buffer, width, height, width, scale, scale,
                    0f, 0f, index);
            var coverage = new byte[width * height];
            buffer.get(coverage);
            return coverage;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private int glyphIndex(int codepoint) {
        var info = this.info;
        if (info == null || skip.contains(codepoint)) {
            return 0;
        }
        return STBTruetype.stbtt_FindGlyphIndex(info, codepoint);
    }

    @Override
    public synchronized void close() {
        if (info != null) {
            info.free();
            info = null;
        }
        if (fontData != null) {
            MemoryUtil.memFree(fontData);
            fontData = null;
        }
    }
}
