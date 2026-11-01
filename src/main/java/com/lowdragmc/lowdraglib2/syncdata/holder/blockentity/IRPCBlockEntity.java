package com.lowdragmc.lowdraglib2.syncdata.holder.blockentity;

import com.lowdragmc.lowdraglib2.networking.both.PacketRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.holder.IRPCManagedHolder;

public interface IRPCBlockEntity extends IRPCManagedHolder, IBlockEntityManagedHolder {
    @Override
    default PacketRPCBlockEntity createRPCPacket(byte[] data) {
        return PacketRPCBlockEntity.of(this, data);
    }
}
