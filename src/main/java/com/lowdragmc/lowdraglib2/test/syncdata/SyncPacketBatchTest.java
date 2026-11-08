package com.lowdragmc.lowdraglib2.test.syncdata;

import com.lowdragmc.lowdraglib2.CommonProxy;
import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.compat.network.IPayloadContext;
import com.lowdragmc.lowdraglib2.compat.network.custom.CustomPacketPayload;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketAutoSyncBlockEntities;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketAutoSyncBlockEntities.Entry;
import com.lowdragmc.lowdraglib2.networking.s2c.SPacketAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.lowdragmc.lowdraglib2.syncdata.holder.SyncPacketBatch;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;
import com.lowdragmc.lowdraglib2.utils.TagBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.nikdo53.neobackports.io.StreamCodec;

import java.lang.reflect.Proxy;
import java.util.*;

@GameTestHolder(LDLib2.MOD_ID)
public class SyncPacketBatchTest {

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void batchedPacketRoundTrips(GameTestHelper helper) {
        var level = helper.getLevel();
        var bigData = new byte[10_000];
        new Random(42).nextBytes(bigData);
        var entries = List.of(
                new Entry(new BlockPos(1, 2, 3), CommonProxy.SYNC_TEST_BE_TYPE.get(), bits(0, 3), new byte[]{1, 2, 3}, new CompoundTag()),
                new Entry(new BlockPos(-4, 5, -6), CommonProxy.SYNC_BENCH_BE_TYPE.get(), new BitSet(), new byte[0],
                        TagBuilder.compound().add("a", 1).add("b", "text").add("c", TagBuilder.compound().add("d", 2.5).build()).build()),
                new Entry(new BlockPos(7, -64, 9), BlockEntityType.FURNACE, bits(70), bigData, new CompoundTag()),
                new Entry(BlockPos.ZERO, BlockEntityType.CHEST, bits(1), new byte[]{4},
                        TagBuilder.compound().add("\u540d\u5b57", "\u5b57".repeat(4000)).build()));

        var decoded = SPacketAutoSyncBlockEntities.CODEC.decode(buffer(level, SPacketAutoSyncBlockEntities.CODEC, new SPacketAutoSyncBlockEntities(entries)));
        check(helper, decoded.entries().size() == entries.size(), "decoded " + decoded.entries().size() + " entries");
        for (int i = 0; i < entries.size(); i++) {
            var sent = entries.get(i);
            var got = decoded.entries().get(i);
            check(helper, sent.pos().equals(got.pos()) && sent.type() == got.type() && sent.changed().equals(got.changed())
                    && Arrays.equals(sent.data(), got.data()) && sent.extra().equals(got.extra()), "entry " + i + " changed on the way: " + got);
            int encoded = buffer(level, Entry.CODEC, sent).readableBytes();
            check(helper, encoded <= sent.sizeBound(), "entry " + i + " encodes to " + encoded + " bytes, bound " + sent.sizeBound());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void packingKeepsOrderAndSize(GameTestHelper helper) {
        var level = helper.getLevel();
        int maxBytes = 400;
        var entries = new ArrayList<Entry>();
        for (int size : new int[]{100, 100, 300, 50, 1000, 10, 0, 0}) {
            entries.add(new Entry(new BlockPos(entries.size(), 0, 0), CommonProxy.SYNC_TEST_BE_TYPE.get(), bits(1), new byte[size], new CompoundTag()));
        }
        var packets = SPacketAutoSyncBlockEntities.pack(entries, maxBytes);

        var flattened = packets.stream().flatMap(packet -> packet.entries().stream()).toList();
        check(helper, flattened.equals(entries), "packing reordered or lost entries");
        for (var packet : packets) {
            int bound = packet.entries().stream().mapToInt(Entry::sizeBound).sum();
            check(helper, bound <= maxBytes || packet.entries().size() == 1, "a packet of " + packet.entries().size() + " entries holds " + bound + " bytes");
            int encoded = buffer(level, SPacketAutoSyncBlockEntities.CODEC, packet).readableBytes();
            check(helper, encoded <= bound + 5, "a packet encodes to " + encoded + " bytes, over its bound " + bound);
        }
        check(helper, packets.stream().anyMatch(packet -> packet.entries().equals(List.of(entries.get(4)))), "the oversized entry shares a packet");
        check(helper, packets.size() < entries.size(), "nothing was packed together");
        check(helper, SPacketAutoSyncBlockEntities.pack(List.of(), maxBytes).isEmpty(), "no entries made a packet");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void clientAppliesEachEntryToItsBlockEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        var first = new BlockPos(1, 2, 1);
        var second = new BlockPos(3, 2, 1);
        var broken = new BlockPos(5, 2, 1);
        for (var pos : List.of(first, second, broken)) {
            helper.setBlock(pos, SyncTestBlock.BLOCK);
        }
        var corrupt = new Entry(helper.absolutePos(broken), CommonProxy.SYNC_TEST_BE_TYPE.get(), bits(syncIndex("label")),
                new byte[]{(byte) 0xFF}, new CompoundTag());
        var packet = new SPacketAutoSyncBlockEntities(List.of(
                counterUpdate(level, helper.absolutePos(first), 5),
                withType(counterUpdate(level, helper.absolutePos(first), 9), BlockEntityType.FURNACE),
                counterUpdate(level, helper.absolutePos(new BlockPos(7, 2, 1)), 11),
                corrupt,
                counterUpdate(level, helper.absolutePos(second), 7)));

        SPacketAutoSyncBlockEntities.execute(packet, contextOf(level));
        SyncTestBlockEntity firstBe = (SyncTestBlockEntity) helper.getBlockEntity(first);
        SyncTestBlockEntity secondBe = (SyncTestBlockEntity) helper.getBlockEntity(second);
        check(helper, firstBe.counter == 5, "the first block entity has counter " + firstBe.counter + ", expected 5 and not the mismatched type's 9");
        check(helper, secondBe.counter == 7, "a corrupt entry stopped the ones after it, counter " + secondBe.counter);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void serverBatchesDefaultPacketsAndSkipsBrokenUpdates(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = SyncTestBlock.BLOCK.defaultBlockState();
        var first = new SyncTestBlockEntity(new BlockPos(1, 2, 3), state) {
            @Override
            public void writeCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
                tag.putString("note", "first");
            }
        };
        var removed = new SyncTestBlockEntity(new BlockPos(4, 5, 6), state);
        removed.setRemoved();
        var throwing = new ManagedCodecTest.SyncedHolder(level) {
            @Override
            public void writeCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
                throw new IllegalStateException("broken holder");
            }
        };
        var custom = new ManagedCodecTest.SyncedHolder(level);
        var second = new SyncTestBlockEntity(new BlockPos(7, 8, 9), state);
        var updates = List.of(first, removed, throwing, custom, second).stream()
                .map(holder -> new SyncPacketBatch.Update(holder, bits(0), new byte[]{1})).toList();

        var packets = SyncPacketBatch.build(level, updates);
        var positions = packets.entries().stream().map(Entry::pos).toList();
        check(helper, positions.equals(List.of(first.getBlockPos(), second.getBlockPos())), "batched " + positions);
        check(helper, packets.entries().get(0).extra().getString("note").equals("first"), "custom sync data lost: " + packets.entries().get(0).extra());
        check(helper, packets.singles().size() == 2, packets.singles().size() + " single packets for the players who can't take a batch");
        check(helper, packets.custom().size() == 1 && packets.custom().get(0).getIdentifier().equals(LDLib2.id("managed_codec_test")),
                "the holder's own payload was not passed through: " + packets.custom());
        helper.succeed();
    }

    private record FakeReceiver(Set<CustomPacketPayload.Type<?>> channels, boolean broken, List<CustomPacketPayload> received)
            implements SyncPacketBatch.Receiver {
        FakeReceiver(boolean broken, CustomPacketPayload.Type<?>... channels) {
            this(Set.of(channels), broken, new ArrayList<>());
        }

        @Override
        public boolean hasChannel(CustomPacketPayload.Type<?> type) {
            if (broken) throw new IllegalStateException("expected by the test");
            return channels.contains(type);
        }

        @Override
        public void send(Packet<?> packet) {
            //received.add(((ClientboundCustomPayloadPacket) packet).payload());
        }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void serverSendsWhatEachPlayerSupports(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = SyncTestBlock.BLOCK.defaultBlockState();
        var customType = new CustomPacketPayload.Type<>(LDLib2.id("managed_codec_test"));
        var updates = List.<ISyncMangedHolder>of(new SyncTestBlockEntity(new BlockPos(1, 2, 3), state),
                        new ManagedCodecTest.SyncedHolder(level), new SyncTestBlockEntity(new BlockPos(4, 5, 6), state)).stream()
                .map(holder -> new SyncPacketBatch.Update(holder, bits(0), new byte[]{1})).toList();
        var packets = SyncPacketBatch.build(level, updates);

        var broken = new FakeReceiver(true);
        var batching = new FakeReceiver(false, SPacketAutoSyncBlockEntities.TYPE, SPacketAutoSyncBlockEntity.TYPE, customType);
        var singleOnly = new FakeReceiver(false, SPacketAutoSyncBlockEntity.TYPE, customType);
        var none = new FakeReceiver(false);
        SyncPacketBatch.send(List.of(broken, batching, singleOnly, none), packets);

        check(helper, batching.received().size() == 2 && batching.received().get(0) instanceof SPacketAutoSyncBlockEntities batch
                && batch.entries().size() == 2 && batching.received().get(batching.received.size() - 1).type().equals(customType),
                "a player taking batches got " + batching.received());
        check(helper, singleOnly.received().size() == 3 && singleOnly.received().stream().filter(SPacketAutoSyncBlockEntity.class::isInstance).count() == 2,
                "a player without the batch channel got " + singleOnly.received());
        check(helper, none.received().isEmpty(), "a player without the channels got " + none.received());
        helper.succeed();
    }

    private static Entry counterUpdate(ServerLevel level, BlockPos pos, int counter) {
        var source = new SyncTestBlockEntity(pos, SyncTestBlock.BLOCK.defaultBlockState());
        source.counter = counter;
        var fields = source.getSyncStorage().getSyncFields();
        int index = syncIndex("counter");
        var data = ByteBufUtil.writeCustomData(buf -> fields[index].readSyncToStream(buf));
        return new Entry(pos, CommonProxy.SYNC_TEST_BE_TYPE.get(), bits(index), data, new CompoundTag());
    }

    private static int syncIndex(String name) {
        var fields = new SyncTestBlockEntity(BlockPos.ZERO, SyncTestBlock.BLOCK.defaultBlockState()).getSyncStorage().getSyncFields();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].getKey().getName().equals(name)) return i;
        }
        throw new IllegalArgumentException(name);
    }

    private static Entry withType(Entry entry, BlockEntityType<?> type) {
        return new Entry(entry.pos(), type, entry.changed(), entry.data(), entry.extra());
    }

    private static IPayloadContext contextOf(ServerLevel level) {
        var player = FakePlayerFactory.getMinecraft(level);
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BitSet bits(int... indices) {
        var bits = new BitSet();
        for (int index : indices) {
            bits.set(index);
        }
        return bits;
    }

    private static <T> FriendlyByteBuf buffer(ServerLevel level, StreamCodec<T> codec, T value) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        return buf;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
