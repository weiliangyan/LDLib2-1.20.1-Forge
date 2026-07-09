package com.lowdragmc.lowdraglib2.core.mixins.shader;

import com.lowdragmc.lowdraglib2.client.shader.ILDShaderInstance;
import com.lowdragmc.lowdraglib2.client.shader.LDProgramDefineManager;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Shader;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceMixin implements ILDShaderInstance {
    @Redirect(method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/ProgramManager;linkShader(Lcom/mojang/blaze3d/shaders/Shader;)V"))
    private void ldlib2$linkShader(Shader shader, ResourceProvider resourceProvider, ResourceLocation shaderLocation, VertexFormat vertexFormat) throws IOException {
        var jsonLocation = ResourceLocation.fromNamespaceAndPath(shaderLocation.getNamespace(), "shaders/core/" + shaderLocation.getPath() + ".json");
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
}
