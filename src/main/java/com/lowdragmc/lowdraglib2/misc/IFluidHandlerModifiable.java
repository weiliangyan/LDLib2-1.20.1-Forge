package com.lowdragmc.lowdraglib2.misc;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Extensions to Forge's {@link IFluidHandler}
 */
public interface IFluidHandlerModifiable extends IFluidHandler {

    void setFluidInTank(int tank, FluidStack stack);

    default boolean supportsFill(int tank) {
        return true;
    }

    default boolean supportsDrain(int tank) {
        return true;
    }
}
