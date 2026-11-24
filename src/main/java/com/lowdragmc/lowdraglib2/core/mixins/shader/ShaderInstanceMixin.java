package com.lowdragmc.lowdraglib2.core.mixins.shader;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.lowdraglib2.client.shader.ILDShaderInstance;
import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin implements ILDShaderInstance {
    @Redirect(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/ProgramManager;linkShader(Lcom/mojang/blaze3d/shaders/Shader;)V"))
    private void ldlib2$linkShader(Shader shader, ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat vertexFormat) throws IOException {
        var jsonLocation = new ResourceLocation(shaderLocation.getNamespace(), "shaders/core/" + shaderLocation.getPath() + ".json");
        try (var reader = resourceProvider.openAsReader(jsonLocation)) {
            this.onCreateShader(resourceProvider, shaderLocation, vertexFormat, GsonHelper.parse(reader));
        }
        ProgramManager.linkShader(shader);
    }

    @Inject(method = "getOrCreate", at = {@At(value = "HEAD")}, cancellable = true)
    private static void ldlib2$getOrCreate(ResourceProvider resourceProvider,
                                           Program.Type programType,
                                           String name,
                                           CallbackInfoReturnable<Program> cir) {
        if (LDProgramDefineManager.hasProgramDefines()) {
            var program = programType.getPrograms().get(LDProgramDefineManager.createProgramNameWithDefines(name));
            if (program != null) cir.setReturnValue(program);
        }
    }

    /**
     * While defines are active, a PLAIN-name cache hit in the vanilla body would silently reuse a
     * stage compiled WITHOUT the defines (e.g. the vanilla-registered copy of a shared vsh like
     * photon:particle, or a previously built no-defines variant of the same source) — the defines
     * never reach the program and e.g. an instanced variant links the non-instanced attribute
     * layout and renders nothing. Force a fresh compile instead: the ProgramMixin renames the
     * cache key at put time, and the defines-aware lookup above serves later requests.
     */
    @ModifyExpressionValue(method = "getOrCreate", at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object ldlib2$skipPlainCacheWhenDefinesActive(Object original) {
        return LDProgramDefineManager.hasProgramDefines() ? null : original;
    }
}

@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin {
    @Shadow
    private VertexFormat.Mode mode;

    /**
     * rendertype_lines widens lines by LineWidth / ScreenSize in NDC, which spans the viewport; vanilla passes the
     * window size, so lines drawn into a smaller viewport (scene widgets, other windows) came out thinner per axis.
     */
    @Inject(method = "_drawWithShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;apply()V"))
    private void ldlib2$lineScreenSizeFromViewport(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, ShaderInstance shader, CallbackInfo ci) {
        if (shader.SCREEN_SIZE != null && (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP)) {
            shader.SCREEN_SIZE.set((float) GlStateManager.Viewport.width(), (float) GlStateManager.Viewport.height());
        }
    }

}