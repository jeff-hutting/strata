package io.strata.world.editor;

import io.strata.world.editor.tabs.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

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
    private final BiomeEditorState.UndoManager undoManager;
    private final List<EditorTab> tabs;
    private int activeTabIndex = 0;

    /**
     * Creates a new BiomeEditorScreen with the given editor state.
     *
     * @param state       the current editor state
     * @param undoManager the undo/redo manager
     */
    public BiomeEditorScreen(BiomeEditorState state, BiomeEditorState.UndoManager undoManager) {
        super(Text.translatable("screen.strata_world.biome_editor"));
        this.state = state;
        this.undoManager = undoManager;
        this.tabs = List.of(
                new VisualTab(this, state),
                new TerrainTab(this, state),
                new FeaturesTab(this, state),
                new SpawnsTab(this, state),
                new ExportTab(this, state)
        );
        this.activeTabIndex = state.getActiveTab();
    }

    @Override
    protected void init() {
        super.init();
        // TODO: Initialize header bar widgets (display name field, biome ID label, buttons)
        // TODO: Initialize tab sidebar buttons
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
        BiomeEditorState previous = undoManager.undo(state);
        if (previous != null) {
            // TODO: apply previous state to the editor
        }
    }

    private void performRedo() {
        BiomeEditorState next = undoManager.redo(state);
        if (next != null) {
            // TODO: apply next state to the editor
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Returns the current editor state. */
    public BiomeEditorState getState() { return state; }

    /** Returns the undo manager. */
    public BiomeEditorState.UndoManager getUndoManager() { return undoManager; }

    /**
     * Called by the ASSET_REGISTERED event listener to refresh
     * the feature and spawn lists in the Features and Spawns tabs.
     */
    public static void notifyFeatureListUpdated() {
        // TODO: If an editor screen is currently open, refresh its feature/spawn lists
    }
}
