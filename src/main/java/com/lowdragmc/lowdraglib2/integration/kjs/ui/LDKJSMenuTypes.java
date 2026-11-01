package com.lowdragmc.lowdraglib2.integration.kjs.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;

import java.util.function.Supplier;

import static com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes.MENUS;

@KJSBindings("LDLib2UIFactory")
public class LDKJSMenuTypes {
    @KJSBindings
    public static final Supplier<MenuType<ModularUIContainerMenu>> PLAYER_UI = MENUS.register("kjs_player_ui",
            () -> IForgeMenuType.create(KJSPlayerUIMenuType::create));

    public static final Supplier<MenuType<ModularUIContainerMenu>> HELD_ITEM_UI = MENUS.register("kjs_held_item_ui",
            () -> IForgeMenuType.create(KJSHeldItemUIMenuType::create));

    public static final Supplier<MenuType<ModularUIContainerMenu>> BLOCK_UI = MENUS.register("kjs_block_ui",
            () -> IForgeMenuType.create(KJSBlockUIMenuType::create));

    public static void init() {

    }

    public static void registerMenuScreens() {
        MenuScreens.register(PLAYER_UI.get(), ModularUIContainerScreen::new);
        MenuScreens.register(HELD_ITEM_UI.get(), ModularUIContainerScreen::new);
        MenuScreens.register(BLOCK_UI.get(), ModularUIContainerScreen::new);
    }

    public static boolean openPlayerUI(Player player, String id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        return KJSPlayerUIMenuType.openUI(serverPlayer, id);
    }

    public static boolean openHeldItemUI(Player player, InteractionHand hand, String id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        return KJSHeldItemUIMenuType.openUI(serverPlayer, hand, id);
    }

    public static boolean openBlockUI(Player player, BlockPos pos, String id) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        return KJSBlockUIMenuType.openUI(serverPlayer, pos, id);
    }

}
