package io.strata.core.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

public final class StrataConfigHelper {

    private StrataConfigHelper() {}

    public static <T extends ConfigData> void register(Class<T> configClass) {
        AutoConfig.register(configClass, GsonConfigSerializer::new);
    }

    public static <T extends ConfigData> T get(Class<T> configClass) {
        return AutoConfig.getConfigHolder(configClass).getConfig();
    }
}
