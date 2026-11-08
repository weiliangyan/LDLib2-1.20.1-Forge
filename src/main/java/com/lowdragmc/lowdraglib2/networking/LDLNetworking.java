package com.lowdragmc.lowdraglib2.networking;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import com.lowdragmc.lowdraglib2.networking.both.*;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketAutoSyncBlockEntities;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketAutoSyncBlockEntity;
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.network.simple.SimpleChannel;
import net.nikdo53.neobackports.io.StreamCodec;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Author: KilaBash
 * Date: 2022/04/27
 * Description:
 */
public class LDLNetworking {
    private static final String NETWORK_VERSION = "5";
    public static SimpleChannel CHANNEL;

    public static void init() {
        if (CHANNEL != null) return;
        java.util.function.Predicate<String> lenientCheck = version -> NETWORK_VERSION.equals(version) ||
                NetworkRegistry.ABSENT.version().equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version);
        CHANNEL = NetworkRegistry.newSimpleChannel(
                LDLib2.id("main"), () -> NETWORK_VERSION,
                lenientCheck, NETWORK_VERSION::equals); // 允许服务端没有装LDLib2（应该是这样）

        int id = 0;
        register(id++, SPacketAutoSyncBlockEntity.class, SPacketAutoSyncBlockEntity.CODEC, SPacketAutoSyncBlockEntity::execute, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        register(id++, PacketUIRPCEvent.class, PacketUIRPCEvent.CODEC, PacketUIRPCEvent::execute, Optional.empty());
        register(id++, PacketUIRPCEventReturn.class, PacketUIRPCEventReturn.CODEC, PacketUIRPCEventReturn::execute, Optional.empty());

        register(id++, PacketRPCBlockEntity.class, PacketRPCBlockEntity.CODEC, PacketRPCBlockEntity::execute, Optional.empty());
        register(id++, PacketModularUISync.class, PacketModularUISync.CODEC, PacketModularUISync::execute, Optional.empty());

        register(id++, PacketRPCPacket.class, PacketRPCPacket.CODEC, PacketRPCPacket::execute, Optional.empty());

        register(id++, SPacketAutoSyncBlockEntities.class, SPacketAutoSyncBlockEntities.CODEC, SPacketAutoSyncBlockEntities::execute, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <MSG extends CustomPacketPayload> void register(
            int id,
            Class<MSG> type,
            StreamCodec<MSG> codec,
            BiConsumer<MSG, IPayloadContext> handler,
            Optional<NetworkDirection> direction) {
        CHANNEL.registerMessage(id, type,
                codec::encode,
                codec::decode,
                (message, contextSupplier) -> handle(message, contextSupplier, handler),
                direction);
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
