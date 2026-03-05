package io.strata.world.mixin;

import io.strata.world.editor.BiomeEditorSession;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.SpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Provides dynamic {@link GenerationSettings} and {@link SpawnSettings} for the
 * {@code strata_world:editor_preview} biome.
 *
 * <p>When the editor is active and the biome being queried is the editor's preview
 * biome, this mixin replaces the static registry-backed settings with live values
 * from {@link BiomeEditorSession}. This makes features and mob spawns set in the
 * editor actually appear in newly generated chunks — no datapack reload required.
 *
 * <p>This mixin is in the common {@code "mixins"} section (not client-only) because
 * both chunk generation (features) and the mob spawning tick (spawns) run on the
 * server/integrated-server thread.
 */
@Mixin(Biome.class)
public class BiomeGenerationMixin {

    @Inject(method = "getGenerationSettings", at = @At("HEAD"), cancellable = true)
    private void strata$dynamicGenerationSettings(
            CallbackInfoReturnable<GenerationSettings> cir) {
        GenerationSettings dynamic =
                BiomeEditorSession.getDynamicGenerationSettings((Biome) (Object) this);
        if (dynamic != null) {
            cir.setReturnValue(dynamic);
        }
    }

    @Inject(method = "getSpawnSettings", at = @At("HEAD"), cancellable = true)
    private void strata$dynamicSpawnSettings(
            CallbackInfoReturnable<SpawnSettings> cir) {
        SpawnSettings dynamic =
                BiomeEditorSession.getDynamicSpawnSettings((Biome) (Object) this);
        if (dynamic != null) {
            cir.setReturnValue(dynamic);
        }
    }
}
