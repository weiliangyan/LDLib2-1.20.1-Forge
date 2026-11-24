package com.lowdragmc.lowdraglib2.client;

import com.lowdragmc.lowdraglib2.CommonProxy;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.font.LDFontManager;
import com.lowdragmc.lowdraglib2.client.font.LDFontStatsOverlay;
import com.lowdragmc.lowdraglib2.client.model.forge.LDLRendererModel;
import com.lowdragmc.lowdraglib2.client.renderer.ATESRRendererProvider;
import com.lowdragmc.lowdraglib2.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib2.client.shader.LDLibShaders;
import com.lowdragmc.lowdraglib2.core.mixins.ParticleEngineAccessor;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import com.lowdragmc.lowdraglib2.editor.resource.PackResourceManager;
import com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUIClientElementComponent;
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent;
import com.lowdragmc.lowdraglib2.integration.kjs.ui.LDKJSMenuTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public class ClientProxy {
    public static final Minecraft minecraft = Minecraft.getInstance();

    public ClientProxy(IEventBus eventBus, ModLoadingContext modContainer) {
        eventBus.register(this);
        modContainer.registerConfig(ModConfig.Type.CLIENT, LDLibClientConfig.SPEC);
        // Without a screen factory NeoForge shows no Config button for the mod in the mod list, leaving the
        // file as the only way in. ConfigurationScreen builds the screen from the spec.
        // modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public void onRegisterClientTooltipComponentFactoriesEvent(final RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ModularUITooltipComponent.class, ModularUIClientElementComponent::new);
    }

    @SubscribeEvent
    public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (Platform.isDevEnv()) {
            event.registerBlockEntityRenderer(CommonProxy.TEST_BE_TYPE.get(), ATESRRendererProvider::new);
        }
        event.registerBlockEntityRenderer(CommonProxy.RENDERER_BE_TYPE.get(), ATESRRendererProvider::new);
    }

    @SubscribeEvent
    public void clientSetup(final FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            MenuScreens.register(LDMenuTypes.PLAYER_UI.get(), ModularUIContainerScreen::new);
            MenuScreens.register(LDMenuTypes.HELD_ITEM_UI.get(), ModularUIContainerScreen::new);
            MenuScreens.register(LDMenuTypes.BLOCK_UI.get(), ModularUIContainerScreen::new);
            if (LDLib2.isKubejsLoaded()) {
                LDKJSMenuTypes.registerMenuScreens();
            }
            LDLibShaders.init();
        });
    }

    @SubscribeEvent
    public void modelRegistry(final ModelEvent.RegisterGeometryLoaders e) {
        e.register("renderer", LDLRendererModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    public void shaderRegistry(RegisterShadersEvent event) {
        LDLibShaders.registerShaders(event);
    }

    /**
     * The client config decides how glyphs are baked, so a change to it invalidates every atlas.
     * <p>
     * Only {@code Reloading} matters: at {@code Loading} time nothing has been baked yet. The rebuild is handed
     * to the client thread because this event is documented to fire on any thread and freeing a texture is not
     * thread safe.
     */
    @SubscribeEvent
    public void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == LDLibClientConfig.SPEC) {
            minecraft.execute(LDFontManager.INSTANCE::invalidate);
        }
    }

    /**
     * TEMPORARY: development readout, see {@link LDFontStatsOverlay}. Registered above everything so it is not
     * hidden by the rest of the HUD.
     */
    @SubscribeEvent
    public static void registerFontStatsOverlay(RenderGuiEvent.Post event) {
        if (Platform.isDevEnv()) {
            LDFontStatsOverlay.INSTANCE.render((ForgeGui) minecraft.gui, event.getGuiGraphics(), event.getPartialTick(), minecraft.getWindow().getScreenWidth(), minecraft.getWindow().getScreenHeight());
        }
    }

    @SubscribeEvent
    public void onRegisterClientReloadListenersEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PackResourceManager.INSTANCE);
        event.registerReloadListener(StylesheetManager.INSTANCE);
        event.registerReloadListener(LDFontManager.INSTANCE);
    }

    @SubscribeEvent
    public void registerModels(ModelEvent.RegisterAdditional event) {
        // load all models under the ldlib folder
        for (var entry : Minecraft.getInstance().getResourceManager().listResources("models",
                id -> id.getNamespace().equals(LDLib2.MOD_ID) && id.getPath().endsWith(".json")).entrySet()) {
            if (entry.getValue().sourcePackId().equals(LDLib2.MOD_ID)) {
                var modelLocation = new ResourceLocation(
                        entry.getKey().getNamespace(),
                        entry.getKey().getPath()
                                .replace("models/", "")
                                .replace(".json", ""));
                event.register(modelLocation);
            }
        }
        IRendererResource.INSTANCE.onAdditionalModel(event::register);
        for (IRenderer renderer : IRenderer.EVENT_REGISTERS) {
            renderer.onAdditionalModel(event::register);
        }
    }

    public static ParticleProvider getProvider(ParticleType<?> type) {
        if (Minecraft.getInstance().particleEngine instanceof ParticleEngineAccessor accessor) {
            return accessor.getProviders().get(BuiltInRegistries.PARTICLE_TYPE.getKey(type));
        }
        return null;
    }

}
