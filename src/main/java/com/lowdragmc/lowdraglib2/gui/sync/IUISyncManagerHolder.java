package com.lowdragmc.lowdraglib2.gui.sync;

import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

public interface IUISyncManagerHolder {
    @Nullable
    UISyncManager getSyncManager();

    default void writeInitialData(FriendlyByteBuf buf) {
        var syncManager = getSyncManager();
        if (syncManager == null) return;
        syncManager.writeInitialData(buf);
    }

    default void readInitialData(FriendlyByteBuf buf) {
        var syncManager = getSyncManager();
        if (syncManager == null) return;
        syncManager.readInitialData(buf);
    }
}
