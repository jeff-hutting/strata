package io.strata.world.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for {@link BiomeEditorState} — pure Java / Gson, no Minecraft runtime needed.
 *
 * <p>Covers SPEC §7.7 (state persistence, undo/redo, draft serialization) and §7.6 (naming).
 */
class BiomeEditorStateTest {

    private BiomeEditorState state;

    @BeforeEach
    void setUp() {
        state = new BiomeEditorState();
    }

    // -----------------------------------------------------------------------
    // Serialization round-trip
    // -----------------------------------------------------------------------

    @Test
    void roundTripPreservesDisplayName() {
        state.setDisplayName("Verdant Highlands");
        BiomeEditorState restored = BiomeEditorState.fromJson(state.toJson());
        assertEquals("Verdant Highlands", restored.getDisplayName());
    }

    @Test
    void roundTripPreservesBiomeId() {
        state.setDisplayName("Verdant Highlands");
        BiomeEditorState restored = BiomeEditorState.fromJson(state.toJson());
        assertEquals("strata_world:verdant_highlands", restored.getBiomeId());
    }

    @Test
    void roundTripPreservesLayer1Colors() {
        state.setSkyColor(0x1234AB);
        state.setFogColor(0xDEADBE);
        state.setWaterColor(0xCAFEBA);
        state.setWaterFogColor(0xBEEF00);
        state.setGrassColor(0x112233);
        state.setFoliageColor(0x445566);

        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());

