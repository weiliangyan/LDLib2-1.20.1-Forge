package com.lowdragmc.lowdraglib2.core.mixins;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.syncdata.holder.IManagedHolder;
import com.lowdragmc.lowdraglib2.syncdata.holder.IPersistManagedHolder;
import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.jetbrains.annotations.Nullable;

/**
 * @author KilaBash
 * @date 2022/11/27
 * @implNote BlockEntityMixin
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    @Shadow(aliases = "m_58904_")
    @Nullable
    public abstract Level getLevel();

    @Inject(method = "getUpdateTag", at = @At(value = "RETURN"))
    private void injectGetUpdateTag(CallbackInfoReturnable<CompoundTag> cir) {
        if (this instanceof ISyncMangedHolder syncMangedHolder) {
            var tag = cir.getReturnValue();
            tag.put(syncMangedHolder.getSyncTag(), syncMangedHolder.serializeInitialData(Platform.getFrozenRegistry()));
        }
    }

    @Inject(method = "saveAdditional", at = @At(value = "RETURN"))
    private void injectSaveAdditional(CompoundTag pTag, CallbackInfo ci) {
        if (this instanceof IPersistManagedHolder persistManagedHolder) {
            persistManagedHolder.saveManagedPersistentData(Platform.getFrozenRegistry(), pTag, false);
        }
    }

    @Inject(method = "load", at = @At(value = "RETURN"))
    private void injectLoad(CompoundTag pTag, CallbackInfo ci) {
        var provider = Platform.getFrozenRegistry();
        if (this instanceof ISyncMangedHolder syncMangedHolder && pTag.get(syncMangedHolder.getSyncTag()) instanceof CompoundTag tag) {
            syncMangedHolder.deserializeInitialData(provider, tag);
        } else if (this instanceof IPersistManagedHolder persistManagedHolder) {
            persistManagedHolder.loadManagedPersistentData(provider, pTag);
        }
    }

    @Inject(method = "setRemoved", at = @At(value = "RETURN"))
    private void injectSetRemoved(CallbackInfo ci) {
        if (this instanceof ISyncPersistRPCBlockEntity syncMangedHolder && getLevel() instanceof ServerLevel) {
            syncMangedHolder.detachAsyncLogic();
        }
    }

    @Inject(method = "clearRemoved", at = @At(value = "RETURN"))
    private void injectClearRemoved(CallbackInfo ci) {
        if (this instanceof IManagedHolder managed) {
            managed.getRootStorage().requireInit();
            if (managed instanceof ISyncMangedHolder syncMangedHolder && getLevel() instanceof ServerLevel) {
                syncMangedHolder.attachAsyncLogic();
            }
        }
    }

}
