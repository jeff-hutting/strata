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
 * <p>The {@code ASSET_REGISTERED} client-side listener for refreshing the Biome
 * Editor when {@code strata-creator} registers a new asset lives in
 * {@link io.strata.world.StrataWorldClient}, not here, to avoid loading
 * {@code BiomeEditorScreen} before the render device is initialized.
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
     *
     * <p>The {@code ASSET_REGISTERED} client-side listener is registered in
     * {@link io.strata.world.StrataWorldClient#onInitializeClient()} instead of here.
     * Registering it from the common initializer would force {@code BiomeEditorScreen}
     * (a {@code Screen} subclass) to be loaded via the lambda bootstrap before the
     * render device is initialized, triggering
     * {@code IllegalStateException: Can't getDevice() before it was initialized}.
     */
    public static void initialize() {
        StrataLogger.debug("StrataWorldEvents initialized.");
        StrataWorldFeatures.initialize();
    }
}
