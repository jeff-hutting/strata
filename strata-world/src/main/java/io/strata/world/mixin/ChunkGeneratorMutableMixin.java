package io.strata.world.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Function;

/**
 * Companion mixin that strips the {@code ACC_FINAL} modifier from
 * {@link ChunkGenerator#generationSettingsGetter} at class-load time.
 *
 * <p>The field is declared {@code final} in vanilla, which means the JVM
 * only permits writes from {@code <init>}.  The {@link @Mutable} annotation
 * instructs Mixin to remove the {@code ACC_FINAL} flag during bytecode
 * transformation so that {@link ChunkGeneratorAccessor#strata$setGenerationSettingsGetter}
 * can replace it at runtime without triggering an {@link IllegalAccessError}.
 *
 * <p>This class does not inject any logic; it exists solely to carry the
 * {@code @Shadow @Mutable} declaration.  All actual logic lives in
 * {@link ChunkGeneratorAccessor} and
 * {@link io.strata.world.editor.BiomeEditorSession#invalidateIndexedFeaturesList}.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMutableMixin {

    /**
     * Shadows {@code generationSettingsGetter} with {@code @Mutable} so that
     * Mixin strips {@code ACC_FINAL} from the field in the transformed bytecode.
     */
    @Shadow
    @Mutable
    @SuppressWarnings("unused")
    private Function<RegistryEntry<Biome>, GenerationSettings> generationSettingsGetter;
}
