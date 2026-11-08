package com.lowdragmc.lowdraglib2.gui.factory;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.editor.UIEditor;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.integration.kjs.ui.LDKJSMenuTypes;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public final class LDMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, LDLib2.MOD_ID);

    public static final Supplier<MenuType<ModularUIContainerMenu>> PLAYER_UI = MENUS.register("player_ui",
            () -> IForgeMenuType.create(PlayerUIMenuType::create));

    public static final Supplier<MenuType<ModularUIContainerMenu>> HELD_ITEM_UI = MENUS.register("held_item_ui",
            () -> IForgeMenuType.create(HeldItemUIMenuType::create));

    public static final Supplier<MenuType<ModularUIContainerMenu>> BLOCK_UI = MENUS.register("block_ui",
            () -> IForgeMenuType.create(BlockUIMenuType::create));

    public static void init(IEventBus eventBus) {
        PlayerUIMenuType.register(UIEditor.WINDOW_ID, ignored -> player -> {
            if (player.level().isClientSide) {
                var window = EditorWindow.open(UIEditor.WINDOW_ID, UIEditor::new)
                        .setGameWindowReopener(UIEditor::requestMenu);
                return new ModularUI(UI.of(window))
                        .shouldCloseOnEsc(false)
                        .shouldCloseOnKeyInventory(false);
            }
            return new ModularUI(UI.empty());
        });

        if (LDLib2.isKubejsLoaded()) {
            LDKJSMenuTypes.init();
        }
        MENUS.register(eventBus);
    }
}
