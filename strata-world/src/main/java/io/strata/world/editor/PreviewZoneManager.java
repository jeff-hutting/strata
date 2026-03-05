package io.strata.world.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.config.WorldConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Triggers chunk regeneration of the preview zone.
     * Captures an undo snapshot before regeneration.
     *
     * <p>Writes the current biome JSON to the world's datapack folder, reloads
     * datapacks on the server, deletes region files, and triggers a client
     * chunk reload. This ensures features, spawns, and biome properties
     * take effect in the regenerated chunks.
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
            // Write the biome JSON to the datapack so features/spawns take effect
            writeBiomeToDatapack(server);

            // Reload datapacks on the server thread, then clear chunks
            server.execute(() -> {
                reloadAndRegenerateChunks(server, mc);
            });
        } else {
            // Fallback: just reload client rendering
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
     * Resets the entire world — writes current biome to datapack, reloads,
     * clears all region files, and regenerates from scratch.
     */
    public void resetWorld() {
        StrataLogger.info("Resetting Design World — writing datapack, clearing chunks, regenerating");

        MinecraftClient mc = MinecraftClient.getInstance();
        MinecraftServer server = mc.getServer();
        if (server == null) return;

        regenerating = true;
        regenStartTime = System.currentTimeMillis();

        writeBiomeToDatapack(server);

        server.execute(() -> {
            // Save all world data before deleting region files
            server.saveAll(false, true, true);

            // Delete overworld region files so chunks regenerate fresh
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

            reloadAndRegenerateChunks(server, mc);
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

    // ── Datapack + Server Reload Helpers ─────────────────────────────────────

    /**
     * Writes the current biome state as a datapack biome JSON file.
     * Creates the pack.mcmeta if it doesn't exist.
     */
    private void writeBiomeToDatapack(MinecraftServer server) {
        String biomeId = state.getBiomeId();
        if (biomeId.isEmpty() || !biomeId.contains(":")) {
            // Auto-derive from display name if empty
            if (!state.getDisplayName().isEmpty()) {
                state.setDisplayName(state.getDisplayName());
                biomeId = state.getBiomeId();
            }
            if (biomeId.isEmpty() || !biomeId.contains(":")) {
                StrataLogger.warn("Cannot write biome to datapack — no biome ID set.");
                return;
            }
        }

        String idPath = biomeId.split(":", 2)[1];
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Path datapackDir = worldRoot.resolve("datapacks")
                .resolve("strata-custom-biomes")
                .resolve("data")
                .resolve("strata_world")
                .resolve("worldgen")
                .resolve("biome");
        Path biomeFile = datapackDir.resolve(idPath + ".json");

        try {
            Files.createDirectories(datapackDir);

            // Write pack.mcmeta if missing
            Path packMcmeta = worldRoot.resolve("datapacks")
                    .resolve("strata-custom-biomes")
                    .resolve("pack.mcmeta");
            if (!Files.exists(packMcmeta)) {
                JsonObject pack = new JsonObject();
                JsonObject packInfo = new JsonObject();
                packInfo.addProperty("pack_format", 48); // MC 1.21.x
                packInfo.addProperty("description", "Custom biomes created with Strata World");
                pack.add("pack", packInfo);
                Files.writeString(packMcmeta, GSON.toJson(pack));
            }

            Files.writeString(biomeFile, state.buildBiomeJson());
            StrataLogger.debug("Wrote biome JSON to datapack: {}", biomeFile);
        } catch (IOException e) {
            StrataLogger.error("Failed to write biome to datapack: {}", e.getMessage());
        }
    }

    /**
     * Reloads server datapacks and triggers chunk regeneration.
     * Must be called on the server thread.
     */
    private void reloadAndRegenerateChunks(MinecraftServer server, MinecraftClient mc) {
        // Reload datapacks so the biome definition takes effect
        Collection<String> enabledPacks = server.getDataPackManager().getEnabledIds();
        server.reloadResources(enabledPacks).thenAccept(v -> {
            StrataLogger.debug("Datapacks reloaded successfully.");

            // Unload all chunks from the overworld so they regenerate fresh
            ServerWorld overworld = server.getOverworld();
            if (overworld != null) {
                // Teleport player to force chunk reload around them
                if (mc.player != null) {
                    BlockPos pos = mc.player.getBlockPos();
                    server.execute(() -> {
                        var serverPlayer = overworld.getServer()
                                .getPlayerManager().getPlayer(mc.player.getUuid());
                        if (serverPlayer != null) {
                            serverPlayer.teleport(overworld,
                                    pos.getX(), pos.getY(), pos.getZ(),
                                    java.util.Set.of(), serverPlayer.getYaw(),
                                    serverPlayer.getPitch(), true);
                        }
                    });
                }
            }

            // Client-side: reload all chunk rendering
            mc.execute(() -> {
                if (mc.worldRenderer != null) {
                    mc.worldRenderer.reload();
                    StrataLogger.debug("Triggered worldRenderer.reload() after datapack reload");
                }
            });
        }).exceptionally(e -> {
            StrataLogger.error("Datapack reload failed: {}", e.getMessage());
            // Still reload client rendering even if datapack reload failed
            mc.execute(() -> {
                if (mc.worldRenderer != null) {
                    mc.worldRenderer.reload();
                }
            });
            return null;
        });
    }
}
