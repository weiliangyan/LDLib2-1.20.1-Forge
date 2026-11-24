package com.lowdragmc.lowdraglib2.compat.font;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public enum FontOption implements StringRepresentable {
    UNIFORM("uniform"),
    JAPANESE_VARIANTS("jp");

    public static final Codec<FontOption> CODEC = StringRepresentable.fromEnum(FontOption::values);
    private final String name;

    FontOption(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Filter {
        private final Map<FontOption, Boolean> values;
        public static final Codec<Filter> CODEC = Codec.unboundedMap(FontOption.CODEC, Codec.BOOL)
                .xmap(Filter::new, p_326230_ -> p_326230_.values);
        public static final Filter ALWAYS_PASS = new Filter(Map.of());

        public Filter(Map<FontOption, Boolean> values) {
            this.values = values;
        }

        public boolean apply(Set<FontOption> options) {
            for (Map.Entry<FontOption, Boolean> entry : this.values.entrySet()) {
                if (options.contains(entry.getKey()) != entry.getValue()) {
                    return false;
                }
            }

            return true;
        }

        public Filter merge(Filter filter) {
            Map<FontOption, Boolean> map = new HashMap<>(filter.values);
            map.putAll(this.values);
            return new Filter(Map.copyOf(map));
        }
    }
}
