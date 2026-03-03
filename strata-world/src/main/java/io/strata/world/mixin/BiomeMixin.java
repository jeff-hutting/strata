package io.strata.world.mixin;

import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link Biome} accessors to substitute Biome Editor Layer 1 values
 * for water color, grass tint, foliage tint, and precipitation when the editor
 * is active.
 *
 * <p>In MC 1.21.11, {@link net.minecraft.world.biome.BiomeEffects} is a record
 * holding {@code waterColor}, optional {@code grassColor}, optional
 * {@code foliageColor}, and {@code grassColorModifier}. Sky and fog colors moved
 * to the {@link net.minecraft.world.attribute.EnvironmentAttributeMap} system
 * (see {@link EnvironmentAttributeMapMixin}). This mixin covers the remaining
 * Layer 1 overrides that are still on {@code Biome}/{@code BiomeEffects}.
 *
 * <p>Overrides are applied to all biomes while the editor is open. This is
 * intentional for MVP — see {@link EnvironmentAttributeMapMixin} for rationale.
 *
 * <p>This mixin is in the {@code "client"} section of {@code strata_world.mixins.json}
 * so it is never applied on dedicated servers.
 *
 * <p>GRASS COLOR NOTE: {@code -1} in {@link BiomeEditorState#getGrassColor()} means
 * "use biome default". The mixin skips the override in that case so the vanilla
 * temperature/humidity tint still applies.
 */
@Mixin(Biome.class)
public class BiomeMixin {

    /**
     * Overrides {@link Biome#getWaterColor()} when the editor is active.
     * Always returns the editor state's water color — there is no "use default"
     * sentinel for water color in the editor (the field always holds a valid RGB).
     */
    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private void strata$overrideWaterColor(CallbackInfoReturnable<Integer> cir) {
        BiomeEditorState session = BiomeEditorSession.getActive();
        if (session != null) {
            cir.setReturnValue(session.getWaterColor());
        }
    }

    /**
     * Overrides {@link Biome#getGrassColorAt(double, double)} when the editor
     * is active and a specific grass tint has been set ({@code grassColor != -1}).
     *
     * <p>When {@code grassColor == -1} the override is skipped, letting the vanilla
     * temperature/humidity-based tint apply normally.
     */
    @Inject(method = "getGrassColorAt", at = @At("HEAD"), cancellable = true)
    private void strata$overrideGrassColor(double x, double z, CallbackInfoReturnable<Integer> cir) {
        BiomeEditorState session = BiomeEditorSession.getActive();
        if (session != null && session.getGrassColor() != -1) {
            cir.setReturnValue(session.getGrassColor());
        }
    }

    /**
     * Overrides {@link Biome#getFoliageColor()} when the editor is active and a
     * specific foliage tint has been set ({@code foliageColor != -1}).
     *
     * <p>When {@code foliageColor == -1} the override is skipped, letting the vanilla
     * temperature/humidity-based tint apply normally.
     */
    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private void strata$overrideFoliageColor(CallbackInfoReturnable<Integer> cir) {
        BiomeEditorState session = BiomeEditorSession.getActive();
        if (session != null && session.getFoliageColor() != -1) {
            cir.setReturnValue(session.getFoliageColor());
        }
    }

    /**
     * Overrides {@link Biome#hasPrecipitation()} when the editor is active.
     * Returns {@code true} if the editor state has rain OR snow enabled.
     *
     * <p>This controls whether precipitation visuals (rain/snow particles, wet
     * block surfaces) appear. The actual precipitation type (rain vs. snow) is
     * determined by temperature; this only controls whether precipitation occurs
     * at all.
     */
    @Inject(method = "hasPrecipitation", at = @At("HEAD"), cancellable = true)
    private void strata$overridePrecipitation(CallbackInfoReturnable<Boolean> cir) {
        BiomeEditorState session = BiomeEditorSession.getActive();
        if (session != null) {
            cir.setReturnValue(session.hasRain() || session.hasSnow());
        }
    }
}
