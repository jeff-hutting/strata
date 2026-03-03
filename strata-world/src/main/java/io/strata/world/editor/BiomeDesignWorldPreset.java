package io.strata.world.editor;

import io.strata.core.util.StrataLogger;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.gen.WorldPreset;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers the "Strata: Biome Designer" custom world preset.
 *
 * <p>The Biome Design World is a singleplayer-only world type that uses the
 * standard overworld chunk generator with natural terrain. It appears in the
 * world creation screen alongside Superflat and Amplified.
 *
 * <p>The world preset itself is defined as a JSON file at
 * {@code data/strata_world/worldgen/world_preset/biome_designer.json}.
 * This class provides the registry key constant and initialization logic.
 *
 * <p>Multiplayer join is disabled for Biome Design Worlds — enforced by
 * checking the world's generator type on player connection. The Strata Wand
 * is automatically placed in the player's main hand at first spawn.
 *
 * @see io.strata.world.editor.StrataWand
 */
public final class BiomeDesignWorldPreset {

    /** Registry key for the Biome Designer world preset. */
    public static final RegistryKey<WorldPreset> BIOME_DESIGNER =
            RegistryKey.of(RegistryKeys.WORLD_PRESET, Identifier.of("strata_world", "biome_designer"));

    /**
     * Marker dimension key embedded in {@code biome_designer.json}.
     *
     * <p>Minecraft 1.21.x no longer writes a {@code preset} field to
     * {@code WorldGenSettings} in {@code level.dat}. Instead, we detect the
     * Biome Design World by checking for a dedicated marker dimension
     * ({@code strata_world:design_marker}) that is declared in the world preset
     * JSON and therefore always appears in the {@code dimensions} compound of
     * any world created from that preset.
     */
    private static final String MARKER_DIMENSION = "strata_world:design_marker";

    /**
     * Cached result of the world-type detection, set once during
     * {@link #cacheWorldType(MinecraftServer)} at {@code SERVER_STARTING}.
     * Access via {@link #isCurrentWorldBiomeDesignWorld(MinecraftServer)}.
     */
    private static volatile boolean cachedResult = false;
    private static final AtomicBoolean cacheReady = new AtomicBoolean(false);

    private BiomeDesignWorldPreset() {}

    /**
     * Returns whether the given world preset key is the Biome Designer preset.
     *
     * @param presetKey the world preset registry key to check
     * @return {@code true} if the key matches the Biome Designer preset
     */
    public static boolean isBiomeDesignWorld(RegistryKey<WorldPreset> presetKey) {
        return BIOME_DESIGNER.equals(presetKey);
    }

    /**
     * Reads {@code level.dat} once at server startup and caches whether this world
     * was created with the Biome Designer preset.
     *
     * <p>Detects the preset by looking for the {@link #MARKER_DIMENSION} key inside
     * {@code WorldGenSettings.dimensions}, since Minecraft 1.21.x no longer writes
     * a {@code preset} field to {@code level.dat}.
     *
     * <p>Must be called from a {@code ServerLifecycleEvents.SERVER_STARTING} listener
     * so the cache is populated before any player connects.
     * Logs at INFO level so the result is always visible in {@code latest.log}.
     *
     * @param server the starting Minecraft server instance
     */
    public static void cacheWorldType(MinecraftServer server) {
        try {
            Path levelDat = server.getSavePath(WorldSavePath.LEVEL_DAT);
            NbtCompound root = NbtIo.readCompressed(levelDat, NbtSizeTracker.ofUnlimitedBytes());

            NbtCompound data       = root.getCompoundOrEmpty("Data");
            NbtCompound wgs        = data.getCompoundOrEmpty("WorldGenSettings");
            NbtCompound dimensions = wgs.getCompoundOrEmpty("dimensions");

            StrataLogger.info("[BiomeDesignWorldPreset] dimensions keys : {}", dimensions.getKeys());
            StrataLogger.info("[BiomeDesignWorldPreset] marker present  : {}", dimensions.contains(MARKER_DIMENSION));

            cachedResult = dimensions.contains(MARKER_DIMENSION);
        } catch (Exception e) {
            StrataLogger.warn("[BiomeDesignWorldPreset] Failed to read level.dat: {}", e.getMessage());
            cachedResult = false;
        }
        cacheReady.set(true);
        StrataLogger.info("[BiomeDesignWorldPreset] isBiomeDesignWorld = {}", cachedResult);
    }

    /**
     * Returns whether the running server is hosting a Biome Design World.
     *
     * <p>Returns the value cached by {@link #cacheWorldType(MinecraftServer)}.
     * Falls back to a live {@code level.dat} read (with a warning) if the cache
     * was never populated — this should not happen in normal operation.
     *
     * @param server the Minecraft server instance
     * @return {@code true} if the current world was created with the Biome Designer preset
     */
    public static boolean isCurrentWorldBiomeDesignWorld(MinecraftServer server) {
        if (!cacheReady.get()) {
            StrataLogger.warn("[BiomeDesignWorldPreset] Cache not ready — running live level.dat check");
            cacheWorldType(server);
        }
        return cachedResult;
    }

    /**
     * Triggers class loading and logs registration.
     * The actual world preset is data-driven via JSON.
     */
    public static void initialize() {
        StrataLogger.debug("BiomeDesignWorldPreset registered: {}", BIOME_DESIGNER.getValue());
    }
}
