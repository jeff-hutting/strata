package io.strata.world;

import io.strata.core.util.StrataLogger;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side initializer for the {@code strata-world} module.
 *
 * <p>Handles registration of client-only systems: Biome Editor screen,
 * preview zone rendering, and wand interaction client handling.
 */
public class StrataWorldClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        StrataLogger.debug("strata-world client initializing...");

        // TODO: Register keybindings for undo/redo
        // TODO: Register client tick handler for PreviewZoneManager
        // TODO: Register packet handlers for server→client editor communication

        StrataLogger.debug("strata-world client initialized.");
    }
}
