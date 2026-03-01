package io.strata.core.wand;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Interface for Strata Wand interaction handlers.
 *
 * <p>Each Strata module registers one or more {@code WandHandler} implementations
 * with {@link StrataWandRegistry} during mod initialization. When a player
 * right-clicks the Strata Wand, all registered handlers are queried for matches
 * against the current interaction context.
 *
 * @see StrataWandRegistry
 */
public interface WandHandler {

    /**
     * Returns a unique identifier for this handler, used for disambiguation
     * prompts and logging. Example: {@code "biome_editor"}.
     */
    String getId();

    /**
     * Returns a human-readable display name for this handler, shown in
     * disambiguation prompts when multiple handlers match. Example: {@code "Biome Editor"}.
     */
    String getDisplayName();

    /**
     * Tests whether this handler applies to the current interaction context.
     *
     * @param world  the world the player is in
     * @param player the player using the wand
     * @param hit    the ray-trace result (block, entity, or miss)
     * @return {@code true} if this handler should be offered for this interaction
     */
    boolean matches(World world, PlayerEntity player, HitResult hit);

    /**
     * Called when this handler is selected (either as the sole match or
     * after disambiguation). Opens the appropriate editor screen or
     * performs the handler's action.
     *
     * @param world  the world the player is in
     * @param player the player using the wand
     * @param hit    the ray-trace result
     */
    void handle(World world, PlayerEntity player, HitResult hit);
}
