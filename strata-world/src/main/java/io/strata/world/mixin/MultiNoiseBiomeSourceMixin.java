package io.strata.world.mixin;

import io.strata.world.editor.BiomeDesignWorldPreset;
import io.strata.world.editor.BiomeEditorSession;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides the multi-noise biome source in Design Worlds to return the
 * editor's preview biome at every position.
 *
 * <p>This is the key mixin that makes features, spawns, and biome properties
 * from the editor actually take effect during chunk generation. Without it,
 * the vanilla biome source returns vanilla biomes and vanilla features generate.
 *
 * <p>When {@link BiomeEditorSession#getServerBiomeOverride()} is non-null
 * (set after datapack reload), this mixin intercepts every biome lookup and
 * returns the editor's preview biome instead.
 *
 * <p>This mixin is in the common {@code "mixins"} section (not client-only)
 * because chunk generation runs on the server thread, even in singleplayer.
 */
@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void strata$overrideBiomeInDesignWorld(
            int x, int y, int z,
            MultiNoiseUtil.MultiNoiseSampler sampler,
            CallbackInfoReturnable<RegistryEntry<Biome>> cir) {

        RegistryEntry<Biome> override = BiomeEditorSession.getServerBiomeOverride();
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
}
