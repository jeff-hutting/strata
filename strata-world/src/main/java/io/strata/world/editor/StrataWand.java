package io.strata.world.editor;

import io.strata.core.util.StrataLogger;
import io.strata.core.wand.StrataWandRegistry;
import io.strata.core.wand.WandHandler;
import io.strata.world.StrataWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Strata Wand — the universal in-game entry point for all Strata creative editors.
 *
 * <p>Right-clicking with the wand queries {@link StrataWandRegistry} for matching
 * handlers based on the current interaction context. In Phase 2, the only registered
 * handler is {@link BiomeEditorWandHandler}, which opens the Biome Editor.
 *
 * <p>The wand is automatically placed in the player's main hand at first spawn
 * in a Biome Design World, accompanied by a splash message.
 *
 * @see BiomeEditorWandHandler
 * @see StrataWandRegistry
 */
public class StrataWand extends Item {

    /** The registry identifier for the Strata Wand item. */
    public static final Identifier ID = Identifier.of(StrataWorld.MOD_ID, "strata_wand");

    /** The registered Strata Wand item instance. */
    public static final StrataWand INSTANCE = new StrataWand(
            new Item.Settings().maxCount(1)
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, ID)));

    private StrataWand(Settings settings) {
        super(settings);
    }

    /**
     * Handles a right-click with the Strata Wand.
     *
     * <p>On the server side, ray-casts up to 5 blocks from the player, queries
     * {@link StrataWandRegistry} for all handlers that match the interaction
     * context, and invokes the appropriate editor:
     * <ul>
     *   <li>No handlers — shows a "No Strata editor available" action bar message.</li>
     *   <li>One handler — invoked directly.</li>
     *   <li>Multiple handlers — currently uses the first match (Phase 3+ will add
     *       a disambiguation prompt).</li>
     * </ul>
     * The client side returns {@link ActionResult#SUCCESS} immediately without
     * performing any logic, as all routing happens server-side.
     *
     * @param world  the world the player is in
     * @param player the player right-clicking
     * @param hand   the hand holding the wand
     * @return {@link ActionResult#SUCCESS} on the client, or the handler result on the server
     */
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        var hit = player.raycast(5.0, 0.0f, false);
        List<WandHandler> handlers = StrataWandRegistry.findMatching(world, player, hit);

        if (handlers.isEmpty()) {
            player.sendMessage(
                    Text.translatable("message.strata_world.no_handler")
                            .formatted(Formatting.GRAY),
                    true);
            return ActionResult.PASS;
        }

        if (handlers.size() == 1) {
            handlers.getFirst().handle(world, player, hit);
        } else {
            // TODO Phase 3+: disambiguation prompt when multiple handlers match
            handlers.getFirst().handle(world, player, hit);
            StrataLogger.debug("Multiple wand handlers matched ({}), using first: {}",
                    handlers.size(), handlers.getFirst().getId());
        }

        return ActionResult.SUCCESS;
    }

    /**
     * Registers the Strata Wand item in the game registry.
     */
    public static void register() {
        Registry.register(Registries.ITEM, ID, INSTANCE);
        StrataLogger.debug("Registered Strata Wand item: {}", ID);
    }
}
