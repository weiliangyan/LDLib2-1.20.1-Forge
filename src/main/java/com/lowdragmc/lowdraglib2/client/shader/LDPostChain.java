package com.lowdragmc.lowdraglib2.client.shader;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;

public class LDPostChain extends PostChain {
    public LDPostChain(TextureManager textureManager, ResourceManager resourceManager, RenderTarget screenTarget, ResourceLocation resourceLocation) throws IOException, JsonSyntaxException {
        super(textureManager, resourceManager, screenTarget, resourceLocation);
    }
}
