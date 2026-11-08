package com.lowdragmc.lowdraglib2.editor.resource;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

public abstract class EditorResourceEvent extends Event implements IModBusEvent {
    public final ResourceInstance<?> resourceInstance;

    public EditorResourceEvent(ResourceInstance<?> resourceInstance) {
        this.resourceInstance = resourceInstance;
    }

    public static class LoadBuiltin extends EditorResourceEvent {
        public <T> LoadBuiltin(ResourceInstance<T> resourceInstance) {
            super(resourceInstance);
        }
    }
}
