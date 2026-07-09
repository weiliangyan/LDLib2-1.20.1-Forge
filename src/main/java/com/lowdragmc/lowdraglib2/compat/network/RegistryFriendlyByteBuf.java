package com.lowdragmc.lowdraglib2.compat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;

public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    private final HolderLookup.Provider registryAccess;
    private final ConnectionType connectionType;

    public RegistryFriendlyByteBuf(ByteBuf source, HolderLookup.Provider registryAccess, ConnectionType connectionType) {
        super(source);
        this.registryAccess = registryAccess;
        this.connectionType = connectionType;
    }

    public HolderLookup.Provider registryAccess() {
        return registryAccess;
    }

    public ConnectionType connectionType() {
        return connectionType;
    }
}
