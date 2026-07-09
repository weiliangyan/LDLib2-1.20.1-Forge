package com.lowdragmc.lowdraglib2.core.mixins.ui;

import com.lowdragmc.lowdraglib2.gui.event.ContainerMenuEvent;
import com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MenuType.class)
public abstract class MenuTypeMixin<T extends AbstractContainerMenu> {

    @Shadow(aliases = "f_39981_")
    @Final
    private MenuType.MenuSupplier<T> constructor;

    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At(value = "RETURN"))
    private void ldlib2$create1(int containerId, Inventory playerInventory, CallbackInfoReturnable<T> cir) {
        var menu = cir.getReturnValue();
        if (menu != null) {
            MinecraftForge.EVENT_BUS.post(new ContainerMenuEvent.Create(playerInventory.player, menu));
        }
    }


    @Inject(method = "create(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At(value = "RETURN"),
            remap = false)
    private void ldlib2$create2$return(int containerId, Inventory playerInventory, FriendlyByteBuf extraData, CallbackInfoReturnable<T> cir) {
        var menu = cir.getReturnValue();
        if (this.constructor instanceof net.minecraftforge.network.IContainerFactory) {
            MinecraftForge.EVENT_BUS.post(new ContainerMenuEvent.Create(playerInventory.player, menu));
        }
        if (menu instanceof IModularUIHolder holder) {
            holder.readInitialData(LDMenuTypes.wrapMenuDataBuffer(extraData));
        }
    }
}
