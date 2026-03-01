package io.strata.core;

import io.strata.core.attachment.StrataAttachments;
import io.strata.core.config.StrataConfigHelper;
import io.strata.core.config.StrataCoreConfig;
import io.strata.core.event.StrataEvents;
import io.strata.core.util.StrataLogger;
import io.strata.core.wand.StrataWandRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class StrataCore implements ModInitializer {

    @Override
    public void onInitialize() {
        StrataLogger.info("========================================");
        StrataLogger.info("  Strata v{} initializing (MC {})",
                StrataVersion.STRATA_VERSION, StrataVersion.getMinecraftVersion());
        StrataLogger.info("========================================");

        // Initialize configuration
        StrataConfigHelper.register(StrataCoreConfig.class);

        // Initialize attachment types
        StrataAttachments.init();

        // Register player join listener for first-join detection
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            boolean initialized = player.getAttachedOrElse(StrataAttachments.PLAYER_INITIALIZED, false);
            if (!initialized) {
                player.setAttached(StrataAttachments.PLAYER_INITIALIZED, true);
                StrataEvents.PLAYER_FIRST_JOIN.invoker().onPlayerFirstJoin(player);
                StrataLogger.debug("Player {} first join — initialized Strata data", player.getName().getString());
            }
            StrataEvents.PLAYER_DATA_LOADED.invoker().onPlayerDataLoaded(player);
        });

        // Initialize wand handler registry
        StrataWandRegistry.initialize();

        // Fire the initialized event
        StrataEvents.STRATA_INITIALIZED.invoker().onStrataInitialized();

        StrataLogger.info("Strata Core initialized successfully.");
    }
}
