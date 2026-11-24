package com.lowdragmc.lowdraglib2.test.syncdata;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.TagBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LDLib2.MOD_ID)
public class PersistedCustomDataTest {

    public static class CustomOnly extends SyncProbeHolder {
        public String note = "";

        public CustomOnly(ServerLevel level) {
            super(level);
        }

        @Override
        public void saveCustomPersistedData(HolderLookup.Provider provider, CompoundTag tag, boolean forDrop) {
            if (!note.isEmpty()) tag.putString("note", note);
        }

        @Override
        public void loadCustomPersistedData(HolderLookup.Provider provider, CompoundTag tag) {
            note = tag.getString("note");
        }
    }

    public static class WithFields extends CustomOnly {
        @Persisted
        public int value;

        public WithFields(ServerLevel level) {
            super(level);
        }
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void customDataSurvivesSaveAndLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var access = level.registryAccess();
        var saved = new WithFields(level);
        saved.value = 3;
        saved.note = "hello";
        var tag = new CompoundTag();
        saved.saveManagedPersistentData(access, tag, false);

        var loaded = new WithFields(level);
        loaded.loadManagedPersistentData(access, tag);
        check(helper, loaded.value == 3, "managed field lost: " + tag);
        check(helper, loaded.note.equals("hello"), "custom data lost: " + tag);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void customDataSurvivesWithoutManagedFields(GameTestHelper helper) {
        var level = helper.getLevel();
        var access = level.registryAccess();
        var saved = new CustomOnly(level);
        saved.note = "hello";
        var tag = new CompoundTag();
        saved.saveManagedPersistentData(access, tag, false);
        check(helper, !tag.isEmpty(), "custom data was not saved");

        var loaded = new CustomOnly(level);
        loaded.loadManagedPersistentData(access, tag);
        check(helper, loaded.note.equals("hello"), "custom data lost: " + tag);
        helper.succeed();
    }

    @GameTest(template = "empty")
    @PrefixGameTestTemplate(false)
    public static void customDataFromEarlierSavesLoads(GameTestHelper helper) {
        var level = helper.getLevel();
        var access = level.registryAccess();
        var tag = TagBuilder.compound()
                .add("managed", TagBuilder.compound()
                        .add("value", 3)
                        .add("custom", TagBuilder.compound().add("note", "hello").build())
                        .build())
                .build();

        var loaded = new WithFields(level);
        loaded.loadManagedPersistentData(access, tag);
        check(helper, loaded.value == 3, "managed field lost: " + tag);
        check(helper, loaded.note.equals("hello"), "custom data lost: " + tag);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
