package io.strata.world;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.event.StrataEvents;
import io.strata.core.util.StrataLogger;
import io.strata.core.wand.StrataWandRegistry;
import io.strata.world.biome.StrataBiomes;
import io.strata.world.config.WorldConfig;
import io.strata.world.editor.BiomeDesignWorldPreset;
import io.strata.world.editor.BiomeEditorSession;
import io.strata.world.editor.BiomeEditorState;
import io.strata.world.editor.BiomeEditorWandHandler;
import io.strata.world.editor.StrataWand;
import io.strata.world.network.BiomeSamplePayload;
import io.strata.world.network.OpenBiomeEditorPayload;
import io.strata.world.worldgen.StrataWorldEvents;
import io.strata.world.worldgen.StrataWorldgen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the {@code strata-world} module.
 *
 * <p>Initializes configuration, biome registry keys, worldgen noise parameters,
 * and worldgen event hooks in dependency order. All Strata biomes are injected
 * into the overworld at world-generation time via
 * {@link io.strata.world.mixin.VanillaBiomeParametersMixin}.
 */
public class StrataWorld implements ModInitializer {

    /** Fabric mod ID for the {@code strata-world} module. */
    public static final String MOD_ID = "strata_world";

    /**
     * Called by Fabric during mod initialization.
     *
     * <p>Registers the world config, triggers biome key class loading,
     * initializes worldgen noise parameters, and hooks worldgen events.
     * Logs a summary line with the biome count on completion.
     */
    @Override
    public void onInitialize() {
        StrataLogger.info("========================================");
        StrataLogger.info("  Strata World initializing...");
        StrataLogger.info("========================================");

        StrataConfigHelper.register(WorldConfig.class);
        StrataBiomes.initialize();
        StrataWorldgen.initialize();
        StrataWorldEvents.initialize();

        // Phase 2: Biome Editor systems
        BiomeDesignWorldPreset.initialize();
        StrataWand.register();
        StrataWandRegistry.register(new BiomeEditorWandHandler());

        // Register S2C payload types — must happen on both sides in onInitialize()
        // (not in the client initializer) so the server is aware of the packet.
        PayloadTypeRegistry.playS2C().register(
                OpenBiomeEditorPayload.ID, OpenBiomeEditorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                BiomeSamplePayload.ID, BiomeSamplePayload.CODEC);

        // Cache Biome Design World detection at SERVER_STARTING — before any player connects.
        // This ensures isCurrentWorldBiomeDesignWorld() never reads level.dat on the hot path,
        // and that INFO-level logs in cacheWorldType() always appear in latest.log for debugging.
        ServerLifecycleEvents.SERVER_STARTING.register(BiomeDesignWorldPreset::cacheWorldType);

        // Eagerly initialize the editor preview biome override and dynamic settings from the
        // saved draft at SERVER_STARTED — after the registry is fully set up but before any
        // player joins. This ensures that features and spawns defined in the draft appear in
        // all newly generated chunks (non-spawn chunks explored after joining). Without this,
        // editorPreviewBiome is null during chunk generation and BiomeGenerationMixin is a no-op.
        //
        // Spawn chunks (~80 blocks of world spawn) are a known limitation: they are generated
        // by prepareStartRegion() BEFORE SERVER_STARTED fires, so they will not have custom
        // features until the player uses Reset World and walks away from spawn.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!BiomeDesignWorldPreset.isCurrentWorldBiomeDesignWorld(server)) return;
            ServerWorld overworld = server.getOverworld();
            if (overworld == null) return;

            // Set editorPreviewBiome from the server's biome registry
            overworld.getRegistryManager()
                    .getOptional(RegistryKeys.BIOME)
                    .flatMap(reg -> reg.getOptional(BiomeEditorSession.EDITOR_PREVIEW_KEY))
                    .ifPresent(entry -> {
                        BiomeEditorSession.setServerBiomeOverride(entry);
                        StrataLogger.info("SERVER_STARTED: initialized editor_preview biome override");
                    });

            // Load dynamic features and spawns from the saved draft
            Path draftPath = server.getSavePath(WorldSavePath.ROOT)
                    .resolve("strata_biomes").resolve("_session.draft.json");
            BiomeEditorState draft = BiomeEditorState.loadDraft(draftPath);
            if (draft != null) {
                BiomeEditorSession.updateDynamicFeatures(overworld, draft.getFeatures());
                BiomeEditorSession.updateDynamicSpawnSettings(draft.getSpawnEntries());
                StrataLogger.info("SERVER_STARTED: loaded {} feature(s) and {} spawn entry/entries from draft",
                        draft.getFeatures().size(), draft.getSpawnEntries().size());
            } else {
                StrataLogger.info("SERVER_STARTED: no draft file found — dynamic settings left empty");
            }
        });

        // Clear the server biome override when the server stops to prevent stale references
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                BiomeEditorSession.clearServerBiomeOverride());

        // Delete region files AFTER the server's final saveAll() completes.
        // PreviewZoneManager.resetWorld() disconnects and sets a pending path; we consume it here.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Path regionDir = BiomeEditorSession.takePendingWorldReset();
            if (regionDir != null && Files.isDirectory(regionDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
                    int deleted = 0;
                    for (Path f : stream) {
                        Files.deleteIfExists(f);
                        deleted++;
                    }
                    StrataLogger.info("Reset World: deleted {} region file(s) from {}", deleted, regionDir);
                } catch (IOException e) {
                    StrataLogger.error("Reset World: failed to delete region files: {}", e.getMessage());
                }
            }
        });

        // Issue #1 — Singleplayer enforcement: kick non-host players in a Biome Design World.
        // Biome Design Worlds are singleplayer-only per SPEC §7.0. Only the host player
        // is permitted; any other join attempt (e.g. via Open to LAN) is rejected.
        // We track the host's UUID and reject any other player.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!BiomeDesignWorldPreset.isCurrentWorldBiomeDesignWorld(server)) return;
            // In an integrated server (singleplayer/LAN), the host is the first player.
            // If the server is singleplayer-only, reject any non-first player.
            var joiningPlayer = handler.getPlayer();
            boolean isHost = server.isSingleplayer()
                    && server.getHostProfile() != null
                    && server.getHostProfile().id().equals(joiningPlayer.getUuid());
            if (!isHost) {
                handler.disconnect(
                        Text.translatable("disconnect.strata_world.biome_design_singleplayer"));
            }
        });

        // Issue #2 — Wand auto-give + splash message on first join to a Biome Design World.
        // Fires exactly once per player (PLAYER_FIRST_JOIN is not re-fired on respawn).
        StrataEvents.PLAYER_FIRST_JOIN.register(player -> {
            var server = player.getEntityWorld().getServer();
            if (BiomeDesignWorldPreset.isCurrentWorldBiomeDesignWorld(server)) {
                player.getInventory().setStack(
                        player.getInventory().getSelectedSlot(),
                        new ItemStack(StrataWand.INSTANCE));
                player.sendMessage(
                        Text.translatable("message.strata_world.welcome"),
                        false);
            }
        });

        // Issue #3 — Re-give Strata Wand on respawn in a Biome Design World.
        // The wand is not craftable or searchable in creative inventory, so dying
        // loses it permanently unless we re-give it on respawn.
        StrataEvents.PLAYER_RESPAWN.register(player -> {
            var server = player.getEntityWorld().getServer();
            if (BiomeDesignWorldPreset.isCurrentWorldBiomeDesignWorld(server)) {
                // Only give if the player doesn't already have one
                boolean hasWand = false;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    if (player.getInventory().getStack(i).getItem() instanceof StrataWand) {
                        hasWand = true;
                        break;
                    }
                }
                if (!hasWand) {
                    player.getInventory().setStack(
                            player.getInventory().getSelectedSlot(),
                            new ItemStack(StrataWand.INSTANCE));
                    player.sendMessage(
                            Text.translatable("message.strata_world.wand_restored"),
                            true);
                }
            }
        });

        StrataLogger.info("strata-world initialized. {} biomes registered.", StrataBiomes.count());
    }
}
