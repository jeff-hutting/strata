package io.strata.world.editor;

import io.strata.core.util.StrataLogger;
import io.strata.core.wand.WandHandler;
import io.strata.world.network.OpenBiomeEditorPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Wand handler for the Biome Editor.
 *
 * <p>Matches when the player right-clicks in open air or points at terrain.
 * Opens the {@link BiomeEditorScreen} with the current draft loaded, or
 * samples the biome at the player's position as a starting template.
 *
 * <p>Registered with {@link io.strata.core.wand.StrataWandRegistry} during
 * {@code strata-world} initialization.
 */
public class BiomeEditorWandHandler implements WandHandler {

    /** Unique identifier for this handler, used in disambiguation prompts and logging. */
    public static final String HANDLER_ID = "biome_editor";

    @Override
    public String getId() {
        return HANDLER_ID;
    }

    @Override
    public String getDisplayName() {
        return "Biome Editor";
    }

    /**
     * The biome editor matches all interactions in Phase 2 — it is the only
     * registered handler. Future phases will add context-awareness (e.g.,
     * distinguishing between terrain clicks and entity clicks).
     */
    @Override
    public boolean matches(World world, PlayerEntity player, HitResult hit) {
        // Phase 2: always match — biome editor is the sole handler
        return true;
    }

    /**
     * Opens the Biome Editor screen on the client side.
     *
     * <p>If the hit result is a block hit, samples the biome at that position
     * and loads it as a template. Otherwise, opens the editor with the
     * current draft state.
     */
    @Override
    public void handle(World world, PlayerEntity player, HitResult hit) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        StrataLogger.debug("Biome editor wand handler: sending OpenBiomeEditor to {}",
                player.getName().getString());

        ServerPlayNetworking.send(serverPlayer, new OpenBiomeEditorPayload());

        // TODO Phase 3+: if block hit, sample biome at position and include template data
    }
}
