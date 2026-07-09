package com.lowdragmc.lowdraglib2.networking.s2c;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.client.ClientCommands;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.codec.StreamCodec;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Opens the UI editor on the receiving client after a server command request.
 */
public class SPacketOpenUIEditor implements CustomPacketPayload {
    public static final ResourceLocation ID = LDLib2.id("open_ui_editor");
    public static final Type<SPacketOpenUIEditor> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketOpenUIEditor> CODEC = StreamCodec.ofMember(SPacketOpenUIEditor::write, SPacketOpenUIEditor::decode);

    public void write(RegistryFriendlyByteBuf buf) {
    }

    public static SPacketOpenUIEditor decode(RegistryFriendlyByteBuf buffer) {
        return new SPacketOpenUIEditor();
    }

    public static void execute(SPacketOpenUIEditor packet, IPayloadContext context) {
        ClientCommands.openUIEditor();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
