package io.strata.core.event.callback;

import net.minecraft.server.network.ServerPlayerEntity;

@FunctionalInterface
public interface PlayerRespawn {
    void onPlayerRespawn(ServerPlayerEntity player);
}
