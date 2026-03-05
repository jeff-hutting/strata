package io.strata.world;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.event.StrataEvents;
import io.strata.core.util.StrataLogger;
import io.strata.core.wand.StrataWandRegistry;
import io.strata.world.biome.StrataBiomes;
import io.strata.world.config.WorldConfig;
import io.strata.world.editor.BiomeDesignWorldPreset;
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
import net.minecraft.text.Text;

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
