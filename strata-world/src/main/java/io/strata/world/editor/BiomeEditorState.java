package io.strata.world.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.strata.core.util.StrataLogger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the full state of the Biome Editor at any point in time.
 *
 * <p>Includes the biome's display name, auto-derived ID, all Layer 1 (visual)
 * and Layer 2 (structural) parameter values, the undo/redo history, and
 * serialization to/from {@code saves/<world>/strata_biomes/<name>.draft.json}.
 *
 * <p>The {@link UndoManager} is owned by this state object so that undo history
 * is included in draft serialization per SPEC §7.7.
 *
 * @see UndoManager
 * @see BiomeEditorScreen
 */
public class BiomeEditorState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Identity ---

    /** The human-readable display name. Editable at any time. */
    private String displayName = "";

    /** The namespaced biome ID, auto-derived from displayName unless manually overridden. */
    private String biomeId = "";

    /** Whether the biome ID has been manually overridden. */
    private boolean biomeIdOverridden = false;

    /** The source biome used as a template (e.g. "minecraft:plains"), or empty. */
    private String templateSource = "";

    // --- Layer 1: Visual Properties (instant, no chunk regen) ---

    private int skyColor = 7972607;
    private int fogColor = 12638463;
    private int waterColor = 4159204;
    private int waterFogColor = 329011;
    private int grassColor = -1;     // -1 = use biome default
    private int foliageColor = -1;   // -1 = use biome default
    private boolean hasRain = true;
    private boolean hasSnow = false;

    // --- Layer 2: Structural Properties (debounced chunk regen) ---

    private float temperature = 0.5f;
    private float humidity = 0.5f;
    private float continentalness = 0.3f;
    private float erosion = 0.0f;
    private float weirdness = 0.0f;
    private float depth = 0.0f;

    /** Feature identifiers active in this biome. */
    private List<String> features = new ArrayList<>();

    /** Mob spawn entries active in this biome. */
    private List<SpawnEntry> spawnEntries = new ArrayList<>();

    // --- Metadata ---

    /**
     * Whether the current state has been exported since the last change.
     * Persisted in drafts so reopening a world correctly shows the export indicator.
     */
    private boolean exported = false;

    /** Whether any change has been made since the last save. */
    private transient boolean dirty = false;

    /** The active tab index (0-4). */
    private int activeTab = 0;

    /**
     * The undo/redo manager for this editor session. Owned here so Gson
     * serializes the full undo history as part of the draft (SPEC §7.7).
     *
     * <p>Null until {@link #getUndoManager()} is first called (lazy-initialized
     * with depth 20). Snapshots created by {@link #copy()} intentionally leave
     * this null so nested serialization of the undo stacks stays bounded.
     */
    private UndoManager undoManager = null;

    // --- Constructors ---

    public BiomeEditorState() {}

    /**
     * Creates a deep copy of this state for undo snapshots.
     * Does not copy the {@link UndoManager} — snapshot copies carry no undo history.
     */
    public BiomeEditorState copy() {
        BiomeEditorState copy = new BiomeEditorState();
        copy.displayName = this.displayName;
        copy.biomeId = this.biomeId;
        copy.biomeIdOverridden = this.biomeIdOverridden;
        copy.templateSource = this.templateSource;
        copy.skyColor = this.skyColor;
        copy.fogColor = this.fogColor;
        copy.waterColor = this.waterColor;
        copy.waterFogColor = this.waterFogColor;
        copy.grassColor = this.grassColor;
        copy.foliageColor = this.foliageColor;
        copy.hasRain = this.hasRain;
        copy.hasSnow = this.hasSnow;
        copy.temperature = this.temperature;
        copy.humidity = this.humidity;
        copy.continentalness = this.continentalness;
        copy.erosion = this.erosion;
        copy.weirdness = this.weirdness;
        copy.depth = this.depth;
        copy.features = new ArrayList<>(this.features);
        copy.spawnEntries = new ArrayList<>();
        for (SpawnEntry entry : this.spawnEntries) {
            copy.spawnEntries.add(entry.copy());
        }
        copy.activeTab = this.activeTab;
        // undoManager intentionally not copied — snapshots carry no undo history
        return copy;
    }

    /**
     * Restores all property fields from the given snapshot, preserving this
     * object's identity and {@link UndoManager}. Used by undo/redo to apply
     * a previous or next state without replacing the state object reference.
     *
     * @param snapshot the state to copy fields from
     */
    public void restoreFrom(BiomeEditorState snapshot) {
        this.displayName = snapshot.displayName;
        this.biomeId = snapshot.biomeId;
        this.biomeIdOverridden = snapshot.biomeIdOverridden;
        this.templateSource = snapshot.templateSource;
        this.skyColor = snapshot.skyColor;
        this.fogColor = snapshot.fogColor;
        this.waterColor = snapshot.waterColor;
        this.waterFogColor = snapshot.waterFogColor;
        this.grassColor = snapshot.grassColor;
        this.foliageColor = snapshot.foliageColor;
        this.hasRain = snapshot.hasRain;
        this.hasSnow = snapshot.hasSnow;
        this.temperature = snapshot.temperature;
        this.humidity = snapshot.humidity;
        this.continentalness = snapshot.continentalness;
        this.erosion = snapshot.erosion;
        this.weirdness = snapshot.weirdness;
        this.depth = snapshot.depth;
        this.features = new ArrayList<>(snapshot.features);
        this.spawnEntries = new ArrayList<>();
        for (SpawnEntry entry : snapshot.spawnEntries) {
            this.spawnEntries.add(entry.copy());
        }
        this.activeTab = snapshot.activeTab;
        this.dirty = true;
        this.exported = false;
    }

    // --- Display Name / Biome ID ---

    /** Returns the human-readable display name. */
    public String getDisplayName() { return displayName; }

    /**
     * Sets the display name. Auto-derives the biome ID unless the ID has been
     * manually overridden.
     *
     * @param displayName the new display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (!biomeIdOverridden) {
            this.biomeId = deriveId(displayName);
        }
        markDirty();
    }

    /** Returns the namespaced biome ID (auto-derived or manually set). */
    public String getBiomeId() { return biomeId; }

    /**
     * Manually sets the biome ID, overriding auto-derivation from the display name.
     *
     * @param biomeId the namespaced biome ID to use
     */
    public void setBiomeId(String biomeId) {
        this.biomeId = biomeId;
        this.biomeIdOverridden = true;
        markDirty();
    }

    /**
     * Derives a biome ID from a display name by lowercasing and replacing
     * spaces with underscores. Example: "Verdant Highlands" -> "strata_world:verdant_highlands".
     */
    public static String deriveId(String displayName) {
        if (displayName == null || displayName.isBlank()) return "";
        String path = displayName.toLowerCase().strip().replaceAll("\\s+", "_")
                .replaceAll("[^a-z0-9_]", "");
        return "strata_world:" + path;
    }

    /** Returns the template source biome ID, or empty string if none. */
    public String getTemplateSource() { return templateSource; }

    // --- Layer 1 Accessors ---

    /** Returns the sky color as a packed RGB decimal integer. */
    public int getSkyColor() { return skyColor; }

    /** Sets the sky color. Layer 1 — changes apply instantly without chunk regen. */
    public void setSkyColor(int skyColor) { this.skyColor = skyColor; markDirty(); }

    /** Returns the fog color as a packed RGB decimal integer. */
    public int getFogColor() { return fogColor; }

    /** Sets the fog color. Layer 1 — changes apply instantly without chunk regen. */
    public void setFogColor(int fogColor) { this.fogColor = fogColor; markDirty(); }

    /** Returns the water color as a packed RGB decimal integer. */
    public int getWaterColor() { return waterColor; }

    /** Sets the water color. Layer 1 — changes apply instantly without chunk regen. */
    public void setWaterColor(int waterColor) { this.waterColor = waterColor; markDirty(); }

    /** Returns the water fog color as a packed RGB decimal integer. */
    public int getWaterFogColor() { return waterFogColor; }

    /** Sets the water fog color. Layer 1 — changes apply instantly without chunk regen. */
    public void setWaterFogColor(int waterFogColor) { this.waterFogColor = waterFogColor; markDirty(); }

    /** Returns the grass tint color, or {@code -1} to use the biome's default tint. */
    public int getGrassColor() { return grassColor; }

    /** Sets the grass tint color. Layer 1 — changes apply instantly. Use {@code -1} for biome default. */
    public void setGrassColor(int grassColor) { this.grassColor = grassColor; markDirty(); }

    /** Returns the foliage tint color, or {@code -1} to use the biome's default tint. */
    public int getFoliageColor() { return foliageColor; }

    /** Sets the foliage tint color. Layer 1 — changes apply instantly. Use {@code -1} for biome default. */
    public void setFoliageColor(int foliageColor) { this.foliageColor = foliageColor; markDirty(); }

    /** Returns whether this biome has rain precipitation. */
    public boolean hasRain() { return hasRain; }

    /** Sets whether this biome has rain. Layer 1 — changes apply instantly. */
    public void setHasRain(boolean hasRain) { this.hasRain = hasRain; markDirty(); }

    /** Returns whether this biome has snow precipitation. */
    public boolean hasSnow() { return hasSnow; }

    /** Sets whether this biome has snow. Layer 1 — changes apply instantly. */
    public void setHasSnow(boolean hasSnow) { this.hasSnow = hasSnow; markDirty(); }

    // --- Layer 2 Accessors ---

    /** Returns the temperature multi-noise parameter. */
    public float getTemperature() { return temperature; }

    /** Sets the temperature. Layer 2 — triggers debounced chunk regen. */
    public void setTemperature(float temperature) { this.temperature = temperature; markDirty(); }

    /** Returns the humidity multi-noise parameter. */
    public float getHumidity() { return humidity; }

    /** Sets the humidity. Layer 2 — triggers debounced chunk regen. */
    public void setHumidity(float humidity) { this.humidity = humidity; markDirty(); }

    /** Returns the continentalness multi-noise parameter. */
    public float getContinentalness() { return continentalness; }

    /** Sets the continentalness. Layer 2 — triggers debounced chunk regen. */
    public void setContinentalness(float continentalness) { this.continentalness = continentalness; markDirty(); }

    /** Returns the erosion multi-noise parameter. */
    public float getErosion() { return erosion; }

    /** Sets the erosion. Layer 2 — triggers debounced chunk regen. */
    public void setErosion(float erosion) { this.erosion = erosion; markDirty(); }

    /** Returns the weirdness multi-noise parameter. */
    public float getWeirdness() { return weirdness; }

    /** Sets the weirdness. Layer 2 — triggers debounced chunk regen. */
    public void setWeirdness(float weirdness) { this.weirdness = weirdness; markDirty(); }

    /** Returns the depth multi-noise parameter. */
    public float getDepth() { return depth; }

    /** Sets the depth. Layer 2 — triggers debounced chunk regen. */
    public void setDepth(float depth) { this.depth = depth; markDirty(); }

    /** Returns the list of placed-feature identifiers active in this biome. */
    public List<String> getFeatures() { return features; }

    /** Sets the active feature list. Layer 2 — triggers debounced chunk regen. */
    public void setFeatures(List<String> features) { this.features = features; markDirty(); }

    /** Returns the list of mob spawn entries active in this biome. */
    public List<SpawnEntry> getSpawnEntries() { return spawnEntries; }

    /** Sets the mob spawn entries. Layer 2 — triggers debounced chunk regen. */
    public void setSpawnEntries(List<SpawnEntry> spawnEntries) { this.spawnEntries = spawnEntries; markDirty(); }

    // --- Metadata ---

    /** Returns the currently active tab index (0 = Visual, 1 = Terrain, etc.). */
    public int getActiveTab() { return activeTab; }

    /** Sets the active tab index. Does not mark the state dirty. */
    public void setActiveTab(int activeTab) { this.activeTab = activeTab; }

    /** Returns whether the state has been modified since the last save. */
    public boolean isDirty() { return dirty; }

    /** Returns whether the current state has been exported since the last change. */
    public boolean isExported() { return exported; }

    /** Marks this state as exported. Clears the "unexported changes" indicator. */
    public void markExported() { this.exported = true; }

    private void markDirty() {
        this.dirty = true;
        this.exported = false;
    }

    /** Clears the dirty flag after a successful save. */
    public void clearDirty() { this.dirty = false; }

    // --- Undo Manager ---

    /**
     * Returns the undo/redo manager for this state.
     *
     * <p>Lazy-initialized with depth 20 if not yet set. The Biome Editor screen
     * calls {@link UndoManager#setMaxDepth(int)} after construction to apply
     * the configured depth from {@link io.strata.world.config.WorldConfig}.
     *
     * @return the undo manager (never null)
     */
    public UndoManager getUndoManager() {
        if (undoManager == null) {
            undoManager = new UndoManager(20);
        }
        return undoManager;
    }

    // --- Serialization ---

    /**
     * Serializes this state to JSON.
     * Includes the undo/redo history per SPEC §7.7.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Deserializes a state from JSON.
     */
    public static BiomeEditorState fromJson(String json) {
        return GSON.fromJson(json, BiomeEditorState.class);
    }

    /**
     * Creates a BiomeEditorState from a biome sample JSON sent by the server.
     * Populates visual and structural properties from the sampled biome.
     *
     * @param json the JSON string from the BiomeSamplePayload
     * @return a new state populated from the sample, or null on error
     */
    public static BiomeEditorState fromSampleJson(String json) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            BiomeEditorState state = new BiomeEditorState();

            if (obj.has("displayName")) {
                state.displayName = obj.get("displayName").getAsString();
                state.biomeId = deriveId(state.displayName);
            }
            if (obj.has("templateSource")) {
                state.templateSource = obj.get("templateSource").getAsString();
            } else if (obj.has("biomeId")) {
                state.templateSource = obj.get("biomeId").getAsString();
            }

            // Layer 1
            if (obj.has("skyColor")) state.skyColor = obj.get("skyColor").getAsInt();
            if (obj.has("fogColor")) state.fogColor = obj.get("fogColor").getAsInt();
            if (obj.has("waterColor")) state.waterColor = obj.get("waterColor").getAsInt();
            if (obj.has("waterFogColor")) state.waterFogColor = obj.get("waterFogColor").getAsInt();
            if (obj.has("grassColor")) state.grassColor = obj.get("grassColor").getAsInt();
            if (obj.has("foliageColor")) state.foliageColor = obj.get("foliageColor").getAsInt();
            if (obj.has("hasRain")) state.hasRain = obj.get("hasRain").getAsBoolean();
            if (obj.has("hasSnow")) state.hasSnow = obj.get("hasSnow").getAsBoolean();

            // Layer 2
            if (obj.has("temperature")) state.temperature = obj.get("temperature").getAsFloat();
            if (obj.has("humidity")) state.humidity = obj.get("humidity").getAsFloat();

            // Spawn entries
            if (obj.has("spawnEntries")) {
                JsonArray spawns = obj.getAsJsonArray("spawnEntries");
                for (JsonElement el : spawns) {
                    JsonObject s = el.getAsJsonObject();
                    SpawnEntry entry = new SpawnEntry(
                            s.get("entityId").getAsString(),
                            s.has("weight") ? s.get("weight").getAsInt() : 10,
                            s.has("minGroupSize") ? s.get("minGroupSize").getAsInt() : 1,
                            s.has("maxGroupSize") ? s.get("maxGroupSize").getAsInt() : 4
                    );
                    state.spawnEntries.add(entry);
                }
            }

            state.dirty = true;
            state.exported = false;
            return state;
        } catch (Exception e) {
            StrataLogger.error("Failed to parse biome sample JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds a vanilla-compatible biome JSON string from the current state.
     * Used by Export tab and preview zone regen.
     */
    public String buildBiomeJson() {
        JsonObject root = new JsonObject();
        root.addProperty("has_precipitation", hasRain || hasSnow);
        root.addProperty("temperature", Math.round(temperature * 1000f) / 1000f);
        root.addProperty("downfall", Math.round(humidity * 1000f) / 1000f);

        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", skyColor);
        effects.addProperty("fog_color", fogColor);
        effects.addProperty("water_color", waterColor);
        effects.addProperty("water_fog_color", waterFogColor);
        if (grassColor >= 0) effects.addProperty("grass_color", grassColor);
        if (foliageColor >= 0) effects.addProperty("foliage_color", foliageColor);

        JsonObject moodSound = new JsonObject();
        moodSound.addProperty("sound", "minecraft:ambient.cave");
        moodSound.addProperty("tick_delay", 6000);
        moodSound.addProperty("block_search_extent", 8);
        moodSound.addProperty("offset", 2.0);
        effects.add("mood_sound", moodSound);
        root.add("effects", effects);

        // Features — all in generation step 10 (vegetal_decoration)
        JsonArray featureSteps = new JsonArray();
        for (int i = 0; i < 11; i++) featureSteps.add(new JsonArray());
        JsonArray vegetalStep = new JsonArray();
        for (String feature : features) vegetalStep.add(feature);
        featureSteps.add(vegetalStep);
        featureSteps.add(new JsonArray()); // step 11
        root.add("features", featureSteps);

        // Spawners
        JsonObject spawners = new JsonObject();
        JsonArray creatureSpawns = new JsonArray();
        for (SpawnEntry entry : spawnEntries) {
            JsonObject spawn = new JsonObject();
            spawn.addProperty("type", entry.getEntityId());
            spawn.addProperty("weight", entry.getWeight());
            spawn.addProperty("minCount", entry.getMinGroupSize());
            spawn.addProperty("maxCount", entry.getMaxGroupSize());
            creatureSpawns.add(spawn);
        }
        spawners.add("creature", creatureSpawns);
        spawners.add("monster", new JsonArray());
        spawners.add("ambient", new JsonArray());
        spawners.add("underground_water_creature", new JsonArray());
        spawners.add("water_creature", new JsonArray());
        spawners.add("water_ambient", new JsonArray());
        spawners.add("misc", new JsonArray());
        root.add("spawners", spawners);
        root.add("spawn_costs", new JsonObject());

        return GSON.toJson(root);
    }

    /**
     * Saves this state as a draft file at the given path.
     * Path format: {@code saves/<world>/strata_biomes/<name>.draft.json}
     *
     * @param draftPath the path to write the draft file
     */
    public void saveDraft(Path draftPath) {
        try {
            Files.createDirectories(draftPath.getParent());
            Files.writeString(draftPath, toJson());
            clearDirty();
            StrataLogger.debug("Saved biome draft to {}", draftPath);
        } catch (IOException e) {
            StrataLogger.error("Failed to save biome draft to {}: {}", draftPath, e.getMessage());
        }
    }

    /**
     * Loads a draft state from the given path.
     *
     * @param draftPath the path to read the draft file
     * @return the loaded state, or {@code null} if the file does not exist or is invalid
     */
    public static BiomeEditorState loadDraft(Path draftPath) {
        if (!Files.exists(draftPath)) {
            return null;
        }
        try {
            String json = Files.readString(draftPath);
            BiomeEditorState state = fromJson(json);
            state.clearDirty();
            StrataLogger.debug("Loaded biome draft from {}", draftPath);
            return state;
        } catch (IOException e) {
            StrataLogger.error("Failed to load biome draft from {}: {}", draftPath, e.getMessage());
            return null;
        }
    }

    // --- Inner Classes ---

    /**
     * Represents a mob spawn entry in the biome editor.
     *
     * <p>Mirrors vanilla's spawn list format: entity type, spawn weight,
     * and minimum/maximum group size. Instances live in
     * {@link BiomeEditorState#spawnEntries}.
     */
    public static class SpawnEntry {
        private String entityId;
        private int weight;
        private int minGroupSize;
        private int maxGroupSize;

        /** No-arg constructor for Gson deserialization. */
        public SpawnEntry() {}

        /**
         * Creates a spawn entry with all fields specified.
         *
         * @param entityId     the namespaced entity type identifier
         * @param weight       spawn weight relative to other entries in the biome
         * @param minGroupSize minimum number of entities per spawn attempt
         * @param maxGroupSize maximum number of entities per spawn attempt
         */
        public SpawnEntry(String entityId, int weight, int minGroupSize, int maxGroupSize) {
            this.entityId = entityId;
            this.weight = weight;
            this.minGroupSize = minGroupSize;
            this.maxGroupSize = maxGroupSize;
        }

        /** Returns a deep copy of this spawn entry. */
        public SpawnEntry copy() {
            return new SpawnEntry(entityId, weight, minGroupSize, maxGroupSize);
        }

        /** Returns the namespaced entity type identifier. */
        public String getEntityId() { return entityId; }

        /** Sets the namespaced entity type identifier. */
        public void setEntityId(String entityId) { this.entityId = entityId; }

        /** Returns the spawn weight relative to other entries in the biome. */
        public int getWeight() { return weight; }

        /** Sets the spawn weight. Higher values increase relative spawn probability. */
        public void setWeight(int weight) { this.weight = weight; }

        /** Returns the minimum group size per spawn attempt. */
        public int getMinGroupSize() { return minGroupSize; }

        /** Sets the minimum group size per spawn attempt. */
        public void setMinGroupSize(int minGroupSize) { this.minGroupSize = minGroupSize; }

        /** Returns the maximum group size per spawn attempt. */
        public int getMaxGroupSize() { return maxGroupSize; }

        /** Sets the maximum group size per spawn attempt. */
        public void setMaxGroupSize(int maxGroupSize) { this.maxGroupSize = maxGroupSize; }
    }

    /**
     * Manages undo/redo snapshots of {@link BiomeEditorState}.
     *
     * <p>Captures a snapshot on each Layer 2 debounce fire (chunk regen) and
     * on a shorter debounce (~500ms) for Layer 1 changes. Stack depth is
     * configurable via the editor's preferences panel (range 5–100).
     *
     * <p>Fields use {@link ArrayDeque} (concrete type) rather than the {@code Deque}
     * interface so Gson can round-trip the stacks correctly during draft serialization.
     */
    public static class UndoManager {

        private ArrayDeque<BiomeEditorState> undoStack = new ArrayDeque<>();
        private ArrayDeque<BiomeEditorState> redoStack = new ArrayDeque<>();
        private int maxDepth;

        /**
         * Creates an UndoManager with the given maximum stack depth.
         *
         * @param maxDepth the maximum number of undo steps (clamped to 5–100)
         */
        public UndoManager(int maxDepth) {
            this.maxDepth = Math.max(5, Math.min(100, maxDepth));
        }

        /**
         * Captures a snapshot of the given state for undo.
         * Clears the redo stack (new changes invalidate redo history).
         */
        public void captureSnapshot(BiomeEditorState state) {
            undoStack.push(state.copy());
            if (undoStack.size() > maxDepth) {
                // Remove the oldest entry (bottom of stack)
                undoStack.removeLast();
            }
            redoStack.clear();
        }

        /**
         * Undoes the last change, returning the previous state.
         *
         * @param currentState the current state to push onto the redo stack
         * @return the previous state, or {@code null} if no undo history
         */
        public BiomeEditorState undo(BiomeEditorState currentState) {
            if (undoStack.isEmpty()) return null;
            redoStack.push(currentState.copy());
            return undoStack.pop();
        }

        /**
         * Redoes the last undone change, returning the next state.
         *
         * @param currentState the current state to push onto the undo stack
         * @return the next state, or {@code null} if no redo history
         */
        public BiomeEditorState redo(BiomeEditorState currentState) {
            if (redoStack.isEmpty()) return null;
            undoStack.push(currentState.copy());
            return redoStack.pop();
        }

        /** Returns whether there are undo steps available. */
        public boolean canUndo() { return !undoStack.isEmpty(); }

        /** Returns whether there are redo steps available. */
        public boolean canRedo() { return !redoStack.isEmpty(); }

        /** Returns the current maximum undo depth. */
        public int getMaxDepth() { return maxDepth; }

        /**
         * Updates the maximum undo depth. If the current stack exceeds
         * the new depth, oldest entries are discarded.
         */
        public void setMaxDepth(int maxDepth) {
            this.maxDepth = Math.max(5, Math.min(100, maxDepth));
            while (undoStack.size() > this.maxDepth) {
                undoStack.removeLast();
            }
        }
    }
}
