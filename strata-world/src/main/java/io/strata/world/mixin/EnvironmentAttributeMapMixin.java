package io.strata.world.mixin;

import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link EnvironmentAttributeMap#apply} to substitute Biome Editor
 * Layer 1 color values for sky, fog, and water-fog when the editor is active.
 *
 * <p>In MC 1.21.11, sky and fog colors moved from {@code BiomeEffects} into the
 * {@code EnvironmentAttributeMap} system. The rendering pipeline evaluates these
 * attributes by calling {@code EnvironmentAttributeMap.apply(attribute, input)},
 * which this mixin intercepts to inject the editor's live values.
 *
 * <p>The override applies to <em>all</em> biomes while the editor is open.
 * This is intentional for MVP: the editor preview world is dominated by the
 * single strata biome, so cross-biome contamination is negligible.
 *
 * <p>This mixin is in the {@code "client"} section of {@code strata_world.mixins.json}
 * so it is never applied on dedicated servers.
 *
 * <p>WHY apply() AND NOT getEntry():
 * {@code apply(EnvironmentAttribute, Object)} is the single choke-point that all
 * rendering code goes through to resolve an attribute's value. Intercepting here
 * is simpler than constructing synthetic {@code Entry} objects and avoids needing
 * to know the positional context passed to each attribute.
 */
@Mixin(EnvironmentAttributeMap.class)
public class EnvironmentAttributeMapMixin {

    /**
     * When the editor is active, short-circuits attribute evaluation for
     * sky, fog, and water-fog colors and returns the editor state's value instead.
     *
     * <p>The full method descriptor is specified to future-proof against any
     * same-name overload that might be added in later MC versions.
     *
     * @param attribute the attribute being evaluated
     * @param input     the evaluation context (position, time, etc.) — ignored here
     * @param cir       callback used to cancel and return the editor value
     */
    @Inject(
        method = "apply(Lnet/minecraft/world/attribute/EnvironmentAttribute;Ljava/lang/Object;)Ljava/lang/Object;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void strata$overrideEditorColors(
            EnvironmentAttribute<?> attribute, Object input,
            CallbackInfoReturnable<Object> cir) {
        BiomeEditorState session = BiomeEditorSession.getActive();
        if (session == null) return;

        if (attribute == EnvironmentAttributes.SKY_COLOR_VISUAL) {
            cir.setReturnValue(session.getSkyColor());
        } else if (attribute == EnvironmentAttributes.FOG_COLOR_VISUAL) {
            cir.setReturnValue(session.getFogColor());
        } else if (attribute == EnvironmentAttributes.WATER_FOG_COLOR_VISUAL) {
            cir.setReturnValue(session.getWaterFogColor());
        }
    }
}
