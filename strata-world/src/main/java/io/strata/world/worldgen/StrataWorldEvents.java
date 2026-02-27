package io.strata.world.worldgen;

import io.strata.core.util.StrataLogger;

/**
 * Handles Fabric worldgen events for strata-world.
 * In Phase 1, this is minimal — biome injection is handled by the Mixin.
 * Phase 2 will add BiomeModifications and asset registry listeners here.
 */
public final class StrataWorldEvents {

    private StrataWorldEvents() {}

    public static void initialize() {
        StrataLogger.debug("StrataWorldEvents initialized.");
    }
}
