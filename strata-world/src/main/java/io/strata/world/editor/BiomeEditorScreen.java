package io.strata.world.editor;

import io.strata.core.config.StrataConfigHelper;
import io.strata.core.util.StrataLogger;
import io.strata.world.config.WorldConfig;
import io.strata.world.editor.tabs.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/**
 * Full-screen Biome Editor panel using Fabric's Screen API.
 *
 * <p>Layout: a header bar with editable display name, auto-derived biome ID,
 * and action buttons (New, Load, Save, Export, Close). Below the header is
 * a five-tab panel: Visual, Terrain, Features, Spawns, Export.
 *
 * <p>The editor operates on a {@link BiomeEditorState} instance. Layer 1 changes
 * are applied instantly; Layer 2 changes trigger a debounced chunk regen via
 * {@link PreviewZoneManager}.
 *
 * @see BiomeEditorState
 * @see PreviewZoneManager
 */
public class BiomeEditorScreen extends Screen {

    /** Width of the tab sidebar in pixels. */
    private static final int TAB_SIDEBAR_WIDTH = 80;

    /** Height of the header bar in pixels. */
    private static final int HEADER_HEIGHT = 40;

    private final BiomeEditorState state;
    private final List<EditorTab> tabs;
    private int activeTabIndex = 0;

    /**
     * Creates a new BiomeEditorScreen with the given editor state.
     *
     * <p>The {@link BiomeEditorState.UndoManager} is owned by the state object
     * (see {@link BiomeEditorState#getUndoManager()}). The undo depth is
     * synchronized from {@link WorldConfig#editorUndoDepth} on construction.
     *
     * @param state the current editor state (owns the undo/redo manager)
     */
    public BiomeEditorScreen(BiomeEditorState state) {
        super(Text.translatable("screen.strata_world.biome_editor"));
        this.state = state;
        this.tabs = List.of(
                new VisualTab(this, state),
                new TerrainTab(this, state),
                new FeaturesTab(this, state),
                new SpawnsTab(this, state),
                new ExportTab(this, state)
        );
        this.activeTabIndex = state.getActiveTab();
        // Apply the configured undo depth so the manager reflects current preferences
        int configDepth = StrataConfigHelper.get(WorldConfig.class).editorUndoDepth;
        state.getUndoManager().setMaxDepth(configDepth);
    }

    @Override
    protected void init() {
        super.init();
        // TODO: Initialize header bar widgets (display name field, biome ID label, buttons)
        // TODO: Initialize tab sidebar buttons
        // TODO: Add Load button to header bar — triggers loadBiome() with a file picker
        // TODO: Initialize active tab content
        initActiveTab();
    }

    private void initActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).init(
                    TAB_SIDEBAR_WIDTH, HEADER_HEIGHT,
                    width - TAB_SIDEBAR_WIDTH, height - HEADER_HEIGHT);
        }
    }

    /**
     * Switches to the tab at the given index.
     *
     * @param tabIndex the tab index (0 = Visual, 1 = Terrain, etc.)
     */
    public void setActiveTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabs.size()) {
            this.activeTabIndex = tabIndex;
            this.state.setActiveTab(tabIndex);
            clearChildren();
            init();
        }
    }

    /**
     * Renders the editor screen: background, header bar, tab sidebar, and active tab content.
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        renderBackground(context, mouseX, mouseY, delta);

        // Draw header bar
        renderHeader(context, mouseX, mouseY);

        // Draw tab sidebar
        renderTabSidebar(context, mouseX, mouseY);

        // Draw active tab content
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY) {
        // TODO: Render display name (editable), biome ID, action buttons
        // Placeholder: draw display name text
        String displayName = state.getDisplayName().isEmpty() ? "Untitled Biome" : state.getDisplayName();
        context.drawText(textRenderer, displayName, TAB_SIDEBAR_WIDTH + 10, 10, 0xFFFFFF, true);

        String biomeId = state.getBiomeId().isEmpty() ? "strata_world:untitled" : state.getBiomeId();
        context.drawText(textRenderer, biomeId, TAB_SIDEBAR_WIDTH + 10, 24, 0xAAAAAA, false);
    }

    private void renderTabSidebar(DrawContext context, int mouseX, int mouseY) {
        // TODO: Render tab buttons in sidebar
        // Placeholder: draw tab labels
        String[] tabLabels = {"Visual", "Terrain", "Features", "Spawns", "Export"};
        for (int i = 0; i < tabLabels.length; i++) {
            int y = HEADER_HEIGHT + 5 + (i * 22);
            int color = (i == activeTabIndex) ? 0xFFFFFF : 0x888888;
            context.drawText(textRenderer, tabLabels[i], 10, y, color, false);
        }
    }

    /**
     * Handles keyboard input. Ctrl+Z triggers undo; Ctrl+Y triggers redo.
     * All other keys are delegated to the superclass.
     *
     * @param keyInput the key input event
     * @return {@code true} if the key was consumed
     */
    @Override
    public boolean keyPressed(KeyInput keyInput) {
        // Ctrl+Z = undo, Ctrl+Y = redo
        var window = MinecraftClient.getInstance().getWindow();
        boolean ctrlHeld = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrlHeld) {
            if (keyInput.key() == GLFW.GLFW_KEY_Z) {
                performUndo();
                return true;
            }
            if (keyInput.key() == GLFW.GLFW_KEY_Y) {
                performRedo();
                return true;
            }
        }
        return super.keyPressed(keyInput);
    }

    private void performUndo() {
        BiomeEditorState previous = state.getUndoManager().undo(state);
        if (previous != null) {
            // TODO: apply previous state to the editor
        }
    }

    private void performRedo() {
        BiomeEditorState next = state.getUndoManager().redo(state);
        if (next != null) {
            // TODO: apply next state to the editor
        }
    }

    /**
     * Returns {@code false} so the game continues ticking while the editor is open,
     * allowing {@link PreviewZoneManager} debounce timers to fire normally.
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Returns the current editor state. */
    public BiomeEditorState getState() { return state; }

    /**
     * Returns the undo manager for this editor session.
     * Delegates to {@link BiomeEditorState#getUndoManager()}.
     */
    public BiomeEditorState.UndoManager getUndoManager() { return state.getUndoManager(); }

    /**
     * Loads a biome from an existing JSON file into the editor state.
     *
     * <p>If there are unsaved changes, a confirmation prompt should be shown
     * before proceeding. Once confirmed, the JSON is parsed into a new
     * {@link BiomeEditorState}, the editor state is replaced, and a Reset World
     * regen is triggered to apply the new parameters in the preview zone.
     *
     * @param jsonPath the path to the biome JSON file to load
     * @todo Implement: parse JSON → populate state → unsaved-change prompt → Reset World regen (SPEC §11 Phase 2)
     */
    public void loadBiome(Path jsonPath) {
        // TODO: Implement load biome from list
        // 1. If state.isDirty() || !state.isExported(), show confirmation prompt
        // 2. Read and parse the biome JSON at jsonPath into a new BiomeEditorState
        // 3. Replace the current state's fields with the loaded values
        // 4. Trigger a Reset World regen to apply the new structural parameters
        StrataLogger.debug("loadBiome() called for path {} — not yet implemented", jsonPath);
    }

    /**
     * Called by the {@code ASSET_REGISTERED} event listener to refresh
     * the feature and spawn lists in the Features and Spawns tabs.
     */
    public static void notifyFeatureListUpdated() {
        // TODO: If an editor screen is currently open, refresh its feature/spawn lists
    }
}
