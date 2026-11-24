package com.lowdragmc.lowdraglib2.test.syncdata;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.misc.FluidStorage;
import com.lowdragmc.lowdraglib2.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib2.syncdata.IContentVersion;
import com.lowdragmc.lowdraglib2.syncdata.accessor.readonly.INBTSerializableReadOnlyAccessor;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.ManagedCodec;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.mark.IMarkStrategy;
import com.lowdragmc.lowdraglib2.syncdata.mark.MarkStrategies;
import com.mojang.serialization.Codec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@GameTestHolder(LDLib2.MOD_ID)
public class MarkStrategyTest {

    public static class VersionedHolder extends SyncProbeHolder {
        @Persisted
        @DescSynced
        public final SyncProbeHolder.VersionedItems items = new SyncProbeHolder.VersionedItems(2);
        @Persisted
        @DescSynced
        public final SyncProbeHolder.VersionedTank tank = new SyncProbeHolder.VersionedTank(1000);
        @Persisted
        @DescSynced
        @ManagedCodec("thingCodec")
        public VersionedThing thing = new VersionedThing(1);

        public VersionedHolder(ServerLevel level) {
            super(level);
            items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        }

        private static Codec<VersionedThing> thingCodec() {
            return Codec.INT.xmap(VersionedThing::new, thing -> thing.value);
        }
    }

    public static class VersionedThing implements IContentVersion {
        public int value;
        public long version;

        public VersionedThing(int value) {
            this.value = value;
        }

        @Override
        public long getContentVersion() {
            return version;
        }
    }

    /**
     * Saves and syncs only its counter.
     */
    public static class Counted implements INBTSerializable<CompoundTag> {
        public int counter;
        public String note = "";

        @Override
        public CompoundTag serializeNBT() {
            var tag = new CompoundTag();
            tag.putInt("counter", counter);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            counter = tag.getInt("counter");
        }
    }

    public static class Late extends Counted {
    }

    public static class Muted extends Counted {
    }

    public static class Contested extends Counted {
    }

    public static class CountedHolder extends SyncProbeHolder {
        @Persisted
        @DescSynced
        public final Counted counted = new Counted();
        @Persisted
        @DescSynced
        public final Muted muted = new Muted();

        public CountedHolder(ServerLevel level) {
            super(level);
        }
    }

    private record CounterStrategy(Class<?> type, boolean everChanges) implements IMarkStrategy<Counted, Integer> {
        @Override
        public boolean test(Class<?> type) {
            return type == this.type;
        }

        @Override
        public @NotNull Integer obtainManagedMark(@NotNull Counted value) {
            return value.counter;
        }

        @Override
        public boolean areDifferent(@NotNull Integer managedMark, @NotNull Counted value) {
            return everChanges && managedMark != value.counter;
        }
    }

    // the registry lives as long as the game, so the test strategies go in once
    private static boolean registered;
    private static final CounterStrategy LATE = new CounterStrategy(Late.class, true);
    private static final CounterStrategy PREFERRED = new CounterStrategy(Contested.class, true);
    private static boolean lateRegistered;

