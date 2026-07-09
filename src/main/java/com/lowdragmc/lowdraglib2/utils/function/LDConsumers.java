package com.lowdragmc.lowdraglib2.utils.function;

import java.util.function.Consumer;

public final class LDConsumers {

    private LDConsumers() {
    }

    public static <T> Consumer<T> nop() {
        return ignored -> {
        };
    }
}
