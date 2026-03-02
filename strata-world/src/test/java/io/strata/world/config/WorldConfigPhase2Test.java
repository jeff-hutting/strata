package io.strata.world.config;

import io.strata.world.editor.BiomeEditorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorldConfig} Phase 2 fields (Issue 3 and Issue 4 from the review).
 *
 * <p>Covers acceptance criteria:
 * <ul>
 *   <li>Layer 2 debounce default is 3000 ms (SPEC §7.1)</li>
 *   <li>Layer 1 debounce default is 500 ms (SPEC §7.7)</li>
 *   <li>Undo depth default is 20 (SPEC §7.7)</li>
 *   <li>Undo depth range 5–100 is enforced by {@link BiomeEditorState.UndoManager}</li>
 * </ul>
 */
class WorldConfigPhase2Test {

    private WorldConfig config;

    @BeforeEach
    void setUp() {
        config = new WorldConfig();
    }

    // -----------------------------------------------------------------------
    // Phase 2 field defaults
    // -----------------------------------------------------------------------

    @Test
    void layer2DebounceDefaultIs3000ms() {
        assertEquals(3000L, config.editorLayer2DebounceMs,
                "Layer 2 (structural) debounce must default to 3000 ms per SPEC §7.1");
    }

    @Test
    void layer1DebounceDefaultIs500ms() {
        assertEquals(500L, config.editorLayer1DebounceMs,
                "Layer 1 (visual) debounce must default to 500 ms per SPEC §7.7");
    }

    @Test
    void undoDepthDefaultIs20() {
        assertEquals(20, config.editorUndoDepth,
                "Undo stack depth must default to 20 per SPEC §7.7");
    }

    // -----------------------------------------------------------------------
    // Phase 2 field presence (reflection-based regression guard)
    // -----------------------------------------------------------------------

    @Test
    void phase2FieldsPresentInConfig() {
        Set<String> fieldNames = Arrays.stream(WorldConfig.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertTrue(fieldNames.contains("editorLayer2DebounceMs"),
                "editorLayer2DebounceMs must be a declared field (Phase 2 Issue 3 fix)");
        assertTrue(fieldNames.contains("editorLayer1DebounceMs"),
                "editorLayer1DebounceMs must be a declared field (Phase 2 Issue 3 fix)");
        assertTrue(fieldNames.contains("editorUndoDepth"),
                "editorUndoDepth must be a declared field (Phase 2 Issue 4 fix)");
    }

    // -----------------------------------------------------------------------
    // Undo depth range clamping (enforced by UndoManager, driven by WorldConfig)
    // -----------------------------------------------------------------------

    @Test
    void undoManagerClampsDepthBelow5To5() {
        // WorldConfig could be set to an out-of-range value by a user editing the config file.
        // UndoManager must clamp it.
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(3);
        assertEquals(5, um.getMaxDepth(),
                "depth below 5 must be clamped to 5 — the minimum allowed by SPEC §7.7");
    }

    @Test
    void undoManagerClampsDepthAbove100To100() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(150);
        assertEquals(100, um.getMaxDepth(),
                "depth above 100 must be clamped to 100 — the maximum allowed by SPEC §7.7");
    }

    @Test
    void undoManagerAcceptsExactLowerBound() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(5);
        assertEquals(5, um.getMaxDepth(),
                "exact lower bound (5) must be accepted without clamping");
    }

    @Test
    void undoManagerAcceptsExactUpperBound() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(100);
        assertEquals(100, um.getMaxDepth(),
                "exact upper bound (100) must be accepted without clamping");
    }

    @Test
    void undoManagerAcceptsDefaultConfigValue() {
        // Default editorUndoDepth=20 must be accepted unchanged
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(config.editorUndoDepth);
        assertEquals(20, um.getMaxDepth(),
                "UndoManager created with the default config depth (20) must not clamp");
    }

    @Test
    void undoDepthZeroClampedTo5() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(0);
        assertEquals(5, um.getMaxDepth(),
                "depth of 0 (an invalid config value) must be clamped to 5");
    }

    @Test
    void undoDepthNegativeClampedTo5() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(-10);
        assertEquals(5, um.getMaxDepth(),
                "negative depth (invalid config value) must be clamped to 5");
    }

    @Test
    void setMaxDepthAlsoClampsRange() {
        BiomeEditorState.UndoManager um = new BiomeEditorState.UndoManager(20);
        um.setMaxDepth(2);
        assertEquals(5, um.getMaxDepth(),
                "setMaxDepth() must also clamp values below 5");

        um.setMaxDepth(999);
        assertEquals(100, um.getMaxDepth(),
                "setMaxDepth() must also clamp values above 100");
    }
}
