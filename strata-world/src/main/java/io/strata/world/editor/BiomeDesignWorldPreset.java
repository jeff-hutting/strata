package io.strata.world.editor;

import io.strata.core.util.StrataLogger;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.WorldPreset;

/**
 * Registers the "Strata: Biome Designer" custom world preset.
 *
 * <p>The Biome Design World is a singleplayer-only world type that uses the
 * standard overworld chunk generator with natural terrain. It appears in the
 * world creation screen alongside Superflat and Amplified.
 *
 * <p>The world preset itself is defined as a JSON file at
 * {@code data/strata_world/worldgen/world_preset/biome_designer.json}.
 * This class provides the registry key constant and initialization logic.
 *
 * <p>Multiplayer join is disabled for Biome Design Worlds — enforced by
 * checking the world's generator type on player connection. The Strata Wand
 * is automatically placed in the player's main hand at first spawn.
 *
 * @see io.strata.world.editor.StrataWand
 */
public final class BiomeDesignWorldPreset {

    /** Registry key for the Biome Designer world preset. */
    public static final RegistryKey<WorldPreset> BIOME_DESIGNER =
            RegistryKey.of(RegistryKeys.WORLD_PRESET, Identifier.of("strata_world", "biome_designer"));

    private BiomeDesignWorldPreset() {}

    /**
     * Returns whether the given world preset key is the Biome Designer preset.
     *
     * @param presetKey the world preset registry key to check
     * @return {@code true} if the key matches the Biome Designer preset
     */
    public static boolean isBiomeDesignWorld(RegistryKey<WorldPreset> presetKey) {
        return BIOME_DESIGNER.equals(presetKey);
    }

    /**
     * Triggers class loading and logs registration.
     * The actual world preset is data-driven via JSON.
     */
    public static void initialize() {
        StrataLogger.debug("BiomeDesignWorldPreset registered: {}", BIOME_DESIGNER.getValue());
    }
}
