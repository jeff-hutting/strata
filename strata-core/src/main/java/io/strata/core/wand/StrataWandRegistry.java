package io.strata.core.wand;

import io.strata.core.util.StrataLogger;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry for Strata Wand interaction handlers.
 *
 * <p>Each Strata module registers its {@link WandHandler} implementations here
 * during mod initialization. When the player right-clicks the Strata Wand,
 * the wand item queries this registry to determine which editor(s) to open.
 *
 * <p>If exactly one handler matches, it is invoked directly. If multiple
 * handlers match, the wand presents a disambiguation prompt. If none match,
 * a brief message is shown to the player.
 *
 * @see WandHandler
 */
public final class StrataWandRegistry {

    private static final List<WandHandler> HANDLERS = new ArrayList<>();

    private StrataWandRegistry() {}

    /**
     * Registers a wand handler. Called during mod initialization.
     *
     * @param handler the handler to register
     * @throws IllegalArgumentException if a handler with the same ID is already registered
     */
    public static void register(WandHandler handler) {
        for (WandHandler existing : HANDLERS) {
            if (existing.getId().equals(handler.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate WandHandler ID: " + handler.getId());
            }
        }
        HANDLERS.add(handler);
        StrataLogger.debug("Registered wand handler: {} ({})", handler.getId(), handler.getDisplayName());
    }

    /**
     * Returns all handlers that match the given interaction context.
     *
     * @param world  the world the player is in
     * @param player the player using the wand
     * @param hit    the ray-trace result
     * @return an unmodifiable list of matching handlers (may be empty)
     */
    public static List<WandHandler> findMatching(World world, PlayerEntity player, HitResult hit) {
        List<WandHandler> matches = new ArrayList<>();
        for (WandHandler handler : HANDLERS) {
            if (handler.matches(world, player, hit)) {
                matches.add(handler);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Returns the number of registered handlers.
     */
    public static int count() {
        return HANDLERS.size();
    }

    /**
     * Triggers class loading. Called during mod initialization.
     */
    public static void initialize() {
        // No-op — ensures the registry class is loaded.
    }
}
