package io.strata.world.worldgen;

import com.mojang.datafixers.util.Pair;
import io.strata.core.event.StrataEvents;
import io.strata.core.util.StrataLogger;
import io.strata.world.editor.BiomeEditorScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
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
 * <p>Phase 2 added an {@code ASSET_REGISTERED} listener (client-side only) that
 * calls {@link io.strata.world.editor.BiomeEditorScreen#notifyFeatureListUpdated()}
 * whenever {@code strata-creator} registers a new custom asset, keeping the Biome
 * Editor's feature and spawn lists fresh without a game restart.
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
     * <p>The {@code ASSET_REGISTERED} event listener is registered only on the
     * client side (guarded by an environment check) because {@link BiomeEditorScreen}
     * is a client-only class and must not be loaded on a dedicated server.
     */
    public static void initialize() {
        StrataLogger.debug("StrataWorldEvents initialized.");
        StrataWorldFeatures.initialize();

        // Issue #7 — ASSET_REGISTERED listener: refresh Biome Editor feature/spawn lists
        // when strata-creator registers a new custom asset (SPEC §7.4).
        // Guarded by EnvType.CLIENT because BiomeEditorScreen is a client-only class.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            StrataEvents.ASSET_REGISTERED.register((id, asset) -> {
                BiomeEditorScreen.notifyFeatureListUpdated();
                StrataLogger.debug("Biome editor: new asset registered — {}", id);
            });
        }
    }
}
