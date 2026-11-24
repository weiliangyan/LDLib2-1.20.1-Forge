package com.lowdragmc.lowdraglib2.uitest.record;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL32C.*;

/**
 * One take: every frame rendered while it is open goes, in order, into one video file.
 *
 * <p>Per frame, on the render thread: a GPU pass converts the frame to I420 and starts an asynchronous
 * read into one of {@value #SLOTS} pixel buffers. A slot is only mapped once the ring wraps round to
 * it, by when its read finished long ago, so the render thread never waits on the GPU. The mapped bytes
 * are copied into a pooled array and handed to {@link FfmpegEncoder}'s writer thread.
 */
@OnlyIn(Dist.CLIENT)
public final class VideoRecorder {

    private static final int SLOTS = 3;
    private static final int ENCODER_BUFFERS = 4;
    private static final long FENCE_TIMEOUT_NANOS = 5_000_000_000L;

    private final int fps;
    private final Path output;
    private final int sourceWidth;
    private final int sourceHeight;
    private float regionX;
    private float regionY;
    private float regionWidth;
    private float regionHeight;
    private final YuvConverter converter;
    private final FfmpegEncoder encoder;
    @Nullable
    private final RecordingOverlay overlay;
    @Nullable
    private TextureTarget composite;
    private final int[] pixelBuffers = new int[SLOTS];
    private final long[] fences = new long[SLOTS];
    private ByteBuffer mapped;
    private long issued;
    private long collected;
    private boolean finished;

    private final long startedNanos = System.nanoTime();
    private long finishedNanos;
    private long convertNanos;
    private long collectNanos;

