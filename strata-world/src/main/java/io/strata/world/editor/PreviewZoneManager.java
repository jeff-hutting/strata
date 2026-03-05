package io.strata.world.editor;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.config.WorldConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages debounced chunk regeneration for the Biome Editor preview zone.
 *
 * <p>When Layer 2 (structural) parameters change, the preview zone is
 * regenerated after a 3-second debounce window. The debounce timer resets
 * each time a value changes, so regeneration only fires once the player
 * has stopped making changes for a full 3 seconds.
 *
 * <p>The preview zone radius dynamically matches the player's current
 * render distance setting. An undo snapshot is captured on each regen.
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

    /** Whether a chunk regeneration is currently in progress. */
    private boolean regenerating = false;

    /** Whether biome blending is enabled (vanilla biomes outside render distance). */
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
     * and triggers chunk regeneration or snapshot capture as needed.
     * Debounce durations are read from {@link WorldConfig} so they can be
     * tuned without recompilation.
     */
    public void tick() {
        long now = System.currentTimeMillis();
        WorldConfig config = StrataConfigHelper.get(WorldConfig.class);

        // Check Layer 2 debounce — trigger chunk regen
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
     * Triggers chunk regeneration of the preview zone.
     * Captures an undo snapshot before regeneration.
     *
     * <p>Updates the in-memory dynamic generation settings (features and spawns)
     * on the editor_preview biome, then forces nearby chunks to regenerate so
     * the changes are visible in the world. No datapack reload is required because
     * {@code editor_preview.json} is bundled in the mod JAR and the biome object
     * is mutated in-memory via the accessor mixin.
     */
    private void triggerRegeneration() {
        undoManager.captureSnapshot(state);
        regenerating = true;
        regenStartTime = System.currentTimeMillis();

        StrataLogger.debug("Starting preview zone regeneration (render distance: {} chunks)",
                getRenderDistance());

        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();

        if (server != null) {
            server.execute(() -> {
                // Ensure override is set (may not be if this is the first regen)
                initBiomeOverrideIfNeeded(server);
                // Update in-memory features and spawns on the preview biome
                ServerWorld overworld = server.getOverworld();
                if (overworld != null) {
                    BiomeEditorSession.updateDynamicFeatures(overworld, state.getFeatures());
                    BiomeEditorSession.updateDynamicSpawnSettings(state.getSpawnEntries());
                }
                regenerateNearbyChunks(server, mc);
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
     * Resets the entire world — updates dynamic settings, clears all region
     * files, and forces a full chunk regeneration.
     */
    public void resetWorld() {
        StrataLogger.info("Resetting Design World — clearing chunks, regenerating");

        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();
        if (server == null) return;

        regenerating = true;
        regenStartTime = System.currentTimeMillis();

        server.execute(() -> {
            initBiomeOverrideIfNeeded(server);
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                BiomeEditorSession.updateDynamicFeatures(overworld, state.getFeatures());
                BiomeEditorSession.updateDynamicSpawnSettings(state.getSpawnEntries());
            }

            // Save all world data before deleting region files
            server.saveAll(false, true, true);

            // Delete overworld region files so all chunks regenerate fresh
            Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
            Path regionDir = worldRoot.resolve("region");
            if (Files.isDirectory(regionDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
                    for (Path regionFile : stream) {
                        Files.deleteIfExists(regionFile);
                    }
                    StrataLogger.info("Deleted region files from {}", regionDir);
                } catch (IOException e) {
                    StrataLogger.error("Failed to delete region files: {}", e.getMessage());
                }
            }

            regenerateNearbyChunks(server, mc);
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

    /**
     * Teleports the player in-place (to force the server to unload and regenerate
     * nearby chunks) then reloads client-side chunk rendering.
     * Must be called on the server thread.
     */
    private void regenerateNearbyChunks(MinecraftServer server, MinecraftClient mc) {
        if (mc.player != null) {
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                var serverPlayer = server.getPlayerManager().getPlayer(mc.player.getUuid());
                if (serverPlayer != null) {
                    BlockPos pos = serverPlayer.getBlockPos();
                    serverPlayer.teleport(overworld,
                            pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            java.util.Set.of(), serverPlayer.getYaw(),
                            serverPlayer.getPitch(), true);
                }
            }
        }
        mc.execute(() -> {
            if (mc.worldRenderer != null) {
                mc.worldRenderer.reload();
                StrataLogger.debug("Triggered worldRenderer.reload() after chunk regen");
            }
        });
    }
}
