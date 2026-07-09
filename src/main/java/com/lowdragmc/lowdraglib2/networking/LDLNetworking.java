package com.lowdragmc.lowdraglib2.networking;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.both.PacketModularUISync;
import com.lowdragmc.lowdraglib2.networking.both.PacketRPCBlockEntity;
import com.lowdragmc.lowdraglib2.networking.both.PacketRPCPacket;
import com.lowdragmc.lowdraglib2.networking.both.PacketUIRPCEvent;
import com.lowdragmc.lowdraglib2.networking.both.PacketUIRPCEventReturn;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketOpenUIEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.codec.StreamCodec;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import com.lowdragmc.lowdraglib2.compat.network.ConnectionType;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Author: KilaBash
 * Date: 2022/04/27
 * Description:
 */
public class LDLNetworking {
    private static final String NETWORK_VERSION = "2";
    public static SimpleChannel CHANNEL;

    public static void init() {
        if (CHANNEL != null) return;
        CHANNEL = NetworkRegistry.newSimpleChannel(
                LDLib2.id("main"),
                () -> NETWORK_VERSION,
                NETWORK_VERSION::equals,
                NETWORK_VERSION::equals);

        int id = 0;
        register(id++, SPacketAutoSyncBlockEntity.class, SPacketAutoSyncBlockEntity.CODEC, SPacketAutoSyncBlockEntity::execute, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        register(id++, PacketUIRPCEvent.class, PacketUIRPCEvent.CODEC, PacketUIRPCEvent::execute, Optional.empty());
        register(id++, PacketUIRPCEventReturn.class, PacketUIRPCEventReturn.CODEC, PacketUIRPCEventReturn::execute, Optional.empty());

        register(id++, PacketRPCBlockEntity.class, PacketRPCBlockEntity.CODEC, PacketRPCBlockEntity::execute, Optional.empty());
        register(id++, PacketModularUISync.class, PacketModularUISync.CODEC, PacketModularUISync::execute, Optional.empty());

        register(id++, PacketRPCPacket.class, PacketRPCPacket.CODEC, PacketRPCPacket::execute, Optional.empty());
        register(id, SPacketOpenUIEditor.class, SPacketOpenUIEditor.CODEC, SPacketOpenUIEditor::execute, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <MSG extends CustomPacketPayload> void register(
            int id,
            Class<MSG> type,
            StreamCodec<RegistryFriendlyByteBuf, MSG> codec,
            BiConsumer<MSG, IPayloadContext> handler,
            Optional<NetworkDirection> direction) {
        CHANNEL.registerMessage(id, type,
                (message, buffer) -> codec.encode(wrap(buffer), message),
                buffer -> codec.decode(wrap(buffer)),
                (message, contextSupplier) -> handle(message, contextSupplier, handler),
                direction);
    }

    private static RegistryFriendlyByteBuf wrap(FriendlyByteBuf buffer) {
        if (buffer instanceof RegistryFriendlyByteBuf registryBuffer) {
            return registryBuffer;
        }
        return new RegistryFriendlyByteBuf(buffer, Platform.getFrozenRegistry(), ConnectionType.NEOFORGE);
    }

    private static <MSG> void handle(MSG message, Supplier<NetworkEvent.Context> contextSupplier, BiConsumer<MSG, IPayloadContext> handler) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handler.accept(message, new PayloadContext(context)));
        context.setPacketHandled(true);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAllPlayers(CustomPacketPayload packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload packet) {
        var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk != null) {
            CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
        }
    }

    private record PayloadContext(NetworkEvent.Context context) implements IPayloadContext {
        @Override
        public Player player() {
            var sender = context.getSender();
            if (sender != null) {
                return sender;
            }
            if (Platform.isClient()) {
                return clientPlayer();
            }
            throw new IllegalStateException("Cannot resolve packet player on the dedicated server without a sender");
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static Player clientPlayer() {
        return Minecraft.getInstance().player;
    }

}
