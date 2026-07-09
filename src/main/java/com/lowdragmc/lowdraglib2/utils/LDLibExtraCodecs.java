package com.lowdragmc.lowdraglib2.utils;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.PrimitiveCodec;
import lombok.experimental.UtilityClass;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@UtilityClass
public final class LDLibExtraCodecs {
    public final static MapCodec ERROR_DECODER = MapCodec.of(Encoder.empty(), new MapDecoder.Implementation<>() {
        @Override
        public <T> DataResult<Object> decode(final DynamicOps<T> ops, final MapLike<T> input) {
            return DataResult.error(() -> "Error decoding");
        }

        @Override
        public <T> Stream<T> keys(final DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "ERROR_DECODER";
        }
    });

    public final static Codec<UUID> UUID = Codec.STRING.xmap(java.util.UUID::fromString, java.util.UUID::toString);

    public final static Codec<Tag> TAG = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Tag, T>> decode(DynamicOps<T> ops, T input) {
            return DataResult.success(Pair.of(ops.convertTo(NbtOps.INSTANCE, input), input));
        }

        @Override
        public <T> DataResult<T> encode(Tag input, DynamicOps<T> ops, T prefix) {
            return DataResult.success(NbtOps.INSTANCE.convertTo(ops, input));
        }
    };

    public static <T> T getOrThrow(DataResult<T> result) {
        return result.getOrThrow(false, LDLib2.LOGGER::error);
    }

    public final static Codec<Recipe> RECIPE_ID = ResourceLocation.CODEC.flatXmap(id -> {
        if (Platform.getMinecraftServer() != null) {
            return Platform.getMinecraftServer().getRecipeManager().byKey(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown recipe: " + id));
        } else if (Platform.isClient()){
            var level = Minecraft.getInstance().level;
            if (level == null) return DataResult.error(() -> "No recipe manager available");
            return level.getRecipeManager().byKey(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown recipe: " + id));
        }
        return DataResult.error(() -> "No recipe manager available");
    }, recipe -> DataResult.success(recipe.getId()));

    public final static PrimitiveCodec<Character> CHAR = new PrimitiveCodec<>() {
        public <T> DataResult<Character> read(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).map(number -> (char) number.intValue());
        }

        public <T> T write(DynamicOps<T> ops, Character value) {
            return ops.createInt(value);
        }

        public String toString() {
            return "Char";
        }
    };

    public final static Codec<Number> NUMBER = TAG.xmap(
            tag -> {
                if (tag instanceof IntTag intTag) return intTag.getAsInt();
                if (tag instanceof LongTag longTag) return longTag.getAsLong();
                if (tag instanceof ByteTag byteTag) return byteTag.getAsByte();
                if (tag instanceof ShortTag shortTag) return shortTag.getAsShort();
                if (tag instanceof FloatTag floatTag) return floatTag.getAsFloat();
                if (tag instanceof DoubleTag doubleTag) return doubleTag.getAsDouble();
                if (tag == null) return null;
                throw new IllegalArgumentException("Invalid tag type: " + tag.getClass().getName());
            },
            number -> {
                if (number instanceof Integer value) return IntTag.valueOf(value);
                if (number instanceof Long value) return LongTag.valueOf(value);
                if (number instanceof Byte value) return ByteTag.valueOf(value);
                if (number instanceof Short value) return ShortTag.valueOf(value);
                if (number instanceof Float value) return FloatTag.valueOf(value);
                if (number instanceof Double value) return DoubleTag.valueOf(value);
                if (number == null) return null;
                return DoubleTag.valueOf(number.doubleValue());
            }
    );

    public static final Codec<Vector3i> VECTOR3I = Codec.INT
            .listOf()
            .comapFlatMap(
                    list -> Util.fixedSize(list, 3).map(l -> new Vector3i(l.get(0), l.get(1), l.get(2))),
                    vec3i -> List.of(vec3i.x, vec3i.y, vec3i.z)
            );

    public static final Codec<Vector2f> VECTOR2F = Codec.FLOAT
            .listOf()
            .comapFlatMap(
                    list -> Util.fixedSize(list, 2).map(l -> new Vector2f(l.get(0), l.get(1))),
                    vec2f -> List.of(vec2f.x, vec2f.y)
            );

    public static final Codec<Vector2i> VECTOR2I = Codec.INT
            .listOf()
            .comapFlatMap(
                    list -> Util.fixedSize(list, 2).map(l -> new Vector2i(l.get(0), l.get(1))),
                    vec2i -> List.of(vec2i.x, vec2i.y)
            );

    public static final Codec<Vector4f> VECTOR4F = Codec.FLOAT
            .listOf()
            .comapFlatMap(
                    list -> Util.fixedSize(list, 4).map(l -> new Vector4f(l.get(0), l.get(1), l.get(2), l.get(3))),
                    vec4f -> List.of(vec4f.x, vec4f.y, vec4f.z, vec4f.w)
            );

    public static final Codec<ItemStack> ITEM_STACK = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
            var result = ItemStack.CODEC.decode(ops, input);
            if (result.result().isPresent()) return result;

            var realOp = Platform.registryOps(ops, Platform.getClientRegistryAccess());
            result = ItemStack.CODEC.decode(realOp, input);
            if (result.result().isPresent()) return result;

            realOp = Platform.registryOps(ops, Platform.getServerRegistryAccess());
            return ItemStack.CODEC.decode(realOp, input);
        }

        @Override
        public <T> DataResult<T> encode(ItemStack input, DynamicOps<T> ops, T prefix) {
            var result = ItemStack.CODEC.encode(input, ops, prefix);
            if (result.result().isPresent()) return result;

            var realOp = Platform.registryOps(ops, Platform.getClientRegistryAccess());
            result = ItemStack.CODEC.encode(input, realOp, prefix);
            if (result.result().isPresent()) return result;
            
            realOp = Platform.registryOps(ops, Platform.getServerRegistryAccess());
            return ItemStack.CODEC.encode(input, realOp, prefix);
        }
    };


    /**
     * This codec use an empty encoder and a decoder that always return an error
     */
    public static <T> MapCodec<T> errorDecoder() {
        return ERROR_DECODER;
    }

    public final static String NULL_STRING = "_NULL_";

    /**
     * Why we need it? Because Mojang doesn't support null in the nbt.
     *
     * @param ops
     * @return
     * @param <T>
     */
    public static <T> T createStringNull(DynamicOps<T> ops) {
        return ops.createString(NULL_STRING);
    }

    /**
     * Checks if the provided payload is equivalent to the empty value in the given {@link DynamicOps} instance
     * or if it represents a string with the value of "null".
     *
     * @param <T>    the generic type representing the data structure being operated on within {@link DynamicOps}
     * @param ops    the {@link DynamicOps} instance to interpret the payload
     * @param payload the data payload to be checked
     * @return {@code true} if the payload is empty or evaluates to the string "null"; otherwise, {@code false}
     */
    public static <T> boolean isEmptyOrStringNull(DynamicOps<T> ops, T payload) {
        return ops == ops.empty() || ops.getStringValue(payload).map(s -> s.equals(NULL_STRING)).result().orElse(false);
    }

    /**
     * Combines multiple codecs into one, maintaining compatibility with outdated versions.
     * The method tries to decode values using the latest codec first and falls back to
     * the outdated codecs in the order they are provided.
     *
     * @param <T>      the type of the object being encoded/decoded
     * @param latest   the most recent and preferred {@link Codec} to use for encoding/decoding
     * @param outdated an array of older {@link Codec} versions to maintain backward compatibility
     * @return a {@link Codec} that combines the latest codec with the fallback compatibility of outdated codecs
     */
    @SafeVarargs
    public static <T> Codec<T> compat(Codec<T> latest, Codec<T>... outdated) {
        if (outdated.length < 1) {
            return latest;
        }
        if (outdated.length == 1) {
            return Codec.either(latest, outdated[0]).xmap(
                    either -> either.map(value -> value, value -> value),
                    Either::left
            );
        }
        return Codec.either(latest, compat(outdated[0], Arrays.copyOfRange(outdated, 1, outdated.length))).xmap(
                either -> either.map(value -> value, value -> value),
                Either::left
        );
    }

    /**
     * Creates a codec for a given {@link Enum} type, allowing serialization and deserialization
     * between the enum's name and its corresponding instance. If deserialization fails, a fallback
     * enum value is returned.
     *
     * @param <T>      the type of the enum
     * @param clazz    the class object of the enum type
     * @param fallback the fallback value to return in case of a deserialization failure
     * @return a {@link Codec} for the supplied enum type
     */
    public static <T extends Enum<T>> Codec<T> enumCodec(Class<T> clazz, T fallback) {
        return Codec.STRING.xmap(
                name -> {
                    try {
                        return Enum.valueOf(clazz, name);
                    } catch (Exception e) {
                        return fallback;
                    }
                },
                Enum::name
        );
    }
}
