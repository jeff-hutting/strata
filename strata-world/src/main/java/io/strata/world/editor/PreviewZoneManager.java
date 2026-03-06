package io.strata.world.editor;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.config.WorldConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages debounced preview refresh for the Biome Editor.
 *
 * <h3>Design World terrain model</h3>
 * <p>The Design World uses a flat terrain generator with
 * {@code strata_world:editor_preview} as its only biome. This means:
 * <ul>
 *   <li><b>Visual changes</b> (sky/water/fog/foliage colors, Layer 1) take effect
 *       immediately via {@code worldRenderer.reload()} — no chunk regeneration needed.
 *   <li><b>Feature and spawn changes</b> (Layer 2) are registered in dynamic
 *       in-memory {@link GenerationSettings} / {@link SpawnSettings}, but the physical
 *       blocks baked into existing chunks cannot be updated in-place. Players must
 *       use <em>Reset World</em> to delete and regenerate chunk files.
 * </ul>
 *
 * <h3>Reset World flow</h3>
 * <ol>
 *   <li>Dynamic settings updated.
 *   <li>All overworld region ({@code .mca}) files deleted.
 *   <li>Player teleported 5 000 blocks away so the server evicts all non-spawn
 *       chunks from its in-memory cache (takes ≈7 s at 20 Hz).
 *   <li>Player teleported back; fresh chunks generate using the current dynamic
 *       generation settings via {@link BiomeGenerationMixin}.
 * </ol>
 *
 * @see BiomeEditorState
 * @see BiomeEditorState.UndoManager
 */
public class PreviewZoneManager {

    private final BiomeEditorState state;
    private final BiomeEditorState.UndoManager undoManager;

    /** Timestamp of the last Layer 2 change, or -1 if no pending regen. */
    private long lastLayer2ChangeTime = -1;

    /** Timestamp of the last Layer 1 change, or -1 if no pending snapshot. */
    private long lastLayer1ChangeTime = -1;

    /** Whether a regen/reset is currently in progress (drives the HUD indicator). */
    private boolean regenerating = false;

    /** Whether biome blending is enabled (deferred feature — no-op in current flat-world mode). */
    private boolean biomeBlendingEnabled = false;

    /**
     * Creates a PreviewZoneManager for the given editor state and undo manager.
     *
     * @param state       the editor state whose structural parameters drive regen
     * @param undoManager the undo manager to receive snapshots before each regen
     */
    public PreviewZoneManager(BiomeEditorState state, BiomeEditorState.UndoManager undoManager) {
        this.state = state;
        this.undoManager = undoManager;
    }

    /**
     * Called when a Layer 2 (structural) parameter changes.
     * Resets the debounce timer for chunk regeneration.
     */
    public void onLayer2Changed() {
        lastLayer2ChangeTime = System.currentTimeMillis();
    }

    /**
     * Called when a Layer 1 (visual) parameter changes.
     * Resets the debounce timer for undo snapshot capture.
     */
    public void onLayer1Changed() {
        lastLayer1ChangeTime = System.currentTimeMillis();
    }

    /**
     * Cancels any pending Layer 1 snapshot.
     * Called when the active tab switches so that widget-initialization listeners
     * don't fire a spurious snapshot and pollute the undo stack.
     */
    public void cancelLayer1Snapshot() {
        lastLayer1ChangeTime = -1;
    }

    /**
     * Called every client tick. Checks whether debounce timers have expired
     * and triggers preview refresh or snapshot capture as needed.
     * Also drives the Reset World pending-respawn countdown.
     * Debounce durations are read from {@link WorldConfig} so they can be
     * tuned without recompilation.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        WorldConfig config = StrataConfigHelper.get(WorldConfig.class);

        // Check Layer 2 debounce — update dynamic settings + visual refresh
        if (lastLayer2ChangeTime > 0 && !regenerating) {
            if (now - lastLayer2ChangeTime >= config.editorLayer2DebounceMs) {
                lastLayer2ChangeTime = -1;
                triggerRegeneration();
            }
        }

        // Clear regenerating indicator after minimum display duration
        if (regenerating && regenStartTime > 0
                && now - regenStartTime >= REGEN_INDICATOR_MIN_MS) {
            regenerating = false;
            regenStartTime = -1;
        }

        // Check Layer 1 debounce — capture undo snapshot
        if (lastLayer1ChangeTime > 0) {
            if (now - lastLayer1ChangeTime >= config.editorLayer1DebounceMs) {
                lastLayer1ChangeTime = -1;
                undoManager.captureSnapshot(state);
                StrataLogger.debug("Captured Layer 1 undo snapshot");
            }
        }

    }

    /**
     * Timestamp when regeneration started, used to show "Refreshing preview..."
     * for a minimum visible duration (1 second). -1 when not regenerating.
     */
    private long regenStartTime = -1;

    /** Minimum duration to show the "Refreshing preview..." indicator (ms). */
    private static final long REGEN_INDICATOR_MIN_MS = 1000L;

