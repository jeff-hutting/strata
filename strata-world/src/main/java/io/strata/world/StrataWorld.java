package io.strata.world;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.biome.StrataBiomes;
import io.strata.world.config.WorldConfig;
import io.strata.world.worldgen.StrataWorldEvents;
import io.strata.world.worldgen.StrataWorldgen;
import net.fabricmc.api.ModInitializer;

public class StrataWorld implements ModInitializer {

    public static final String MOD_ID = "strata_world";

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
