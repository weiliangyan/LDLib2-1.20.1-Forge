package com.lowdragmc.lowdraglib2.core.mixins;

import com.lowdragmc.lowdraglib2.client.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "disableBlend", at = @At("HEAD"), cancellable = true)
    private static void disableBlend(CallbackInfo ci) {
        if (RenderUtils.forceBlend) ci.cancel();
    }
}
