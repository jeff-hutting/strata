package io.strata.core.event.callback;

import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface PlayerDataSaving {
    void onPlayerDataSaving(ServerPlayerEntity player);
}
