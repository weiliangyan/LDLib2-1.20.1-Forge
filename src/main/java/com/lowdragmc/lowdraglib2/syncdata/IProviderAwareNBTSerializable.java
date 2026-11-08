package com.lowdragmc.lowdraglib2.syncdata;

import com.lowdragmc.lowdraglib2.Platform;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility bridge for code written against provider-aware NBT methods.
 * Forge 1.20.1 still exposes the no-provider INBTSerializable contract.
 */
public interface IProviderAwareNBTSerializable<T extends Tag> extends INBTSerializable<T> {

    T serializeNBT(HolderLookup.@NotNull Provider provider);

    void deserializeNBT(HolderLookup.@NotNull Provider provider, @NotNull T tag);

    @Override
    default T serializeNBT() {
        return serializeNBT(Platform.getFrozenRegistry());
    }

    @Override
    default void deserializeNBT(@NotNull T tag) {
        deserializeNBT(Platform.getFrozenRegistry(), tag);
    }
}
