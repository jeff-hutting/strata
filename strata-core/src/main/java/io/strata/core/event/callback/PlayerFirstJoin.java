package io.strata.core.event.callback;

import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface PlayerFirstJoin {
    void onPlayerFirstJoin(ServerPlayerEntity player);
}
