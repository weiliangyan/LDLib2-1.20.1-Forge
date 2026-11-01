package com.lowdragmc.lowdraglib2.test.uitest;

import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scene's cached compile, cancelled part-way and left to finish, on both the background and the
 * render-thread path.
 *
 * <p>The background compile used to look for its cancel only between layers, while the cancel waits
 * 200ms for it. A slower layer — a large structure, or any under an Iris shader pack — outlived the
 * wait and the closing scene nulled the buffers under it:
 * {@code Cannot store to byte/boolean array because "this.vertexBuffersUsingMark" is null}. The slow
 * hook stands in for that layer by burning CPU per block, which an interrupt cannot cut short.
 */
@LDLRegisterClient(name = "scene_compile_cancel", group = "ldlib2", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public class SceneCompileCancelScenario implements UIScenario {

    private static final String SCENE = "scene";
    private static final int SIDE = 7;
    /** About half a second a layer, well past the 200ms the cancel waits. */
    private static final long BLOCK_COST_NANOS = 10_000_000L;
    private static final long SLOW_LAYER_MS = SIDE * SIDE * BLOCK_COST_NANOS / 1_000_000L;

    private static final AtomicInteger SLOW_BLOCKS_DONE = new AtomicInteger();

    private final List<Throwable> threadFailures = new CopyOnWriteArrayList<>();
    @Nullable
    private Thread.UncaughtExceptionHandler previousHandler;

    @Override
    public void configure(ScenarioOptions options) {
        options.defaultSettleMs(60).tags("scene").guiScale(2);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("record what background threads throw", ctx -> {
                    threadFailures.clear();
                    previousHandler = Thread.getDefaultUncaughtExceptionHandler();
                    Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                        threadFailures.add(error);
                        if (previousHandler != null) previousHandler.uncaughtException(thread, error);
                    });
                })

                .group("closing the scene mid-compile", g -> {
                    openSlowScene(g);
                    g.closeScreen()
                            .waitMs(SLOW_LAYER_MS + 1000)
                            .step("the compile thread threw nothing", this::checkNoThreadFailures);
                })

                .group("replacing the blocks mid-compile", g -> {
                    openSlowScene(g);
                    g.step("swap in the same blocks without the slow hook",
                                    ctx -> scene(ctx).setRenderedCore(blocks(), null))
                            .frames(3)
                            .waitUntil("the new compile finished", ctx -> !renderer(ctx).isCompiling())
                            // the cancelled one may still be finishing a block, and must not touch the new cache
                            .waitMs(SLOW_LAYER_MS + 500)
                            .frames(3)
                            .step("the scene draws", SceneCompileCancelScenario::checkDraws)
                            .step("the compile thread threw nothing", this::checkNoThreadFailures)
                            .closeScreen();
                })

                .group("a compile left alone still finishes", g -> g
                        .openModularUI("scene", ctx -> sceneUI(ctx, null, false))
                        .awaitScreen(ModularUIScreen.class)
                        .awaitModularUI()
                        .frames(3)
                        .waitUntil("the compile finished", ctx -> !renderer(ctx).isCompiling())
                        // the upload is a queued render call
                        .frames(5)
                        .step("the scene draws", SceneCompileCancelScenario::checkDraws)
                        .screenshot("compiled_scene")
                        .closeScreen())

                .group("a render-thread compile finishes, and can be closed part-way", g -> g
                        .openModularUI("sync scene", ctx -> sceneUI(ctx, null, true))
                        .awaitScreen(ModularUIScreen.class)
                        .awaitModularUI()
                        .frames(3)
                        .check("it is still compiling a few blocks a frame", ctx -> renderer(ctx).isCompiling())
                        .waitUntil("the compile finished", ctx -> !renderer(ctx).isCompiling())
                        .frames(3)
                        .step("the scene draws", SceneCompileCancelScenario::checkDraws)
                        .closeScreen()
                        .openModularUI("sync scene", ctx -> sceneUI(ctx, null, true))
                        .awaitScreen(ModularUIScreen.class)
                        .awaitModularUI()
                        .frames(3)
                        .check("it is part-way through", ctx -> renderer(ctx).isCompiling())
                        .closeScreen()
                        .frames(3))

                .step("nothing was thrown off-thread", this::checkNoThreadFailures)
                .teardown("restore the uncaught exception handler",
                        ctx -> Thread.setDefaultUncaughtExceptionHandler(previousHandler));
    }

    private void openSlowScene(ScenarioBuilder g) {
        g.openModularUI("slow scene", ctx -> sceneUI(ctx, slowHook(), false))
                .awaitScreen(ModularUIScreen.class)
                .awaitModularUI()
                // counted rather than timed: the compile starts on whichever frame first draws the
                // scene, and a close near the end of a layer is waited out by the cancel and proves nothing
                .waitUntil("the compile is a few blocks into its first layer", ctx -> SLOW_BLOCKS_DONE.get() >= 3);
    }

    private void checkNoThreadFailures(TestContext ctx) {
        if (!threadFailures.isEmpty()) {
            ctx.attach("thread_failure", threadFailures.get(0).toString());
        }
        ctx.check("no uncaught exception off the render thread", threadFailures.isEmpty());
    }

    private static List<BlockPos> blocks() {
        var blocks = new ArrayList<BlockPos>();
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                blocks.add(new BlockPos(x, 0, z));
            }
        }
        return blocks;
    }

    private static ModularUI sceneUI(TestContext ctx, @Nullable ISceneBlockRenderHook hook, boolean syncCompile) {
        var level = new TrackedDummyWorld();
        for (var pos : blocks()) {
            level.addBlock(pos, BlockInfo.fromBlock((pos.getX() + pos.getZ()) % 2 == 0 ? Blocks.STONE : Blocks.GOLD_BLOCK));
        }
        var scene = new Scene();
        scene.createScene(level);
        scene.setRenderedCore(blocks(), hook);
        scene.useCacheBuffer();
        if (syncCompile) {
            scene.syncCompile();
            var renderer = scene.getRenderer();
            ctx.require("the scene has a renderer", renderer != null);
            renderer.setSyncCompileMaxBlocksPerFrame(2);
        }
        scene.layout(layout -> layout.width(200).height(200));
        ctx.put(SCENE, scene);
        var root = new UIElement().layout(layout -> layout.width(220).height(220)).addChild(scene);
        return new ModularUI(UI.of(root), ctx.player());
    }

    private static ISceneBlockRenderHook slowHook() {
        SLOW_BLOCKS_DONE.set(0);
        return new ISceneBlockRenderHook() {
            @Override
            public void applyVertexConsumerWrapper(Level world, BlockPos pos, BlockState state,
                                                   WorldSceneRenderer.VertexConsumerWrapper wrapperBuffer,
                                                   RenderType layer, float partialTicks) {
                if (RenderSystem.isOnRenderThread()) return;
                SLOW_BLOCKS_DONE.incrementAndGet();
                var until = System.nanoTime() + BLOCK_COST_NANOS;
                while (System.nanoTime() < until) {
                    Thread.onSpinWait();
                }
            }
        };
    }

    private static Scene scene(TestContext ctx) {
        return ctx.get(SCENE);
    }

    private static WorldSceneRenderer renderer(TestContext ctx) {
        var renderer = scene(ctx).getRenderer();
        ctx.require("the scene has a renderer", renderer != null);
        return renderer;
    }

    private static void checkDraws(TestContext ctx) {
        var image = FrameCapture.grab();
        try {
            ctx.check("the scene drew its blocks",
                    !isRegionUniform(image, scene(ctx), ctx.mc().getWindow().getGuiScale()));
        } finally {
            FrameCapture.closeQuietly(image);
        }
    }

    /** Sampled on a grid within the element's own rectangle, so the surroundings cannot answer for it. */
    private static boolean isRegionUniform(NativeImage image, UIElement element, double guiScale) {
        int x0 = Math.max(0, (int) (element.getPositionX() * guiScale));
        int y0 = Math.max(0, (int) (element.getPositionY() * guiScale));
        int x1 = Math.min(image.getWidth(), (int) ((element.getPositionX() + element.getSizeWidth()) * guiScale));
        int y1 = Math.min(image.getHeight(), (int) ((element.getPositionY() + element.getSizeHeight()) * guiScale));
        if (x1 - x0 < 2 || y1 - y0 < 2) return true;
        int first = image.getPixelRGBA(x0, y0);
        int stepX = Math.max(1, (x1 - x0) / 48);
        int stepY = Math.max(1, (y1 - y0) / 48);
        for (int y = y0; y < y1; y += stepY) {
            for (int x = x0; x < x1; x += stepX) {
                if (image.getPixelRGBA(x, y) != first) return false;
            }
        }
        return true;
    }
}
