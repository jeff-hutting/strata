package io.strata.world.editor;

/**
 * Client-side singleton that tracks whether the Biome Editor is currently open.
 *
 * <p>Mixins ({@code BiomeMixin}, {@code EnvironmentAttributeMapMixin}) read this
 * class to decide whether to substitute Layer 1 visual values from the active
 * editor state instead of the real biome data. The overrides are applied to
 * <em>all</em> biomes while the editor is open; in practice this only affects
 * the preview world where the strata biome dominates.
 *
 * <p>This class has no client-only imports so it can be safely referenced from
 * common-code call sites (e.g. the Mixin injectors). The {@link PreviewZoneManager}
 * field is typed by reference only and is not eagerly loaded.
 *
 * <p>Call {@link #open(BiomeEditorState)} when the {@link BiomeEditorScreen} is
 * created and {@link #close()} from {@link BiomeEditorScreen#removed()}.
 */
public final class BiomeEditorSession {

    private static volatile BiomeEditorState active = null;
    private static volatile PreviewZoneManager previewZoneManager = null;

    private BiomeEditorSession() {}

    /**
     * Opens a new editor session with the given state.
     * Creates a fresh {@link PreviewZoneManager} tied to the state's undo manager.
     *
     * @param state the active editor state
     */
    public static void open(BiomeEditorState state) {
        active = state;
        previewZoneManager = new PreviewZoneManager(state, state.getUndoManager());
    }

    /**
     * Closes the current editor session, clearing both the state and preview manager.
     * Safe to call when no session is active.
     */
    public static void close() {
        active = null;
        previewZoneManager = null;
    }

    /**
     * Returns the active editor state, or {@code null} if no editor is open.
     * Called every frame by the Mixin injectors — must be allocation-free.
     */
    public static BiomeEditorState getActive() {
        return active;
    }

    /**
     * Returns the active preview zone manager, or {@code null} if no editor is open.
     * Called every client tick by the tick handler in {@link io.strata.world.StrataWorldClient}.
     */
    public static PreviewZoneManager getPreviewZoneManager() {
        return previewZoneManager;
    }

    /** Returns {@code true} if an editor session is currently active. */
    public static boolean isActive() {
        return active != null;
    }
}
