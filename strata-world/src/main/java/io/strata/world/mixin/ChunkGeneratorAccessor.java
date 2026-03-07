package io.strata.world.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Exposes the private fields of {@link ChunkGenerator} needed to rebuild the
 * memoized indexed feature list when dynamic generation settings change.
 *
 * <p>{@code indexedFeaturesListSupplier} is a Guava memoized supplier that
 * computes the sorted/deduplicated list of all {@link PlacedFeatureIndexer.IndexedFeatures}
 * across all biomes in the world. It is pre-built by
 * {@code IntegratedServerLoader.initializeIndexedFeaturesList()} before
 * {@code SERVER_STARTING} fires — well before the Biome Editor sets up its
 * dynamic {@link GenerationSettings}. Features not present in the indexed list
 * are silently ignored at generation time even if
 * {@link BiomeGenerationMixin} returns them correctly.
 *
 * <p>After {@link io.strata.world.editor.BiomeEditorSession} builds new dynamic
 * settings, it calls {@link #strata$setIndexedFeaturesListSupplier} to replace
 * the memoized supplier with a fresh one. The new supplier's lambda short-circuits
 * the {@code generationSettingsGetter} for the editor preview biome, injecting
 * {@link io.strata.world.editor.BiomeEditorSession#dynamicGenerationSettings}
 * directly — necessary because {@link net.minecraft.world.gen.chunk.FlatChunkGenerator}
 * uses {@code FlatChunkGeneratorConfig::createGenerationSettings} as its getter,
 * which reads <em>static</em> biome data and bypasses {@link BiomeGenerationMixin}.
 */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {

    /**
     * Returns the generation-settings getter stored on this chunk generator.
     * <p><b>Caution:</b> For {@link net.minecraft.world.gen.chunk.FlatChunkGenerator}
     * this is {@code FlatChunkGeneratorConfig::createGenerationSettings}, which reads
     * static biome data and does NOT go through {@link BiomeGenerationMixin}.
     * Callers must short-circuit at the call site for the editor preview biome.
     */
    @Accessor("generationSettingsGetter")
    Function<RegistryEntry<Biome>, GenerationSettings> strata$getGenerationSettingsGetter();

    /**
     * Replaces the memoized indexed-features supplier.
     * Callers should supply a fresh {@code Suppliers.memoize(…)} so the new
     * list is built lazily on the next {@code generateFeatures()} call.
     */
    @Accessor("indexedFeaturesListSupplier")
    void strata$setIndexedFeaturesListSupplier(
            Supplier<List<PlacedFeatureIndexer.IndexedFeatures>> supplier);
}
