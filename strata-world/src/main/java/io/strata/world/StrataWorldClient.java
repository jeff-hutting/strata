package io.strata.world;

import io.strata.core.event.StrataEvents;
import io.strata.core.util.StrataLogger;
import io.strata.world.editor.BiomeEditorScreen;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initializer for the {@code strata-world} module.
 *
 * <p>Handles registration of client-only systems: Biome Editor screen,
 * preview zone rendering, and wand interaction client handling.
 *
 * <p>The {@code ASSET_REGISTERED} listener is registered here (not in
 * {@link io.strata.world.worldgen.StrataWorldEvents}) because it references
 * {@code BiomeEditorScreen}, a {@code Screen} subclass. Loading that class from
 * the common {@code ModInitializer} — before the render device is ready — causes
 * {@code IllegalStateException: Can't getDevice() before it was initialized}.
 * The client initializer runs in the correct render-ready context.
 */
public class StrataWorldClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        StrataLogger.debug("strata-world client initializing...");

        // ASSET_REGISTERED listener: refresh Biome Editor feature/spawn lists when
        // strata-creator registers a new custom asset (SPEC §7.4). Must live here
        // (not in StrataWorldEvents) to avoid loading BiomeEditorScreen before the
        // render device is initialized.
        StrataEvents.ASSET_REGISTERED.register((id, asset) -> {
            BiomeEditorScreen.notifyFeatureListUpdated();
            StrataLogger.debug("Biome editor: new asset registered — {}", id);
        });

        // TODO: Register keybindings for undo/redo
        // TODO: Register client tick handler for PreviewZoneManager
        // TODO: Register packet handlers for server→client editor communication

        StrataLogger.debug("strata-world client initialized.");
    }
}