    /**
     * Fires after the Layer 2 debounce window expires (or via {@link #forceRegeneration()}).
     *
     * <p>Captures an undo snapshot, updates the in-memory dynamic generation
     * settings so the next Reset World sees the latest values, then reloads the
     * client-side chunk renderer so visual changes (sky/water/fog/foliage/grass
     * color) take effect immediately.
     *
     * <p>Feature and spawn changes are NOT visible until the player uses
     * <em>Reset World</em>, because those are physically baked into saved chunk
     * data and cannot be updated in the existing terrain without regenerating chunks.
     */
    private void triggerRegeneration() {
        undoManager.captureSnapshot(state);
        regenerating = true;
        regenStartTime = System.currentTimeMillis();

        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null) {
            server.execute(() -> {
                initBiomeOverrideIfNeeded(server);
                ServerWorld overworld = server.getOverworld();
                if (overworld != null) {
                    BiomeEditorSession.updateDynamicFeatures(overworld, state.getFeatures());
                    BiomeEditorSession.updateDynamicSpawnSettings(state.getSpawnEntries());
                }
                // Visual refresh only — features/spawns require Reset World
                mc.execute(() -> {
                    if (mc.worldRenderer != null) {
                        mc.worldRenderer.reload();
                        StrataLogger.debug("Visual refresh: worldRenderer.reload()");
                    }
                });
            });
        } else {
            if (mc.worldRenderer != null) {
                mc.worldRenderer.reload();
            }
        }
    }

    /**
     * Forces immediate regeneration, ignoring the debounce timer.
     * Used by the "Refresh Preview" button in the Terrain tab.
     */
    public void forceRegeneration() {
        lastLayer2ChangeTime = -1;
        triggerRegeneration();
    }

    /**
     * Resets the entire world — updates dynamic settings then disconnects so the
     * server can complete its final save before region files are deleted.
     *
     * <h3>Why disconnect?</h3>
     * <p>Spawn chunks (within ~80 blocks of world spawn) are kept permanently
     * loaded by the server and cannot be evicted while the server is running.
     * Worse, calling {@code saveAll()} and then deleting {@code .mca} files is
     * undone by the server's own final save on shutdown — it writes the still-in-
     * memory chunk data back to disk.
     *
     * <p>The only reliable approach:
     * <ol>
     *   <li>Update dynamic settings so they survive the session restart.
     *   <li>Disconnect — the server runs its final {@code saveAll()} and stops.
     *   <li>{@code SERVER_STOPPED} fires (registered in {@code StrataWorld}) →
     *       deletes region files <em>after</em> the final save has completed.
     *   <li>The player sees the world-selection screen and clicks to reopen.
     *   <li>Fresh chunks generate with the current dynamic settings via
     *       {@link BiomeGenerationMixin}.
     * </ol>
     */
    public void resetWorld() {
        StrataLogger.info("Resetting Design World — will delete region files after server stops");

        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();
        if (server == null) return;

        server.execute(() -> {
            initBiomeOverrideIfNeeded(server);
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                BiomeEditorSession.updateDynamicFeatures(overworld, state.getFeatures());
                BiomeEditorSession.updateDynamicSpawnSettings(state.getSpawnEntries());
            }

            // Schedule region-file deletion for AFTER the server's final saveAll() completes.
            // The SERVER_STOPPED handler in StrataWorld picks this up.
            Path regionDir = server.getSavePath(WorldSavePath.ROOT).resolve("region");
            BiomeEditorSession.setPendingWorldReset(regionDir);

            // Disconnect — server performs final save, then stops.
            // SERVER_STOPPED fires → deletes .mca files → player reopens from world list.
            mc.execute(() -> mc.disconnect(
                    new net.minecraft.client.gui.screen.world.SelectWorldScreen(
                            new net.minecraft.client.gui.screen.TitleScreen()),
                    false));
        });
    }

    /**
     * Returns the player's current render distance in chunks.
     */
    public int getRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.options.getViewDistance().getValue();
    }

    /**
     * Returns whether a chunk regeneration is currently in progress.
     */
    public boolean isRegenerating() {
        return regenerating;
    }

    /**
     * Returns whether biome blending is enabled.
     */
    public boolean isBiomeBlendingEnabled() {
        return biomeBlendingEnabled;
    }

    /**
     * Toggles biome blending. When enabled, vanilla biomes generate
     * outside the render distance, allowing the designer to evaluate
     * biome transitions. When disabled, the edited biome dominates
     * the entire world.
     */
    public void setBiomeBlendingEnabled(boolean enabled) {
        this.biomeBlendingEnabled = enabled;
        // Trigger a worldRenderer reload so the blending change is visible
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
        StrataLogger.debug("Biome blending {}", enabled ? "enabled" : "disabled");
    }

    // ── Chunk Regen Helpers ───────────────────────────────────────────────────

    /**
     * Looks up {@code strata_world:editor_preview} from the biome registry and sets
     * it as the server-side override. No-ops if already set.
     * Must be called on the server thread.
     */
    private void initBiomeOverrideIfNeeded(MinecraftServer server) {
        if (BiomeEditorSession.getServerBiomeOverride() != null) return;
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        overworld.getRegistryManager()
                .getOptional(RegistryKeys.BIOME)
                .flatMap(reg -> reg.getOptional(BiomeEditorSession.EDITOR_PREVIEW_KEY))
                .ifPresent(entry -> {
                    BiomeEditorSession.setServerBiomeOverride(entry);
                    StrataLogger.info("Set server biome override to editor_preview");
                });
    }

}
