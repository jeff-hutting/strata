package io.strata.world.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "strata_world")
public class WorldConfig implements ConfigData {

    @Comment("Master toggle. Set false to disable all Strata biomes entirely.")
    public boolean enabled = true;

    @Comment("Controls how frequently Strata biomes appear relative to vanilla biomes.\n" +
             "Higher values = more Strata biomes. Range: 0.1 - 2.0. Default: 1.0")
    public float biomeFrequency = 1.0f;

    @Comment("If true, Strata biomes can generate in existing worlds (not just new ones).\n" +
             "May cause seams at chunk boundaries. Recommended: false for existing worlds.")
    public boolean generateInExistingWorlds = false;
}
