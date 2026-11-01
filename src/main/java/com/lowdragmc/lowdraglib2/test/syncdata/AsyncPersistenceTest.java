package com.lowdragmc.lowdraglib2.test.syncdata;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.syncdata.ref.IRef;
import com.lowdragmc.lowdraglib2.test.syncdata.bench.SyncBenchBlock;
import com.lowdragmc.lowdraglib2.test.syncdata.bench.SyncBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.locks.LockSupport;

/**
 * Persistence of a block entity synced by the async thread, which runs on real time rather than on ticks.
 */
@GameTestHolder(LDLib2.MOD_ID)
public class AsyncPersistenceTest {
    private static final long PERIODS_NANOS = 250_000_000L;
    private static final long GIVE_UP_NANOS = 5_000_000_000L;
    private static final long TICK_NANOS = 2_000_000L;

    @GameTest(template = "empty", batch = "ldlib2_async_persistence", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void changesFoundInOnePeriodMarkTheBlockEntityChangedOnce(GameTestHelper helper) {
        var pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, SyncBenchBlock.BLOCK);
        SyncBenchBlockEntity be = (SyncBenchBlockEntity) helper.getBlockEntity(pos);
        var step = new int[1];
        var since = new long[]{System.nanoTime()};
        helper.onEachTick(() -> {
            long now = System.nanoTime();
            if (step[0] == 0 && now - since[0] >= PERIODS_NANOS) {
                be.setChangedCalls = 0;
                be.tier++;
                be.energy--;
                be.logic.progress++;
                be.logic.working = !be.logic.working;
                step[0] = 1;
                since[0] = now;
            } else if (step[0] == 1 && (be.setChangedCalls > 0 || now - since[0] >= GIVE_UP_NANOS)) {
                step[0] = 2;
                since[0] = now;
            } else if (step[0] == 2 && now - since[0] >= PERIODS_NANOS) {
                // normally found in one period, in two if a period starts between the writes
                if (be.setChangedCalls < 1 || be.setChangedCalls > 2) {
                    helper.fail("four persisted changes found by the async thread caused " + be.setChangedCalls + " setChanged calls");
                }
                be.setChangedCalls = 0;
                be.markDirty("tier");
                if (be.setChangedCalls != 1) {
                    helper.fail("markDirty on the server thread caused " + be.setChangedCalls + " setChanged calls right away");
                }
                helper.setBlock(pos, Blocks.AIR);
                helper.succeed();
                return;
            }
            long wait;
            while ((wait = now + TICK_NANOS - System.nanoTime()) > 0) {
                LockSupport.parkNanos(wait);
            }
        });
    }

    @GameTest(template = "empty", batch = "ldlib2_async_persistence", timeoutTicks = 6000)
    @PrefixGameTestTemplate(false)
    public static void unwatchedChangesWaitUnsentButArePersisted(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 1);
        helper.setBlock(pos, SyncBenchBlock.BLOCK);
        SyncBenchBlockEntity be = (SyncBenchBlockEntity) helper.getBlockEntity(pos);
        var chunk = helper.getLevel().getChunkAt(be.getBlockPos());
        var step = new int[1];
        var since = new long[]{System.nanoTime()};
        // a first change gets the chunk looked at, and found unwatched, since no player is in a gametest
        be.energy--;
        helper.onEachTick(() -> {
            long now = System.nanoTime();
            if (step[0] == 0 && now - since[0] >= PERIODS_NANOS) {
                chunk.setUnsaved(false);
                be.setChangedCalls = 0;
                be.tier++;
                be.logic.progress++;
                step[0] = 1;
                since[0] = now;
            } else if (step[0] == 1 && now - since[0] >= 2 * PERIODS_NANOS) {
                if (!chunk.isUnsaved()) helper.fail("changes nobody watched were not persisted");
                if (be.setChangedCalls < 1 || be.setChangedCalls > 2) {
                    helper.fail("two changes, waiting unsent for ten periods, caused " + be.setChangedCalls + " setChanged calls");
                }
                for (var name : new String[]{"tier", "logic"}) {
                    if (!syncRef(be, name).isSyncDirty()) helper.fail(name + " was sent or dropped instead of waiting for a watcher");
                }
                helper.setBlock(pos, Blocks.AIR);
                helper.succeed();
                return;
            }
            long wait;
            while ((wait = now + TICK_NANOS - System.nanoTime()) > 0) {
                LockSupport.parkNanos(wait);
            }
        });
    }

    private static IRef<?> syncRef(SyncBenchBlockEntity be, String name) {
        for (var ref : be.getSyncStorage().getSyncFields()) {
            if (ref.getKey().getName().equals(name)) return ref;
        }
        throw new IllegalArgumentException(name);
    }
}
