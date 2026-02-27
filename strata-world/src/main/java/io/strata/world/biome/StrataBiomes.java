package io.strata.world.biome;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

public final class StrataBiomes {

    public static final RegistryKey<Biome> VERDANT_HIGHLANDS = register("verdant_highlands");

    private StrataBiomes() {}

    private static RegistryKey<Biome> register(String name) {
        return RegistryKey.of(RegistryKeys.BIOME, Identifier.of("strata_world", name));
    }

    public static int count() {
        return 1;
    }

    public static void initialize() {
        // Triggers class loading, which registers the RegistryKeys above.
    }
}
