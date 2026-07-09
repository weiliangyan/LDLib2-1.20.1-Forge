package com.lowdragmc.lowdraglib2.configurator.ui;

import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.TaffyDirection;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Minecraft 1.20.1 does not have 1.21 data components. The equivalent mutable
 * stack payload on Forge 1.20.1 is the stack NBT tag, so this configurator keeps
 * the same UI slot in the accessor flow while editing that tag directly.
 */
public class DataComponentConfigurator extends ConfiguratorGroup {
    public final Button resetButton = new Button();
    @Getter
    private CompoundTag prototype;
    private final Supplier<CompoundTag> supplier;
    private final Consumer<CompoundTag> consumer;
    private final boolean forceUpdate;
    private TagConfigurator tagConfigurator;

    public DataComponentConfigurator(CompoundTag prototype, Supplier<CompoundTag> supplier, Consumer<CompoundTag> consumer, boolean forceUpdate) {
        super("configurator.data_component");
        this.supplier = supplier;
        this.consumer = consumer;
        this.forceUpdate = forceUpdate;
        setPrototype(prototype);
        this.resetButton.setText("configurator.reset_prototype").setOnClick(e -> {
            resetPrototype();
            e.stopPropagation();
        });
        this.resetButton.text.layout(layout -> layout.widthPercent(100));
        this.resetButton.text.textStyle(textStyle -> textStyle.adaptiveWidth(false).textWrap(TextWrap.HOVER_ROLL)).setOverflowVisible(false);
        this.inlineContainer.layout(layout -> layout.direction(TaffyDirection.RTL));
        this.inlineContainer.addChild(resetButton);
    }

    public void resetPrototype() {
        consumer.accept(prototype.copy());
        notifyChanges();
    }

    public DataComponentConfigurator setPrototype(CompoundTag prototype) {
        this.prototype = prototype == null ? new CompoundTag() : prototype.copy();
        removeAllConfigurators();
        tagConfigurator = new TagConfigurator("configurator.nbt", () -> {
            var tag = supplier.get();
            return tag == null ? new CompoundTag() : tag.copy();
        }, tag -> {
            consumer.accept(tag instanceof CompoundTag compoundTag ? compoundTag.copy() : new CompoundTag());
        }, this.prototype.copy(), forceUpdate);
        addConfigurator(tagConfigurator);
        return this;
    }
}
