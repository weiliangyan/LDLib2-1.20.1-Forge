package com.lowdragmc.lowdraglib2.utils.codec;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Compatibility bridge for mods compiled against the 1.21 LDLib2/NeoForge
 * stream codec package name.
 */
public interface StreamCodec<B, V> extends com.lowdragmc.lowdraglib2.compat.network.codec.StreamCodec<B, V> {
    static <B, V> StreamCodec<B, V> of(BiConsumer<B, V> encoder, Function<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public void encode(B buffer, V value) {
                encoder.accept(buffer, value);
            }

            @Override
            public V decode(B buffer) {
                return decoder.apply(buffer);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(BiConsumer<V, B> encoder, Function<B, V> decoder) {
        return of((buffer, value) -> encoder.accept(value, buffer), decoder);
    }

    @Override
    default <O> StreamCodec<B, O> map(Function<V, O> toOuter, Function<O, V> toInner) {
        return of((buffer, value) -> encode(buffer, toInner.apply(value)), buffer -> toOuter.apply(decode(buffer)));
    }
}
