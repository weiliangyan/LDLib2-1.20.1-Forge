package com.lowdragmc.lowdraglib2.registry;

import com.lowdragmc.lowdraglib2.Platform;

import java.util.Map;

/**
 * Controls when an annotated element should be registered based on the runtime environment.
 */
public enum RegistrationEnvironment {
    /**
     * Always register, regardless of environment.
     */
    ALWAYS,
    /**
     * Only register in development environment.
     */
    DEV_ONLY,
    /**
     * Only register in production environment.
     */
    PRODUCTION_ONLY,
    /**
     * Do not register automatically. Must be registered manually.
     */
    MANUAL;

    /**
     * Whether this environment allows automatic registration in the current runtime.
     */
    public boolean shouldRegister() {
        return switch (this) {
            case ALWAYS -> true;
            case DEV_ONLY -> Platform.isDevEnv();
            case PRODUCTION_ONLY -> !Platform.isDevEnv();
            case MANUAL -> false;
        };
    }

    /**
     * Checks annotation data map for environment and legacy manual fields.
     * Use this in annotation filters for {@link AutoRegistry}.
     */
    public static boolean shouldRegister(Map<String, Object> annotationData) {
        var environment = annotationData.get("environment");
        if (environment != null) {
            var value = readEnumValue(environment);
            if (value != null) {
                return RegistrationEnvironment.valueOf(value).shouldRegister();
            }
        }
        // Legacy: check deprecated manual field
        if (annotationData.get("manual") instanceof Boolean manual && manual) {
            return false;
        }
        return true;
    }

    private static String readEnumValue(Object enumHolder) {
        if (enumHolder instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (enumHolder instanceof String string) {
            return string;
        }
        for (var methodName : new String[] {"value", "getValue"}) {
            try {
                var method = enumHolder.getClass().getMethod(methodName);
                var value = method.invoke(enumHolder);
                if (value instanceof String string) return string;
                if (value instanceof Enum<?> enumValue) return enumValue.name();
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
