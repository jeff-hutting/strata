package io.strata.core.event;

import io.strata.core.event.callback.*;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.Identifier;

public final class StrataEvents {

    private StrataEvents() {}

    public static final Event<StrataInitialized> STRATA_INITIALIZED =
            EventFactory.createArrayBacked(StrataInitialized.class, listeners -> () -> {
                for (StrataInitialized listener : listeners) {
                    listener.onStrataInitialized();
                }
            });

    public static final Event<PlayerFirstJoin> PLAYER_FIRST_JOIN =
            EventFactory.createArrayBacked(PlayerFirstJoin.class, listeners -> player -> {
                for (PlayerFirstJoin listener : listeners) {
                    listener.onPlayerFirstJoin(player);
                }
            });

    public static final Event<PlayerRespawn> PLAYER_RESPAWN =
            EventFactory.createArrayBacked(PlayerRespawn.class, listeners -> player -> {
                for (PlayerRespawn listener : listeners) {
                    listener.onPlayerRespawn(player);
                }
            });

    public static final Event<PlayerDataLoaded> PLAYER_DATA_LOADED =
            EventFactory.createArrayBacked(PlayerDataLoaded.class, listeners -> player -> {
                for (PlayerDataLoaded listener : listeners) {
                    listener.onPlayerDataLoaded(player);
                }
            });

    public static final Event<PlayerDataSaving> PLAYER_DATA_SAVING =
            EventFactory.createArrayBacked(PlayerDataSaving.class, listeners -> player -> {
                for (PlayerDataSaving listener : listeners) {
                    listener.onPlayerDataSaving(player);
                }
            });

    /**
     * Fired when a custom asset is registered in {@code StrataAssetRegistry}.
     * {@code strata-world} listens to refresh the Biome Editor's feature/spawn lists live.
     */
    public static final Event<AssetRegistered> ASSET_REGISTERED =
            EventFactory.createArrayBacked(AssetRegistered.class, listeners -> (id, asset) -> {
                for (AssetRegistered listener : listeners) {
                    listener.onAssetRegistered(id, asset);
                }
            });
}
