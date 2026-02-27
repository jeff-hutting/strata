package io.strata.world.biome;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

public final class StrataBiomes {

    /** Registry key for the Verdant Highlands biome — rolling mid-elevation hills with dense deciduous forest. */
    public static final RegistryKey<Biome> VERDANT_HIGHLANDS = register("verdant_highlands");

    private StrataBiomes() {}

    private static RegistryKey<Biome> register(String name) {
        return RegistryKey.of(RegistryKeys.BIOME, Identifier.of("strata_world", name));
    }

    /** Returns the number of registered Strata biomes. */
    public static int count() {
        return 1;
    }

    /**
     * Triggers class loading to register all Strata biome registry keys.
     * Called during mod initialization.
     */
    public static void initialize() {
        // Triggers class loading, which registers the RegistryKeys above.
    }
}
