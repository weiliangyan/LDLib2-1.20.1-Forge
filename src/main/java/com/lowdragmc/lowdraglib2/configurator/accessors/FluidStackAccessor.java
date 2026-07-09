package com.lowdragmc.lowdraglib2.configurator.accessors;

import com.google.common.base.Predicates;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.DefaultValue;
import com.lowdragmc.lowdraglib2.configurator.ui.*;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;

@LDLRegisterClient(name = "fluidstack", registry = "ldlib2:configurator_accessor")
public class FluidStackAccessor extends TypesAccessor<FluidStack> {

    public FluidStackAccessor() {
        super(FluidStack.class);
    }

    @Override
    public FluidStack defaultValue(@Nullable Field field, @Nullable Class<?> type) {
        if (field != null && field.isAnnotationPresent(DefaultValue.class)) {
            return new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse(field.getAnnotation(DefaultValue.class).stringValue()[0])), 1000);
        }
        return new FluidStack(Fluids.WATER, 1000);
    }

    @Override
    public Configurator create(String name, Supplier<FluidStack> supplier, Consumer<FluidStack> consumer, boolean forceUpdate, @Nullable Field field, @Nullable Object owner) {
        var group = new ConfiguratorGroup(name);
        var slot = new FluidSlot();
        slot.layout(layout -> layout.width(14).height(14));
        slot.bindDataSource(SupplierDataSource.of(supplier));
        Consumer<FluidStack> updater = fluidStack -> {
            slot.setFluid(fluidStack);
            consumer.accept(fluidStack);
        };
        group.inlineContainer.addChild(slot);
        var defaultValue = defaultValue(field);
        var componentsConfigurator = new DataComponentConfigurator(supplier.get().getTag(),
                () -> supplier.get().getTag(),
                tag -> updater.accept(withTag(supplier.get().getFluid(), supplier.get().getAmount(), tag)), forceUpdate);
        var fluidConfigurator = new RegistrySearchComponent.Fluid("configurator.fluid",
                () -> supplier.get().getFluid(),
                fluid -> updater.accept(withTag(fluid, Math.max(supplier.get().getAmount(), 1), supplier.get().getTag())),
                defaultValue.getFluid(), forceUpdate);
        var countConfigurator = new NumberConfigurator("ldlib.gui.editor.configurator.amount",
                () -> supplier.get().getAmount(), count -> {
                    var copy = supplier.get().copy();
                    copy.setAmount(count.intValue());
                    updater.accept(copy);
                },
                defaultValue.getAmount(), forceUpdate)
                .setType(ConfigNumber.Type.INTEGER)
                .setRange(0, Integer.MAX_VALUE)
                .setWheel(1);
        group.addConfigurators(fluidConfigurator, countConfigurator, componentsConfigurator);
        if (LDLib2.isJeiLoaded()) {
            RegistrySearchComponent.JEISupport.ghostFluid(group, Predicates.alwaysTrue(), fluidStack -> {
                updater.accept(fluidStack);
                group.notifyChanges();
            });
        }
        if (LDLib2.isReiLoaded()) {
            RegistrySearchComponent.REISupport.ghostFluid(group, Predicates.alwaysTrue(), fluidStack -> {
                updater.accept(fluidStack);
                group.notifyChanges();
            });
        }
        if (LDLib2.isEmiLoaded()) {
            RegistrySearchComponent.EMISupport.ghostFluid(group, Predicates.alwaysTrue(), fluidStack -> {
                updater.accept(fluidStack);
                group.notifyChanges();
            });
        }
        return group;
    }

    private static FluidStack withTag(Fluid fluid, int amount, @Nullable CompoundTag tag) {
        var stack = new FluidStack(fluid, amount);
        stack.setTag(tag == null ? null : tag.copy());
        return stack;
    }
}
