package com.lowdragmc.lowdraglib2.utils.function;

import java.util.function.Supplier;

public final class LDSuppliers {

    private LDSuppliers() {
    }

    public static <T> Supplier<T> nul() {
        return () -> null;
    }
}
