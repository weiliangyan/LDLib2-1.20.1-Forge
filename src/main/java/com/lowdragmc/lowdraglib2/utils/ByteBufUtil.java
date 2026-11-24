package com.lowdragmc.lowdraglib2.utils;

import io.netty.buffer.Unpooled;
import lombok.experimental.UtilityClass;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Consumer;

@UtilityClass
public final class ByteBufUtil {
    /**
     * Writes custom data to a {@link FriendlyByteBuf}, then read it for consumer.
     *
     * @param data           Data to write.
     * @param dataWriter     The data reader.
     */
    public static void readCustomData(byte[] data, Consumer<FriendlyByteBuf> dataWriter) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            dataWriter.accept(buf);
        } finally {
            buf.release();
        }
    }

    /**
     * Writes custom data to a {@link FriendlyByteBuf}, then returns the written data as a byte array.
     * This implementation fixes byte arrays larger than vanilla's small helper limits.
     *
     * @param dataWriter     The data writer.
     * @return The written data.
     */
    public static byte[] writeCustomData(Consumer<FriendlyByteBuf> dataWriter) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            dataWriter.accept(buf);
            buf.readerIndex(0);
            final byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } finally {
            buf.release();
        }
    }
}
