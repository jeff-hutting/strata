package io.strata.core;

import net.fabricmc.loader.api.FabricLoader;

public final class StrataVersion {
    public static final String STRATA_VERSION = "0.1.0";
    public static final String MOD_ID = "strata_core";

    private StrataVersion() {}

    public static String getMinecraftVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