        assertEquals(0x1234AB, r.getSkyColor());
        assertEquals(0xDEADBE, r.getFogColor());
        assertEquals(0xCAFEBA, r.getWaterColor());
        assertEquals(0xBEEF00, r.getWaterFogColor());
        assertEquals(0x112233, r.getGrassColor());
        assertEquals(0x445566, r.getFoliageColor());
    }

    @Test
    void roundTripPreservesLayer1Weather() {
        state.setHasRain(false);
        state.setHasSnow(true);

        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());

        assertFalse(r.hasRain());
        assertTrue(r.hasSnow());
    }

    @Test
    void roundTripPreservesLayer2Parameters() {
        state.setTemperature(0.7f);
        state.setHumidity(0.4f);
        state.setContinentalness(0.6f);
        state.setErosion(-0.3f);
        state.setWeirdness(0.1f);
        state.setDepth(0.05f);

        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());

        assertEquals(0.7f, r.getTemperature(), 0.0001f);
        assertEquals(0.4f, r.getHumidity(), 0.0001f);
        assertEquals(0.6f, r.getContinentalness(), 0.0001f);
        assertEquals(-0.3f, r.getErosion(), 0.0001f);
        assertEquals(0.1f, r.getWeirdness(), 0.0001f);
        assertEquals(0.05f, r.getDepth(), 0.0001f);
    }

    @Test
    void roundTripPreservesFeaturesList() {
        state.setFeatures(List.of("minecraft:oak_trees", "strata_world:fern_cluster"));
        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());
        assertEquals(List.of("minecraft:oak_trees", "strata_world:fern_cluster"), r.getFeatures());
    }

    @Test
    void roundTripPreservesSpawnEntries() {
        BiomeEditorState.SpawnEntry entry = new BiomeEditorState.SpawnEntry("minecraft:wolf", 5, 1, 4);
        state.setSpawnEntries(List.of(entry));

        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());
        assertEquals(1, r.getSpawnEntries().size());
        BiomeEditorState.SpawnEntry re = r.getSpawnEntries().get(0);
        assertEquals("minecraft:wolf", re.getEntityId());
        assertEquals(5, re.getWeight());
        assertEquals(1, re.getMinGroupSize());
        assertEquals(4, re.getMaxGroupSize());
    }

    @Test
    void roundTripPreservesActiveTab() {
        state.setActiveTab(3);
        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());
        assertEquals(3, r.getActiveTab());
    }

    // -----------------------------------------------------------------------
    // exported field round-trip (Phase 2 review fix: Issue 9 — was transient)
    // -----------------------------------------------------------------------

    @Test
    void exportedFieldSurvivesRoundTripWhenTrue() {
        // exported is set to true via markExported()
        state.setTemperature(0.5f); // marks dirty, sets exported=false
        state.markExported();       // sets exported=true

        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());
        assertTrue(r.isExported(),
                "exported must survive serialize/deserialize — regression guard for Issue 9 fix " +
                "(field was previously marked transient, which would lose it)");
    }

    @Test
    void exportedFieldSurvivesRoundTripWhenFalse() {
        // After a change, exported resets to false
        state.setTemperature(0.5f);
        assertFalse(state.isExported());

        BiomeEditorState r = BiomeEditorState.fromJson(state.toJson());
        assertFalse(r.isExported());
    }

    // -----------------------------------------------------------------------
    // Undo stack — depth 5 eviction
    // -----------------------------------------------------------------------

    @Test
    void undoStackDepth5EvictsOldestWhenExceeded() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(5);

        // Push 6 snapshots; the 1st should be evicted once the 6th is pushed
        for (int i = 0; i < 6; i++) {
            state.setTemperature(i * 0.1f);
            um.captureSnapshot(state);
        }

        // Stack should have exactly 5 entries (one was evicted)
        // Drain the stack and count
        int count = 0;
        BiomeEditorState current = state;
        while (um.canUndo()) {
            current = um.undo(current);
            count++;
        }
        assertEquals(5, count,
                "depth-5 stack must hold exactly 5 entries; the oldest must be evicted when a 6th is pushed");
    }

    @Test
    void undoStackDepth5OldestEntryIsActuallyEvicted() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(5);

        // Push 6 snapshots with identifiable temperatures: 0.0, 0.1, 0.2, 0.3, 0.4, 0.5
        // Snapshot at 0.0 should be evicted; after full undo the oldest reachable is 0.1
        for (int i = 0; i <= 5; i++) {
            state.setTemperature(i * 0.1f);
            um.captureSnapshot(state);
        }

        // Undo all the way to the oldest snapshot — should be temperature=0.1, not 0.0
        BiomeEditorState current = state.copy();
        BiomeEditorState last = current;
        while (um.canUndo()) {
            last = um.undo(current);
            current = last;
        }
        assertEquals(0.1f, last.getTemperature(), 0.001f,
                "oldest snapshot should be temperature=0.1 (0.0 was evicted)");
    }

    // -----------------------------------------------------------------------
    // Undo stack — depth 100 no premature eviction
    // -----------------------------------------------------------------------

    @Test
    void undoStackDepth100DoesNotEvictAt50Snapshots() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(100);

        for (int i = 0; i < 50; i++) {
            state.setTemperature(i * 0.01f);
            um.captureSnapshot(state);
        }

        int count = 0;
        BiomeEditorState current = state;
        while (um.canUndo()) {
            current = um.undo(current);
            count++;
        }
        assertEquals(50, count,
                "depth-100 stack must retain all 50 snapshots without eviction");
    }

    @Test
    void undoManagerClampsDepthBelow5To5() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(3);
        assertEquals(5, um.getMaxDepth(),
                "UndoManager must clamp depths below 5 up to 5");
    }

    @Test
    void undoManagerClampsDepthAbove100To100() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(200);
        assertEquals(100, um.getMaxDepth(),
                "UndoManager must clamp depths above 100 down to 100");
    }

    @Test
    void undoManagerAcceptsExactBoundary5() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(5);
        assertEquals(5, um.getMaxDepth());
    }

    @Test
    void undoManagerAcceptsExactBoundary100() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(100);
        assertEquals(100, um.getMaxDepth());
    }

    @Test
    void setMaxDepthClampsExistingStack() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(20);
        for (int i = 0; i < 15; i++) {
            state.setTemperature(i * 0.1f);
            um.captureSnapshot(state);
        }

        // Reduce depth to 5 — only the 5 most recent entries should remain
        um.setMaxDepth(5);
        assertEquals(5, um.getMaxDepth());

        int count = 0;
        BiomeEditorState current = state;
        while (um.canUndo()) {
            current = um.undo(current);
            count++;
        }
        assertEquals(5, count,
                "setMaxDepth must evict entries beyond the new depth");
    }

    // -----------------------------------------------------------------------
    // Undo round-trip through JSON (Issue 6 fix: UndoManager is non-transient)
    // -----------------------------------------------------------------------

    @Test
    void undoStackSurvivesJsonRoundTrip() {
        BiomeEditorState.UndoManager um = state.getUndoManager();
        state.setTemperature(0.3f);
        um.captureSnapshot(state);
        state.setTemperature(0.6f);
        um.captureSnapshot(state);

        BiomeEditorState restored = BiomeEditorState.fromJson(state.toJson());
        // The restored state should have an undo manager with 2 entries
        assertTrue(restored.getUndoManager().canUndo(),
                "undo stack must survive a JSON round-trip (UndoManager must not be transient)");
    }

    // -----------------------------------------------------------------------
    // Display name → biome ID derivation (SPEC §7.6)
    // -----------------------------------------------------------------------

    @Test
    void deriveIdVerdantHighlands() {
        assertEquals("strata_world:verdant_highlands",
                BiomeEditorState.deriveId("Verdant Highlands"));
    }

    @Test
    void deriveIdAllUppercase() {
        assertEquals("strata_world:forest_highlands",
                BiomeEditorState.deriveId("FOREST HIGHLANDS"));
    }

    @Test
    void deriveIdMixedCaseMultiWord() {
        assertEquals("strata_world:crimson_badlands",
                BiomeEditorState.deriveId("Crimson Badlands"));
    }

    @Test
    void deriveIdStripsSpecialCharacters() {
        // Non-alphanumeric chars (excluding underscore) are stripped
        // "My Biome! (Beta)" → lowercase+strip → "my biome  beta" → spaces→_ → "my_biome__beta" → result
        String result = BiomeEditorState.deriveId("My Biome! (Beta)");
        assertNotNull(result);
        assertTrue(result.startsWith("strata_world:"),
                "derived ID must start with 'strata_world:' namespace");
        assertFalse(result.contains("!"),  "exclamation mark must be stripped");
        assertFalse(result.contains("("),  "parenthesis must be stripped");
        assertFalse(result.contains(")"),  "parenthesis must be stripped");
        assertFalse(result.contains(" "),  "spaces must be replaced with underscores");
    }

    @Test
    void deriveIdSingleWord() {
        assertEquals("strata_world:highlands", BiomeEditorState.deriveId("Highlands"));
    }

    @Test
    void deriveIdBlankReturnsEmpty() {
        assertEquals("", BiomeEditorState.deriveId("   "),
                "blank display name must produce an empty derived ID");
    }

    @Test
    void deriveIdNullReturnsEmpty() {
        assertEquals("", BiomeEditorState.deriveId(null),
                "null display name must produce an empty derived ID");
    }

    @Test
    void setDisplayNameAutoDerivesId() {
        state.setDisplayName("Frost Peaks");
        assertEquals("strata_world:frost_peaks", state.getBiomeId(),
                "setting displayName must auto-derive the biome ID via deriveId()");
    }

    @Test
    void manualBiomeIdOverridePreventsDerivedId() {
        state.setBiomeId("strata_world:custom_id");
        state.setDisplayName("Frost Peaks");
        // After manual override, setDisplayName must not overwrite the ID
        assertEquals("strata_world:custom_id", state.getBiomeId(),
                "once the biome ID is manually set it must not be overwritten by setDisplayName");
    }

    // -----------------------------------------------------------------------
    // Dirty flag / unsaved-change tracking
    // -----------------------------------------------------------------------

    @Test
    void isDirtyFalseOnFreshState() {
        assertFalse(state.isDirty(),
                "a freshly constructed state must not be dirty");
    }

    @Test
    void isDirtyTrueAfterTemperatureChange() {
        state.setTemperature(0.9f);
        assertTrue(state.isDirty(),
                "isDirty must return true after any Layer 2 parameter change");
    }

    @Test
    void isDirtyTrueAfterLayer1ColorChange() {
        state.setSkyColor(0xABCDEF);
        assertTrue(state.isDirty(),
                "isDirty must return true after any Layer 1 visual change");
    }

    @Test
    void isDirtyFalseAfterClearDirty() {
        state.setTemperature(0.5f);
        assertTrue(state.isDirty(), "pre-condition: must be dirty after a change");
        state.clearDirty();
        assertFalse(state.isDirty(),
                "isDirty must return false after clearDirty() — this is called during save/export");
    }

    @Test
    void markExportedSetsExportedFlagWithoutClearingDirty() {
        state.setTemperature(0.5f); // dirty=true, exported=false
        state.markExported();       // exported=true, dirty unchanged

        // Per implementation: markExported() does NOT clear dirty;
        // clearDirty() is called separately after file write
        assertTrue(state.isDirty(),
                "markExported() must not clear the dirty flag");
        assertTrue(state.isExported(),
                "markExported() must set the exported flag");
    }

    @Test
    void parameterChangeResetsExportedFlag() {
        state.setTemperature(0.5f);
        state.markExported();
        assertTrue(state.isExported(), "pre-condition: must be exported");

        state.setTemperature(0.6f); // another change
        assertFalse(state.isExported(),
                "any parameter change must reset the exported flag to false");
    }

    // -----------------------------------------------------------------------
    // copy() must exclude UndoManager (prevent circular serialization)
    // -----------------------------------------------------------------------

    @Test
    void copyExcludesUndoManager() {
        BiomeEditorState.UndoManager um = state.getUndoManager();
        state.setTemperature(0.3f);
        um.captureSnapshot(state);

        BiomeEditorState copy = state.copy();
        // copy() must not carry the undo manager (SPEC §7.7 note + implementation comment)
        // We verify this by serializing and checking no undo history survives the copy
        // (the copy's UndoManager is lazily initialized fresh with no history)
        assertFalse(copy.getUndoManager().canUndo(),
                "copy() must produce a fresh UndoManager with no undo history");
    }
}