    private VideoRecorder(RecordSettings settings, int fps, Path output, @Nullable Path poster, long posterFrame,
                          RenderTarget source, @Nullable RecordingOverlay overlay, float[] region,
                          int outWidth, int outHeight) throws IOException {
        if (VideoClock.isStepping()) throw new IllegalStateException("another take is still open");
        this.fps = fps;
        this.overlay = overlay;
        this.output = output;
        this.sourceWidth = source.width;
        this.sourceHeight = source.height;
        setRegion(region[0], region[1], region[2], region[3]);
        this.converter = new YuvConverter(outWidth, outHeight);
        try {
            Files.createDirectories(output.getParent());
            var name = output.getFileName().toString();
            var log = output.resolveSibling(name.substring(0, name.lastIndexOf('.')) + ".ffmpeg.log");
            this.encoder = FfmpegEncoder.start(settings, fps, outWidth, outHeight, output, log, ENCODER_BUFFERS,
                    poster, posterFrame);
        } catch (IOException | RuntimeException e) {
            converter.close();
            throw e;
        }
        for (int i = 0; i < SLOTS; i++) {
            pixelBuffers[i] = glGenBuffers();
            GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelBuffers[i]);
            glBufferData(GL_PIXEL_PACK_BUFFER, converter.frameBytes, GL_STREAM_READ);
        }
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        VideoClock.beginStepping(fps);
    }

    /**
     * Opens a take and starts stepping {@link VideoClock}: from the next frame on, each
     * {@link #captureFrame} advances time by one video frame until {@link #finish()} or {@link #abort()}.
     *
     * @param region      {@code x, y, width, height} of {@code source} to record, in framebuffer pixels from
     *                    the top-left; scaled to {@code outWidth x outHeight}, and movable with {@link #setRegion}
     * @param outWidth    the video's width, even
     * @param poster      where to write a still of frame {@code posterFrame}, or {@code null} for none
     * @param overlay     drawn over every frame of the video only; the game's own framebuffer is untouched
     * @throws IOException when ffmpeg cannot be started
     * @throws IllegalStateException when another take is still open
     */
    public static VideoRecorder open(RecordSettings settings, int fps, Path output, @Nullable Path poster, long posterFrame,
                                     RenderTarget source, @Nullable RecordingOverlay overlay, float[] region,
                                     int outWidth, int outHeight) throws IOException {
        return new VideoRecorder(settings, fps, output, poster, posterFrame, source, overlay, region, outWidth, outHeight);
    }

    /** Moves the recorded rectangle, in framebuffer pixels; takes effect from the next captured frame. */
    public void setRegion(float x, float y, float width, float height) {
        regionWidth = Math.max(2, Math.min(width, sourceWidth));
        regionHeight = Math.max(2, Math.min(height, sourceHeight));
        regionX = Math.max(0, Math.min(x, sourceWidth - regionWidth));
        regionY = Math.max(0, Math.min(y, sourceHeight - regionHeight));
    }

    public int fps() {
        return fps;
    }

    /** Seconds of video captured so far: the time at which the next frame will appear. */
    public double videoSeconds() {
        return issued / (double) fps;
    }

    /** Converts and queues the frame just rendered into {@code source}. Call once per frame, after rendering. */
    public void captureFrame(RenderTarget source) throws IOException {
        if (finished) throw new IllegalStateException("take already finished");
        if (source.width != sourceWidth || source.height != sourceHeight) {
            throw new IllegalStateException("the frame changed size during a take: " + sourceWidth + "x"
                    + sourceHeight + " -> " + source.width + "x" + source.height);
        }
        if (issued - collected == SLOTS) {
            collectOldest();
        }
        long started = System.nanoTime();
        var frame = overlay == null ? source : composite(source);
        int slot = (int) (issued % SLOTS);
        converter.convertInto(frame.getColorTextureId(), regionX, regionY, regionWidth, regionHeight, pixelBuffers[slot]);
        fences[slot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        issued++;
        convertNanos += System.nanoTime() - started;
        VideoClock.advanceFrame();
    }

    /** A copy of {@code source} with the overlay drawn on it. Leaves {@code source} bound for writing, as it was. */
    private RenderTarget composite(RenderTarget source) {
        if (composite == null) {
            composite = new TextureTarget(source.width, source.height, true, Minecraft.ON_OSX);
        }
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        try {
            GlStateManager._disableScissorTest();
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, composite.frameBufferId);
            glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, source.width, source.height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            composite.bindWrite(true);
            RenderSystem.clearDepth(1);
            RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            overlay.draw(composite);
        } finally {
            source.bindWrite(true);
            if (scissor) GlStateManager._enableScissorTest();
        }
        return composite;
    }

    private void collectOldest() throws IOException {
        long started = System.nanoTime();
        int slot = (int) (collected % SLOTS);
        var status = glClientWaitSync(fences[slot], GL_SYNC_FLUSH_COMMANDS_BIT, FENCE_TIMEOUT_NANOS);
        glDeleteSync(fences[slot]);
        fences[slot] = 0;
        if (status == GL_TIMEOUT_EXPIRED || status == GL_WAIT_FAILED) {
            throw new IOException("frame readback never completed (glClientWaitSync 0x" + Integer.toHexString(status) + ")");
        }
        var frame = encoder.acquire();
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelBuffers[slot]);
        try {
            mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, converter.frameBytes, GL_MAP_READ_BIT, mapped);
            if (mapped == null) throw new IOException("could not map the readback buffer");
            mapped.get(0, frame, 0, converter.frameBytes);
            glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
        } finally {
            GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        }
        encoder.submit(frame);
        collected++;
        collectNanos += System.nanoTime() - started;
    }

    /** Drains the frames still in flight and closes ffmpeg's input. Poll {@link #isDone()} afterwards. */
    public void finish() throws IOException {
        if (finished) return;
        VideoClock.endStepping();
        try {
            while (collected < issued) {
                collectOldest();
            }
        } finally {
            finished = true;
            finishedNanos = System.nanoTime();
            encoder.closeInput();
            releaseGl();
        }
    }

    /** Stops without draining, for a take that cannot complete. The file is left however ffmpeg leaves it. */
    public void abort() {
        VideoClock.endStepping();
        if (!finished) {
            finished = true;
            finishedNanos = System.nanoTime();
            releaseGl();
        }
        encoder.kill();
    }

    /** Whether ffmpeg has exited. Only meaningful after {@link #finish()}. */
    public boolean isDone() {
        return encoder.isFinished();
    }

    public int exitCode() {
        return encoder.exitCode();
    }

    public Path output() {
        return output;
    }

    public Path log() {
        return encoder.log();
    }

    public long frames() {
        return collected;
    }

    public String summary() {
        long frames = Math.max(1, collected);
        double wallSeconds = ((finished ? finishedNanos : System.nanoTime()) - startedNanos) / 1e9;
        double videoSeconds = collected / (double) fps;
        return "%d frames %dx%d (%.2fs of video) in %.2fs wall, %.1f fps, %.2fx real time; per frame: convert %.2fms, collect %.2fms (encoder backpressure %.2fms)"
                .formatted(collected, converter.width, converter.height, videoSeconds, wallSeconds,
                        collected / Math.max(1e-9, wallSeconds), videoSeconds / Math.max(1e-9, wallSeconds),
                        convertNanos / 1e6 / Math.max(1, issued), collectNanos / 1e6 / frames,
                        encoder.acquireWaitNanos() / 1e6 / frames);
    }

    private void releaseGl() {
        for (int i = 0; i < SLOTS; i++) {
            if (fences[i] != 0) {
                glDeleteSync(fences[i]);
                fences[i] = 0;
            }
            if (pixelBuffers[i] != 0) {
                glDeleteBuffers(pixelBuffers[i]);
                pixelBuffers[i] = 0;
            }
        }
        converter.close();
        if (composite != null) {
            composite.destroyBuffers();
            composite = null;
        }
    }
}
