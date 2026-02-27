package io.strata.world.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

/**
 * Configuration for the {@code strata-world} module.
 *
 * <p>Loaded via Cloth Config's AutoConfig system and written to
 * {@code config/strata_world.json}. All world-generation tuning values live
 * here; nothing is hardcoded in the worldgen classes.
 *
 * <p>The six {@code verdantHighlands*} fields are point values in the overworld's
 * six-dimensional multi-noise space. Adjust them to shift where the Verdant
 * Highlands biome appears on the noise map without recompiling.
 */
@Config(name = "strata_world")
public class WorldConfig implements ConfigData {

    @Comment("Master toggle. Set false to disable all Strata biomes entirely.")
    public boolean enabled = true;

    @Comment("Controls how frequently Strata biomes appear relative to vanilla biomes.\n" +
             "Higher values = more Strata biomes. Range: 0.1 - 2.0. Default: 1.0")
    public float biomeFrequency = 1.0f;

    // --- VerdantHighlands multi-noise placement parameters ---
    // These are point values in the overworld's 6D noise space. See StrataWorldgen for usage.

    @Comment("VerdantHighlands: temperature noise point. Mild (0.0 = between cold and warm).")
    public float verdantHighlandsTemperature = 0.0f;

    @Comment("VerdantHighlands: humidity noise point. Moderate-to-lush.")
    public float verdantHighlandsHumidity = 0.3f;

    @Comment("VerdantHighlands: continentalness noise point. Inland.")
    public float verdantHighlandsContinentalness = 0.3f;

    @Comment("VerdantHighlands: erosion noise point. Low erosion = rolling hills.")
    public float verdantHighlandsErosion = -0.4f;

    @Comment("VerdantHighlands: depth noise point. 0.0 = surface.")
    public float verdantHighlandsDepth = 0.0f;

    @Comment("VerdantHighlands: weirdness noise point. 0.0 = normal terrain.")
    public float verdantHighlandsWeirdness = 0.0f;

}
