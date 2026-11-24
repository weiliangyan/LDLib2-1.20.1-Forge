package com.lowdragmc.lowdraglib2.core.mixins.ui;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(ContainerEventHandler.class)
public interface ContainerEventHandlerMixin extends GuiEventListener{

    @Shadow
    List<? extends GuiEventListener> children();

    @Shadow
    GuiEventListener getFocused();

    @Shadow
    boolean isDragging();

    @Override
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (var child : children()) {
            if (child instanceof IModularUIHolder holder) {
                var mui = holder.getModularUI();
                if (mui != null && mui.getWidget().mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    return true;
                }
            }
        }
        return getFocused() != null && isDragging() && button == 0 &&
                getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    default void mouseMoved(double mouseX, double mouseY) {
        for (var child : children()) {
            if (child instanceof IModularUIHolder holder) {
                var mui = holder.getModularUI();
                if (mui == null) continue;
                mui.getWidget().mouseMoved(mouseX, mouseY);
            }
        }
    }
}
