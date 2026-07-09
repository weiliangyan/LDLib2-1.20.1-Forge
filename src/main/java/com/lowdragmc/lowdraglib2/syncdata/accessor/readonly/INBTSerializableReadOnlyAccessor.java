package com.lowdragmc.lowdraglib2.syncdata.accessor.readonly;

import com.lowdragmc.lowdraglib2.core.mixins.accessor.DelegatingOpsAccessor;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.codec.ByteBufCodecs;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unchecked")
public class INBTSerializableReadOnlyAccessor implements IReadOnlyAccessor<INBTSerializable<?>> {

    @Override
    public boolean test(Class<?> type) {
        return INBTSerializable.class.isAssignableFrom(type);
    }

    @Override
    public <T> T readReadOnlyValue(DynamicOps<T> op, @NotNull INBTSerializable<?> value) {
        var tag = value.serializeNBT();
        return (op == NbtOps.INSTANCE || op instanceof DelegatingOpsAccessor<?> accessor && accessor.getDelegate() == NbtOps.INSTANCE) ? (T) tag : NbtOps.INSTANCE.convertTo(op, tag);
    }

    @Override
    public <T> void writeReadOnlyValue(DynamicOps<T> op, INBTSerializable<?> value, T payload) {
        Tag tag = (op == NbtOps.INSTANCE || op instanceof DelegatingOpsAccessor<?> accessor && accessor.getDelegate() == NbtOps.INSTANCE) ?
                (Tag) payload : op.convertTo(NbtOps.INSTANCE, payload);
        ((INBTSerializable)value).deserializeNBT(tag);
    }

    @Override
    public void readReadOnlyValueToStream(RegistryFriendlyByteBuf buffer, @NotNull INBTSerializable<?> value) {
        ByteBufCodecs.TRUSTED_TAG.encode(buffer, value.serializeNBT());
    }

    @Override
    public void writeReadOnlyValueFromStream(RegistryFriendlyByteBuf buffer, @NotNull INBTSerializable<?> value) {
        var nbt = ByteBufCodecs.TRUSTED_TAG.decode(buffer);
        if (nbt != null) {
            ((INBTSerializable)value).deserializeNBT(nbt);
        }
    }

}
