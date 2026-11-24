package com.lowdragmc.lowdraglib2.compat.network.custom;

import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
