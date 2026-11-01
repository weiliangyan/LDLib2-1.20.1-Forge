package com.lowdragmc.lowdraglib2.networking.both;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import com.lowdragmc.lowdraglib2.gui.sync.IUISyncManagerHolder;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import lombok.NoArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.nikdo53.neobackports.io.StreamCodec;

@NoArgsConstructor
public class PacketUIRPCEvent implements CustomPacketPayload {
    public static final ResourceLocation ID = LDLib2.id("ui_rpc_event");
    public static final Type<PacketUIRPCEvent> TYPE = new Type<>(ID);
    public static final StreamCodec<PacketUIRPCEvent> CODEC = StreamCodec.ofMember(PacketUIRPCEvent::write, PacketUIRPCEvent::decode);
    public byte[] eventData;

    public PacketUIRPCEvent(byte[] eventData) {
        this.eventData = eventData;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeByteArray(eventData);
    }

    public static PacketUIRPCEvent decode(FriendlyByteBuf buf) {
        var eventData = buf.readByteArray();
        return new PacketUIRPCEvent(eventData);
    }

    public static void execute(PacketUIRPCEvent packet, IPayloadContext context) {
        var player = context.player();
        if (player.containerMenu instanceof IUISyncManagerHolder syncManagerHolder) {
            var syncManager = syncManagerHolder.getSyncManager();
            if (syncManager == null) return;
            ByteBufUtil.readCustomData(packet.eventData, syncManager::handEvent);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
