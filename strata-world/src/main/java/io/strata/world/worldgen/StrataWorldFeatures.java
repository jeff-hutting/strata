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
 * Registers vanilla placed features for Strata biomes via Fabric's BiomeModifications API.
 *
 * <p>Features are added here rather than inline in biome JSON because inline feature
 * lists impose ordering constraints that cause "Feature order cycle" crashes when
 * shared vanilla features appear in different relative orders across biomes.
 * BiomeModifications inserts into the existing vanilla ordering graph and avoids
 * that problem entirely.
 *
 * <p>Called from {@link StrataWorldEvents#initialize()} during mod init.
 */
public final class StrataWorldFeatures {

    private StrataWorldFeatures() {}

    /**
     * Registers all BiomeModifications feature additions for Strata biomes.
     */
    public static void initialize() {
        registerVerdantHighlandsFeatures();
    }

    private static void registerVerdantHighlandsFeatures() {
        var selector = BiomeSelectors.includeByKey(StrataBiomes.VERDANT_HIGHLANDS);

        // VEGETAL_DECORATION — trees, grass, flowers, mushrooms, sugarcane, pumpkins
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "trees_birch_and_oak_leaf_litter");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "flower_default");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "patch_grass_forest");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "patch_tall_grass");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "brown_mushroom_normal");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "red_mushroom_normal");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "patch_sugar_cane");
        addFeature(selector, GenerationStep.Feature.VEGETAL_DECORATION, "patch_pumpkin");

        // UNDERGROUND_ORES — standard overworld ore suite
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_dirt");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_gravel");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_granite_upper");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_granite_lower");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_diorite_upper");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_diorite_lower");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_andesite_upper");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_andesite_lower");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_coal_upper");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_coal_lower");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_iron_upper");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_iron_middle");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_iron_small");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_gold");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_gold_lower");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_redstone");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_redstone_lower");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_diamond");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_diamond_large");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_diamond_buried");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_lapis");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_lapis_buried");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_copper");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_ORES, "ore_copper_large");

        // UNDERGROUND_DECORATION — glow lichen, clay deposits
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_DECORATION, "glow_lichen");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_DECORATION, "ore_clay");

        // FLUID_SPRINGS — water and lava springs
        addFeature(selector, GenerationStep.Feature.FLUID_SPRINGS, "spring_water");
        addFeature(selector, GenerationStep.Feature.FLUID_SPRINGS, "spring_lava");

        // LOCAL_MODIFICATIONS — lava lakes (underground and surface)
        addFeature(selector, GenerationStep.Feature.LOCAL_MODIFICATIONS, "lake_lava_underground");
        addFeature(selector, GenerationStep.Feature.LOCAL_MODIFICATIONS, "lake_lava_surface");

        // UNDERGROUND_STRUCTURES — monster rooms
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_STRUCTURES, "monster_room");
        addFeature(selector, GenerationStep.Feature.UNDERGROUND_STRUCTURES, "monster_room_deep");

        // TOP_LAYER_MODIFICATION — snow and ice layer for cold-biome compat
        addFeature(selector, GenerationStep.Feature.TOP_LAYER_MODIFICATION, "freeze_top_layer");
    }

    /**
     * Adds a vanilla placed feature to the given biomes via {@link BiomeModifications}.
     *
     * @param selector  predicate selecting which biomes to modify
     * @param step      generation step to insert the feature into
     * @param featureId resource name under the {@code minecraft} namespace
     */
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
