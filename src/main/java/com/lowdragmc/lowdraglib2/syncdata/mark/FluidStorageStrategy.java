package com.lowdragmc.lowdraglib2.syncdata.mark;

import com.lowdragmc.lowdraglib2.misc.FluidStorage;
import com.lowdragmc.lowdraglib2.syncdata.utils.StackSnapshots;
import net.minecraft.core.HolderLookup;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

/**
 * What a {@link FluidStorage} saves is its fluid and capacity, for as long as the class saves and hands them out the way
 * the base class does.
 */
final class FluidStorageStrategy implements IMarkStrategy<FluidStorage, FluidStorageStrategy.Mark> {
    record Mark(FluidStack fluid, int capacity) {}

    @Override
    public boolean test(Class<?> type) {
        return FluidStorage.class.isAssignableFrom(type)
                && MarkStrategies.isDeclaredBy(type, FluidStorage.class, "serializeNBT", HolderLookup.Provider.class)
                && MarkStrategies.isDeclaredBy(type, FluidTank.class, "getFluid")
                && MarkStrategies.isDeclaredBy(type, FluidTank.class, "getCapacity");
    }

    @Override
    public @NotNull Mark obtainManagedMark(@NotNull FluidStorage value) {
        return new Mark(StackSnapshots.of(value.getFluid()), value.getCapacity());
    }

    @Override
    public boolean areDifferent(@NotNull Mark managedMark, @NotNull FluidStorage value) {
        return managedMark.capacity != value.getCapacity() || !managedMark.fluid.isFluidStackIdentical(value.getFluid());
    }
}
