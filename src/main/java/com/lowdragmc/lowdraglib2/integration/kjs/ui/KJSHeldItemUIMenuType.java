package com.lowdragmc.lowdraglib2.integration.kjs.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.ParametersAreNonnullByDefault;

public class KJSHeldItemUIMenuType {
    public static boolean openUI(ServerPlayer player, InteractionHand hand, String id) {
        var heldItem = player.getItemInHand(hand);
        var event = new ItemUIEventJS(player, hand, heldItem, id);
        UIEvents.ITEM.post(ScriptType.SERVER, id, event);
        NetworkHooks.openScreen(player, event, buffer -> event.writeClientSideData(null, LDMenuTypes.wrapMenuDataBuffer(buffer)));
        return true;
    }

    public static ModularUIContainerMenu create(int windowId, Inventory inv, FriendlyByteBuf data) {
        RegistryFriendlyByteBuf registryData = LDMenuTypes.wrapMenuDataBuffer(data);
        var player = inv.player;
        var hand = registryData.readEnum(InteractionHand.class);
        var itemstack = ByteBufCodecs.OPTIONAL_ITEM_STACK.decode(registryData);
        var id = registryData.readUtf();
        var event = new ItemUIEventJS(player, hand, itemstack, id);
        UIEvents.ITEM.post(ScriptType.CLIENT, id, event);
        return event.createMenu(windowId, inv, player);
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class ItemUIEventJS extends UIEventJS {
        public final InteractionHand hand;
        public final ItemStack itemStack;

        public ItemUIEventJS(Player player, InteractionHand hand, ItemStack itemStack, String id) {
            super(player, id);
            this.hand = hand;
            this.itemStack = itemStack;

            // valid if still the same item
            this.validator = p -> ItemStack.matches(p.getItemInHand(hand), itemStack);
            this.displayName = itemStack.getHoverName();
        }

        @Override
        public MenuType<ModularUIContainerMenu> getMenuType() {
            return LDKJSMenuTypes.HELD_ITEM_UI.get();
        }

        public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
            buffer.writeEnum(hand);
            ByteBufCodecs.OPTIONAL_ITEM_STACK.encode(buffer, itemStack);
            super.writeClientSideData(menu, buffer);
        }
    }
}
