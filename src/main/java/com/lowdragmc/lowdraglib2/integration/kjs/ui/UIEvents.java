package com.lowdragmc.lowdraglib2.integration.kjs.ui;

import dev.latvian.mods.kubejs.event.*;
import dev.latvian.mods.kubejs.script.ScriptTypePredicate;

public interface UIEvents {
    EventGroup INSTANCE = EventGroup.of("LDLib2UI");
    EventHandler PLAYER = INSTANCE.add("player", ScriptTypePredicate.ALL, () -> KJSPlayerUIMenuType.PlayerUIEventJS.class);
    EventHandler BLOCK = INSTANCE.add("block", ScriptTypePredicate.ALL, () -> KJSBlockUIMenuType.BlockUIEventJS.class);
    EventHandler ITEM = INSTANCE.add("item", ScriptTypePredicate.ALL, () -> KJSHeldItemUIMenuType.ItemUIEventJS.class);
}
