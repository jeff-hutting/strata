package io.strata.core.event.callback;

import net.minecraft.util.Identifier;

/**
 * Callback fired when a custom asset is registered in {@code StrataAssetRegistry}.
 *
 * <p>{@code strata-world} listens to this event to refresh the Biome Editor's
 * feature and spawn lists without requiring a game restart.
 *
 * @see io.strata.core.event.StrataEvents#ASSET_REGISTERED
 */
@FunctionalInterface
public interface AssetRegistered {

    /**
     * Called when a custom asset is registered.
     *
     * @param id    the namespaced identifier of the newly registered asset
     * @param asset the asset object (a {@code WorldgenFeature}, {@code SpawnableAsset}, etc.)
     */
    void onAssetRegistered(Identifier id, Object asset);
}
