package io.strata.core.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;

@Config(name = "strata_core")
public class StrataCoreConfig implements ConfigData {

    @Comment("Set to true to enable verbose Strata logging. Useful for debugging.")
    public boolean verboseLogging = false;

    @Comment("Set to false to disable Strata's startup banner in the log.")
    public boolean showStartupBanner = true;
}
