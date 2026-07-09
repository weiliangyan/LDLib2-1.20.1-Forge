package com.lowdragmc.lowdraglib2.compat;

/**
 * Forge 1.21 exposes TriState in this package, while Forge 1.20.1 still uses
 * boolean baked-model hooks. LDLib keeps the three-state renderer contract
 * internally and collapses it at the 1.20.1 Forge boundary.
 */
public enum TriState {
    TRUE,
    FALSE,
    DEFAULT
}
