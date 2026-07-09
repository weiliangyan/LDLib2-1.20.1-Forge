package com.lowdragmc.lowdraglib2.integration.kjs.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import com.lowdragmc.lowdraglib2.compat.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.ParametersAreNonnullByDefault;

public class KJSPlayerUIMenuType {
    public static boolean openUI(ServerPlayer player, String id) {
        var event = new PlayerUIEventJS(player, id);
        UIEvents.PLAYER.post(ScriptType.SERVER, id, event);
        NetworkHooks.openScreen(player, event, buffer -> event.writeClientSideData(null, LDMenuTypes.wrapMenuDataBuffer(buffer)));
        return true;
    }

    public static ModularUIContainerMenu create(int windowId, Inventory inv, FriendlyByteBuf data) {
        RegistryFriendlyByteBuf registryData = LDMenuTypes.wrapMenuDataBuffer(data);
        var player = inv.player;
        var id = registryData.readUtf();
        var event = new PlayerUIEventJS(player, id);
        UIEvents.PLAYER.post(ScriptType.CLIENT, id, event);
        return event.createMenu(windowId, inv, player);
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class PlayerUIEventJS extends UIEventJS {
        public PlayerUIEventJS(Player player, String id) {
            super(player, id);
        }

        @Override
        public MenuType<ModularUIContainerMenu> getMenuType() {
            return LDKJSMenuTypes.PLAYER_UI.get();
        }
    }
}
