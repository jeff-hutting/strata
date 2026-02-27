package io.strata.world.mixin;

import com.mojang.datafixers.util.Pair;
import io.strata.world.worldgen.StrataWorldEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.biome.source.util.VanillaBiomeParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/*
 * WHY THIS MIXIN EXISTS
 * ---------------------
 * Fabric's BiomeModifications API only supports modifying *existing* biomes (adding
 * features, spawns, etc.). As of MC 1.21.x, Fabric provides no equivalent to
 * NetherBiomes/TheEndBiomes for injecting *new* biomes into the overworld's
 * multi-noise parameter list. The only supported approach is a Mixin on
 * VanillaBiomeParameters.writeOverworldBiomeParameters.
 *
 * VANILLA TARGET
 * --------------
 * Class:  net.minecraft.world.biome.source.util.VanillaBiomeParameters
 * Method: writeOverworldBiomeParameters(Consumer<Pair<MultiNoiseUtil.NoiseHypercube,
 *             RegistryKey<Biome>>>)V
 * Inject: TAIL — runs after all vanilla biome entries have been added.
 *
 * ON MC VERSION BUMPS
 * -------------------
 * 1. Verify that VanillaBiomeParameters still exists and the method signature
 *    (parameter type, return type) has not changed in the new Yarn mappings.
 * 2. Confirm the Consumer parameter still accepts Pair<NoiseHypercube, RegistryKey<Biome>>.
 * 3. If Fabric API gains a native overworld biome injection hook, migrate to it and
 *    delete this Mixin.
 */
@Mixin(VanillaBiomeParameters.class)
public class VanillaBiomeParametersMixin {

    @Inject(method = "writeOverworldBiomeParameters", at = @At("TAIL"))
    private void strata$addOverworldBiomes(
            Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> parameters,
            CallbackInfo ci) {
        StrataWorldEvents.onOverworldBiomeParameters(parameters);
    }
}
