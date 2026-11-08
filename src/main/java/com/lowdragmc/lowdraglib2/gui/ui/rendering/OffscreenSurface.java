package com.lowdragmc.lowdraglib2.gui.ui.rendering;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * A UI destination that is not the game window: a render target of its own, presented somewhere else.
 *
 * <p>Its gui scale follows the game window's unless {@link #setGuiScaleSetting} gives it one of its
 * own. Everything that turns gui units into pixels while drawing into it — the projection, the
 * scissor box, input mapping, text — reads {@link #guiScale()} here, so changing it never touches the
 * game window. Code that reads {@code Minecraft.getWindow().getGuiScale()} directly instead still
 * gets the game's.
 */
@OnlyIn(Dist.CLIENT)
public final class OffscreenSurface implements UISurface {

    /** {@link #setGuiScaleSetting}: use the game window's scale. */
    public static final int FOLLOW_GAME = -1;

    @Nullable
    private TextureTarget target;
    private long windowHandle;
    private int screenWidth;
    private int screenHeight;
    private int guiScaleSetting = FOLLOW_GAME;

    /**
     * @param windowHandle the GLFW window this will be presented in, for cursor and key queries
     * @param screenWidth  window size in screen coordinates, which is not the framebuffer size on a
     *                     HiDPI display
     */
    public OffscreenSurface(long windowHandle, int framebufferWidth, int framebufferHeight,
                            int screenWidth, int screenHeight) {
        this.windowHandle = windowHandle;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        resize(framebufferWidth, framebufferHeight, screenWidth, screenHeight);
    }

    /**
     * Grows or shrinks the target. Cheap when nothing changed; a real reallocation otherwise, since
     * {@code RenderTarget#resize} destroys and recreates the attachments.
     */
    public void resize(int framebufferWidth, int framebufferHeight, int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        var width = Math.max(1, framebufferWidth);
        var height = Math.max(1, framebufferHeight);
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            target.setClearColor(0, 0, 0, 1);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
    }

    @Override
    public RenderTarget target() {
        if (target == null) {
            throw new IllegalStateException("Surface has been destroyed");
        }
        return target;
    }

    /**
     * The colour attachment to blit from, or {@code -1} once destroyed.
     */
    public int colorTextureId() {
        return target == null ? -1 : target.getColorTextureId();
    }

    @Override
    public double guiScale() {
        if (guiScaleSetting == FOLLOW_GAME) {
            return Minecraft.getInstance().getWindow().getGuiScale();
        }
        return calculateScale(guiScaleSetting);
    }

    /**
     * @param setting {@link #FOLLOW_GAME}; {@code 0} for auto, the largest scale that keeps the UI at
     *                least 320x240 as the game picks its own; or a fixed scale, lowered the same way
     *                when the surface is too small for it
     */
    public void setGuiScaleSetting(int setting) {
        this.guiScaleSetting = setting < 0 ? FOLLOW_GAME : setting;
    }

    public int getGuiScaleSetting() {
        return guiScaleSetting;
    }

    /**
     * {@code Window#calculateScale} against this surface's framebuffer: {@code requested}, or less if
     * the UI would end up smaller than 320x240. {@code 0} asks for the largest that fits.
     */
    public int calculateScale(int requested) {
        var width = framebufferWidth();
        var height = framebufferHeight();
        var scale = 1;
        while (scale != requested && scale < width && scale < height
                && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
            scale++;
        }
        if (Minecraft.getInstance().isEnforceUnicode() && scale % 2 != 0) {
            scale++;
        }
        return scale;
    }

    @Override
    public int screenWidth() {
        return screenWidth;
    }

    @Override
    public int screenHeight() {
        return screenHeight;
    }

    @Override
    public long windowHandle() {
        return windowHandle;
    }

    public void destroy() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
