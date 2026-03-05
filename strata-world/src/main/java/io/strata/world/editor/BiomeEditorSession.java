package io.strata.world.editor;

import io.strata.core.util.StrataLogger;
import io.strata.world.mixin.GenerationSettingsAccessor;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton that tracks the active Biome Editor session.
 *
 * <p>Mixins ({@code BiomeMixin}, {@code EnvironmentAttributeMapMixin},
 * {@code MultiNoiseBiomeSourceMixin}, {@code BiomeGenerationMixin}) read this
 * class to substitute editor values during rendering and chunk generation.
 *
 * <p>This class has no client-only imports so it can be safely referenced from
 * common-code call sites (e.g. server-side Mixin injectors).
 */
public final class BiomeEditorSession {

    /** Fixed biome ID used for the editor preview biome in the mod's resources. */
    public static final String EDITOR_PREVIEW_ID = "strata_world:editor_preview";

    /** Registry key for the editor preview biome. */
    public static final RegistryKey<Biome> EDITOR_PREVIEW_KEY =
            RegistryKey.of(RegistryKeys.BIOME, Identifier.of("strata_world", "editor_preview"));

    /**
     * Generation step index for vegetal_decoration — where user-added features are placed.
     * Steps 0–8 come from the static {@code editor_preview.json} (ores, caves, etc.).
     */
    public static final int VEGETAL_DECORATION_STEP = 9;

    private static volatile BiomeEditorState active = null;
    private static volatile PreviewZoneManager previewZoneManager = null;

    /**
     * Server-side biome override. When non-null, the MultiNoiseBiomeSourceMixin
     * returns this entry at every position in Design Worlds.
     */
    private static volatile RegistryEntry<Biome> serverBiomeOverride = null;

    /**
     * The actual {@link Biome} object backing the editor preview entry.
     * Used by {@link io.strata.world.mixin.BiomeGenerationMixin} to identify
     * when the biome being queried is the editor preview.
     */
    private static volatile Biome editorPreviewBiome = null;

    /** Cached dynamic generation settings built from the editor's feature list. */
    private static volatile GenerationSettings dynamicGenerationSettings = null;

    /** Cached dynamic spawn settings built from the editor's spawn entry list. */
    private static volatile SpawnSettings dynamicSpawnSettings = null;

    private BiomeEditorSession() {}

    // ── Session lifecycle ──────────────────────────────────────────────────

    /**
     * Opens a new editor session with the given state.
     * Creates a fresh {@link PreviewZoneManager} tied to the state's undo manager.
     */
    public static void open(BiomeEditorState state) {
        active = state;
        previewZoneManager = new PreviewZoneManager(state, state.getUndoManager());
    }

    /** Closes the current editor session, clearing state and preview manager. */
    public static void close() {
        active = null;
        previewZoneManager = null;
    }

    /** Returns the active editor state, or {@code null} if no session is open. */
    public static BiomeEditorState getActive() {
        return active;
    }

    /** Returns the active preview zone manager, or {@code null}. */
    public static PreviewZoneManager getPreviewZoneManager() {
        return previewZoneManager;
    }

    /** Returns {@code true} if an editor session is currently active. */
    public static boolean isActive() {
        return active != null;
    }

    // ── Server biome override ──────────────────────────────────────────────

    /**
     * Sets the server-side biome override.
     * Also stores the underlying {@link Biome} object so the generation mixin
     * can identify when it is being called on the preview biome.
     */
    public static void setServerBiomeOverride(RegistryEntry<Biome> biome) {
        serverBiomeOverride = biome;
        editorPreviewBiome = biome.value();
    }

    /**
     * Returns the server-side biome override entry, or {@code null}.
     * Called by {@link io.strata.world.mixin.MultiNoiseBiomeSourceMixin} on every
     * biome lookup — must be allocation-free.
     */
    public static RegistryEntry<Biome> getServerBiomeOverride() {
        return serverBiomeOverride;
    }

    /** Clears the server biome override and all dynamic settings. Called on server stop. */
    public static void clearServerBiomeOverride() {
        serverBiomeOverride = null;
        editorPreviewBiome = null;
        dynamicGenerationSettings = null;
        dynamicSpawnSettings = null;
    }

    // ── Dynamic generation settings ────────────────────────────────────────

    /**
     * Returns the dynamic {@link GenerationSettings} if {@code biome} is the editor
     * preview biome and dynamic settings have been built, otherwise {@code null}.
     * Called by {@link io.strata.world.mixin.BiomeGenerationMixin} on every
     * {@code getGenerationSettings()} call — must be allocation-free.
     */
    public static GenerationSettings getDynamicGenerationSettings(Biome biome) {
        if (editorPreviewBiome != null && biome == editorPreviewBiome) {
            return dynamicGenerationSettings; // may be null — caller handles that
        }
        return null;
    }

