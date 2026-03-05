package io.strata.world.editor;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

/**
 * Singleton that tracks the active Biome Editor session.
 *
 * <p>Mixins ({@code BiomeMixin}, {@code EnvironmentAttributeMapMixin},
 * {@code MultiNoiseBiomeSourceMixin}) read this class to substitute editor
 * values during rendering and chunk generation. In a Design World, the
 * biome source mixin returns the editor's preview biome at every position.
 *
 * <p>This class has no client-only imports so it can be safely referenced from
 * common-code call sites (e.g. server-side Mixin injectors).
 */
public final class BiomeEditorSession {

    /** Fixed biome ID used for the editor preview biome in datapacks. */
    public static final String EDITOR_PREVIEW_ID = "strata_world:editor_preview";

    /** Registry key for the editor preview biome. */
    public static final RegistryKey<Biome> EDITOR_PREVIEW_KEY =
            RegistryKey.of(RegistryKeys.BIOME, Identifier.of("strata_world", "editor_preview"));

    private static volatile BiomeEditorState active = null;
    private static volatile PreviewZoneManager previewZoneManager = null;

    /**
     * Server-side biome override. When non-null, the MultiNoiseBiomeSourceMixin
     * returns this instead of the vanilla biome at every position (in Design Worlds).
     * Set after datapack reload when the editor_preview biome is registered.
     */
    private static volatile RegistryEntry<Biome> serverBiomeOverride = null;

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

    /**
     * Sets the server-side biome override used by the biome source mixin.
     * Called after datapack reload when the editor_preview biome is registered.
     */
    public static void setServerBiomeOverride(RegistryEntry<Biome> biome) {
        serverBiomeOverride = biome;
    }

    /**
     * Returns the server-side biome override, or null if not set.
     * Called by MultiNoiseBiomeSourceMixin on every biome lookup during chunk generation.
     */
    public static RegistryEntry<Biome> getServerBiomeOverride() {
        return serverBiomeOverride;
    }

    /**
     * Clears the server biome override. Called on server stop.
     */
    public static void clearServerBiomeOverride() {
        serverBiomeOverride = null;
    }
}
