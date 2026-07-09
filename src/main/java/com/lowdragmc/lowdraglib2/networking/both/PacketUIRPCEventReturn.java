package com.lowdragmc.lowdraglib2.networking.both;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.sync.IUISyncManagerHolder;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import lombok.NoArgsConstructor;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.codec.StreamCodec;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;

import javax.annotation.Nonnull;

@NoArgsConstructor
public class PacketUIRPCEventReturn implements CustomPacketPayload {
    public static final ResourceLocation ID = LDLib2.id("ui_rpc_event_return");
    public static final Type<PacketUIRPCEventReturn> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketUIRPCEventReturn> CODEC = StreamCodec.ofMember(PacketUIRPCEventReturn::write, PacketUIRPCEventReturn::decode);

    public byte[] returnData;

    public PacketUIRPCEventReturn(byte[] returnData) {
        this.returnData = returnData;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeByteArray(returnData);
    }

    public static PacketUIRPCEventReturn decode(RegistryFriendlyByteBuf buf) {
        var returnData = buf.readByteArray();
        return new PacketUIRPCEventReturn(returnData);
    }

    public static void execute(PacketUIRPCEventReturn packet, IPayloadContext context) {
        var player = context.player();
        if (player.containerMenu instanceof IUISyncManagerHolder syncManagerHolder) {
            var syncManager = syncManagerHolder.getSyncManager();
            if (syncManager == null) return;
            ByteBufUtil.readCustomData(packet.returnData,
                    syncManager::handEventReturn,
                    context.player().level().registryAccess());
        }
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
