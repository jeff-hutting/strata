package io.strata.world.mixin;

import com.mojang.datafixers.util.Pair;
import io.strata.world.worldgen.StrataWorldgen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.VanillaBiomeParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(VanillaBiomeParameters.class)
public class VanillaBiomeParametersMixin {

    @Inject(method = "writeOverworldBiomeParameters", at = @At("TAIL"))
    private void strata$addOverworldBiomes(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
            CallbackInfo ci) {
        StrataWorldgen.addOverworldBiomes(parameters);
    }
}
