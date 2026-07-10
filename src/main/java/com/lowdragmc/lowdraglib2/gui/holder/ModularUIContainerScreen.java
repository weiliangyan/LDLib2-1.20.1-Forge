package com.lowdragmc.lowdraglib2.gui.holder;

import com.lowdragmc.lowdraglib2.LDLib2;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.ParametersAreNonnullByDefault;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ModularUIContainerScreen extends AbstractContainerScreen<ModularUIContainerMenu> {

    public ModularUIContainerScreen(ModularUIContainerMenu container, Inventory inventory, Component title) {
        super(container, inventory, title);
    }

    @Override
    public void init() {
        var modularUI = getMenu().getModularUI();
        modularUI.setScreenAndInit(this);
        this.imageWidth = (int) modularUI.getWidth();
        this.imageHeight = (int) modularUI.getHeight();
        super.init();
        this.addRenderableWidget(modularUI.getWidget());
        setFocused(modularUI.getWidget());
        LDLib2.LOGGER.info("Initialized LDLib2 container UI screen: {}x{}", this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {

    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        getMenu().syncModularSlotPositions();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {

    }

}