    /**
     * Returns the dynamic {@link SpawnSettings} if {@code biome} is the editor
     * preview biome and dynamic settings have been built, otherwise {@code null}.
     */
    public static SpawnSettings getDynamicSpawnSettings(Biome biome) {
        if (editorPreviewBiome != null && biome == editorPreviewBiome) {
            return dynamicSpawnSettings;
        }
        return null;
    }

    /**
     * Rebuilds the cached {@link GenerationSettings} from the editor's current
     * feature list and stores it. Subsequent calls to
     * {@code biome.getGenerationSettings()} on the editor preview biome will
     * return this until it is updated again.
     *
     * <p>Puts all editor-added features at step {@value #VEGETAL_DECORATION_STEP}
     * (vegetal_decoration). Steps 0–8 are preserved from the static biome JSON
     * (ores, dungeons, springs, etc.).
     *
     * @param world      the server world, used to look up the PLACED_FEATURE registry
     * @param featureIds list of placed-feature resource-location strings from the editor
     */
    public static void updateDynamicFeatures(ServerWorld world, List<String> featureIds) {
        Biome preview = editorPreviewBiome;
        if (preview == null) return;

        if (featureIds.isEmpty()) {
            dynamicGenerationSettings = null;
            return;
        }

        var reg = world.getRegistryManager().getOptional(RegistryKeys.PLACED_FEATURE);
        if (reg.isEmpty()) {
            StrataLogger.warn("PLACED_FEATURE registry not available — skipping feature update");
            return;
        }

        // Collect RegistryEntry<PlacedFeature> for each editor feature ID
        List<RegistryEntry<PlacedFeature>> entries = new ArrayList<>();
        for (String id : featureIds) {
            reg.get()
               .getOptional(RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(id)))
               .ifPresent(entries::add);
        }

        if (entries.isEmpty()) {
            dynamicGenerationSettings = null;
            return;
        }

        // Build a new feature list, keeping steps 0–8 from the existing static settings
        GenerationSettings existing = preview.getGenerationSettings();
        GenerationSettingsAccessor accessor = (GenerationSettingsAccessor) existing;

        List<RegistryEntryList<PlacedFeature>> newFeatures =
                new ArrayList<>(existing.getFeatures());
        // Pad to VEGETAL_DECORATION_STEP + 1 entries if the static JSON has fewer
        while (newFeatures.size() <= VEGETAL_DECORATION_STEP) {
            newFeatures.add(RegistryEntryList.of());
        }
        newFeatures.set(VEGETAL_DECORATION_STEP, RegistryEntryList.of(entries));

        dynamicGenerationSettings = GenerationSettingsAccessor.strata$create(
                accessor.strata$getCarvers(), newFeatures);

        StrataLogger.debug("Updated dynamic generation settings ({} features at step {})",
                entries.size(), VEGETAL_DECORATION_STEP);
    }

    /**
     * Rebuilds the cached {@link SpawnSettings} from the editor's current spawn
     * entry list. All editor entries are placed in their natural {@link SpawnGroup}
     * (determined by the entity type) so they spawn at the correct time of day and
     * in the correct conditions.
     *
     * @param spawnEntries the editor's spawn entries
     */
    public static void updateDynamicSpawnSettings(List<BiomeEditorState.SpawnEntry> spawnEntries) {
        if (spawnEntries.isEmpty()) {
            dynamicSpawnSettings = null;
            return;
        }

        SpawnSettings.Builder builder = new SpawnSettings.Builder();
        int added = 0;
        for (BiomeEditorState.SpawnEntry editorEntry : spawnEntries) {
            var entityType = Registries.ENTITY_TYPE.get(Identifier.of(editorEntry.getEntityId()));
            if (entityType == null) continue;
            SpawnGroup group = entityType.getSpawnGroup();
            SpawnSettings.SpawnEntry mcEntry = new SpawnSettings.SpawnEntry(
                    entityType,
                    editorEntry.getMinGroupSize(),
                    editorEntry.getMaxGroupSize());
            builder.spawn(group, editorEntry.getWeight(), mcEntry);
            added++;
        }

        if (added == 0) {
            dynamicSpawnSettings = null;
            return;
        }

        dynamicSpawnSettings = builder.build();
        StrataLogger.debug("Updated dynamic spawn settings ({} entries)", added);
    }
}
