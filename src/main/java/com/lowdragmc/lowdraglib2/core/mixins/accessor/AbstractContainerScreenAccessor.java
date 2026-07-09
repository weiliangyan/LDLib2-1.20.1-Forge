package com.lowdragmc.lowdraglib2.core.mixins.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("clickedSlot")
    Slot ldlib2$getClickedSlot();

    @Accessor("draggingItem")
    ItemStack ldlib2$getDraggingItem();

    @Accessor("isSplittingStack")
    boolean ldlib2$isSplittingStack();

    @Accessor("isQuickCrafting")
    boolean ldlib2$isQuickCrafting();

    @Accessor("quickCraftSlots")
    Set<Slot> ldlib2$getQuickCraftSlots();

    @Accessor("quickCraftingType")
    int ldlib2$getQuickCraftingType();

    @Invoker("recalculateQuickCraftRemaining")
    void ldlib2$invokeRecalculateQuickCraftRemaining();
}
