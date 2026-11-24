package com.lowdragmc.lowdraglib2.networking.s2c;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import com.lowdragmc.lowdraglib2.networking.PacketIntLocation;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncBlockEntity;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.nikdo53.neobackports.io.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Objects;

/**
 * a packet that contains payload for managed fields
 */
@Getter
public class SPacketAutoSyncBlockEntity extends PacketIntLocation {
    public static final ResourceLocation ID = LDLib2.id("auto_sync_block_entity");
    public static final Type<SPacketAutoSyncBlockEntity> TYPE = new Type<>(ID);
    public static final StreamCodec<SPacketAutoSyncBlockEntity> CODEC = StreamCodec.ofMember(SPacketAutoSyncBlockEntity::write, SPacketAutoSyncBlockEntity::decode);

    private final BlockEntityType<?> blockEntityType;
    private final BitSet changed;
    private final byte[] data;
    private final CompoundTag extra;

    private SPacketAutoSyncBlockEntity(BlockEntityType<?> type, BlockPos pos, BitSet changed, byte[] data, CompoundTag extra) {
        super(pos);
        blockEntityType = type;
        this.changed = changed;
        this.data = data;
        this.extra = extra;
    }

    /**
     * Create a packet to sync fields of a block entity. This will also clear the dirty flag of all synced fields.
     */
    public BlockPos getPos() {
        return pos;
    }

    public static SPacketAutoSyncBlockEntity of(ISyncBlockEntity tile, BitSet changed, byte[] data, CompoundTag extra) {
        return new SPacketAutoSyncBlockEntity(tile.getSelf().getType(), tile.getSelf().getBlockPos(), changed, data, extra);
    }

    public static void processPacket(@NotNull ISyncBlockEntity blockEntity, SPacketAutoSyncBlockEntity packet) {
        if (blockEntity.getSelf().getType() != packet.blockEntityType) {
            LDLib2.LOGGER.warn("Block entity type mismatch in managed payload packet!");
            return;
        }
        var level = blockEntity.getSelf().getLevel();
        if (level == null) return;
        blockEntity.handleSyncPacket(level.registryAccess(), packet.changed, packet.data, packet.extra);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        super.write(buf);
        buf.writeResourceLocation(Objects.requireNonNull(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntityType)));
        buf.writeByteArray(changed.toByteArray());
        buf.writeByteArray(data);
        buf.writeNbt(extra);
    }

    public static SPacketAutoSyncBlockEntity decode(FriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        var blockEntityType = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(buffer.readResourceLocation());
        var changed = BitSet.valueOf(buffer.readByteArray());
        var data = buffer.readByteArray();
        var extra = buffer.readNbt();
        return new SPacketAutoSyncBlockEntity(blockEntityType, pos, changed, data, extra);
    }

    public static void execute(SPacketAutoSyncBlockEntity packet, IPayloadContext context) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            if (level.getBlockEntity(packet.pos) instanceof ISyncBlockEntity syncBlockEntity) {
                processPacket(syncBlockEntity, packet);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
