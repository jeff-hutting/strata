package io.strata.core.event;

import io.strata.core.event.callback.*;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.util.Identifier;

/**
 * Central event bus for cross-module communication in the Strata ecosystem.
 *
 * <p>Each Strata module registers listeners here during mod initialization to react
 * to lifecycle events (player joins, data loading, asset registration, etc.) without
 * taking direct dependencies on other modules. All events are backed by
 * {@link net.fabricmc.fabric.api.event.EventFactory#createArrayBacked}.
 *
 * @see net.fabricmc.fabric.api.event.Event
 */
public final class StrataEvents {

    private StrataEvents() {}

    /** Fired once when all Strata modules have completed initialization. */
    public static final Event<StrataInitialized> STRATA_INITIALIZED =
            EventFactory.createArrayBacked(StrataInitialized.class, listeners -> () -> {
                for (StrataInitialized listener : listeners) {
                    listener.onStrataInitialized();
                }
            });

    /** Fired the first time a player ever joins a world (not on respawn or reconnect). */
    public static final Event<PlayerFirstJoin> PLAYER_FIRST_JOIN =
            EventFactory.createArrayBacked(PlayerFirstJoin.class, listeners -> player -> {
                for (PlayerFirstJoin listener : listeners) {
                    listener.onPlayerFirstJoin(player);
                }
            });

    /** Fired each time a player respawns after death. */
    public static final Event<PlayerRespawn> PLAYER_RESPAWN =
            EventFactory.createArrayBacked(PlayerRespawn.class, listeners -> player -> {
                for (PlayerRespawn listener : listeners) {
                    listener.onPlayerRespawn(player);
                }
            });

    /** Fired after a player's Strata data attachment has been loaded from NBT. */
    public static final Event<PlayerDataLoaded> PLAYER_DATA_LOADED =
            EventFactory.createArrayBacked(PlayerDataLoaded.class, listeners -> player -> {
                for (PlayerDataLoaded listener : listeners) {
                    listener.onPlayerDataLoaded(player);
                }
            });

    /** Fired just before a player's Strata data attachment is serialized to NBT. */
    public static final Event<PlayerDataSaving> PLAYER_DATA_SAVING =
            EventFactory.createArrayBacked(PlayerDataSaving.class, listeners -> player -> {
                for (PlayerDataSaving listener : listeners) {
                    listener.onPlayerDataSaving(player);
                }
            });

    /**
     * Fired when a custom asset is registered in {@code StrataAssetRegistry}.
     *
     * <p>{@code strata-world} registers a client-side listener (guarded by
     * {@link net.fabricmc.api.EnvType#CLIENT}) that calls
     * {@link io.strata.world.editor.BiomeEditorScreen#notifyFeatureListUpdated()}
     * to refresh the Biome Editor's feature and spawn lists without a restart.
     *
     * @see AssetRegistered
     */
    public static final Event<AssetRegistered> ASSET_REGISTERED =
            EventFactory.createArrayBacked(AssetRegistered.class, listeners -> (id, asset) -> {
                for (AssetRegistered listener : listeners) {
                    listener.onAssetRegistered(id, asset);
                }
            });
}
