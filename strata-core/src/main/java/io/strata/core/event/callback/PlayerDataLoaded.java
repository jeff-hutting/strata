package io.strata.core.event.callback;

import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface PlayerDataLoaded {
    void onPlayerDataLoaded(ServerPlayerEntity player);
}
