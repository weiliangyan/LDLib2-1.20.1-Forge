package com.lowdragmc.lowdraglib2.utils.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

public record BlockPosFace(BlockPos pos, Direction facing) {

    @Override
    public boolean equals(@Nullable Object other) {
        if (other instanceof BlockPosFace blockPosFace) {
            return pos.equals(blockPosFace.pos()) && blockPosFace.facing() == facing;
        }
        return false;
    }

}
