package com.lowdragmc.lowdraglib2.core.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.Platform;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

@Mixin(WorldLoader.class)
public abstract class WorldLoaderMixin {
    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/RegistryDataLoader;load(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;)Lnet/minecraft/core/RegistryAccess$Frozen;",
            shift = At.Shift.BEFORE)
    )
    private static <D, R> void ldlib2$loadResourceManager(
            WorldLoader.InitConfig initConfig,
            WorldLoader.WorldDataSupplier<D> worldDataSupplier,
            WorldLoader.ResultFactory<D, R> resultFactory,
            Executor backgroundExecutor,
            Executor gameExecutor,
            CallbackInfoReturnable<CompletableFuture<R>> cir,
            @Local CloseableResourceManager resourceManager
    ) {
        Platform.RESOURCE_MANAGER = resourceManager;
    }

    @WrapOperation(
            method = "load",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;whenComplete(Ljava/util/function/BiConsumer;)Ljava/util/concurrent/CompletableFuture;")
    )
    private static <T> CompletableFuture<T> ldlib2$clearResourceManagerOnComplete(
            CompletableFuture<T> instance,
            BiConsumer<? super T, ? super Throwable> action,
            Operation<CompletableFuture<T>> original
    ) {
        CompletableFuture<T> future = original.call(instance, action);
        return future.whenComplete((result, throwable) -> Platform.RESOURCE_MANAGER = null);
    }
}
