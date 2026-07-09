package com.lowdragmc.lowdraglib2.core.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib2.core.mixins.accessor.SpriteResourceLoaderAccessor;
import com.lowdragmc.lowdraglib2.editor.resource.IRendererResource;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

/**
 * @author KilaBash
 * @date 2023/7/20
 * @implNote SpriteSourceListMixin
 */
@Mixin(SpriteResourceLoader.class)
public abstract class SpriteSourceListMixin {

    // try to load all renderer textures
    @Inject(method = "load", at = @At(value = "RETURN"))
    private static void ldlib2$injectLoad(ResourceManager resourceManager, ResourceLocation location, CallbackInfoReturnable<SpriteResourceLoader> cir,
                                          @Local(ordinal = 0) List list) {
        ResourceLocation atlas = location.withPath("textures/atlas/%s.png"::formatted);
        Set<ResourceLocation> sprites = new HashSet<>();
        IRendererResource.INSTANCE.onPrepareTextureAtlas(atlas, sprites::add);
        for (var renderer : IRenderer.EVENT_REGISTERS) {
            renderer.onPrepareTextureAtlas(atlas, sprites::add);
        }
        var sources = ((SpriteResourceLoaderAccessor) cir.getReturnValue()).getSources();
        for (ResourceLocation sprite : sprites) {
            sources.add(new SingleFile(sprite, Optional.empty()));
        }
    }
}
