package io.strata.world;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.biome.StrataBiomes;
import io.strata.world.config.WorldConfig;
import io.strata.world.worldgen.StrataWorldEvents;
import io.strata.world.worldgen.StrataWorldgen;
import net.fabricmc.api.ModInitializer;

/**
 * Entry point for the {@code strata-world} module.
 *
 * <p>Initializes configuration, biome registry keys, worldgen noise parameters,
 * and worldgen event hooks in dependency order. All Strata biomes are injected
 * into the overworld at world-generation time via
 * {@link io.strata.world.mixin.VanillaBiomeParametersMixin}.
 */
public class StrataWorld implements ModInitializer {

    /** Fabric mod ID for the {@code strata-world} module. */
    public static final String MOD_ID = "strata_world";

    /**
     * Called by Fabric during mod initialization.
     *
     * <p>Registers the world config, triggers biome key class loading,
     * initializes worldgen noise parameters, and hooks worldgen events.
     * Logs a summary line with the biome count on completion.
     */
    @Override
    public void onInitialize() {
        StrataLogger.info("========================================");
        StrataLogger.info("  Strata World initializing...");
        StrataLogger.info("========================================");

        StrataConfigHelper.register(WorldConfig.class);
        StrataBiomes.initialize();
        StrataWorldgen.initialize();
        StrataWorldEvents.initialize();

        StrataLogger.info("strata-world initialized. {} biomes registered.", StrataBiomes.count());
    }
}
