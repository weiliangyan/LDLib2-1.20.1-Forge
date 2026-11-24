package com.lowdragmc.lowdraglib2.networking;

import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * a packet that contains a BlockPos
 */
@NoArgsConstructor
public abstract class PacketIntLocation implements CustomPacketPayload {
    protected BlockPos pos;

    public PacketIntLocation(BlockPos pos) {
        this.pos = pos;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }
}
