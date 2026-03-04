package io.strata.world;

import io.strata.core.event.StrataEvents;
import io.strata.core.util.StrataLogger;
import io.strata.world.editor.BiomeEditorScreen;
import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import io.strata.world.editor.PreviewZoneManager;
import io.strata.world.network.OpenBiomeEditorPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;

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

        // S2C: server asks client to open the Biome Editor (fired on wand right-click).
        // Priority order:
        //   1. Reuse in-memory session (user closed the screen but is still in the same world)
        //   2. Load the draft file saved on the last disconnect for this world/server
        //   3. Fall back to a blank BiomeEditorState (very first use, or no draft found)
        ClientPlayNetworking.registerGlobalReceiver(OpenBiomeEditorPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    BiomeEditorState state;
                    if (BiomeEditorSession.isActive()) {
                        // Screen was closed mid-session — reuse without re-loading from disk.
                        state = BiomeEditorSession.getActive();
                    } else {
                        // New session for this world — try to restore from the previous draft.
                        Path draftPath = getDraftPath(context.client());
                        state = (draftPath != null) ? BiomeEditorState.loadDraft(draftPath) : null;
                        if (state == null) {
                            StrataLogger.debug("No draft found — starting fresh BiomeEditorState.");
                            state = new BiomeEditorState();
                        } else {
                            StrataLogger.debug("Restored BiomeEditorState from draft: {}", draftPath);
                        }
                    }
                    context.client().setScreen(new BiomeEditorScreen(state));
                }));

        // Save the editor state to disk and then clear the session when the player
        // disconnects from a world. Saving here ensures changes survive world close/reopen.
        // Session lifetime is wand right-click → world disconnect, NOT editor-screen lifetime.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (BiomeEditorSession.isActive()) {
                Path draftPath = getDraftPath(client);
                if (draftPath != null) {
                    BiomeEditorSession.getActive().saveDraft(draftPath);
                    StrataLogger.debug("Saved BiomeEditorSession draft on disconnect: {}", draftPath);
                } else {
                    StrataLogger.debug("Could not determine draft path — session state not persisted.");
                }
                StrataLogger.debug("World disconnect — clearing BiomeEditorSession.");
                BiomeEditorSession.close();
            }
        });

        // Client tick handler: drives PreviewZoneManager debounce timers.
        // Fires every client tick (~20 Hz). When no editor is open, getPreviewZoneManager()
        // returns null and we skip immediately, so there is no per-tick overhead.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PreviewZoneManager pzm = BiomeEditorSession.getPreviewZoneManager();
            if (pzm != null) {
                pzm.tick();
            }
        });

        // Undo/redo keybindings (Ctrl+Z / Ctrl+Y) are handled directly in
        // BiomeEditorScreen.keyPressed() — no global KeyBinding registration needed.

        StrataLogger.debug("strata-world client initialized.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the path to the session draft file for the current world or server.
     *
     * <p>For singleplayer: {@code <world_save_dir>/strata_biomes/_session.draft.json}
     * <br>For multiplayer: {@code <game_dir>/strata-world-sessions/<server_key>/strata_biomes/_session.draft.json}
     *
     * <p>Returns {@code null} if the path cannot be determined (e.g. client is not
     * connected to any world).
     *
     * @param client the current {@link MinecraftClient} instance
     * @return the draft path, or {@code null}
     */
    private static Path getDraftPath(MinecraftClient client) {
        Path worldRoot;
        if (client.getServer() != null) {
            // Singleplayer: save the draft inside the world's own save directory so that
            // each world has its own independent biome editor session.
            worldRoot = client.getServer().getSavePath(WorldSavePath.ROOT);
        } else if (client.getCurrentServerEntry() != null) {
            // Multiplayer: key by server address to keep sessions separate per server.
            String serverKey = client.getCurrentServerEntry().address
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            worldRoot = client.runDirectory.toPath()
                    .resolve("strata-world-sessions")
                    .resolve(serverKey);
        } else {
            return null;
        }
        return worldRoot.resolve("strata_biomes").resolve("_session.draft.json");
    }
}
