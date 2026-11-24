package com.lowdragmc.lowdraglib2.uitest;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.uitest.mp.MPRunConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Arms {@link MPServerRunner} when this process is the dedicated server of a multi-process run —
 * that is, when the {@code runMpServer} Gradle run set the mptest role/hub system properties.
 * Inert everywhere else, including ordinary {@code runServer} launches.
 */
@Mod.EventBusSubscriber(modid = LDLib2.MOD_ID)
public final class MPServerBootstrap {

    @Nullable
    private static MPServerRunner runner;

    private MPServerBootstrap() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Platform.isDevEnv()) return;
        var config = MPRunConfig.fromSystemProperties();
        if (config == null || !config.isServer()) return;
        runner = new MPServerRunner(config, event.getServer());
        runner.start();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && runner != null) {
            runner.tick();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (runner != null) {
            runner.close();
            runner = null;
        }
    }
}