    private static synchronized void registerCounterStrategy() {
        if (registered) return;
        registered = true;
        MarkStrategies.register(new CounterStrategy(Counted.class, true), 1000);
        // tells the sync a Muted never changes, which only holds if the sync asks the registry rather than the save
        MarkStrategies.register(new CounterStrategy(Muted.class, false), 1000);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void builtInStrategiesTakeOnlyContainersSavedTheBaseWay(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        var items = MarkStrategies.find(ItemStackTransfer.class);
        var tank = MarkStrategies.find(FluidStorage.class);
        var version = MarkStrategies.find(SyncProbeHolder.VersionedItems.class);
        if (items == null || tank == null || items == tank) failures.add("plain containers got " + items + " and " + tank);
        if (MarkStrategies.find(SyncProbeHolder.LockableItems.class) != null) failures.add("a handler saving extra state got a slot strategy");
        if (MarkStrategies.find(SyncProbeHolder.LockableTank.class) != null) failures.add("a tank saving extra state got a fluid strategy");
        if (MarkStrategies.find(SyncChangeDetectionTest.PassThroughItems.class) != null) failures.add("a handler reading slots its own way got a slot strategy");
        if (version == null || version == items || version != MarkStrategies.find(SyncProbeHolder.VersionedTank.class)) {
            failures.add("versioned containers got " + version);
        }
        if (MarkStrategies.find(String.class) != null) failures.add("a String got a strategy");
        report(helper, failures);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void contentVersionDecidesWhetherAValueChanged(GameTestHelper helper) {
        var level = helper.getLevel();
        var failures = new ArrayList<String>();
        expect(failures, level, "slot set", "items", true, h -> h.items.setStackInSlot(1, new ItemStack(Items.DIAMOND)));
        expect(failures, level, "extra state changed through its versioned setter", "items", true, h -> h.items.setLocked(true));
        expect(failures, level, "stack grown in place, which the version doesn't count", "items", false, h -> h.items.getStackInSlot(0).grow(1));
        expect(failures, level, "tank filled", "tank", true, h -> h.tank.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE));
        expect(failures, level, "codec field changed with a new version", "thing", true, h -> {
            h.thing.value = 7;
            h.thing.version++;
        });
        expect(failures, level, "codec field changed without a new version", "thing", false, h -> h.thing.value = 7);
        report(helper, failures);
    }

    private static void expect(List<String> failures, ServerLevel level, String name, String field, boolean synced, Consumer<VersionedHolder> change) {
        var server = new VersionedHolder(level);
        server.settle();
        var client = new VersionedHolder(level);
        server.initClient(client);
        change.accept(server);
        boolean sent = server.syncTo(client);
        if (sent != synced) {
            failures.add(name + ": " + (sent ? "synced " + server.syncFieldNames(server.sentChanged) : "not synced"));
        } else if (sent && !client.syncedValue(field).equals(server.syncedValue(field))) {
            failures.add(name + ": client has " + client.syncedValue(field) + ", server " + server.syncedValue(field));
        }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void registeredStrategyDecidesForItsClass(GameTestHelper helper) {
        registerCounterStrategy();
        var level = helper.getLevel();
        var failures = new ArrayList<String>();
        var server = new CountedHolder(level);
        server.settle();
        var client = new CountedHolder(level);
        server.initClient(client);

        server.counted.note = "only in memory";
        if (server.syncTo(client)) failures.add("a change the strategy leaves out was synced");
        server.counted.counter = 5;
        if (!server.syncTo(client) || client.counted.counter != 5) failures.add("the counter was not synced, client has " + client.counted.counter);
        server.muted.counter = 5;
        if (server.syncTo(client)) failures.add("the registered strategy was passed over for what the value saves");
        report(helper, failures);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void registeringReplacesEarlierLookupsAndLowerPrioritiesWin(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        synchronized (MarkStrategyTest.class) {
            if (!lateRegistered) {
                lateRegistered = true;
                if (MarkStrategies.find(Late.class) != null) failures.add("a class nobody registered for got a strategy");
                MarkStrategies.register(LATE, 1000);
                MarkStrategies.register(new CounterStrategy(Contested.class, false), 500);
                MarkStrategies.register(PREFERRED, 100);
            }
        }
        if ((Object) MarkStrategies.find(Late.class) != LATE) failures.add("a lookup made before the registration stayed: " + MarkStrategies.find(Late.class));
        if ((Object) MarkStrategies.find(Contested.class) != PREFERRED) failures.add("the higher priority number won: " + MarkStrategies.find(Contested.class));
        report(helper, failures);
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void aValueTurningIntoAnotherClassCountsAsChanged(GameTestHelper helper) {
        var failures = new ArrayList<String>();
        var accessor = new INBTSerializableReadOnlyAccessor();
        var mark = accessor.obtainManagedMark(new ItemStackTransfer(1));
        var other = new SyncProbeHolder.LockableItems(1);
        if (!accessor.areDifferent(mark, other)) failures.add("a value compared another way looked unchanged");
        var next = accessor.updateMark(mark, other);
        if (next == null) failures.add("a value compared another way got no new mark");
        else if (accessor.updateMark(next, other) != null) failures.add("an unchanged value got a new mark after switching");
        report(helper, failures);
    }

    private static void report(GameTestHelper helper, List<String> failures) {
        if (failures.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(failures.size() + " failed:\n" + String.join("\n", failures));
        }
    }
}
