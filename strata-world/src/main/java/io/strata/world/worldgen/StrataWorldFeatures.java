package io.strata.world.worldgen;

import io.strata.world.biome.StrataBiomes;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.function.Predicate;

/**
 * Adds placed features to <em>vanilla</em> biomes via Fabric's
 * {@link BiomeModifications} API.
 *
 * <h2>Important: this class is NOT used for Strata's own custom biomes.</h2>
 *
 * <p>Custom biomes (e.g. {@code strata_world:verdant_highlands}) declare their
 * full feature set inline in their biome JSON files, exactly mirroring vanilla's
 * own format. This avoids the feature-order-cycle crashes that occur when
 * {@link BiomeModifications#addFeature} introduces ordering constraints that
 * conflict with vanilla's topological feature graph (notably with
 * {@code minecraft:deep_dark}).
 *
 * <p>{@link BiomeModifications} is the correct tool for <em>modifying existing
 * vanilla biomes</em> — for example, adding a new Strata tree type to all
 * forest biomes. That is the intended use of this class going forward.
 *
 * <p>Called from {@link StrataWorldEvents#initialize()} during mod init, before any
 * world is loaded.
 *
 * @see net.fabricmc.fabric.api.biome.v1.BiomeModifications
 * @see net.fabricmc.fabric.api.biome.v1.BiomeSelectors
 */
public final class StrataWorldFeatures {

    private StrataWorldFeatures() {}

    /**
     * Registers {@link BiomeModifications} feature additions for vanilla biomes.
     *
     * <p>Must be called during mod initialization (from
     * {@link StrataWorldEvents#initialize()}) so that Fabric processes the
     * modifications before the first world-generation pass.
     *
     * <p>Currently empty — reserved for future vanilla-biome modifications.
     */
    public static void initialize() {
        // Reserved for vanilla biome modifications (e.g. adding Strata features
        // to existing forest/plains biomes). Custom Strata biomes declare their
        // features inline in their biome JSON files.
    }

    /**
     * Adds a vanilla placed feature to the given biomes via {@link BiomeModifications}.
     *
     * @param selector  predicate selecting which biomes to modify
     * @param step      generation step to insert the feature into
     * @param featureId resource name under the {@code minecraft} namespace
     */
    @SuppressWarnings("unused") // will be used for vanilla biome modifications
    private static void addFeature(
            Predicate<BiomeSelectionContext> selector,
            GenerationStep.Feature step,
            String featureId) {
        RegistryKey<PlacedFeature> key = RegistryKey.of(
                RegistryKeys.PLACED_FEATURE,
                Identifier.of("minecraft", featureId)
        );
        BiomeModifications.addFeature(selector, step, key);
    }
}
