package io.strata.world.worldgen;

import com.mojang.datafixers.util.Pair;
import io.strata.world.biome.StrataBiomes;
import io.strata.world.config.WorldConfig;
import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.function.Consumer;

/**
 * Defines multi-noise placement parameters for all Strata biomes
 * and provides the injection method called by the Mixin.
 */
public final class StrataWorldgen {

    private StrataWorldgen() {}

    /**
     * Called by VanillaBiomeParametersMixin at the tail of writeOverworldBiomeParameters.
     * Adds all Strata biomes to the overworld noise parameter list.
     */
    public static void addOverworldBiomes(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters) {

        WorldConfig config = StrataConfigHelper.get(WorldConfig.class);
        if (!config.enabled) {
            StrataLogger.debug("Strata biomes disabled via config — skipping overworld injection.");
            return;
        }

        // Offset controls rarity: higher offset = biome is less likely to win ties.
        // Base offset 0.375 is conservative. biomeFrequency adjusts it inversely.
        float baseOffset = 0.375f;
        float adjustedOffset = baseOffset / Math.max(config.biomeFrequency, 0.1f);

        addVerdantHighlands(parameters, adjustedOffset);

        StrataLogger.debug("Injected {} Strata biome(s) into overworld noise parameters.", StrataBiomes.count());
    }

    /**
     * Verdant Highlands: rolling mid-elevation hills with dense deciduous forest.
     * Targets mid-temperature, mid-to-high humidity, inland, low erosion (hilly).
     */
    private static void addVerdantHighlands(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
            float offset) {

        parameters.accept(Pair.of(
                MultiNoiseUtil.createNoiseHypercube(
                        0.0f,    // temperature: mild (between cold and warm)
                        0.3f,    // humidity: moderate-to-lush
                        0.3f,    // continentalness: inland
                        -0.4f,   // erosion: low erosion = rolling hills
                        0.0f,    // depth: surface
                        0.0f,    // weirdness: normal terrain
                        offset   // rarity offset
                ),
                StrataBiomes.VERDANT_HIGHLANDS
        ));
    }

    public static void initialize() {
        // Injection happens via Mixin; this triggers class loading.
    }
}
