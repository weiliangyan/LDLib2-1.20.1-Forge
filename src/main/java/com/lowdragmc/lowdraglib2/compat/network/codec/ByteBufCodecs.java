package com.lowdragmc.lowdraglib2.compat.network.codec;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagTypes;
import net.minecraft.network.FriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ByteBufCodecs {
    public static final StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of(ByteBufCodecs::writeVarInt, ByteBufCodecs::readVarInt);
    public static final StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of(ByteBufCodecs::writeVarLong, ByteBufCodecs::readVarLong);
    public static final StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
    public static final StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    public static final StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final StreamCodec<ByteBuf, Byte> BYTE = StreamCodec.of((buf, value) -> buf.writeByte(value), ByteBuf::readByte);
    public static final StreamCodec<ByteBuf, Short> SHORT = StreamCodec.of((buf, value) -> buf.writeShort(value), ByteBuf::readShort);
    public static final StreamCodec<ByteBuf, String> STRING_UTF8 = StreamCodec.of(ByteBufCodecs::writeUtf, ByteBufCodecs::readUtf);

    public static final StreamCodec<FriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION =
            StreamCodec.of(FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::readResourceLocation);
    public static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS =
            StreamCodec.of(FriendlyByteBuf::writeBlockPos, FriendlyByteBuf::readBlockPos);
    public static final StreamCodec<FriendlyByteBuf, ItemStack> OPTIONAL_ITEM_STACK =
            StreamCodec.of(FriendlyByteBuf::writeItem, FriendlyByteBuf::readItem);
    public static final StreamCodec<FriendlyByteBuf, FluidStack> OPTIONAL_FLUID_STACK =
            StreamCodec.of((buf, stack) -> stack.writeToPacket(buf), FluidStack::readFromPacket);
    public static final StreamCodec<FriendlyByteBuf, Vector3f> VECTOR3F =
            StreamCodec.of(FriendlyByteBuf::writeVector3f, FriendlyByteBuf::readVector3f);
    public static final StreamCodec<FriendlyByteBuf, Quaternionf> QUATERNIONF =
            StreamCodec.of(FriendlyByteBuf::writeQuaternion, FriendlyByteBuf::readQuaternion);
    public static final StreamCodec<FriendlyByteBuf, Tag> TRUSTED_TAG =
            StreamCodec.of(ByteBufCodecs::writeTag, ByteBufCodecs::readTag);

    private ByteBufCodecs() {
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodec(Codec<T> codec) {
        return StreamCodec.of((buf, value) -> buf.writeWithCodec(RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, buf.registryAccess()), codec, value),
                buf -> buf.readWithCodec(RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, buf.registryAccess()), codec));
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodec(codec);
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> registry(ResourceKey<? extends Registry<T>> registryKey) {
        return StreamCodec.of((buf, value) -> {
            var registry = buf.registryAccess().lookupOrThrow(registryKey);
            var key = registry.listElements()
                    .filter(holder -> Objects.equals(holder.value(), value))
                    .findFirst()
                    .flatMap(holder -> holder.unwrapKey().map(ResourceKey::location))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown registry value " + value + " in " + registryKey.location()));
            buf.writeResourceLocation(key);
        }, buf -> {
            var registry = buf.registryAccess().lookupOrThrow(registryKey);
            return registry.getOrThrow(ResourceKey.create((ResourceKey<? extends Registry<T>>) registryKey, buf.readResourceLocation())).value();
        });
    }

    private static void writeUtf(ByteBuf buf, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        var bytes = new byte[readVarInt(buf)];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static int readVarInt(ByteBuf buf) {
        int result = 0;
        int shift = 0;
        byte next;
        do {
            next = buf.readByte();
            result |= (next & 127) << shift++ * 7;
            if (shift > 5) throw new RuntimeException("VarInt too big");
        } while ((next & 128) == 128);
        return result;
    }

    private static void writeVarLong(ByteBuf buf, long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int)(value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int)value);
    }

    private static long readVarLong(ByteBuf buf) {
        long result = 0L;
        int shift = 0;
        byte next;
        do {
            next = buf.readByte();
            result |= (long)(next & 127) << shift++ * 7;
            if (shift > 10) throw new RuntimeException("VarLong too big");
        } while ((next & 128) == 128);
        return result;
    }

    private static void writeTag(FriendlyByteBuf buf, Tag tag) {
        try {
            if (tag == null) {
                buf.writeByte(0);
            } else {
                buf.writeByte(tag.getId());
                tag.write(new DataOutputStream(new ByteBufOutputStreamAdapter(buf)));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Tag readTag(FriendlyByteBuf buf) {
        try {
            byte id = buf.readByte();
            if (id == 0) return null;
            return TagTypes.getType(id).load(new DataInputStream(new ByteBufInputStreamAdapter(buf)), 0, NbtAccounter.UNLIMITED);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class ByteBufOutputStreamAdapter extends java.io.OutputStream {
        private final ByteBuf buf;

        private ByteBufOutputStreamAdapter(ByteBuf buf) {
            this.buf = buf;
        }

        @Override
        public void write(int b) {
            buf.writeByte(b);
        }
    }

    private static final class ByteBufInputStreamAdapter extends java.io.InputStream {
        private final ByteBuf buf;

        private ByteBufInputStreamAdapter(ByteBuf buf) {
            this.buf = buf;
        }

        @Override
        public int read() {
            return buf.isReadable() ? buf.readUnsignedByte() : -1;
        }
    }
}
