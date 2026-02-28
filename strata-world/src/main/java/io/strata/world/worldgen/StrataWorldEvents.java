package io.strata.world.worldgen;

import com.mojang.datafixers.util.Pair;
import io.strata.core.util.StrataLogger;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.function.Consumer;

/**
 * Handles Fabric worldgen events for strata-world.
 *
 * <p>This class is the single orchestration point for biome injection, matching the
 * pipeline documented in the spec (Section 3.1):
 * JSON → StrataBiomes → StrataWorldgen → StrataWorldEvents → overworld.
 *
 * <p>Phase 2 will add BiomeModifications and asset registry listeners here.
 */
public final class StrataWorldEvents {

    private StrataWorldEvents() {}

    /**
     * Called by {@code VanillaBiomeParametersMixin} at the tail of
     * {@code writeOverworldBiomeParameters}. Delegates to {@link StrataWorldgen}
     * to inject all Strata biomes into the overworld noise parameter list.
     */
    public static void onOverworldBiomeParameters(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters) {
        StrataWorldgen.addOverworldBiomes(parameters);
    }

    /**
     * Called during mod initialization to register Fabric event listeners and
     * BiomeModifications feature additions.
     *
     * <p>Biome noise-parameter injection is driven by
     * {@link io.strata.world.mixin.VanillaBiomeParametersMixin} at world-generation
     * time. Feature registration via {@link StrataWorldFeatures} happens here so
     * that BiomeModifications callbacks are registered before the first world loads.
     */
    public static void initialize() {
        StrataLogger.debug("StrataWorldEvents initialized.");
        StrataWorldFeatures.initialize();
    }
}
