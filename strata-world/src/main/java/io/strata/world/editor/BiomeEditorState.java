package io.strata.world.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.strata.core.util.StrataLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Holds the full state of the Biome Editor at any point in time.
 *
 * <p>Includes the biome's display name, auto-derived ID, all Layer 1 (visual)
 * and Layer 2 (structural) parameter values, and serialization to/from
 * {@code saves/<world>/strata_biomes/<name>.draft.json}.
 *
 * <p>Undo/redo is managed via an external snapshot stack — see {@link UndoManager}.
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

    /** Whether the current state has been exported since the last change. */
    private transient boolean exported = false;

    /** Whether any change has been made since the last save. */
    private transient boolean dirty = false;

    /** The active tab index (0-4). */
    private int activeTab = 0;

    // --- Constructors ---

    public BiomeEditorState() {}

    /**
     * Creates a deep copy of this state for undo snapshots.
     */
    public BiomeEditorState copy() {
        BiomeEditorState copy = new BiomeEditorState();
        copy.displayName = this.displayName;
        copy.biomeId = this.biomeId;
        copy.biomeIdOverridden = this.biomeIdOverridden;
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
        return copy;
    }

    // --- Display Name / Biome ID ---

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (!biomeIdOverridden) {
            this.biomeId = deriveId(displayName);
        }
        markDirty();
    }

    public String getBiomeId() { return biomeId; }

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

    // --- Layer 1 Accessors ---

    public int getSkyColor() { return skyColor; }
    public void setSkyColor(int skyColor) { this.skyColor = skyColor; markDirty(); }

    public int getFogColor() { return fogColor; }
    public void setFogColor(int fogColor) { this.fogColor = fogColor; markDirty(); }

    public int getWaterColor() { return waterColor; }
    public void setWaterColor(int waterColor) { this.waterColor = waterColor; markDirty(); }

    public int getWaterFogColor() { return waterFogColor; }
    public void setWaterFogColor(int waterFogColor) { this.waterFogColor = waterFogColor; markDirty(); }

    public int getGrassColor() { return grassColor; }
    public void setGrassColor(int grassColor) { this.grassColor = grassColor; markDirty(); }

    public int getFoliageColor() { return foliageColor; }
    public void setFoliageColor(int foliageColor) { this.foliageColor = foliageColor; markDirty(); }

    public boolean hasRain() { return hasRain; }
    public void setHasRain(boolean hasRain) { this.hasRain = hasRain; markDirty(); }

    public boolean hasSnow() { return hasSnow; }
    public void setHasSnow(boolean hasSnow) { this.hasSnow = hasSnow; markDirty(); }

    // --- Layer 2 Accessors ---

    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; markDirty(); }

    public float getHumidity() { return humidity; }
    public void setHumidity(float humidity) { this.humidity = humidity; markDirty(); }

    public float getContinentalness() { return continentalness; }
    public void setContinentalness(float continentalness) { this.continentalness = continentalness; markDirty(); }

    public float getErosion() { return erosion; }
    public void setErosion(float erosion) { this.erosion = erosion; markDirty(); }

    public float getWeirdness() { return weirdness; }
    public void setWeirdness(float weirdness) { this.weirdness = weirdness; markDirty(); }

    public float getDepth() { return depth; }
    public void setDepth(float depth) { this.depth = depth; markDirty(); }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; markDirty(); }

    public List<SpawnEntry> getSpawnEntries() { return spawnEntries; }
    public void setSpawnEntries(List<SpawnEntry> spawnEntries) { this.spawnEntries = spawnEntries; markDirty(); }

    // --- Metadata ---

    public int getActiveTab() { return activeTab; }
    public void setActiveTab(int activeTab) { this.activeTab = activeTab; }

    public boolean isDirty() { return dirty; }
    public boolean isExported() { return exported; }
    public void markExported() { this.exported = true; }

    private void markDirty() {
        this.dirty = true;
        this.exported = false;
    }

    public void clearDirty() { this.dirty = false; }

    // --- Serialization ---

    /**
     * Serializes this state to JSON.
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
     */
    public static class SpawnEntry {
        private String entityId;
        private int weight;
        private int minGroupSize;
        private int maxGroupSize;

        public SpawnEntry() {}

        public SpawnEntry(String entityId, int weight, int minGroupSize, int maxGroupSize) {
            this.entityId = entityId;
            this.weight = weight;
            this.minGroupSize = minGroupSize;
            this.maxGroupSize = maxGroupSize;
        }

        public SpawnEntry copy() {
            return new SpawnEntry(entityId, weight, minGroupSize, maxGroupSize);
        }

        public String getEntityId() { return entityId; }
        public void setEntityId(String entityId) { this.entityId = entityId; }

        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }

        public int getMinGroupSize() { return minGroupSize; }
        public void setMinGroupSize(int minGroupSize) { this.minGroupSize = minGroupSize; }

        public int getMaxGroupSize() { return maxGroupSize; }
        public void setMaxGroupSize(int maxGroupSize) { this.maxGroupSize = maxGroupSize; }
    }

    /**
     * Manages undo/redo snapshots of {@link BiomeEditorState}.
     *
     * <p>Captures a snapshot on each Layer 2 debounce fire (chunk regen) and
     * on a shorter debounce (~500ms) for Layer 1 changes. Stack depth is
     * configurable via the editor's preferences panel (range 5–100, default 20).
     */
    public static class UndoManager {

        private final Deque<BiomeEditorState> undoStack = new ArrayDeque<>();
        private final Deque<BiomeEditorState> redoStack = new ArrayDeque<>();
        private int maxDepth;

        /**
         * Creates an UndoManager with the given maximum stack depth.
         *
         * @param maxDepth the maximum number of undo steps (5–100)
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
                ((ArrayDeque<BiomeEditorState>) undoStack).removeLast();
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
                ((ArrayDeque<BiomeEditorState>) undoStack).removeLast();
            }
        }
    }
}
